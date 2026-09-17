export const SAMPLES = {
  routine: {
    label: "常规 · 王奶奶",
    text: "3床王奶奶，早上血压140，吃了大半碗粥，精神还行",
  },
  abnormal: {
    label: "异常 · 李爷爷",
    text: "5床李爷爷，下午开始咳嗽，没发烧，护士长来看过了，让多喝水，晚上每两小时看一次",
  },
  leftover: {
    label: "遗留 · 张奶奶",
    text: "2床张奶奶家属交代明天上午送药",
  },
};

export const STEPS = [
  { id: "asr", title: "识别" },
  { id: "record", title: "记录" },
  { id: "confirm", title: "确认" },
  { id: "handover", title: "交接" },
  { id: "ack", title: "接班" },
  { id: "admin", title: "院长" },
];

export function typeLabel(type) {
  return { routine: "常规", abnormal: "异常", leftover: "遗留", supply: "物资" }[type] || type || "-";
}

export function itemTypeLabel(type) {
  return { focus: "重点关注", leftover: "遗留事项", supply: "物资 / 设备" }[type] || type || "-";
}
