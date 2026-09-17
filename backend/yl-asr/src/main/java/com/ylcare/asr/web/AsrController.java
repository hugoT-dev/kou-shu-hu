package com.ylcare.asr.web;

import com.ylcare.asr.engine.StreamingAsrEngine;
import com.ylcare.common.ApiResult;
import com.ylcare.common.AsrEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/asr")
public class AsrController {
    private final StreamingAsrEngine engine;

    public AsrController(StreamingAsrEngine engine) {
        this.engine = engine;
    }

    @GetMapping("/health")
    public ApiResult<?> health() {
        return ApiResult.ok(Map.of(
                "provider", engine.provider(),
                "model", engine.model(),
                "pcm", engine.acceptsPcm()));
    }

    @PostMapping("/stream-text")
    public ApiResult<List<AsrEvent>> streamText(@RequestBody Map<String, String> body) {
        List<AsrEvent> events = new ArrayList<>();
        engine.streamText(body.getOrDefault("text", ""), events::add);
        return ApiResult.ok(events);
    }
}
