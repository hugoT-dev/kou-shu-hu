package com.ylcare.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureParserTest {

    @Test
    void sceneA_routine() {
        StructuredRecord r = StructureParser.parse("3床王奶奶，早上血压140，吃了大半碗粥，精神还行");
        assertEquals("3", r.getBedNo());
        assertEquals("王奶奶", r.getElderlyName());
        assertEquals("routine", r.getRecordType());
        assertEquals("140", r.getBp());
        assertEquals("粥", r.getFood());
        assertEquals("大半碗", r.getAmount());
        assertEquals("fair", r.getMentalStatus());
        assertEquals("一般", r.getMentalLabel());
    }

    @Test
    void sceneB_abnormalCough() {
        StructuredRecord r = StructureParser.parse(
                "5床李爷爷，下午开始咳嗽，没发烧，护士长来看过了，让多喝水，晚上每两小时看一次");
        assertEquals("5", r.getBedNo());
        assertEquals("李爷爷", r.getElderlyName());
        assertEquals("abnormal", r.getRecordType());
        assertEquals("咳嗽", r.getAbnormalType());
        assertEquals("下午", r.getStartTime());
        assertEquals("无发热", r.getAccompanying());
        assertTrue(r.getTreatment().contains("护士长"));
        assertTrue(r.getHandoverFocus().contains("2小时"));
    }

    @Test
    void leftoverMedicine() {
        StructuredRecord r = StructureParser.parse("2床张奶奶的家属交代明天上午来送药");
        assertEquals("2", r.getBedNo());
        assertEquals("leftover", r.getRecordType());
        assertTrue(r.getFamilyMessage().contains("送药"));
    }

    @Test
    void supply() {
        StructuredRecord r = StructureParser.parse("呼叫铃正常制氧机正常轮椅两台能用");
        assertEquals("supply", r.getRecordType());
    }
}
