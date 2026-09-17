package com.ylcare.asr.engine;

import com.ylcare.common.AsrEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 自建 FunASR paraformer-zh-streaming：yl-asr 把 16k PCM 转到 Python/C++ 运行时 WebSocket。
 */
@Component
@ConditionalOnProperty(name = "yl.asr.provider", havingValue = "funasr")
public class FunAsrStreamingEngine implements StreamingAsrEngine {
    private static final Logger log = LoggerFactory.getLogger(FunAsrStreamingEngine.class);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    @Value("${yl.asr.funasr.ws-url:ws://127.0.0.1:10095}")
    private String wsUrl;

    @Value("${yl.asr.funasr.model:paraformer-zh-streaming}")
    private String modelName;

    @Value("${yl.asr.funasr.sample-rate:16000}")
    private int sampleRate;

    @Value("${yl.asr.funasr.hotwords:}")
    private String hotwords;

    @Value("${yl.asr.funasr.chunk-size:0,10,5}")
    private String chunkSizeRaw;

    @Value("${yl.asr.funasr.token:}")
    private String token;

    @Override
    public String provider() {
        return "funasr";
    }

    @Override
    public String model() {
        return modelName;
    }

    @Override
    public boolean acceptsPcm() {
        return true;
    }

    @Override
    public void streamText(String fullText, Consumer<AsrEvent> sink) {
        sink.accept(AsrEvent.error("FunASR paraformer-zh-streaming 只接受 16k PCM，请走 /ws/asr/stream"));
    }

    @Override
    public LiveSession open(Consumer<AsrEvent> sink) {
        return new FunAsrLiveSession(sink);
    }

    private int[] chunkSize() {
        String[] parts = chunkSizeRaw.split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Integer.parseInt(parts[i].trim());
        }
        return out;
    }

    private final class FunAsrLiveSession implements LiveSession, WebSocket.Listener {
        private final Consumer<AsrEvent> sink;
        private final WebSocket socket;
        private final AtomicReference<String> lastText = new AtomicReference<>("");
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private final CountDownLatch done = new CountDownLatch(1);
        private final StringBuilder textBuf = new StringBuilder();

        FunAsrLiveSession(Consumer<AsrEvent> sink) {
            this.sink = sink;
            try {
                this.socket = http.newWebSocketBuilder()
                        .buildAsync(URI.create(wsUrl), this)
                        .get(12, TimeUnit.SECONDS);
                String start = FunAsrProtocol.startFrame(
                        "yl-care", sampleRate, hotwords, chunkSize(), token);
                this.socket.sendText(start, true).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("连不上 FunASR（" + wsUrl + "）：" + e.getMessage(), e);
            }
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (last) {
                textBuf.append(data);
                handlePayload(textBuf.toString());
                textBuf.setLength(0);
            } else {
                textBuf.append(data);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        private void handlePayload(String json) {
            AsrEvent ev = FunAsrProtocol.toEvent(json, provider());
            if (ev.getText() != null && !ev.getText().isBlank() && !"error".equals(ev.getEvent())) {
                lastText.set(ev.getText());
            }
            sink.accept(ev);
            if ("final".equals(ev.getEvent()) || "error".equals(ev.getEvent())) {
                finished.set(true);
                done.countDown();
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (!finished.get()) {
                String text = lastText.get();
                sink.accept(text.isBlank()
                        ? AsrEvent.error("FunASR 连接已关闭")
                        : AsrEvent.fin(text, provider()));
                finished.set(true);
                done.countDown();
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("FunASR websocket: {}", error.getMessage());
            sink.accept(AsrEvent.error("FunASR 中断: " + error.getMessage()));
            finished.set(true);
            done.countDown();
        }

        @Override
        public synchronized void sendPcm(byte[] pcm) {
            if (pcm == null || pcm.length == 0 || finished.get()) {
                return;
            }
            socket.sendBinary(ByteBuffer.wrap(pcm), true);
        }

        @Override
        public void finish() {
            if (finished.get()) {
                return;
            }
            try {
                socket.sendText(FunAsrProtocol.stopFrame(), true).get(5, TimeUnit.SECONDS);
                if (!done.await(10, TimeUnit.SECONDS)) {
                    String text = lastText.get();
                    sink.accept(text.isBlank()
                            ? AsrEvent.error("FunASR 等待终稿超时")
                            : AsrEvent.fin(text, provider()));
                }
            } catch (Exception e) {
                sink.accept(AsrEvent.error("结束 FunASR 失败: " + e.getMessage()));
            } finally {
                close();
            }
        }

        @Override
        public void close() {
            finished.set(true);
            done.countDown();
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
            } catch (Exception ignored) {
                // already closed
            }
        }
    }
}
