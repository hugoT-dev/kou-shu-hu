package com.ylcare.asr.engine;

import com.ylcare.common.AsrEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunAsrProtocolTest {
    @Test
    void startFrameUses2passAndHotwords() {
        String json = FunAsrProtocol.startFrame(
                "yl-care", 16000, "{\"王奶奶\":20}", new int[]{0, 10, 5}, "secret");
        assertTrue(json.contains("\"mode\":\"2pass\""));
        assertTrue(json.contains("\"audio_fs\":16000"));
        assertTrue(json.contains("\"token\":\"secret\""));
        assertTrue(json.contains("王奶奶"));
    }

    @Test
    void onlinePartialAndOfflineFinal() {
        AsrEvent partial = FunAsrProtocol.toEvent(
                "{\"mode\":\"2pass-online\",\"text\":\"王奶奶\",\"is_final\":false}", "funasr");
        assertEquals("partial", partial.getEvent());
        assertEquals("王奶奶", partial.getText());
        AsrEvent fin = FunAsrProtocol.toEvent(
                "{\"mode\":\"2pass-offline\",\"text\":\"3床王奶奶\",\"is_final\":true}", "funasr");
        assertEquals("final", fin.getEvent());
        assertEquals("3床王奶奶", fin.getText());
    }
}
