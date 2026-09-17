const TARGET_RATE = 16000;

export function wsUrl() {
  const proto = location.protocol === "https:" ? "wss:" : "ws:";
  return `${proto}//${location.host}/ws/asr/stream`;
}

export function downsample(input, inRate, outRate) {
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

export function floatTo16BitPcm(float32) {
  const out = new Int16Array(float32.length);
  for (let i = 0; i < float32.length; i++) {
    const s = Math.max(-1, Math.min(1, float32[i]));
    out[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
  }
  return out.buffer;
}

function openSocket() {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl());
    ws.binaryType = "arraybuffer";
    const timer = setTimeout(() => {
      ws.close();
      reject(new Error("WebSocket 连接超时"));
    }, 8000);
    ws.onopen = () => {
      clearTimeout(timer);
      ws.send(JSON.stringify({ event: "start" }));
      resolve(ws);
    };
    ws.onerror = () => {
      clearTimeout(timer);
      reject(new Error("WebSocket 失败（前端 → 网关 → yl-asr）"));
    };
  });
}

export class AsrClient {
  constructor({ onText, onStatus }) {
    this.onText = onText;
    this.onStatus = onStatus;
    this.ws = null;
    this.ctx = null;
    this.stream = null;
    this.proc = null;
    this.chunks = [];
    this.recorder = null;
  }

  bindMessages(ws) {
    ws.onmessage = (ev) => {
      try {
        const msg = JSON.parse(ev.data);
        if (msg.text) this.onText(msg.text, msg.event);
        if (msg.event === "final") this.onStatus("终稿");
        else if (msg.event === "error") this.onStatus("错误: " + (msg.text || ""));
        else if (msg.text) this.onStatus("识别中…");
      } catch {
        this.onStatus("无法解析识别消息");
      }
    };
    ws.onclose = () => this.onStatus("连接已关闭");
  }

  async startMic() {
    await this.stop();
    this.chunks = [];
    this.ws = await openSocket();
    this.bindMessages(this.ws);
    this.stream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true },
    });
    this.recorder = new MediaRecorder(this.stream);
    this.recorder.ondataavailable = (e) => {
      if (e.data.size) this.chunks.push(e.data);
    };
    this.recorder.start();
    this.ctx = new AudioContext();
    if (this.ctx.state === "suspended") await this.ctx.resume();
    const src = this.ctx.createMediaStreamSource(this.stream);
    this.proc = this.ctx.createScriptProcessor(4096, 1, 1);
    this.proc.onaudioprocess = (e) => {
      if (!this.ws || this.ws.readyState !== 1) return;
      const f32 = e.inputBuffer.getChannelData(0);
      const ds = downsample(f32, this.ctx.sampleRate, TARGET_RATE);
      this.ws.send(floatTo16BitPcm(ds));
    };
    const mute = this.ctx.createGain();
    mute.gain.value = 0;
    src.connect(this.proc);
    this.proc.connect(mute);
    mute.connect(this.ctx.destination);
    this.onStatus("录音中 · 16k PCM → 网关 → yl-asr");
  }

  async startHint(text) {
    await this.stop();
    this.ws = await openSocket();
    this.bindMessages(this.ws);
    this.ws.send(JSON.stringify({ event: "hint", text }));
    this.onStatus("已发送样本文本，等待 mock 流式出字…");
    this.ws.send(JSON.stringify({ event: "stop" }));
  }

  async sendFile(file) {
    await this.stop();
    const ctx = new AudioContext();
    const buf = await ctx.decodeAudioData(await file.arrayBuffer());
    const ch0 = buf.getChannelData(0);
    const ds = downsample(ch0, buf.sampleRate, TARGET_RATE);
    const pcm = new Int16Array(floatTo16BitPcm(ds));
    await ctx.close();
    this.ws = await openSocket();
    this.bindMessages(this.ws);
    this.onStatus("正在推送文件 PCM…");
    const stride = 1600;
    for (let i = 0; i < pcm.length; i += stride) {
      if (this.ws.readyState !== 1) break;
      this.ws.send(pcm.slice(i, i + stride).buffer);
      await new Promise((r) => setTimeout(r, 50));
    }
    if (this.ws.readyState === 1) {
      this.ws.send(JSON.stringify({ event: "stop" }));
      this.onStatus("等待终稿…");
    }
  }

  audioBlob() {
    if (!this.chunks.length) return null;
    return new Blob(this.chunks, { type: this.chunks[0].type || "audio/webm" });
  }

  async stop() {
    if (this.proc) {
      try {
        this.proc.disconnect();
      } catch {
        /* ignore */
      }
      this.proc = null;
    }
    if (this.recorder && this.recorder.state === "recording") {
      this.recorder.stop();
    }
    if (this.stream) {
      this.stream.getTracks().forEach((t) => t.stop());
      this.stream = null;
    }
    if (this.ctx) {
      await this.ctx.close().catch(() => {});
      this.ctx = null;
    }
    if (this.ws && this.ws.readyState === 1) {
      this.ws.send(JSON.stringify({ event: "stop" }));
      const ws = this.ws;
      this.ws = null;
      setTimeout(() => ws.close(), 2500);
      this.onStatus("等待终稿…");
    } else {
      this.ws = null;
    }
  }
}
