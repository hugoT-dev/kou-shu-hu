package com.ylcare.asr.engine;

import com.ylcare.common.AsrEvent;

import java.util.function.Consumer;

/**
 * 流式识别。mock 走文本 hint；funasr / dashscope 走 16k PCM。
 */
public interface StreamingAsrEngine {
    String provider();

    String model();

    default boolean acceptsPcm() {
        return false;
    }

    void streamText(String fullText, Consumer<AsrEvent> sink);

    default LiveSession open(Consumer<AsrEvent> sink) {
        throw new UnsupportedOperationException(provider() + " 不接受 PCM");
    }

    interface LiveSession extends AutoCloseable {
        void sendPcm(byte[] pcm);

        void finish();

        @Override
        void close();
    }
}
