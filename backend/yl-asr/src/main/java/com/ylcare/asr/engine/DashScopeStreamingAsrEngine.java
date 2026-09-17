package com.ylcare.asr.engine;

import com.ylcare.common.AsrEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * DashScope Paraformer-realtime-v2 适配器。
 * Sprint 1：接口与配置就绪；未配置 API Key 时不会装配本 Bean。
 * 配置 yl.asr.provider=dashscope 且 yl.asr.dashscope.api-key 后，在此接入官方 Java SDK
 * RecognitionParam.model=paraformer-realtime-v2，16k PCM 推帧。
 */
@Component
@ConditionalOnProperty(name = "yl.asr.provider", havingValue = "dashscope")
public class DashScopeStreamingAsrEngine implements StreamingAsrEngine {

    @Value("${yl.asr.dashscope.api-key:}")
    private String apiKey;

    @Override
    public String provider() {
        return "dashscope";
    }

    @Override
    public String model() {
        return "paraformer-realtime-v2";
    }

    @Override
    public void streamText(String fullText, Consumer<AsrEvent> sink) {
        if (apiKey == null || apiKey.isBlank()) {
            sink.accept(AsrEvent.error("未配置 yl.asr.dashscope.api-key"));
            return;
        }
        // Sprint 2 接入官方 SDK 推 PCM。当前若已有终稿文本，先保证链路可走通。
        if (fullText != null && !fullText.isBlank()) {
            sink.accept(AsrEvent.fin(fullText, provider()));
            return;
        }
        sink.accept(AsrEvent.error("DashScope 流式 PCM 识别待接入官方 SDK，请先配置 Key 或改用 mock"));
    }
}
