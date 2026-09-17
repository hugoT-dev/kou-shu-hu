export async function api(path, options = {}) {
  let res;
  try {
    res = await fetch(path, options);
  } catch {
    throw new Error("无法连接网关，请先启动 docker compose 或本机 8080");
  }
  const contentType = res.headers.get("content-type") || "";
  if (!contentType.includes("application/json")) {
    throw new Error(`接口异常 HTTP ${res.status}（${path}）`);
  }
  const body = await res.json();
  if (!res.ok || body.ok === false) {
    throw new Error(body.message || `接口异常 HTTP ${res.status}`);
  }
  return body;
}

export function dummyAudio() {
  return new Blob([new Uint8Array([0x1a, 0x45, 0xdf, 0xa3, 0x01, 0x02, 0x03])], { type: "audio/webm" });
}

export function dummyPhoto() {
  const canvas = document.createElement("canvas");
  canvas.width = 320;
  canvas.height = 180;
  const ctx = canvas.getContext("2d");
  ctx.fillStyle = "#2f7d5a";
  ctx.fillRect(0, 0, 320, 180);
  ctx.fillStyle = "#fff";
  ctx.font = "16px Microsoft YaHei, sans-serif";
  ctx.fillText("口述护 Demo 现场留痕", 24, 96);
  return new Promise((resolve) => canvas.toBlob((blob) => resolve(blob), "image/jpeg", 0.85));
}

export async function createRecord(staffId, asrText, audioBlob) {
  const fd = new FormData();
  fd.append("staffId", String(staffId));
  fd.append("asrText", asrText);
  fd.append("audio", audioBlob || dummyAudio(), "record.webm");
  return api("/api/nursing/records", { method: "POST", body: fd });
}

export async function confirmRecord(id) {
  return api(`/api/nursing/records/${id}/confirm`, { method: "POST" });
}

export async function addMedia(recordId, staffId, mediaType, file) {
  const fd = new FormData();
  fd.append("staffId", String(staffId));
  fd.append("mediaType", mediaType);
  fd.append("file", file, file.name || (mediaType === "photo" ? "photo.jpg" : "clip.bin"));
  return api(`/api/nursing/records/${recordId}/media`, { method: "POST", body: fd });
}

export function mediaUrl(id) {
  return `/api/nursing/media/${id}`;
}
