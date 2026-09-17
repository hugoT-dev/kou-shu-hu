#!/usr/bin/env python3
"""paraformer-zh-streaming (online) + paraformer-zh (2pass 句尾纠错)，CPU WebSocket。"""
from __future__ import annotations

import asyncio
import http
import json
import logging
import os
import threading
from concurrent.futures import ThreadPoolExecutor

import numpy as np
import websockets
from funasr import AutoModel

HOST = os.environ.get("FUNASR_BIND", "127.0.0.1")
PORT = int(os.environ.get("FUNASR_PORT", "10095"))
TOKEN = os.environ.get("FUNASR_TOKEN", "")
ONLINE_MODEL = os.environ.get("FUNASR_ONLINE_MODEL", "paraformer-zh-streaming")
OFFLINE_MODEL = os.environ.get("FUNASR_OFFLINE_MODEL", "paraformer-zh")
DEVICE = os.environ.get("FUNASR_DEVICE", "cpu")
CHUNK_SIZE = [0, 10, 5]
CHUNK_SAMPLES = CHUNK_SIZE[1] * 960
SAMPLE_RATE = 16000

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("yl-funasr")

online_model: AutoModel | None = None
offline_model: AutoModel | None = None
pool = ThreadPoolExecutor(max_workers=max(2, os.cpu_count() or 2))


def load_models() -> None:
    global online_model, offline_model
    log.info("loading %s on %s", ONLINE_MODEL, DEVICE)
    online_model = AutoModel(model=ONLINE_MODEL, device=DEVICE, disable_update=True)
    log.info("loading %s for 2pass", OFFLINE_MODEL)
    offline_model = AutoModel(model=OFFLINE_MODEL, device=DEVICE, disable_update=True)
    log.info("models ready")


def pcm16_to_float(data: bytes) -> np.ndarray:
    if not data:
        return np.zeros((0,), dtype=np.float32)
    return np.frombuffer(data, dtype=np.int16).astype(np.float32) / 32768.0


def generate_online(chunk: np.ndarray, cache: dict, is_final: bool) -> str:
    assert online_model is not None
    res = online_model.generate(
        input=chunk,
        cache=cache,
        is_final=is_final,
        chunk_size=CHUNK_SIZE,
        encoder_chunk_look_back=4,
        decoder_chunk_look_back=1,
    )
    if not res:
        return ""
    return (res[0].get("text") or "") if isinstance(res[0], dict) else str(res[0])


def generate_offline(wav: np.ndarray, hotwords: str) -> str:
    assert offline_model is not None
    kwargs = {"input": wav, "batch_size": 1}
    if hotwords:
        kwargs["hotword"] = hotwords
    res = offline_model.generate(**kwargs)
    if not res:
        return ""
    return (res[0].get("text") or "") if isinstance(res[0], dict) else str(res[0])


def authorized(init: dict, path: str) -> bool:
    if not TOKEN:
        return True
    q = ""
    if "?" in (path or ""):
        q = path.split("?", 1)[1]
    qs_token = ""
    for part in q.split("&"):
        if part.startswith("token="):
            qs_token = part.split("=", 1)[1]
    got = init.get("token") or qs_token
    return got == TOKEN


async def handler(websocket, path=None):
    path = path or getattr(websocket, "path", "") or ""
    raw = await websocket.recv()
    if not isinstance(raw, str):
        await websocket.send(json.dumps({"event": "error", "text": "第一帧必须是 JSON 配置"}))
        return
    try:
        init = json.loads(raw)
    except json.JSONDecodeError:
        await websocket.send(json.dumps({"event": "error", "text": "JSON 无法解析"}))
        return
    if not authorized(init, path):
        await websocket.send(json.dumps({"event": "error", "text": "FunASR token 无效"}))
        return

    cache: dict = {}
    pending = bytearray()
    acc_online = ""
    full_audio = []
    sample_rate = int(init.get("audio_fs") or SAMPLE_RATE)
    hotwords = init.get("hotwords") or ""
    if isinstance(hotwords, dict):
        hotwords = " ".join(hotwords.keys())
    loop = asyncio.get_running_loop()

    async def emit(mode: str, text: str, is_final: bool) -> None:
        await websocket.send(json.dumps({"mode": mode, "text": text, "is_final": is_final}, ensure_ascii=False))

    try:
        async for message in websocket:
            speaking = True
            pcm = b""
            if isinstance(message, str):
                try:
                    msg = json.loads(message)
                except json.JSONDecodeError:
                    continue
                speaking = bool(msg.get("is_speaking", False))
            else:
                pcm = message if isinstance(message, (bytes, bytearray)) else bytes(message)

            if pcm:
                if sample_rate != SAMPLE_RATE:
                    await websocket.send(json.dumps({"event": "error", "text": "仅支持 16k PCM"}))
                    return
                pending.extend(pcm)
                while len(pending) >= CHUNK_SAMPLES * 2:
                    piece = bytes(pending[: CHUNK_SAMPLES * 2])
                    del pending[: CHUNK_SAMPLES * 2]
                    wav = pcm16_to_float(piece)
                    full_audio.append(wav)
                    text = await loop.run_in_executor(pool, generate_online, wav, cache, False)
                    if text:
                        acc_online += text
                        await emit("2pass-online", acc_online, False)

            if not speaking:
                if pending:
                    wav = pcm16_to_float(bytes(pending))
                    pending.clear()
                    if wav.size:
                        full_audio.append(wav)
                    text = await loop.run_in_executor(pool, generate_online, wav if wav.size else np.zeros(960, np.float32), cache, True)
                    if text:
                        acc_online += text
                elif acc_online:
                    await loop.run_in_executor(
                        pool,
                        generate_online,
                        np.zeros(960, dtype=np.float32),
                        cache,
                        True,
                    )
                if acc_online:
                    await emit("2pass-online", acc_online, False)
                offline_text = acc_online
                if full_audio:
                    merged = np.concatenate(full_audio)
                    try:
                        offline_text = await loop.run_in_executor(pool, generate_offline, merged, hotwords) or acc_online
                    except Exception as e:
                        log.warning("2pass offline failed: %s", e)
                await emit("2pass-offline", offline_text, True)
                return
    except websockets.exceptions.ConnectionClosed:
        log.info("client disconnected")


def process_request(*args):
    """GET /health 走普通 HTTP，避免 Docker 健康检查把 WebSocket 打成 400。"""
    path = ""
    if len(args) >= 2 and hasattr(args[1], "path"):
        path = args[1].path or ""
    elif args and isinstance(args[0], str):
        path = args[0]
    if path.split("?", 1)[0] != "/health":
        return None
    if args and hasattr(args[0], "respond"):
        return args[0].respond(http.HTTPStatus.OK, "ok\n")
    return http.HTTPStatus.OK, [], b"ok\n"


async def main() -> None:
    load_models()
    log.info("listen ws://%s:%s  health http://%s:%s/health", HOST, PORT, HOST, PORT)
    async with websockets.serve(
            handler,
            HOST,
            PORT,
            max_size=8 * 1024 * 1024,
            ping_interval=20,
            process_request=process_request,
    ):
        await asyncio.Future()


if __name__ == "__main__":
    threading.current_thread().name = "funasr-main"
    asyncio.run(main())
