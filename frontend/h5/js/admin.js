const main = document.getElementById("main");
const title = document.getElementById("title");

const pages = {
  alerts() {
    title.textContent = "今日异常";
    main.innerHTML = `
      <div class="kpis">
        <div class="kpi"><div class="hint">待处理异常</div><div class="n o">2</div></div>
        <div class="kpi"><div class="hint">已交接未确认</div><div class="n r">1</div></div>
        <div class="kpi"><div class="hint">本班记录</div><div class="n g">11</div></div>
      </div>
      <div class="panel">
        <table>
          <thead>
            <tr><th>床号</th><th>老人</th><th>异常</th><th>处理</th><th>交接</th><th>留痕</th><th>确认</th></tr>
          </thead>
          <tbody>
            <tr>
              <td>5床</td><td>李爷爷</td><td>咳嗽 · 下午起</td>
              <td>护士长评估，嘱饮水</td><td>每2小时观察</td>
              <td>原声+1图1视频</td>
              <td class="tag-o">待确认</td>
            </tr>
            <tr>
              <td>3床</td><td>王奶奶</td><td>情绪低落 / 进食↓</td>
              <td>已报告护士长</td><td>夜班关注情绪</td>
              <td>原声+1图</td>
              <td>已确认</td>
            </tr>
          </tbody>
        </table>
      </div>
    `;
  },
  records() {
    title.textContent = "护理记录查询与导出";
    main.innerHTML = `
      <div class="filters">
        <input type="date" value="2026-09-14" />
        <select><option>老人 全部</option><option>3床 王奶奶</option><option>5床 李爷爷</option></select>
        <select><option>护理员 全部</option><option>李护理</option></select>
        <span class="spacer"></span>
        <button class="el-btn plain" id="q" type="button">查询</button>
        <button class="el-btn solid" id="exp" type="button">导出</button>
      </div>
      <div class="panel">
        <table>
          <thead>
            <tr><th>时间</th><th>床号/老人</th><th>类型</th><th>口述摘要</th><th>原声</th><th>影像</th><th>状态</th><th></th></tr>
          </thead>
          <tbody>
            <tr>
              <td>08:32</td><td>3床 王奶奶</td><td>常规</td>
              <td>血压140，粥大半碗，精神一般</td>
              <td><span class="link" data-play>▶ 0:09</span></td><td>—</td><td>已确认</td>
              <td><span class="link" data-detail="wang">详情</span></td>
            </tr>
            <tr>
              <td>15:18</td><td>5床 李爷爷</td><td class="tag-o">异常</td>
              <td>下午咳嗽，无发热，嘱饮水</td>
              <td><span class="link" data-play>▶ 0:18</span></td>
              <td><span class="link" data-detail="li">1图1视频</span></td><td>已确认</td>
              <td><span class="link" data-detail="li">详情</span></td>
            </tr>
            <tr>
              <td>18:40</td><td>3床 王奶奶</td><td class="tag-o">异常</td>
              <td>探视后低落，晚餐减少约1/3</td>
              <td><span class="link" data-play>▶ 0:14</span></td>
              <td><span class="link" data-detail="wang2">1图</span></td><td>已确认</td>
              <td><span class="link" data-detail="wang2">详情</span></td>
            </tr>
          </tbody>
        </table>
        <div class="pager">共 11 条　点原声即播　点影像看大图/视频　可导出记录表</div>
      </div>
    `;
    document.getElementById("exp").onclick = () => alert("已生成护理记录表.xlsx（含媒体数量说明，原声影像在系统内回放）");
    document.getElementById("q").onclick = () => alert("已按当前条件查询（示意数据不变）");
    main.querySelectorAll("[data-play]").forEach((el) => {
      el.onclick = () => alert("正在播放原始录音（示意）");
    });
    main.querySelectorAll("[data-detail]").forEach((el) => {
      el.onclick = () => openDetail(el.getAttribute("data-detail"));
    });
  },
  stats() {
    title.textContent = "交接确认率";
    main.innerHTML = `
      <div class="kpis">
        <div class="kpi"><div class="hint">今日应交班次</div><div class="n">2</div></div>
        <div class="kpi"><div class="hint">已完成接班</div><div class="n g">1</div></div>
        <div class="kpi"><div class="hint">确认率</div><div class="n o">50%</div></div>
      </div>
      <div class="panel">
        <table>
          <thead>
            <tr><th>班次</th><th>交班人</th><th>交班时间</th><th>接班人</th><th>状态</th></tr>
          </thead>
          <tbody>
            <tr>
              <td>白班 08:00-20:00</td><td>李护理</td><td>19:48</td>
              <td>—</td><td class="tag-o">已交未接</td>
            </tr>
            <tr>
              <td>夜班（昨日）20:00-08:00</td><td>王护理</td><td>07:52</td>
              <td>李护理 08:03</td><td>已接</td>
            </tr>
          </tbody>
        </table>
      </div>
    `;
  },
};

document.querySelectorAll(".menu").forEach((btn) => {
  btn.addEventListener("click", () => {
    document.querySelectorAll(".menu").forEach((b) => b.classList.remove("on"));
    btn.classList.add("on");
    pages[btn.dataset.page]();
  });
});

pages.alerts();

function openDetail(id) {
  closeDetail();
  const map = {
    li: {
      title: "5床 李爷爷 · 异常咳嗽",
      asr: "5床李爷爷，下午开始咳嗽，没发烧，护士长来看过了，让多喝水，晚上每两小时看一次",
      audio: "原声 0:18",
      media: "现场照片 1 张，短视频 8 秒",
    },
    wang: {
      title: "3床 王奶奶 · 常规",
      asr: "3床王奶奶，早上血压140，吃了大半碗粥，精神还行",
      audio: "原声 0:09",
      media: "无影像（常规记录不强制拍照）",
    },
    wang2: {
      title: "3床 王奶奶 · 情绪/进食",
      asr: "家属探视后情绪低落，晚餐进食量减少约1/3。已报告护士长。",
      audio: "原声 0:14",
      media: "现场照片 1 张",
    },
  };
  const d = map[id] || map.li;
  const mask = document.createElement("div");
  mask.className = "mask";
  mask.id = "mask";
  mask.onclick = closeDetail;
  const box = document.createElement("div");
  box.className = "drawer";
  box.id = "drawer";
  box.innerHTML = `
    <h3 style="margin-bottom:12px">${d.title}</h3>
    <p style="color:#6b7280;margin-bottom:12px">李护理　已确认　原始录音已归档</p>
    <button class="el-btn solid" type="button" id="playInDrawer">▶ ${d.audio}</button>
    <p style="margin:16px 0;line-height:1.6">${d.asr}</p>
    <p style="margin-bottom:16px">影像留痕：${d.media}</p>
    <button class="el-btn plain" type="button" id="closeDrawer">关闭</button>
  `;
  document.body.appendChild(mask);
  document.body.appendChild(box);
  document.getElementById("closeDrawer").onclick = closeDetail;
  document.getElementById("playInDrawer").onclick = () => alert("正在播放原始录音（示意）");
}

function closeDetail() {
  const m = document.getElementById("mask");
  const d = document.getElementById("drawer");
  if (m) m.remove();
  if (d) d.remove();
}
