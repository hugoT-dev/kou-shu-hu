package com.ylcare.asr.engine;

import com.ylcare.common.AsrEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * 本地/测试引擎：把完整句子按字推 partial，最后推 final。
 * 真实 PCM 识别在 DashScope 引擎中完成；无 Key 时前端可把演示文本作为 hint 走本引擎。
 */
@Component
@ConditionalOnProperty(name = "yl.asr.provider", havingValue = "mock", matchIfMissing = true)
public class MockStreamingAsrEngine implements StreamingAsrEngine {
    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public String model() {
        return "mock";
    }

    @Override
    public void streamText(String fullText, Consumer<AsrEvent> sink) {
        if (fullText == null || fullText.isBlank()) {
            sink.accept(AsrEvent.error("没有可供识别的文本，且未配置 DashScope Key"));
            return;
        }
        StringBuilder acc = new StringBuilder();
        for (int i = 0; i < fullText.length(); i++) {
            acc.append(fullText.charAt(i));
            sink.accept(AsrEvent.partial(acc.toString(), provider()));
            try {
                Thread.sleep(18);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        sink.accept(AsrEvent.fin(fullText, provider()));
    }
}
