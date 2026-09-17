package com.ylcare.nursing;

import com.ylcare.common.ApiResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:nursingtest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "yl.media.dir=${java.io.tmpdir}/yl-care-media"
})
class RecordApiTest {
    @Autowired
    TestRestTemplate rest;

    @Test
    void createConfirmAndBuildHandover() {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("staffId", "1");
        body.add("asrText", "5床李爷爷，下午开始咳嗽，没发烧，护士长来看过了，让多喝水，晚上每两小时看一次");
        body.add("audio", new ByteArrayResource("fake-pcm-original".getBytes()) {
            @Override
            public String getFilename() {
                return "cough.webm";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<ApiResult> created = rest.postForEntity("/api/nursing/records", new HttpEntity<>(body, headers), ApiResult.class);
        assertTrue(created.getBody().isOk());
        @SuppressWarnings("unchecked")
        Map<String, Object> rec = (Map<String, Object>) created.getBody().getData();
        Number id = (Number) rec.get("id");
        assertNotNull(id);
        assertEquals("abnormal", rec.get("recordType"));
        assertNotNull(rec.get("audioPath"));
        assertEquals(true, rec.get("originalAudio"));

        ApiResult confirm = rest.postForObject("/api/nursing/records/" + id.longValue() + "/confirm", null, ApiResult.class);
        assertTrue(confirm.isOk());

        ApiResult sheet = rest.getForObject("/api/nursing/handovers/current?staffId=1", ApiResult.class);
        assertTrue(sheet.isOk());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) sheet.getData();
        @SuppressWarnings("unchecked")
        List<?> items = (List<?>) data.get("items");
        assertTrue(items.size() >= 1);
    }
}
