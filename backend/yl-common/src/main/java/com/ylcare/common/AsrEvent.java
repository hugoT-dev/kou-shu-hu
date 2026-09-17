package com.ylcare.common;

/**
 * 流式 ASR 事件。partial=边说边出字，final=松开后终稿。
 */
public class AsrEvent {
    private String event;
    private String text;
    private String provider;

    public static AsrEvent partial(String text, String provider) {
        AsrEvent e = new AsrEvent();
        e.event = "partial";
        e.text = text;
        e.provider = provider;
        return e;
    }

    public static AsrEvent fin(String text, String provider) {
        AsrEvent e = new AsrEvent();
        e.event = "final";
        e.text = text;
        e.provider = provider;
        return e;
    }

    public static AsrEvent error(String message) {
        AsrEvent e = new AsrEvent();
        e.event = "error";
        e.text = message;
        return e;
    }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
}
