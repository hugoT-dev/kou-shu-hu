package com.ylcare.nursing.web;

import com.ylcare.common.ApiResult;
import com.ylcare.nursing.service.HandoverService;
import com.ylcare.nursing.service.RecordService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/nursing")
public class NursingController {
    private final RecordService records;
    private final HandoverService handovers;
    private final com.ylcare.nursing.repo.MediaAssetRepo mediaRepo;
    private final com.ylcare.nursing.service.MediaStorage storage;
    private final com.ylcare.nursing.repo.ElderlyRepo elderlyRepo;
    private final com.ylcare.nursing.repo.StaffRepo staffRepo;

    public NursingController(RecordService records, HandoverService handovers,
                             com.ylcare.nursing.repo.MediaAssetRepo mediaRepo,
                             com.ylcare.nursing.service.MediaStorage storage,
                             com.ylcare.nursing.repo.ElderlyRepo elderlyRepo,
                             com.ylcare.nursing.repo.StaffRepo staffRepo) {
        this.records = records;
        this.handovers = handovers;
        this.mediaRepo = mediaRepo;
        this.storage = storage;
        this.elderlyRepo = elderlyRepo;
        this.staffRepo = staffRepo;
    }

    @GetMapping("/meta/elderly")
    public ApiResult<?> elderly() {
        return ApiResult.ok(elderlyRepo.findByStatus("in"));
    }

    @GetMapping("/meta/staff")
    public ApiResult<?> staff() {
        return ApiResult.ok(staffRepo.findAll());
    }

    @PostMapping(value = "/records", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<?> create(@RequestParam Long staffId,
                               @RequestParam String asrText,
                               @RequestParam(required = false) MultipartFile audio) throws Exception {
        return ApiResult.ok(records.create(staffId, asrText, audio));
    }

    @PostMapping("/records/{id}/confirm")
    public ApiResult<?> confirm(@PathVariable Long id) {
        return ApiResult.ok(records.confirm(id));
    }

    @PostMapping(value = "/records/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<?> media(@PathVariable Long id,
                              @RequestParam Long staffId,
                              @RequestParam String mediaType,
                              @RequestParam MultipartFile file) throws Exception {
        return ApiResult.ok(records.addMedia(id, staffId, mediaType, file));
    }

    @GetMapping("/records/{id}")
    public ApiResult<?> detail(@PathVariable Long id) throws Exception {
        return ApiResult.ok(records.detail(id));
    }

    @GetMapping("/admin/records")
    public ApiResult<?> adminRecords() {
        return ApiResult.ok(records.listAll());
    }

    @GetMapping("/admin/alerts")
    public ApiResult<?> alerts() {
        var confirmed = records.listConfirmed();
        long abnormal = confirmed.stream().filter(r -> "abnormal".equals(r.getRecordType())).count();
        return ApiResult.ok(Map.of("abnormal", abnormal, "records", confirmed.size(), "list", confirmed));
    }

    @GetMapping("/handovers/current")
    public ApiResult<?> current(@RequestParam Long staffId) throws Exception {
        return ApiResult.ok(handovers.current(staffId));
    }

    @PostMapping("/handovers/{id}/submit")
    public ApiResult<?> submit(@PathVariable Long id) throws Exception {
        return ApiResult.ok(handovers.submit(id));
    }

    @PostMapping("/handovers/items/{id}/ack")
    public ApiResult<?> ack(@PathVariable Long id) {
        return ApiResult.ok(handovers.ack(id));
    }

    @GetMapping("/media/{id}")
    public ResponseEntity<Resource> play(@PathVariable Long id) {
        var asset = mediaRepo.findById(id).orElseThrow();
        Path path = storage.resolve(asset.getPath());
        String mime = asset.getMediaType() != null && asset.getMediaType().contains("video")
                ? "video/mp4"
                : asset.getMediaType() != null && asset.getMediaType().contains("photo")
                ? "image/jpeg"
                : "audio/webm";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.parseMediaType(mime))
                .body(new FileSystemResource(path));
    }
}
