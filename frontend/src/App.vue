<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { addMedia, api, confirmRecord, createRecord, dummyPhoto, mediaUrl } from "./api";
import { AsrClient } from "./asr";
import { STEPS, SAMPLES, itemTypeLabel, typeLabel } from "./samples";

const step = ref("asr");
const busy = ref(false);
const recording = ref(false);
const banner = reactive({ type: "", text: "" });
const logText = ref("就绪。建议先点「探测接口」，再「一键跑通」或逐步操作。\n");
const healthText = ref("尚未探测");
const health = ref(null);
const staff = ref([]);
const elderly = ref([]);
const staffId = ref(1);
const liveText = ref("");
const asrStatus = ref("未开始");
const audioBlob = ref(null);
const preview = ref(null);
const detail = ref(null);
const handover = ref(null);
const alerts = ref(null);
const records = ref([]);
const sampleKey = ref("abnormal");

const asr = new AsrClient({
  onText: (text) => {
    liveText.value = text;
  },
  onStatus: (status) => {
    asrStatus.value = status;
    log(status);
  },
});

const currentStaff = computed(() => staff.value.find((s) => s.id === Number(staffId.value)));
const structured = computed(() => detail.value?.structured || preview.value?.structured);
const previewRecord = computed(() => detail.value?.record || preview.value);
const audioAsset = computed(() => (detail.value?.media || []).find((m) => m.mediaType === "original_audio"));

function log(msg) {
  const t = new Date().toLocaleTimeString();
  logText.value = `[${t}] ${msg}\n` + logText.value;
}

function flash(type, text) {
  banner.type = type;
  banner.text = text;
}

function setStep(id) {
  step.value = id;
}

function parseMaybe(json) {
  if (!json) return null;
  if (typeof json === "object") return json;
  try {
    return JSON.parse(json);
  } catch {
    return null;
  }
}

function withStructured(rec) {
  if (!rec) return rec;
  return { ...rec, structured: parseMaybe(rec.structuredJson) };
}

async function probe(opts = {}) {
  try {
    const asrHealth = await api("/api/asr/health");
    health.value = asrHealth.data;
    healthText.value = JSON.stringify(asrHealth, null, 2);
    log(`GET /api/asr/health  provider=${asrHealth.data?.provider} pcm=${asrHealth.data?.pcm}`);
    const [staffRes, elderlyRes] = await Promise.all([
      api("/api/nursing/meta/staff"),
      api("/api/nursing/meta/elderly"),
    ]);
    staff.value = staffRes.data || [];
    elderly.value = elderlyRes.data || [];
    const caregiver = staff.value.find((s) => s.role === "caregiver") || staff.value[0];
    if (caregiver) staffId.value = caregiver.id;
    flash("ok", "接口可用。护理员默认 " + (caregiver?.name || staffId.value));
  } catch (e) {
    healthText.value = "探测失败: " + e.message;
    if (!opts.silent) flash("err", e.message);
    log("探测失败: " + e.message);
  }
}

async function startMic() {
  try {
    recording.value = true;
    liveText.value = "";
    await asr.startMic();
  } catch (e) {
    recording.value = false;
    flash("err", e.message);
    log(e.message);
  }
}

async function stopMic() {
  recording.value = false;
  await asr.stop();
  audioBlob.value = asr.audioBlob();
}

function useSample(key) {
  sampleKey.value = key;
  liveText.value = SAMPLES[key].text;
  asrStatus.value = "已填入样本文本（可不走麦克风）";
  log("样本文本: " + SAMPLES[key].text);
}

async function streamSample() {
  const text = SAMPLES[sampleKey.value].text;
  liveText.value = "";
  try {
    if (health.value?.pcm) {
      liveText.value = text;
      asrStatus.value = "当前引擎收 PCM，样本文本已直接填入";
      log("FunASR/PCM 引擎不走 hint，已本地填入样本文本");
      return;
    }
    await asr.startHint(text);
  } catch (e) {
    liveText.value = text;
    flash("err", e.message + "，已回退为本地样本文本");
  }
}

async function onWav(ev) {
  const file = ev.target.files && ev.target.files[0];
  ev.target.value = "";
  if (!file) return;
  try {
    await asr.sendFile(file);
  } catch (e) {
    flash("err", e.message);
  }
}

async function saveRecord() {
  const text = liveText.value.trim();
  if (!text) {
    flash("err", "请先识别或填入口述文本");
    return;
  }
  busy.value = true;
  try {
    const created = await createRecord(staffId.value, text, audioBlob.value);
    preview.value = withStructured(created.data);
    detail.value = null;
    log(`POST /api/nursing/records id=${created.data.id} type=${created.data.recordType}`);
    flash("ok", "已生成预览记录 #" + created.data.id);
    setStep("record");
    await loadDetail(created.data.id);
  } catch (e) {
    flash("err", e.message);
    log("保存记录失败: " + e.message);
  } finally {
    busy.value = false;
  }
}

async function loadDetail(id) {
  const res = await api(`/api/nursing/records/${id}`);
  detail.value = res.data;
  preview.value = withStructured(res.data.record);
  log(`GET /api/nursing/records/${id}`);
}

async function doConfirm() {
  const rec = previewRecord.value;
  if (!rec?.id) {
    flash("err", "还没有可确认的记录");
    return;
  }
  busy.value = true;
  try {
    await confirmRecord(rec.id);
    await loadDetail(rec.id);
    flash("ok", "记录已确认，原声锁定");
    log(`POST /api/nursing/records/${rec.id}/confirm`);
    setStep("confirm");
  } catch (e) {
    flash("err", e.message);
  } finally {
    busy.value = false;
  }
}

async function attachPhoto() {
  const rec = previewRecord.value;
  if (!rec?.id) {
    flash("err", "请先生成记录");
    return;
  }
  const file = await dummyPhoto();
  await addMedia(rec.id, staffId.value, "photo", file);
  await loadDetail(rec.id);
  flash("ok", "已附加现场照片");
  log(`POST /api/nursing/records/${rec.id}/media photo`);
}

async function loadHandover() {
  const res = await api(`/api/nursing/handovers/current?staffId=${staffId.value}`);
  handover.value = res.data;
  log("GET /api/nursing/handovers/current");
  setStep("handover");
}

async function submitHandover() {
  const id = handover.value?.sheet?.id;
  if (!id) {
    flash("err", "请先拉取本班清单");
    return;
  }
  const res = await api(`/api/nursing/handovers/${id}/submit`, { method: "POST" });
  handover.value = res.data;
  flash("ok", "已确认交班");
  log(`POST /api/nursing/handovers/${id}/submit`);
}

async function ackItem(id) {
  await api(`/api/nursing/handovers/items/${id}/ack`, { method: "POST" });
  log(`POST /api/nursing/handovers/items/${id}/ack`);
  await loadHandover();
  setStep("ack");
}

async function ackAll() {
  const rows = handover.value?.items || [];
  for (const row of rows) {
    if (row.item?.ackStatus !== "understood") {
      await api(`/api/nursing/handovers/items/${row.item.id}/ack`, { method: "POST" });
    }
  }
  await loadHandover();
  flash("ok", "接班条目已全部确认");
  setStep("ack");
}

async function loadAdmin() {
  const [a, r] = await Promise.all([
    api("/api/nursing/admin/alerts"),
    api("/api/nursing/admin/records"),
  ]);
  alerts.value = a.data;
  records.value = r.data || [];
  log("GET /api/nursing/admin/alerts + /admin/records");
  setStep("admin");
}

async function runHappyPath() {
  busy.value = true;
  flash("", "");
  try {
    await probe();
    liveText.value = SAMPLES.abnormal.text;
    await saveRecord();
    await attachPhoto();
    await doConfirm();
    liveText.value = SAMPLES.leftover.text;
    audioBlob.value = null;
    await saveRecord();
    await doConfirm();
    await loadHandover();
    await submitHandover();
    await ackAll();
    await loadAdmin();
    flash("ok", "主流程已跑通：异常记录 + 遗留事项 → 交班 → 接班 → 院长查询");
  } catch (e) {
    flash("err", "一键跑通失败: " + e.message);
    log("一键跑通失败: " + e.message);
  } finally {
    busy.value = false;
  }
}

onMounted(() => probe({ silent: true }));

const fields = computed(() => {
  const s = structured.value;
  if (!s) return [];
  const rows = [
    ["老人", [s.bedNo ? s.bedNo + "床" : "", s.elderlyName].filter(Boolean).join(" ")],
    ["类型", typeLabel(s.recordType)],
    ["血压", s.bp],
    ["进食", [s.food, s.amount].filter(Boolean).join(" ")],
    ["精神", s.mentalLabel],
    ["异常", s.abnormalType],
    ["开始", s.startTime],
    ["伴随", s.accompanying],
    ["处理", s.treatment],
    ["交接重点", s.handoverFocus],
    ["家属交代", s.familyMessage],
    ["备注", s.remark],
  ];
  return rows.filter(([, v]) => v);
});
</script>

<template>
  <div class="app">
    <header class="top">
      <div>
        <h1>口述护 Demo</h1>
        <p class="sub">
          第一步只覆盖主流程：说话/样本文本 → 结构化记录 → 确认留痕 → 交班清单 → 接班确认 → 院长查询。
          可点击原型在 <code>docs/prototype</code>。
        </p>
      </div>
      <div class="top-actions">
        <button class="btn ghost" type="button" @click="probe">探测接口</button>
        <button class="btn primary" type="button" :disabled="busy" @click="runHappyPath">一键跑通</button>
      </div>
    </header>

    <p v-if="banner.text" class="banner" :class="banner.type">{{ banner.text }}</p>

    <nav class="steps">
      <button
        v-for="s in STEPS"
        :key="s.id"
        class="step"
        :class="{ on: step === s.id }"
        type="button"
        @click="setStep(s.id)"
      >
        <b>{{ s.title }}</b>
        {{ s.id }}
      </button>
    </nav>

    <div class="layout">
      <main class="card">
        <div class="meta">
          <label>
            护理员
            <select v-model.number="staffId">
              <option v-for="s in staff" :key="s.id" :value="s.id">{{ s.name }} · {{ s.role }}</option>
            </select>
          </label>
          <span class="hint">在院老人 {{ elderly.length }} 人{{ currentStaff ? " · 当前 " + currentStaff.name : "" }}</span>
        </div>

        <section v-if="step === 'asr'">
          <h2>1. 语音识别</h2>
          <p class="hint">真实链路走麦克风/WAV；没有麦克风时用样本文本即可测后面的护理流程。</p>
          <div class="row">
            <button
              class="btn"
              :class="recording ? 'rec' : 'primary'"
              type="button"
              @pointerdown.prevent="startMic"
              @pointerup.prevent="stopMic"
              @pointercancel.prevent="stopMic"
            >{{ recording ? "松开结束" : "按住说话" }}</button>
            <label class="btn ghost file">
              上传 WAV
              <input type="file" accept="audio/*,.wav,.pcm" hidden @change="onWav" />
            </label>
            <button class="btn ghost" type="button" @click="streamSample">流式样本文本</button>
          </div>
          <div class="row">
            <button
              v-for="(s, key) in SAMPLES"
              :key="key"
              class="btn ghost"
              type="button"
              @click="useSample(key)"
            >{{ s.label }}</button>
          </div>
          <div class="live" :class="{ has: !!liveText }">{{ liveText || "识别结果会出现在这里" }}</div>
          <p class="hint">{{ asrStatus }}</p>
          <div class="row">
            <button class="btn primary" type="button" :disabled="busy" @click="saveRecord">生成护理记录</button>
          </div>
        </section>

        <section v-else-if="step === 'record' || step === 'confirm'">
          <h2>{{ step === 'confirm' ? '3. 确认留痕' : '2. 结构化记录' }}</h2>
          <p v-if="!previewRecord" class="hint">还没有记录。请先识别并点「生成护理记录」，或直接「一键跑通」。</p>
          <template v-else>
            <p class="hint">
              #{{ previewRecord.id }}
              <span class="tag" :class="previewRecord.recordType">{{ typeLabel(previewRecord.recordType) }}</span>
              · {{ previewRecord.status }}
            </p>
            <p class="live has">{{ previewRecord.asrText }}</p>
            <div class="fields">
              <div v-for="[k, v] in fields" :key="k" class="field" :class="{ warn: k === '异常' || k === '交接重点' }">
                <div class="k">{{ k }}</div>
                <div class="v">{{ v }}</div>
              </div>
            </div>
            <audio v-if="audioAsset" class="audio" controls :src="mediaUrl(audioAsset.id)" />
            <div class="row">
              <button class="btn ghost" type="button" :disabled="busy" @click="attachPhoto">附加现场照片</button>
              <button class="btn primary" type="button" :disabled="busy || previewRecord.status === 'confirmed'" @click="doConfirm">
                {{ previewRecord.status === 'confirmed' ? '已确认' : '确认保存' }}
              </button>
              <button class="btn ghost" type="button" @click="loadHandover">去交接</button>
            </div>
            <p class="hint">影像 {{ (detail?.media || []).length }} 个。确认后原始录音不可替换。</p>
          </template>
        </section>

        <section v-else-if="step === 'handover' || step === 'ack'">
          <h2>{{ step === 'ack' ? '5. 接班确认' : '4. 本班交接清单' }}</h2>
          <div class="row">
            <button class="btn ghost" type="button" @click="loadHandover">刷新清单</button>
            <button class="btn primary" type="button" :disabled="!handover?.sheet || handover.sheet.status === 'submitted'" @click="submitHandover">
              {{ handover?.sheet?.status === 'submitted' ? '已交班' : '确认交班' }}
            </button>
            <button class="btn ghost" type="button" :disabled="!handover?.items?.length" @click="ackAll">全部已了解</button>
          </div>
          <p v-if="handover?.sheet" class="hint">
            {{ handover.sheet.shiftDate }} {{ handover.sheet.shiftName }} · {{ handover.sheet.status }}
            · {{ handover.sheet.summary }}
          </p>
          <div v-for="row in handover?.items || []" :key="row.item.id" class="item">
            <h3>{{ itemTypeLabel(row.item.itemType) }}</h3>
            <p>{{ row.item.content }}</p>
            <p class="hint">来源记录 #{{ row.item.sourceRecordId }} · 媒体 {{ (row.media || []).length }}</p>
            <button
              class="btn"
              :class="row.item.ackStatus === 'understood' ? 'ghost' : 'primary'"
              type="button"
              :disabled="row.item.ackStatus === 'understood'"
              @click="ackItem(row.item.id)"
            >{{ row.item.ackStatus === 'understood' ? '已了解' : '标记已了解' }}</button>
          </div>
          <p v-if="handover && !(handover.items || []).length" class="hint">清单为空：先确认至少一条异常/遗留记录。</p>
        </section>

        <section v-else-if="step === 'admin'">
          <h2>6. 院长查询</h2>
          <div class="row">
            <button class="btn primary" type="button" @click="loadAdmin">刷新</button>
          </div>
          <p v-if="alerts" class="hint">已确认 {{ alerts.records }} 条，其中异常 {{ alerts.abnormal }} 条。</p>
          <div class="table-wrap">
            <table>
              <thead>
                <tr><th>ID</th><th>类型</th><th>状态</th><th>口述</th></tr>
              </thead>
              <tbody>
                <tr v-for="r in records" :key="r.id">
                  <td>{{ r.id }}</td>
                  <td><span class="tag" :class="r.recordType">{{ typeLabel(r.recordType) }}</span></td>
                  <td>{{ r.status }}</td>
                  <td>{{ r.asrText }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </main>

      <aside>
        <section class="card">
          <h2>链路状态</h2>
          <pre class="health">{{ healthText }}</pre>
          <p class="hint">浏览器 → 本页（dev :5181 / Docker :8181）→ 网关 :8080 → yl-asr / yl-nursing。</p>
        </section>
        <section class="card" style="margin-top:16px">
          <h2>事件日志</h2>
          <pre class="log">{{ logText }}</pre>
        </section>
      </aside>
    </div>
  </div>
</template>
