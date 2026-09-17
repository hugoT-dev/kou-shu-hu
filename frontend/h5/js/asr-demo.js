const TARGET_RATE = 16000;

const els = {
  health: document.getElementById("health"),
  live: document.getElementById("live"),
  log: document.getElementById("log"),
  status: document.getElementById("status"),
  meta: document.getElementById("meta"),
  micBtn: document.getElementById("micBtn"),
  fileInput: document.getElementById("fileInput"),
  insecureHint: document.getElementById("insecureHint"),
};

function apiOrigin() {
  if (location.protocol.startsWith("http")) return "";
  return "http://127.0.0.1:8181";
}

function wsUrl() {
  const proto = location.protocol === "https:" ? "wss:" : "ws:";
  if (location.protocol.startsWith("http")) {
    return `${proto}//${location.host}/ws/asr/stream`;
  }
  return "ws://127.0.0.1:8181/ws/asr/stream";
}

function logLine(msg) {
  const t = new Date().toLocaleTimeString();
  els.log.textContent = `[${t}] ${msg}\n` + els.log.textContent;
}

function setHop(name, on) {
  const el = document.querySelector(`[data-hop="${name}"]`);
  if (el) el.classList.toggle("on", on);
}

function downsample(input, inRate, outRate) {
  if (inRate === outRate) return input;
  const ratio = inRate / outRate;
  const newLen = Math.round(input.length / ratio);
  const result = new Float32Array(newLen);
  for (let i = 0; i < newLen; i++) {
    const idx = i * ratio;
    const i0 = Math.floor(idx);
    const i1 = Math.min(i0 + 1, input.length - 1);
    const frac = idx - i0;
    result[i] = input[i0] * (1 - frac) + input[i1] * frac;
  }
  return result;
}

function floatTo16BitPcm(float32) {
  const out = new Int16Array(float32.length);
  for (let i = 0; i < float32.length; i++) {
    const s = Math.max(-1, Math.min(1, float32[i]));
    out[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
  }
  return out.buffer;
}

async function probe() {
  try {
    const res = await fetch(apiOrigin() + "/api/asr/health");
    const body = await res.json();
    els.health.textContent = JSON.stringify(body, null, 2);
    const data = body.data || {};
    setHop("nginx", true);
    setHop("asr", body.ok === true);
    setHop("funasr", data.pcm === true && data.provider === "funasr");
    els.meta.textContent = data.pcm
      ? `引擎 ${data.provider} / ${data.model} · 将推 16k PCM`
      : `当前引擎不收 PCM（${data.provider || "未知"}），请把 YL_ASR_PROVIDER 设为 funasr`;
    logLine("GET /api/asr/health via nginx:80 → gateway → yl-asr");
  } catch (e) {
    els.health.textContent = "健康检查失败: " + e.message;
    setHop("nginx", false);
    setHop("asr", false);
    setHop("funasr", false);
    logLine("health 失败: " + e.message);
  }
}

function openAsrSocket() {
  return new Promise((resolve, reject) => {
    const url = wsUrl();
    logLine("WebSocket " + url);
    const ws = new WebSocket(url);
    ws.binaryType = "arraybuffer";
    const timer = setTimeout(() => {
      ws.close();
      reject(new Error("WebSocket 连接超时"));
    }, 8000);
    ws.onopen = () => {
      clearTimeout(timer);
      setHop("browser", true);
      setHop("nginx", true);
      setHop("asr", true);
      ws.send(JSON.stringify({ event: "start" }));
      logLine("已连接，发送 start，随后推 PCM");
      resolve(ws);
    };
    ws.onerror = () => {
      clearTimeout(timer);
      reject(new Error("WebSocket 失败（nginx /ws → 网关 → yl-asr）"));
    };
  });
}

function attachAsrMessages(ws) {
  ws.onmessage = (ev) => {
    try {
      const msg = JSON.parse(ev.data);
      if (msg.text) {
        els.live.textContent = msg.text;
        els.live.classList.add("has-text");
      }
      if (msg.event === "final") {
        els.status.textContent = "终稿";
        setHop("funasr", true);
        logLine("final: " + (msg.text || "(空)"));
      } else if (msg.event === "error") {
        els.status.textContent = "错误: " + (msg.text || "");
        logLine("error: " + msg.text);
      } else if (msg.text) {
        els.status.textContent = "识别中…";
        setHop("funasr", true);
      }
    } catch (e) {
      logLine("无法解析消息");
    }
  };
  ws.onclose = () => logLine("WebSocket 已关闭");
}

const rec = {
  ws: null,
  ctx: null,
  stream: null,
  proc: null,
};

async function startMic() {
  if (rec.ws) return;
  els.live.textContent = "正在听…";
  els.status.textContent = "申请麦克风";
  rec.ws = await openAsrSocket();
  attachAsrMessages(rec.ws);
  rec.stream = await navigator.mediaDevices.getUserMedia({
    audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true },
  });
  rec.ctx = new AudioContext();
  if (rec.ctx.state === "suspended") {
    await rec.ctx.resume();
  }
  const src = rec.ctx.createMediaStreamSource(rec.stream);
  rec.proc = rec.ctx.createScriptProcessor(4096, 1, 1);
  rec.proc.onaudioprocess = (e) => {
    if (!rec.ws || rec.ws.readyState !== 1) return;
    const f32 = e.inputBuffer.getChannelData(0);
    const ds = downsample(f32, rec.ctx.sampleRate, TARGET_RATE);
    rec.ws.send(floatTo16BitPcm(ds));
  };
  const mute = rec.ctx.createGain();
  mute.gain.value = 0;
  src.connect(rec.proc);
  rec.proc.connect(mute);
  mute.connect(rec.ctx.destination);
  els.status.textContent = "录音中 · 16k PCM → nginx:80 → yl-asr → funasr:10095";
  els.micBtn.classList.add("rec");
  els.micBtn.textContent = "松开结束";
}

async function stopMic() {
  els.micBtn.classList.remove("rec");
  els.micBtn.textContent = "按住说话";
  if (rec.proc) {
    rec.proc.disconnect();
    rec.proc = null;
  }
  if (rec.stream) {
    rec.stream.getTracks().forEach((t) => t.stop());
    rec.stream = null;
  }
  if (rec.ctx) {
    rec.ctx.close();
    rec.ctx = null;
  }
  if (rec.ws && rec.ws.readyState === 1) {
    rec.ws.send(JSON.stringify({ event: "stop" }));
    els.status.textContent = "等待终稿…";
    logLine("发送 stop，等待 FunASR 2pass 终稿");
    setTimeout(() => {
      if (rec.ws) {
        rec.ws.close();
        rec.ws = null;
      }
    }, 2500);
  } else {
    rec.ws = null;
  }
}

async function sendBuffer(float32, sampleRate) {
  const ds = downsample(float32, sampleRate, TARGET_RATE);
  const pcm = new Int16Array(floatTo16BitPcm(ds));
  const ws = await openAsrSocket();
  attachAsrMessages(ws);
  els.status.textContent = "正在推送文件 PCM…";
  const stride = 1600;
  for (let i = 0; i < pcm.length; i += stride) {
    const slice = pcm.slice(i, i + stride);
    if (ws.readyState !== 1) break;
    ws.send(slice.buffer);
    await new Promise((r) => setTimeout(r, 50));
  }
  if (ws.readyState === 1) {
    ws.send(JSON.stringify({ event: "stop" }));
    els.status.textContent = "等待终稿…";
    setTimeout(() => ws.close(), 3000);
  }
}

els.micBtn.addEventListener("pointerdown", (e) => {
  e.preventDefault();
  startMic().catch((err) => {
    els.status.textContent = err.message;
    logLine(err.message);
    rec.ws = null;
  });
});
els.micBtn.addEventListener("pointerup", (e) => {
  e.preventDefault();
  stopMic();
});
els.micBtn.addEventListener("pointerleave", () => {
  if (rec.ws) stopMic();
});
els.micBtn.addEventListener("pointercancel", () => stopMic());

els.fileInput.addEventListener("change", async () => {
  const file = els.fileInput.files && els.fileInput.files[0];
  els.fileInput.value = "";
  if (!file) return;
  try {
    const ctx = new AudioContext();
    const buf = await ctx.decodeAudioData(await file.arrayBuffer());
    const ch0 = buf.getChannelData(0);
    await ctx.close();
    els.live.textContent = "正在识别上传音频…";
    logLine("上传 " + file.name + " " + buf.sampleRate + "Hz → 重采样 16k");
    await sendBuffer(ch0, buf.sampleRate);
  } catch (e) {
    els.status.textContent = e.message;
    logLine(e.message);
  }
});

if (!window.isSecureContext) {
  els.insecureHint.hidden = false;
}

setHop("browser", true);
probe();
