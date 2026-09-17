const DEMOS = {
  A: {
    asr: "3床王奶奶，早上血压140，吃了大半碗粥，精神还行",
    fields: [
      ["老人", "3床 王奶奶", false],
      ["时间", "今天 08:32（系统写入）", false],
      ["血压", "140", false],
      ["进食", "粥，约大半碗", false],
      ["精神状态", "一般（由“还行”映射）", false],
    ],
    type: "routine",
    elderly: "3床 王奶奶",
    duration: "0:09",
    needMedia: false,
  },
  B: {
    asr: "5床李爷爷，下午开始咳嗽，没发烧，护士长来看过了，让多喝水，晚上每两小时看一次",
    fields: [
      ["老人", "5床 李爷爷", false],
      ["异常类型", "咳嗽", true],
      ["开始时间", "下午（今日）", true],
      ["交接重点", "每2小时观察一次，警惕气促或发热", true],
    ],
    type: "abnormal",
    elderly: "5床 李爷爷",
    duration: "0:18",
    needMedia: true,
  },
};

const STAFF_ID = 1;

function apiBase() {
  if (location.protocol.startsWith("http")) return "";
  return "http://127.0.0.1:8082";
}

function wsUrl() {
  const proto = location.protocol === "https:" ? "wss:" : "ws:";
  const hostname = location.hostname || "127.0.0.1";
  if (location.protocol === "http:" || location.protocol === "https:") {
    if (location.port === "8082") return `${proto}//${hostname}:8081/ws/asr/stream`;
    return `${proto}//${location.host}/ws/asr/stream`;
  }
  return "ws://127.0.0.1:8081/ws/asr/stream";
}

async function api(path, options) {
  const res = await fetch(apiBase() + path, options);
  return res.json();
}

const state = {
  page: "home",
  demo: "B",
  recording: false,
  liveText: "",
  saved: [],
  currentId: null,
  handoverSubmitted: false,
  acks: {},
  photos: 0,
  videos: 0,
  playing: null,
  streamTimer: null,
};

const screen = document.getElementById("screen");
const navTitle = document.getElementById("navTitle");
const back = document.getElementById("back");
const toast = document.getElementById("toast");

function showToast(msg) {
  toast.textContent = msg;
  toast.style.display = "block";
  setTimeout(() => {
    toast.style.display = "none";
  }, 1800);
}

function go(page) {
  stopStream();
  state.page = page;
  render();
}

back.addEventListener("click", () => go("home"));

function render() {
  const needBack = state.page !== "home";
  back.classList.toggle("hidden", !needBack);
  const titles = {
    home: "口述护",
    record: "语音记录",
    preview: "确认记录",
    detail: "记录详情",
    handover: "本班交接清单",
    confirm: "接班确认",
  };
  navTitle.textContent = titles[state.page] || "口述护";
  const html = {
    home: homeHtml,
    record: recordHtml,
    preview: previewHtml,
    detail: detailHtml,
    handover: handoverHtml,
    confirm: confirmHtml,
  }[state.page];
  screen.innerHTML = html();
  bind();
}

function playerHtml(id, duration) {
  return `<div class="player" data-player="${id}">
    <button class="play-btn" type="button" data-play="${id}">▶</button>
    <div class="bar"><i></i></div>
    <span class="dur">${duration}</span>
  </div>`;
}

function homeHtml() {
  const n = state.saved.length;
  return `
    <p class="hint">白班 · 08:00-20:00</p>
    <div class="hello">你好，李护理</div>
    <p class="hint">本班负责 8 位老人，已记录 ${n} 条</p>
    <button class="big-card" data-go="record" style="--accent:#2f7d5a">
      <h3>记录</h3>
      <p>对着说话，文字边说边出</p>
    </button>
    <button class="big-card" data-go="handover" style="--accent:#345c8c">
      <h3>交接</h3>
      <p>清单带原声和现场影像</p>
    </button>
    <button class="big-card" data-go="confirm" style="--accent:#e67a35">
      <h3>确认</h3>
      <p>听原声、看影像后再接班</p>
    </button>
    <p class="foot-hint">流式识别 · 原声可播 · 关键环节可拍照</p>
  `;
}

function recordHtml() {
  const demo = DEMOS[state.demo];
  const live = state.liveText || "按住后，这里会边说边出字";
  return `
    <p class="hint" style="text-align:center">按住说话，文字流式出现，原声同步保存</p>
    <div class="example live" id="liveText">${live}</div>
    <div class="mic-wrap">
      <button class="mic" id="mic" type="button">${state.recording ? "松开结束" : "按住"}</button>
      <div class="wave" id="wave">${state.recording ? "原始录音保存中…" : "最长 60 秒"}</div>
    </div>
  `;
}

function previewHtml() {
  const demo = DEMOS[state.demo];
  const fields = demo.fields
    .map(
      ([k, v, warn]) =>
        `<div class="field ${warn ? "warn" : ""}"><div class="k">${k}</div><div class="v">${v}</div></div>`
    )
    .join("");
  const media = demo.needMedia
    ? `<p class="hint">关键环节可拍照 / 短视频留痕</p>
       <div class="media-row">
         ${state.photos ? `<button class="thumb" type="button" data-view="photo">现场照片 ×${state.photos}</button>` : ""}
         ${state.videos ? `<button class="thumb dark" type="button" data-view="video">▶ ${state.videos * 8}秒视频</button>` : ""}
         <button class="thumb add" type="button" data-add="photo">拍照</button>
         <button class="thumb add" type="button" data-add="video">短视频</button>
       </div>`
    : "";
  return `
    <div class="asr">${demo.asr}</div>
    ${playerHtml("preview", demo.duration)}
    ${fields}
    ${media}
    <div class="row-btns">
      <button class="btn ghost" data-go="record" type="button">重录</button>
      <button class="btn primary" id="save" type="button">确认保存</button>
    </div>
  `;
}

function lastSaved() {
  return state.saved[state.saved.length - 1] || DEMOS.B;
}

function detailHtml() {
  const rec = lastSaved();
  return `
    <p class="hint">${rec.elderly}　李护理</p>
    <div class="block ${rec.type === "abnormal" ? "" : "ok"}">
      <h4 class="${rec.type === "abnormal" ? "warn" : ""}">${rec.type === "abnormal" ? "异常" : "常规"} · 原始录音已归档</h4>
    </div>
    ${playerHtml("detail", rec.duration)}
    <div class="asr">${rec.asr}</div>
    <p class="hint">影像留痕</p>
    <div class="media-row">
      ${rec.photos ? `<button class="thumb" type="button" data-view="photo">现场照片 ×${rec.photos}</button>` : `<span class="hint">无照片</span>`}
      ${rec.videos ? `<button class="thumb dark" type="button" data-view="video">▶ 短视频</button>` : ""}
    </div>
    <button class="btn ghost" data-go="home" type="button" style="width:100%;margin-top:16px">关闭</button>
  `;
}

function handoverHtml() {
  const locked = state.handoverSubmitted;
  const hasAbn = state.saved.some((x) => x.type === "abnormal") || state.saved.length === 0;
  return `
    <p class="hint">白班 2026-09-14　交班人 李护理${locked ? "　已提交" : ""}</p>
    <div class="block ok"><p>本班负责 8 位老人，整体平稳。${hasAbn ? "1" : "0"} 位需重点关注。</p></div>
    <div class="block">
      <h4 class="warn">重点关注</h4>
      ${
        hasAbn
          ? `<p><strong>5床 李爷爷</strong>　下午咳嗽，无发热。每2小时观察一次。</p>
             ${playerHtml("li", "0:18")}
             <div class="media-row">
               <button class="thumb" type="button" data-view="photo">现场照片</button>
               <button class="thumb dark" type="button" data-view="video">▶ 8秒</button>
             </div>`
          : "<p>无</p>"
      }
    </div>
    <div class="block">
      <h4>遗留事项</h4>
      <p>2床张奶奶家属交代明天上午送药。可拍照药盒。</p>
      <button class="chip ghost" data-add="photo" type="button">拍照留痕</button>
    </div>
    <div class="block">
      <h4>物资 / 设备</h4>
      <p>呼叫铃正常，制氧机正常。轮椅 2 台可用。</p>
    </div>
    <button class="btn primary" id="submitHandover" ${locked ? "disabled" : ""} type="button" style="width:100%;margin-top:8px">
      ${locked ? "已确认交班" : "确认交班"}
    </button>
  `;
}

function confirmHtml() {
  const items = [
    { id: "li", title: "5床 李爷爷 · 咳嗽", desc: "每2小时观察，警惕气促或发热", media: true, duration: "0:18" },
    { id: "zhang", title: "2床 张奶奶 · 遗留", desc: "家属明天上午送药", media: false, duration: "" },
  ];
  const done = items.filter((i) => state.acks[i.id]).length;
  const all = done === items.length;
  return `
    <p class="hint">夜班接班 · 可听原声、看影像后再确认</p>
    ${items
      .map((i) => {
        const ok = !!state.acks[i.id];
        return `<div class="item">
          <h4>${i.title}</h4>
          <p>${i.desc}</p>
          ${i.media ? playerHtml(i.id, i.duration) : ""}
          ${i.media ? `<div class="media-row"><button class="thumb" type="button" data-view="photo">现场照片</button><button class="thumb dark" type="button" data-view="video">▶ 8秒</button></div>` : ""}
          <button class="chip ${ok ? "ok" : "go"}" data-ack="${i.id}" type="button">已了解</button>
          <button class="chip ghost" data-add="photo" type="button">拍照</button>
          <button class="chip ghost" data-add="video" type="button">录像</button>
        </div>`;
      })
      .join("")}
    <button class="btn primary" id="finish" ${all ? "" : "disabled"} type="button" style="width:100%">
      ${all ? "确认接班" : `全部确认后接班（${done}/2）`}
    </button>
  `;
}

function downsampleBuffer(buffer, inRate, outRate) {
  if (inRate === outRate) return buffer;
  const ratio = inRate / outRate;
  const newLen = Math.round(buffer.length / ratio);
  const result = new Float32Array(newLen);
  let offsetResult = 0;
  let offsetBuffer = 0;
  while (offsetResult < result.length) {
    const next = Math.round((offsetResult + 1) * ratio);
    let accum = 0;
    let count = 0;
    for (let i = offsetBuffer; i < next && i < buffer.length; i++) {
      accum += buffer[i];
      count += 1;
    }
    result[offsetResult] = count ? accum / count : 0;
    offsetResult += 1;
    offsetBuffer = next;
  }
  return result;
}

function floatTo16BitPcm(float32) {
  const buf = new ArrayBuffer(float32.length * 2);
  const view = new DataView(buf);
  for (let i = 0; i < float32.length; i++) {
    const s = Math.max(-1, Math.min(1, float32[i]));
    view.setInt16(i * 2, s < 0 ? s * 0x8000 : s * 0x7fff, true);
  }
  return buf;
}

function stopPcmCapture() {
  if (state.audioProc) {
    try {
      state.audioProc.disconnect();
    } catch (e) {
      /* ignore */
    }
    state.audioProc = null;
  }
  if (state.audioCtx) {
    state.audioCtx.close().catch(() => {});
    state.audioCtx = null;
  }
}

function stopStream() {
  if (state.streamTimer) {
    clearInterval(state.streamTimer);
    state.streamTimer = null;
  }
  stopPcmCapture();
  state.recording = false;
}

async function startStream() {
  stopStream();
  const demo = DEMOS[state.demo];
  state.recording = true;
  state.liveText = "";
  state.chunks = [];
  state.asrPcm = false;
  const el = document.getElementById("liveText");
  const mic = document.getElementById("mic");
  const wave = document.getElementById("wave");
  if (mic) {
    mic.classList.add("rec");
    mic.textContent = "松开结束";
  }
  if (wave) wave.textContent = "原始录音保存中…";
  try {
    const health = await api("/api/asr/health");
    state.asrPcm = !!(health && health.ok && health.data && health.data.pcm);
  } catch (e) {
    state.asrPcm = false;
  }
  try {
    state.ws = new WebSocket(wsUrl());
    state.ws.binaryType = "arraybuffer";
    state.ws.onopen = () => {
      state.ws.send(JSON.stringify({ event: "start" }));
      if (!state.asrPcm) {
        state.ws.send(JSON.stringify({ event: "hint", text: demo.asr }));
      }
    };
    state.ws.onmessage = (ev) => {
      try {
        const msg = JSON.parse(ev.data);
        if (msg.event === "error") {
          showToast(msg.text || "识别失败");
          return;
        }
        if (msg.text) {
          state.liveText = msg.text;
          if (el) el.textContent = msg.text;
        }
        if (msg.event === "final") {
          state.liveText = msg.text || state.liveText || demo.asr;
        }
      } catch (e) {
        /* ignore */
      }
    };
  } catch (e) {
    /* fallback local stream */
  }
  if (!state.asrPcm) {
    let i = 0;
    state.streamTimer = setInterval(() => {
      i += 1;
      if (!state.liveText || state.liveText.length < i) {
        state.liveText = demo.asr.slice(0, i);
        if (el) el.textContent = state.liveText;
      }
      if (i >= demo.asr.length) {
        clearInterval(state.streamTimer);
        state.streamTimer = null;
      }
    }, 45);
  }
  if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
    navigator.mediaDevices.getUserMedia({ audio: true }).then(async (stream) => {
      state.mediaStream = stream;
      state.recorder = new MediaRecorder(stream);
      state.recorder.ondataavailable = (e) => {
        if (e.data.size) state.chunks.push(e.data);
      };
      state.recorder.start();
      if (!state.asrPcm) return;
      const Ctx = window.AudioContext || window.webkitAudioContext;
      const ctx = new Ctx();
      await ctx.resume();
      const source = ctx.createMediaStreamSource(stream);
      const proc = ctx.createScriptProcessor(4096, 1, 1);
      proc.onaudioprocess = (e) => {
        if (!state.ws || state.ws.readyState !== 1) return;
        const input = e.inputBuffer.getChannelData(0);
        const resampled = downsampleBuffer(input, ctx.sampleRate, 16000);
        state.ws.send(floatTo16BitPcm(resampled));
      };
      const mute = ctx.createGain();
      mute.gain.value = 0;
      source.connect(proc);
      proc.connect(mute);
      mute.connect(ctx.destination);
      state.audioCtx = ctx;
      state.audioProc = proc;
    }).catch(() => {
      if (state.asrPcm) showToast("需要麦克风才能识别");
    });
  }
}

function endStream() {
  if (!state.recording && !state.streamTimer && !state.liveText) return;
  const demo = DEMOS[state.demo];
  stopPcmCapture();
  if (state.ws && state.ws.readyState === 1) {
    state.ws.send(JSON.stringify({ event: "stop" }));
  }
  if (state.recorder && state.recorder.state === "recording") {
    state.recorder.stop();
  }
  if (state.mediaStream) {
    state.mediaStream.getTracks().forEach((t) => t.stop());
  }
  stopStream();
  if (!state.asrPcm) {
    state.liveText = state.liveText || demo.asr;
  }
  const el = document.getElementById("liveText");
  if (el) el.textContent = state.liveText || "正在出字…";
  const wave = document.getElementById("wave");
  if (wave) wave.textContent = "正在整理字段…";
  const waitMs = state.asrPcm ? 1200 : 400;
  setTimeout(async () => {
    const fd = new FormData();
    fd.append("staffId", String(STAFF_ID));
    fd.append("asrText", state.liveText || demo.asr);
    const blob = state.chunks && state.chunks.length
      ? new Blob(state.chunks, { type: "audio/webm" })
      : new Blob([new Uint8Array([1, 2, 3, 4])], { type: "audio/webm" });
    fd.append("audio", blob, "record.webm");
    try {
      const created = await api("/api/nursing/records", { method: "POST", body: fd });
      if (created && created.ok) {
        state.currentId = created.data.id;
        state.currentType = created.data.recordType;
      }
    } catch (e) {
      showToast("后端未启动时仅本地预览");
    }
    state.photos = demo.needMedia ? 1 : 0;
    state.videos = demo.needMedia ? 1 : 0;
    go("preview");
  }, waitMs);
}

function animatePlay(id) {
  const wrap = screen.querySelector(`[data-player="${id}"]`);
  if (!wrap) {
    showToast("正在播放原声（示意）");
    return;
  }
  const bar = wrap.querySelector(".bar i");
  const btn = wrap.querySelector(".play-btn");
  btn.textContent = "■";
  bar.style.width = "0%";
  let p = 0;
  const t = setInterval(() => {
    p += 4;
    bar.style.width = Math.min(p, 100) + "%";
    if (p >= 100) {
      clearInterval(t);
      btn.textContent = "▶";
      showToast("原声播放完毕");
    }
  }, 80);
}

function bind() {
  screen.querySelectorAll("[data-go]").forEach((el) => {
    el.addEventListener("click", () => go(el.getAttribute("data-go")));
  });
  const mic = document.getElementById("mic");
  if (mic) {
    mic.addEventListener("pointerdown", (e) => {
      e.preventDefault();
      startStream();
    });
    mic.addEventListener("pointerup", (e) => {
      e.preventDefault();
      endStream();
    });
    mic.addEventListener("pointercancel", (e) => {
      e.preventDefault();
      endStream();
    });
  }
  screen.querySelectorAll("[data-play]").forEach((el) => {
    el.addEventListener("click", (e) => {
      e.stopPropagation();
      animatePlay(el.getAttribute("data-play"));
    });
  });
  screen.querySelectorAll("[data-view]").forEach((el) => {
    el.addEventListener("click", () => {
      const kind = el.getAttribute("data-view");
      showToast(kind === "video" ? "正在播放现场短视频（示意）" : "查看现场照片（示意）");
    });
  });
  screen.querySelectorAll("[data-add]").forEach((el) => {
    el.addEventListener("click", () => {
      if (el.getAttribute("data-add") === "photo") {
        state.photos += 1;
        showToast("已拍摄现场照片并留痕");
      } else {
        state.videos += 1;
        showToast("已录制 8 秒短视频并留痕");
      }
      render();
    });
  });
  const save = document.getElementById("save");
  if (save) {
    save.addEventListener("click", async () => {
      const demo = DEMOS[state.demo];
      if (state.currentId) {
        try {
          await api("/api/nursing/records/" + state.currentId + "/confirm", { method: "POST" });
        } catch (e) {
          /* ignore */
        }
      }
      state.saved.push({
        ...demo,
        id: state.currentId,
        photos: state.photos,
        videos: state.videos,
        at: new Date().toISOString(),
      });
      showToast("已保存原文、原声和影像");
      setTimeout(() => go("detail"), 400);
    });
  }
  const submit = document.getElementById("submitHandover");
  if (submit) {
    submit.addEventListener("click", async () => {
      try {
        const cur = await api("/api/nursing/handovers/current?staffId=" + STAFF_ID);
        if (cur && cur.ok && cur.data.sheet) {
          await api("/api/nursing/handovers/" + cur.data.sheet.id + "/submit", { method: "POST" });
        }
      } catch (e) {
        /* local fallback */
      }
      state.handoverSubmitted = true;
      showToast("已交班，原文/原声/影像已锁定");
      render();
    });
  }
  screen.querySelectorAll("[data-ack]").forEach((el) => {
    el.addEventListener("click", () => {
      state.acks[el.getAttribute("data-ack")] = true;
      render();
    });
  });
  const finish = document.getElementById("finish");
  if (finish) {
    finish.addEventListener("click", () => showToast("王护理于 20:06 确认接班"));
  }
}

document.getElementById("demoA").addEventListener("click", () => {
  state.demo = "A";
  showToast("已切换为常规示例，请去记录");
});
document.getElementById("demoB").addEventListener("click", () => {
  state.demo = "B";
  showToast("已切换为异常示例，请去记录");
});

render();
