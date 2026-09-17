package com.ylcare.asr.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylcare.asr.engine.StreamingAsrEngine;
import com.ylcare.common.AsrEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 流式识别通道。
 * JSON：{"event":"start"} / {"event":"hint","text":"..."} / {"event":"stop"}
 * 二进制帧：16kHz / 16bit / mono PCM（funasr）。
 */
@Component
public class AsrWebSocketHandler extends AbstractWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(AsrWebSocketHandler.class);
    private static final int MAX_PENDING_BYTES = 2_000_000;
    private final StreamingAsrEngine engine;
    private final ObjectMapper mapper;
    private final Map<String, ClientState> states = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public AsrWebSocketHandler(StreamingAsrEngine engine, ObjectMapper mapper) {
        this.engine = engine;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        states.put(session.getId(), new ClientState());
        send(session, AsrEvent.partial("", engine.provider()));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ClientState st = states.get(session.getId());
        if (st == null || st.stopped) {
            return;
        }
        ByteBuffer buf = message.getPayload();
        byte[] pcm = new byte[buf.remaining()];
        buf.get(pcm);
        synchronized (st) {
            if (st.live != null) {
                st.live.sendPcm(pcm);
                return;
            }
            if (st.pendingBytes + pcm.length > MAX_PENDING_BYTES) {
                return;
            }
            st.pending.add(pcm);
            st.pendingBytes += pcm.length;
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = mapper.readTree(message.getPayload());
        String event = node.path("event").asText("");
        ClientState st = states.computeIfAbsent(session.getId(), id -> new ClientState());
        if ("hint".equals(event) || "text".equals(event)) {
            st.hint.append(node.path("text").asText(""));
            return;
        }
        if ("start".equals(event)) {
            st.hint.setLength(0);
            st.stopped = false;
            st.pending.clear();
            st.pendingBytes = 0;
            closeLive(st);
            if (engine.acceptsPcm()) {
                WebSocketSession safe = decorate(session);
                pool.submit(() -> openLive(st, safe));
            }
            return;
        }
        if ("stop".equals(event)) {
            st.stopped = true;
            WebSocketSession safe = decorate(session);
            pool.submit(() -> stopSession(st, safe));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        ClientState st = states.remove(session.getId());
        if (st != null) {
            closeLive(st);
        }
    }

    private void openLive(ClientState st, WebSocketSession safe) {
        try {
            StreamingAsrEngine.LiveSession live = engine.open(ev -> send(safe, ev));
            synchronized (st) {
                st.live = live;
                byte[] pcm;
                while ((pcm = st.pending.poll()) != null) {
                    live.sendPcm(pcm);
                }
                st.pendingBytes = 0;
            }
        } catch (Exception e) {
            log.warn("open FunASR session failed: {}", e.getMessage());
            send(safe, AsrEvent.error(e.getMessage()));
        }
    }

    private void stopSession(ClientState st, WebSocketSession safe) {
        StreamingAsrEngine.LiveSession live;
        synchronized (st) {
            live = st.live;
        }
        if (live != null) {
            try {
                live.finish();
            } catch (Exception e) {
                send(safe, AsrEvent.error(e.getMessage()));
            } finally {
                closeLive(st);
            }
            return;
        }
        engine.streamText(st.hint.toString(), ev -> send(safe, ev));
    }

    private void closeLive(ClientState st) {
        StreamingAsrEngine.LiveSession live;
        synchronized (st) {
            live = st.live;
            st.live = null;
        }
        if (live != null) {
            try {
                live.close();
            } catch (Exception ignored) {
                // already closed
            }
        }
    }

    private WebSocketSession decorate(WebSocketSession session) {
        return new ConcurrentWebSocketSessionDecorator(session, 5_000, 1024 * 1024);
    }

    private void send(WebSocketSession session, AsrEvent event) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(mapper.writeValueAsString(event)));
            }
        } catch (Exception e) {
            log.warn("asr push failed: {}", e.getMessage());
        }
    }

    private static final class ClientState {
        final StringBuilder hint = new StringBuilder();
        final ConcurrentLinkedQueue<byte[]> pending = new ConcurrentLinkedQueue<>();
        volatile StreamingAsrEngine.LiveSession live;
        volatile boolean stopped;
        int pendingBytes;
    }
}
