package com.ylcare.asr;

import com.ylcare.common.AsrEvent;
import com.ylcare.common.ApiResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "yl.asr.provider=mock"
})
class AsrApiTest {
    @Autowired
    TestRestTemplate rest;

    @Test
    void healthExposesParaformerSelection() {
        ApiResult<?> body = rest.getForObject("/api/asr/health", ApiResult.class);
        assertTrue(body.isOk());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.getData();
        assertEquals("mock", data.get("provider"));
        assertEquals("mock", data.get("model"));
        assertEquals(false, data.get("pcm"));
    }

    @Test
    void streamTextEmitsPartialThenFinal() {
        ApiResult<?> body = rest.postForObject("/api/asr/stream-text", Map.of("text", "咳嗽"), ApiResult.class);
        assertTrue(body.isOk());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) body.getData();
        assertTrue(events.size() >= 2);
        assertEquals("final", events.get(events.size() - 1).get("event"));
        assertEquals("咳嗽", events.get(events.size() - 1).get("text"));
    }
}
