package com.ylcare.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MVP 规则引擎：把护理口述终稿解析为结构化字段。
 */
public final class StructureParser {
    private static final Pattern BED = Pattern.compile("(\\d+)\\s*床");
    private static final Pattern BP = Pattern.compile("血压\\s*(\\d{2,3})");
    private static final String[] ABNORMAL = {
            "咳嗽", "发烧", "发热", "气促", "喘", "胸闷", "跌倒", "摔倒", "坠床",
            "呕吐", "腹泻", "便秘", "拒食", "走失", "走丢", "压疮", "红肿",
            "磕碰", "抽搐", "血压高", "低血糖", "尿湿", "不肯睡"
    };

    private StructureParser() {
    }

    public static StructuredRecord parse(String text) {
        StructuredRecord r = new StructuredRecord();
        if (text == null || text.isBlank()) {
            r.setRecordType("routine");
            r.setRemark("空口述");
            return r;
        }
        Matcher bed = BED.matcher(text);
        if (bed.find()) {
            r.setBedNo(bed.group(1));
        }
        if (text.contains("王奶奶")) {
            r.setElderlyName("王奶奶");
        } else if (text.contains("李爷爷")) {
            r.setElderlyName("李爷爷");
        } else if (text.contains("张奶奶")) {
            r.setElderlyName("张奶奶");
        }
        Matcher bp = BP.matcher(text);
        if (bp.find()) {
            r.setBp(bp.group(1));
        }
        if (text.contains("粥")) {
            r.setFood("粥");
            if (text.contains("大半碗")) {
                r.setAmount("大半碗");
            } else if (text.contains("三分之一") || text.contains("1/3")) {
                r.setAmount("约1/3");
            }
        }
        if (text.contains("还行") || text.contains("一般") || text.contains("正常")) {
            r.setMentalStatus("fair");
            r.setMentalLabel("一般");
        } else if (text.contains("很好") || text.contains("精神好")) {
            r.setMentalStatus("good");
            r.setMentalLabel("良好");
        } else if (text.contains("低落") || text.contains("不高兴") || text.contains("哭")) {
            r.setMentalStatus("low");
            r.setMentalLabel("低落");
        } else if (text.contains("没精神") || text.contains("不太好")) {
            r.setMentalStatus("poor");
            r.setMentalLabel("较差");
        }

        String abnormal = firstHit(text, ABNORMAL);
        boolean leftover = text.contains("明天") || text.contains("送药") || text.contains("交代") || text.contains("家属");
        boolean supply = text.contains("呼叫铃") || text.contains("轮椅") || text.contains("制氧机");
        boolean moodDrop = text.contains("低落") || text.contains("不高兴") || (text.contains("探视") && text.contains("少"));

        if (abnormal != null) {
            r.setRecordType("abnormal");
            r.setAbnormalType(abnormal);
            if (text.contains("下午")) {
                r.setStartTime("下午");
            }
            if (text.contains("没发烧") || text.contains("不发烧") || text.contains("无发热")) {
                r.setAccompanying("无发热");
            }
            if (text.contains("护士长") || text.contains("多喝水") || text.contains("多饮水")) {
                r.setTreatment("护士长评估，嘱多饮水");
            }
            if (text.contains("两小时") || text.contains("2小时") || text.contains("观察")) {
                r.setHandoverFocus("每2小时观察一次，警惕气促或发热");
            } else if ("跌倒".equals(abnormal) || "摔倒".equals(abnormal)) {
                r.setHandoverFocus("继续观察神志与外伤，异常立即报告");
            }
        } else if (moodDrop) {
            r.setRecordType("abnormal");
            r.setAbnormalType("情绪/进食变化");
            r.setHandoverFocus("夜班关注情绪与进食");
            if (text.contains("报告护士长") || text.contains("已报告")) {
                r.setTreatment("已报告护士长");
            }
        } else if (leftover && (text.contains("送药") || text.contains("明天"))) {
            r.setRecordType("leftover");
            r.setFamilyMessage(text.contains("送药") ? "家属明天上午来送药" : text);
        } else if (supply) {
            r.setRecordType("supply");
            r.setRemark(text);
        } else {
            r.setRecordType("routine");
        }
        return r;
    }

    private static String firstHit(String text, String[] words) {
        for (String w : words) {
            if (text.contains(w)) {
                return w;
            }
        }
        return null;
    }
}
