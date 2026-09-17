package com.ylcare.nursing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylcare.common.StructureParser;
import com.ylcare.common.StructuredRecord;
import com.ylcare.nursing.domain.Elderly;
import com.ylcare.nursing.domain.MediaAsset;
import com.ylcare.nursing.domain.NursingRecord;
import com.ylcare.nursing.repo.ElderlyRepo;
import com.ylcare.nursing.repo.MediaAssetRepo;
import com.ylcare.nursing.repo.NursingRecordRepo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RecordService {
    private final NursingRecordRepo records;
    private final ElderlyRepo elderlyRepo;
    private final MediaAssetRepo mediaRepo;
    private final MediaStorage storage;
    private final ObjectMapper mapper;

    public RecordService(NursingRecordRepo records, ElderlyRepo elderlyRepo, MediaAssetRepo mediaRepo,
                         MediaStorage storage, ObjectMapper mapper) {
        this.records = records;
        this.elderlyRepo = elderlyRepo;
        this.mediaRepo = mediaRepo;
        this.storage = storage;
        this.mapper = mapper;
    }

    @Transactional
    public NursingRecord create(Long staffId, String asrText, MultipartFile audio) throws IOException {
        StructuredRecord parsed = StructureParser.parse(asrText);
        NursingRecord rec = new NursingRecord();
        rec.setStaffId(staffId);
        rec.setAsrText(asrText);
        rec.setRecordType(parsed.getRecordType());
        rec.setStatus("preview");
        rec.setOriginalAudio(true);
        rec.setStructuredJson(mapper.writeValueAsString(parsed));
        matchElderly(parsed).ifPresent(e -> rec.setElderlyId(e.getId()));
        if (audio != null && !audio.isEmpty()) {
            rec.setAudioPath(storage.save(audio, "audio"));
        }
        NursingRecord saved = records.save(rec);
        if (saved.getAudioPath() != null) {
            MediaAsset asset = new MediaAsset();
            asset.setBizType("record");
            asset.setBizId(saved.getId());
            asset.setMediaType("original_audio");
            asset.setPath(saved.getAudioPath());
            asset.setOriginal(true);
            asset.setStaffId(staffId);
            mediaRepo.save(asset);
        }
        return saved;
    }

    @Transactional
    public NursingRecord confirm(Long id) {
        NursingRecord rec = records.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在"));
        rec.setStatus("confirmed");
        rec.setConfirmedAt(Instant.now());
        return records.save(rec);
    }

    @Transactional
    public MediaAsset addMedia(Long recordId, Long staffId, String mediaType, MultipartFile file) throws IOException {
        NursingRecord rec = records.findById(recordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在"));
        if ("confirmed".equals(rec.getStatus()) && "original_audio".equals(mediaType)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已确认记录的原始录音不可替换");
        }
        String path = storage.save(file, mediaType);
        MediaAsset asset = new MediaAsset();
        asset.setBizType("record");
        asset.setBizId(recordId);
        asset.setMediaType(mediaType);
        asset.setPath(path);
        asset.setOriginal(true);
        asset.setStaffId(staffId);
        return mediaRepo.save(asset);
    }

    public Map<String, Object> detail(Long id) throws Exception {
        NursingRecord rec = records.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在"));
        Map<String, Object> map = new HashMap<>();
        map.put("record", rec);
        map.put("structured", mapper.readValue(rec.getStructuredJson(), StructuredRecord.class));
        map.put("media", mediaRepo.findByBizTypeAndBizId("record", id));
        if (rec.getElderlyId() != null) {
            elderlyRepo.findById(rec.getElderlyId()).ifPresent(e -> map.put("elderly", e));
        }
        return map;
    }

    public List<NursingRecord> listConfirmed() {
        return records.findByStatusOrderByCreatedAtDesc("confirmed");
    }

    public List<NursingRecord> listAll() {
        return records.findAllByOrderByCreatedAtDesc();
    }

    private java.util.Optional<Elderly> matchElderly(StructuredRecord parsed) {
        if (parsed.getBedNo() == null) {
            return java.util.Optional.empty();
        }
        return elderlyRepo.findByBedNoAndStatus(parsed.getBedNo(), "in");
    }
}
