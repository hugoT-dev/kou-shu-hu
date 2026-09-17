package com.ylcare.asr.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ylcare.common.AsrEvent;

/**
 * FunASR WebSocket 在线协议（与 runtime/docs/websocket_protocol 对齐）。
 * 自建 paraformer-zh-streaming：mode=2pass（流式 online + 句尾 offline）。
 */
public final class FunAsrProtocol {
    static final ObjectMapper MAPPER = new ObjectMapper();

    private FunAsrProtocol() {
    }

    public static String startFrame(String wavName, int sampleRate, String hotwords, int[] chunkSize, String token) {
        try {
            ObjectNode n = MAPPER.createObjectNode();
            n.put("mode", "2pass");
            ArrayNode chunks = n.putArray("chunk_size");
            for (int c : chunkSize) {
                chunks.add(c);
            }
            n.put("wav_name", wavName);
            n.put("is_speaking", true);
            n.put("wav_format", "pcm");
            n.put("audio_fs", sampleRate);
            n.put("itn", true);
            if (hotwords != null && !hotwords.isBlank()) {
                n.put("hotwords", hotwords);
            }
            if (token != null && !token.isBlank()) {
                n.put("token", token);
            }
            return MAPPER.writeValueAsString(n);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String stopFrame() {
        return "{\"is_speaking\":false}";
    }

    public static AsrEvent toEvent(String json, String provider) {
        try {
            return toEvent(MAPPER.readTree(json), provider);
        } catch (Exception e) {
            return AsrEvent.error("FunASR 返回无法解析: " + e.getMessage());
        }
    }

    public static AsrEvent toEvent(JsonNode n, String provider) {
        if (n == null || n.isMissingNode()) {
            return AsrEvent.error("FunASR 空响应");
        }
        String err = n.path("error").asText("");
        if (!err.isBlank()) {
            return AsrEvent.error(err);
        }
        if ("error".equals(n.path("event").asText(""))) {
            return AsrEvent.error(n.path("text").asText("FunASR 错误"));
        }
        String text = n.path("text").asText("");
        boolean fin = n.path("is_final").asBoolean(false);
        if (fin) {
            return AsrEvent.fin(text, provider);
        }
        return AsrEvent.partial(text, provider);
    }
}
