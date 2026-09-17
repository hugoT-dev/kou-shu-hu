# -*- coding: utf-8 -*-
"""Generate UI sketches and flowcharts for 口述护 PRD."""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(r"D:\yl-care\docs\images")
FONT = r"C:\Windows\Fonts\msyh.ttc"
FONT_BD = r"C:\Windows\Fonts\msyhbd.ttc"

GREEN = (47, 125, 90)
GREEN_D = (36, 96, 70)
ORANGE = (230, 122, 53)
ORANGE_BG = (255, 243, 232)
GREEN_BG = (232, 245, 237)
BG = (247, 244, 238)
INK = (31, 42, 36)
MUTED = (107, 114, 128)
WHITE = (255, 255, 255)
LINE = (226, 221, 212)
BLUE = (43, 114, 254)
NAVY = (0, 21, 41)
RED = (214, 69, 69)


def fnt(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT_BD if bold else FONT, size)


def rr(d: ImageDraw.ImageDraw, xy, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(xy, radius=r, fill=fill, outline=outline, width=width)


def text(d, xy, s, size=16, fill=INK, bold=False, anchor="lt"):
    d.text(xy, s, font=fnt(size, bold), fill=fill, anchor=anchor)


def tw(s, size, bold=False) -> int:
    return int(fnt(size, bold).getlength(s))


def wrap(d, xy, s, size, fill, max_w, bold=False, leading=None):
    font = fnt(size, bold)
    leading = leading or int(size * 1.45)
    x, y = xy
    line = ""
    for ch in s:
        if font.getlength(line + ch) > max_w:
            d.text((x, y), line, font=font, fill=fill)
            y += leading
            line = ch
        else:
            line += ch
    if line:
        d.text((x, y), line, font=font, fill=fill)
        y += leading
    return y


def phone(w=430, h=900, title="口述护") -> tuple[Image.Image, ImageDraw.ImageDraw]:
    img = Image.new("RGB", (w + 80, h + 80), (236, 232, 224))
    d = ImageDraw.Draw(img)
    rr(d, (20, 20, w + 60, h + 60), 42, fill=(20, 20, 20))
    rr(d, (40, 40, w + 40, h + 40), 34, fill=BG)
    # status
    text(d, (68, 58), "9:41", 15, INK, True)
    d.ellipse((w, 56, w + 14, 70), fill=INK)
    d.rounded_rectangle((w - 28, 58, w - 6, 70), 3, outline=INK, width=2)
    # nav
    d.rectangle((40, 86, w + 40, 148), fill=WHITE)
    d.line((40, 148, w + 40, 148), fill=LINE, width=1)
    text(d, ((w + 80) // 2, 117), title, 20, INK, True, anchor="mm")
    return img, d


def save(img: Image.Image, name: str):
    path = OUT / name
    img.save(path, "PNG", optimize=True)
    print("saved", path)


def sketch_home():
    img, d = phone(title="口述护")
    w = 430
    text(d, (70, 172), "白班 · 08:00-20:00", 16, MUTED)
    text(d, (70, 204), "你好，李护理", 26, INK, True)
    text(d, (70, 248), "本班负责 8 位老人，已记录 5 条", 16, MUTED)

    cards = [
        ((70, 300, 430, 430), GREEN, "记录", "对着说话，自动整理成护理记录"),
        ((70, 450, 430, 580), (52, 92, 140), "交接", "查看本班次自动生成的清单"),
        ((70, 600, 430, 730), ORANGE, "确认", "接班时逐条确认重点事项"),
    ]
    for (x1, y1, x2, y2), color, title, desc in cards:
        rr(d, (x1, y1, x2, y2), 22, fill=WHITE, outline=LINE, width=1)
        d.rounded_rectangle((x1, y1, x1 + 10, y2), 0, fill=color)
        text(d, (x1 + 36, y1 + 36), title, 28, INK, True)
        wrap(d, (x1 + 36, y1 + 80), desc, 16, MUTED, 300)
    text(d, (250, 800), "字体加大 · 三步完成 · 不用填表", 14, MUTED, anchor="mm")
    save(img, "sketch_care_home.png")


def sketch_record():
    img, d = phone(title="语音记录")
    text(d, (250, 172), "按住说话，文字边说边出", 16, MUTED, anchor="mm")
    rr(d, (70, 198, 430, 430), 18, fill=WHITE)
    text(d, (86, 214), "实时识别", 13, GREEN, True)
    wrap(
        d,
        (86, 242),
        "5床李爷爷，下午开始咳嗽，没发烧，护士长来看过了…",
        18,
        INK,
        320,
    )
    text(d, (86, 390), "原始录音同步保存中  00:12", 14, MUTED)
    d.ellipse((165, 470, 335, 640), fill=RED)
    text(d, (250, 555), "松开结束", 20, WHITE, True, anchor="mm")
    text(d, (250, 670), "最长 60 秒 · 断线也保留原声", 15, MUTED, anchor="mm")
    rr(d, (140, 760, 360, 812), 20, fill=WHITE, outline=LINE)
    text(d, (250, 786), "选择老人（可选）", 16, MUTED, anchor="mm")
    save(img, "sketch_record.png")


def sketch_preview_abnormal():
    img, d = phone(title="确认记录")
    rr(d, (70, 164, 430, 248), 14, fill=WHITE)
    wrap(
        d,
        (86, 176),
        "5床李爷爷，下午开始咳嗽，没发烧，护士长来看过了，让多喝水，晚上每两小时看一次",
        15,
        INK,
        328,
    )
    rr(d, (70, 260, 430, 328), 14, fill=WHITE, outline=LINE)
    d.ellipse((86, 274, 130, 318), fill=GREEN)
    d.polygon([(100, 284), (100, 308), (120, 296)], fill=WHITE)
    rr(d, (146, 292, 300, 300), 3, fill=LINE)
    rr(d, (146, 292, 220, 300), 3, fill=GREEN)
    text(d, (318, 288), "0:12 / 0:18", 13, MUTED)
    items = [
        ("老人", "5床 李爷爷", False),
        ("异常类型", "咳嗽", True),
        ("交接重点", "每2小时观察，警惕气促或发热", True),
    ]
    y = 344
    for k, v, warn in items:
        bg = ORANGE_BG if warn else WHITE
        rr(d, (70, y, 430, y + 64), 12, fill=bg, outline=LINE)
        text(d, (86, y + 8), k, 13, MUTED)
        text(d, (86, y + 32), v, 16, ORANGE if warn else INK, True)
        y += 72
    text(d, (70, 568), "关键环节可拍照 / 短视频留痕", 14, MUTED)
    rr(d, (70, 592, 240, 700), 12, fill=(235, 232, 226), outline=LINE)
    text(d, (155, 632), "现场照片", 14, MUTED, True, "mm")
    text(d, (155, 658), "咳嗽时面色", 13, MUTED, False, "mm")
    rr(d, (256, 592, 430, 700), 12, fill=(235, 232, 226), outline=LINE)
    text(d, (343, 632), "视频 8秒", 16, INK, True, "mm")
    text(d, (343, 662), "短视频", 13, MUTED, False, "mm")
    rr(d, (70, 716, 240, 760), 12, fill=WHITE, outline=LINE)
    text(d, (155, 738), "再拍一张", 15, MUTED, True, "mm")
    rr(d, (256, 716, 430, 760), 12, fill=WHITE, outline=LINE)
    text(d, (343, 738), "再录视频", 15, MUTED, True, "mm")
    rr(d, (70, 800, 230, 858), 16, fill=WHITE, outline=LINE)
    text(d, (150, 829), "重录", 18, MUTED, True, anchor="mm")
    rr(d, (250, 800, 430, 858), 16, fill=GREEN)
    text(d, (340, 829), "确认保存", 18, WHITE, True, anchor="mm")
    save(img, "sketch_preview.png")


def sketch_handover():
    img, d = phone(title="本班交接清单")
    text(d, (70, 168), "白班 2026-09-14  ·  交班人 李护理", 14, MUTED)
    rr(d, (70, 196, 430, 268), 14, fill=GREEN_BG)
    wrap(d, (86, 210), "本班负责 8 位老人，整体平稳。2 位需重点关注。", 16, GREEN_D, 320)

    rr(d, (70, 284, 430, 520), 14, fill=WHITE, outline=LINE)
    text(d, (86, 298), "重点关注", 16, ORANGE, True)
    wrap(d, (86, 328), "5床 李爷爷  下午咳嗽，无发热。每2小时观察一次。", 15, INK, 328)
    rr(d, (86, 400, 158, 468), 8, fill=(235, 232, 226), outline=LINE)
    text(d, (122, 434), "照片", 12, MUTED, True, "mm")
    rr(d, (170, 400, 242, 468), 8, fill=(235, 232, 226), outline=LINE)
    text(d, (206, 434), "视频8s", 12, INK, True, "mm")
    rr(d, (254, 416, 410, 452), 10, fill=GREEN_BG)
    text(d, (332, 434), "听原声 0:18", 13, GREEN, True, "mm")
    wrap(d, (86, 480), "3床 王奶奶  探视后低落，晚餐减少约1/3。", 15, INK, 328)

    rr(d, (70, 536, 430, 640), 14, fill=WHITE, outline=LINE)
    text(d, (86, 550), "遗留事项", 16, INK, True)
    wrap(d, (86, 582), "2床张奶奶家属交代：明天上午来送药。可拍照药盒。", 15, INK, 328)

    rr(d, (70, 656, 430, 760), 14, fill=WHITE, outline=LINE)
    text(d, (86, 670), "物资 / 设备", 16, INK, True)
    wrap(d, (86, 702), "呼叫铃正常，制氧机正常。轮椅 2 台。异常时拍照。", 15, INK, 328)

    rr(d, (70, 800, 430, 858), 16, fill=GREEN)
    text(d, (250, 829), "确认交班", 18, WHITE, True, anchor="mm")
    save(img, "sketch_handover.png")


def sketch_confirm():
    img, d = phone(title="接班确认")
    text(d, (70, 168), "夜班接班 · 可听原声、看影像后再确认", 15, MUTED)
    cards = [
        ("5床 李爷爷 · 咳嗽", "每2小时观察，警惕气促或发热", True, True),
        ("2床 张奶奶 · 遗留", "家属明天上午送药", False, False),
    ]
    y = 200
    for title, desc, done, media in cards:
        rr(d, (70, y, 430, y + 220), 14, fill=WHITE, outline=LINE)
        text(d, (86, y + 14), title, 17, INK, True)
        wrap(d, (86, y + 46), desc, 15, MUTED, 320)
        if media:
            rr(d, (86, y + 92, 150, y + 148), 8, fill=(235, 232, 226), outline=LINE)
            text(d, (118, y + 120), "照片", 12, MUTED, True, "mm")
            rr(d, (162, y + 108, 300, y + 140), 10, fill=GREEN_BG)
            text(d, (231, y + 124), "听原声", 13, GREEN, True, "mm")
        if done:
            rr(d, (86, y + 164, 186, y + 200), 12, fill=GREEN_BG)
            text(d, (136, y + 182), "已了解", 14, GREEN, True, "mm")
        else:
            rr(d, (86, y + 164, 186, y + 200), 12, fill=ORANGE)
            text(d, (136, y + 182), "已了解", 14, WHITE, True, "mm")
        rr(d, (198, y + 164, 300, y + 200), 12, fill=WHITE, outline=LINE)
        text(d, (249, y + 182), "拍照", 14, MUTED, True, "mm")
        rr(d, (312, y + 164, 414, y + 200), 12, fill=WHITE, outline=LINE)
        text(d, (363, y + 182), "录像", 14, MUTED, True, "mm")
        y += 236
    rr(d, (70, 800, 430, 858), 16, fill=(180, 186, 176))
    text(d, (250, 829), "全部确认后接班（1/2）", 16, WHITE, True, anchor="mm")
    save(img, "sketch_confirm.png")


def sketch_detail():
    img, d = phone(title="记录详情")
    text(d, (70, 168), "5床 李爷爷  ·  15:18  ·  李护理", 14, MUTED)
    rr(d, (70, 200, 430, 268), 14, fill=ORANGE_BG)
    text(d, (86, 214), "异常 · 咳嗽", 16, ORANGE, True)
    text(d, (86, 242), "已确认  ·  原始录音已归档", 14, MUTED)
    rr(d, (70, 284, 430, 372), 14, fill=WHITE, outline=LINE)
    d.ellipse((86, 304, 142, 360), fill=GREEN)
    d.polygon([(104, 318), (104, 346), (128, 332)], fill=WHITE)
    rr(d, (162, 328, 310, 336), 3, fill=LINE)
    rr(d, (162, 328, 240, 336), 3, fill=GREEN)
    text(d, (330, 324), "0:12 / 0:18", 13, MUTED)
    rr(d, (70, 388, 430, 500), 14, fill=WHITE)
    wrap(d, (86, 404), "下午开始咳嗽，没发烧，护士长来看过了，让多喝水，晚上每两小时看一次", 16, INK, 320)
    text(d, (70, 520), "影像留痕", 16, INK, True)
    rr(d, (70, 552, 230, 700), 12, fill=(235, 232, 226), outline=LINE)
    text(d, (150, 612), "现场照片", 15, MUTED, True, "mm")
    rr(d, (246, 552, 430, 700), 12, fill=(40, 40, 40))
    text(d, (338, 612), "播放视频", 15, WHITE, True, "mm")
    text(d, (338, 642), "8 秒", 13, (200, 200, 200), False, "mm")
    rr(d, (70, 800, 430, 858), 16, fill=WHITE, outline=LINE)
    text(d, (250, 829), "关闭", 18, MUTED, True, anchor="mm")
    save(img, "sketch_detail.png")


def sketch_admin_alerts():
    W, H = 1440, 900
    img = Image.new("RGB", (W, H), (244, 246, 250))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 220, H), fill=NAVY)
    text(d, (24, 28), "口述护 院长端", 18, WHITE, True)
    menus = [("今日异常", True), ("护理记录", False), ("交接确认率", False), ("导出", False), ("基础设置", False)]
    y = 90
    for name, on in menus:
        if on:
            rr(d, (12, y, 208, y + 44), 6, fill=BLUE)
        text(d, (32, y + 12), name, 15, WHITE, True)
        y += 52
    d.rectangle((220, 0, W, 48), fill=WHITE)
    d.line((220, 48, W, 48), fill=(230, 232, 237))
    text(d, (240, 14), "今日异常", 16, INK, True)
    text(d, (1320, 14), "张院长", 14, MUTED)

    # kpi
    kpis = [("待处理异常", "2", ORANGE), ("已交接未确认", "1", RED), ("本班记录", "11", GREEN)]
    x = 248
    for title, val, color in kpis:
        rr(d, (x, 72, x + 360, 168), 8, fill=WHITE)
        text(d, (x + 24, 88), title, 14, MUTED)
        text(d, (x + 24, 116), val, 32, color, True)
        x += 380

    # table
    rr(d, (248, 192, 1412, 860), 8, fill=WHITE)
    d.rectangle((248, 192, 1412, 244), fill=(245, 248, 255))
    d.line((248, 244, 1412, 244), fill=BLUE, width=2)
    headers = ["床号", "老人", "异常", "处理", "交接", "确认"]
    xs = [270, 360, 520, 780, 1040, 1240]
    for i, h in enumerate(headers):
        text(d, (xs[i], 208), h, 14, INK, True)
    rows = [
        ("5床", "李爷爷", "咳嗽 · 下午起", "护士长评估，嘱饮水", "每2小时观察", "待确认"),
        ("3床", "王奶奶", "情绪低落 / 进食↓", "已报告护士长", "夜班关注情绪", "已确认"),
    ]
    y = 260
    for i, row in enumerate(rows):
        if i % 2 == 1:
            d.rectangle((248, y - 12, 1412, y + 52), fill=(250, 251, 253))
        for j, cell in enumerate(row):
            color = ORANGE if (j == 5 and cell == "待确认") else INK
            text(d, (xs[j], y), cell, 14, color, bold=(j == 5))
        y += 56
    save(img, "sketch_admin_alerts.png")


def sketch_admin_export():
    W, H = 1440, 900
    img = Image.new("RGB", (W, H), (244, 246, 250))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 220, H), fill=NAVY)
    text(d, (24, 28), "口述护 院长端", 18, WHITE, True)
    for i, name in enumerate(["今日异常", "护理记录", "交接确认率", "导出", "基础设置"]):
        y = 90 + i * 52
        if name == "护理记录":
            rr(d, (12, y, 208, y + 44), 6, fill=BLUE)
        text(d, (32, y + 12), name, 15, WHITE, True)
    d.rectangle((220, 0, W, 48), fill=WHITE)
    text(d, (240, 14), "护理记录查询与导出", 16, INK, True)

    rr(d, (248, 72, 1412, 148), 8, fill=WHITE)
    rr(d, (268, 92, 488, 128), 4, fill=(245, 247, 250), outline=(220, 223, 230))
    text(d, (280, 100), "日期  2026-09-14", 14, MUTED)
    rr(d, (508, 92, 728, 128), 4, fill=(245, 247, 250), outline=(220, 223, 230))
    text(d, (520, 100), "老人  全部", 14, MUTED)
    rr(d, (748, 92, 968, 128), 4, fill=(245, 247, 250), outline=(220, 223, 230))
    text(d, (760, 100), "护理员  全部", 14, MUTED)
    rr(d, (1168, 92, 1268, 128), 4, outline=BLUE)
    text(d, (1218, 110), "查询", 14, BLUE, True, anchor="mm")
    rr(d, (1284, 92, 1392, 128), 4, fill=BLUE)
    text(d, (1338, 110), "导出", 14, WHITE, True, anchor="mm")

    rr(d, (248, 168, 1412, 860), 8, fill=WHITE)
    d.rectangle((248, 168, 1412, 220), fill=(245, 248, 255))
    d.line((248, 220, 1412, 220), fill=BLUE, width=2)
    headers = ["时间", "床号/老人", "类型", "口述摘要", "原声", "影像", "状态"]
    xs = [268, 400, 560, 680, 1040, 1160, 1288]
    for i, h in enumerate(headers):
        text(d, (xs[i], 184), h, 14, INK, True)
    rows = [
        ("08:32", "3床 王奶奶", "常规", "血压140，粥大半碗", "听 0:09", "—", "已确认"),
        ("15:18", "5床 李爷爷", "异常", "下午咳嗽，嘱饮水", "听 0:18", "1图1视频", "已确认"),
        ("18:40", "3床 王奶奶", "异常", "探视后低落，进食↓", "听 0:14", "1图", "已确认"),
    ]
    y = 244
    for i, row in enumerate(rows):
        if i % 2 == 1:
            d.rectangle((248, y - 16, 1412, y + 48), fill=(250, 251, 253))
        for j, cell in enumerate(row):
            col = ORANGE if cell == "异常" else INK
            text(d, (xs[j], y), cell, 14, col, bold=(cell == "异常"))
        y += 56
    text(d, (268, 820), "共 11 条  ·  点原声即播  ·  点影像看大图/视频  ·  可导出记录表", 13, MUTED)
    save(img, "sketch_admin_export.png")


def _box(d, xy, title, fill, tw_=220, th=56):
    x, y = xy
    rr(d, (x, y, x + tw_, y + th), 10, fill=fill)
    text(d, (x + tw_ // 2, y + th // 2), title, 15, WHITE if fill != (240, 242, 245) else INK, True, "mm")
    return x + tw_ // 2, y + th, x + tw_, y


def _arrow(d, x1, y1, x2, y2):
    d.line((x1, y1, x2, y2), fill=(90, 96, 104), width=2)
    # simple chevron
    if abs(x2 - x1) < 4:  # vertical
        d.polygon([(x2 - 6, y2 - 10), (x2 + 6, y2 - 10), (x2, y2)], fill=(90, 96, 104))
    else:
        d.polygon([(x2 - 10, y2 - 6), (x2 - 10, y2 + 6), (x2, y2)], fill=(90, 96, 104))


def flow_overall():
    img = Image.new("RGB", (1600, 720), WHITE)
    d = ImageDraw.Draw(img)
    text(d, (800, 36), "口述护总体业务路径", 22, INK, True, "mm")
    nodes = [
        ((80, 120), "微信登录\n识别班次与角色", GREEN),
        ((320, 120), "按住流式口述\n同步存原声", GREEN),
        ((560, 120), "预览+播放\n可拍照录像", BLUE),
        ((800, 120), "班末自动汇总\n交接清单", ORANGE),
        ((1040, 120), "交班提交\n关键项留痕", GREEN),
        ((1280, 120), "接班确认\n听原声看影像", GREEN_D),
    ]
    centers = []
    for (x, y), title, color in nodes:
        rr(d, (x, y, x + 200, y + 88), 12, fill=color)
        lines = title.split("\n")
        text(d, (x + 100, y + 28), lines[0], 15, WHITE, True, "mm")
        if len(lines) > 1:
            text(d, (x + 100, y + 56), lines[1], 13, WHITE, False, "mm")
        centers.append((x + 200, y + 44, x, y + 44))
    for i in range(len(centers) - 1):
        _arrow(d, centers[i][0] + 8, centers[i][1], centers[i + 1][2] - 8, centers[i + 1][3])

    rr(d, (200, 300, 700, 620), 14, fill=(247, 250, 255), outline=(210, 220, 240))
    text(d, (450, 328), "护理员路径（不做填表）", 16, BLUE, True, "mm")
    steps = ["边说边出字，原声同时落盘", "异常标橙，可拍照或短视频留痕", "清单整理好，关键项能回放原声"]
    yy = 370
    for s in steps:
        rr(d, (230, yy, 670, yy + 56), 8, fill=WHITE, outline=LINE)
        text(d, (250, yy + 16), s, 15, INK)
        yy += 70

    rr(d, (900, 300, 1400, 620), 14, fill=(255, 248, 242), outline=(240, 214, 190))
    text(d, (1150, 328), "院长路径（不增加整理负担）", 16, ORANGE, True, "mm")
    steps = ["看异常、处理、交接是否确认", "点开即可播放原声、查看影像", "按日期导出检查用表"]
    yy = 370
    for s in steps:
        rr(d, (930, yy, 1370, yy + 56), 8, fill=WHITE, outline=LINE)
        text(d, (950, yy + 16), s, 15, INK)
        yy += 70
    save(img, "flow_overall.png")


def flow_voice():
    img = Image.new("RGB", (1500, 820), WHITE)
    d = ImageDraw.Draw(img)
    text(d, (750, 36), "语音护理记录流程", 22, INK, True, "mm")

    def node(x, y, t, c=GREEN, w=200, h=70):
        rr(d, (x, y, x + w, y + h), 10, fill=c)
        lines = t.split("\n")
        if len(lines) == 1:
            text(d, (x + w // 2, y + h // 2), t, 15, WHITE if c != (240, 242, 245) else INK, True, "mm")
        else:
            text(d, (x + w // 2, y + 22), lines[0], 15, WHITE, True, "mm")
            text(d, (x + w // 2, y + 48), lines[1], 13, WHITE, False, "mm")
        return x + w // 2, y + h, x, y + h // 2, x + w, y + h // 2

    a = node(80, 100, "进入记录页")
    b = node(360, 100, "按住口述")
    c = node(640, 100, "流式出字\n同步写原声", GREEN)
    e = node(920, 100, "松开出终稿")
    f = node(1200, 100, "结构化解析")
    for p, n in [(a, b), (b, c), (c, e), (e, f)]:
        _arrow(d, p[4] + 6, p[3], n[2] - 6, n[3])

    g = node(1200, 250, "老人匹配", BLUE)
    _arrow(d, f[0], f[1] + 6, g[0], g[1] - 70 + 6)

    h = node(860, 250, "预览结构化结果\n大字+颜色标注", GREEN)
    # left from g
    d.line((g[2], g[3], h[4] + 6, h[3]), fill=(90, 96, 104), width=2)
    d.polygon([(h[4] + 16, h[3] - 6), (h[4] + 16, h[3] + 6), (h[4] + 6, h[3])], fill=(90, 96, 104))

    i = node(500, 250, "手动选择床位/姓名", ORANGE)
    text(d, (1180, 220), "未匹配", 13, ORANGE, True, "mm")
    d.line((1200, 250, 700, 250), fill=ORANGE, width=2)
    # actually g top to i - skip messy. Use labels.

    j = node(80, 430, "修改字段")
    k = node(360, 430, "确认保存")
    m = node(640, 430, "重录")
    n = node(920, 430, "写入老人时间线\n并标记异常/交接", GREEN_D, w=240)
    o = node(1240, 430, "结束", (90, 96, 104), w=160)

    _arrow(d, h[0] - 80, h[1] + 6, k[0], k[1] - 70)
    _arrow(d, k[4] + 6, k[3], n[2] - 6, n[3])
    _arrow(d, n[4] + 6, n[3], o[2] - 6, o[3])
    d.line((h[2], h[3], 180, 285), fill=(90, 96, 104), width=2)
    d.line((180, 285, 180, 430), fill=(90, 96, 104), width=2)
    d.polygon([(174, 420), (186, 420), (180, 430)], fill=(90, 96, 104))
    d.line((h[4], h[3], 740, 285), fill=(90, 96, 104), width=2)
    d.line((740, 285, 740, 430), fill=(90, 96, 104), width=2)
    d.polygon([(734, 420), (746, 420), (740, 430)], fill=(90, 96, 104))

    rr(d, (80, 560, 1420, 760), 12, fill=(247, 250, 255), outline=(210, 220, 240))
    text(d, (100, 580), "规则要点", 16, BLUE, True)
    notes = [
        "识别必须流式：说话过程中屏幕同步出字；原始 PCM/音频同时写入对象存储，作为不可替换原件。",
        "预览页必须能播放刚录的原声。识别失败仍保留原声，可重录；结构化失败标记“待整理”，不丢音频。",
        "记录一经确认不可删除或覆盖原声与影像。更正只能追加，满足“不得涂改、可追溯”。",
        "异常、交接、接班等关键环节可附加照片和短视频；常规生命体征不必强制拍。",
    ]
    yy = 620
    for t in notes:
        text(d, (100, yy), "·  " + t, 14, INK)
        yy += 32
    save(img, "flow_voice.png")


def flow_handover():
    img = Image.new("RGB", (1500, 780), WHITE)
    d = ImageDraw.Draw(img)
    text(d, (750, 36), "交接班生成与确认流程", 22, INK, True, "mm")

    def node(x, y, t, c=GREEN, w=210, h=74):
        rr(d, (x, y, x + w, y + h), 10, fill=c)
        lines = t.split("\n")
        text(d, (x + w // 2, y + (22 if len(lines) > 1 else 37)), lines[0], 15, WHITE, True, "mm")
        if len(lines) > 1:
            text(d, (x + w // 2, y + 50), lines[1], 13, WHITE, False, "mm")
        return x + w, y + h // 2

    xs = [70, 330, 590, 850, 1110]
    titles = [
        ("班末前提醒\n汇总本班记录", GREEN),
        ("生成四段清单\n整体/重点/遗留/物资", BLUE),
        ("交班人核对补充\n可拍照录像", GREEN),
        ("接班人听原声看影像\n逐条已了解", ORANGE),
        ("全部确认后记\n人员+时间", GREEN_D),
    ]
    rights = []
    for i, (t, c) in enumerate(titles):
        r = node(xs[i], 110, t, c)
        rights.append(r)
    for i in range(4):
        _arrow(d, rights[i][0] + 8, rights[i][1], xs[i + 1] - 8, 147)

    rr(d, (70, 260, 720, 500), 12, fill=ORANGE_BG, outline=(240, 214, 190))
    text(d, (90, 280), "自动提取规则", 16, ORANGE, True)
    for i, t in enumerate(
        [
            "异常 / 变化 / 医嘱 / 家属交代 → 重点关注",
            "明天、送药、待办、未完成 → 遗留事项",
            "呼叫铃、轮椅、制氧机等 → 物资设备",
            "无上述信息 → 计入整体平稳人数",
        ]
    ):
        text(d, (90, 328 + i * 36), "·  " + t, 15, INK)

    rr(d, (760, 260, 1430, 500), 12, fill=GREEN_BG, outline=(190, 220, 200))
    text(d, (780, 280), "确认与追溯", 16, GREEN, True)
    for i, t in enumerate(
        [
            "重点关注、遗留事项必须逐条点“已了解”",
            "物资设备可一次确认，有异常则改写",
            "未全部确认不可完成接班（不清不接）",
            "系统记录：谁交、谁接、何时、原声、照片、视频",
        ]
    ):
        text(d, (780, 328 + i * 36), "·  " + t, 15, INK)

    rr(d, (70, 530, 1430, 730), 12, fill=(247, 250, 255), outline=(210, 220, 240))
    text(d, (90, 550), "超时与院方可见", 16, BLUE, True)
    for i, t in enumerate(
        [
            "接班后 30 分钟仍有未确认条目，向接班人推送提醒；超过 1 小时未完成接班，院长端“今日异常”出现“交接未确认”。",
            "院长不需要重新整理清单，只看确认率和未关闭异常。导出时交接班记录簿带双方确认时间，作为检查与纠纷追溯依据。",
            "交班提交后本班清单锁定，不得删改原文、原声和影像；接班人只能追加语音、照片或短视频备注。",
        ]
    ):
        wrap(d, (90, 590 + i * 42), t, 15, INK, 1300)
    save(img, "flow_handover.png")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    sketch_home()
    sketch_record()
    sketch_preview_abnormal()
    sketch_handover()
    sketch_confirm()
    sketch_detail()
    sketch_admin_alerts()
    sketch_admin_export()
    flow_overall()
    flow_voice()
    flow_handover()
    print("all sketches done")


if __name__ == "__main__":
    main()
