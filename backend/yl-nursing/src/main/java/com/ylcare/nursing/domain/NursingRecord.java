package com.ylcare.nursing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

import java.time.Instant;

@Entity
public class NursingRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long elderlyId;
    private Long staffId;
    @Lob
    private String asrText;
    @Lob
    private String structuredJson;
    private String recordType;
    private String status;
    private String audioPath;
    private Long audioDurationMs;
    private boolean originalAudio = true;
    private Instant createdAt = Instant.now();
    private Instant confirmedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getElderlyId() { return elderlyId; }
    public void setElderlyId(Long elderlyId) { this.elderlyId = elderlyId; }
    public Long getStaffId() { return staffId; }
    public void setStaffId(Long staffId) { this.staffId = staffId; }
    public String getAsrText() { return asrText; }
    public void setAsrText(String asrText) { this.asrText = asrText; }
    public String getStructuredJson() { return structuredJson; }
    public void setStructuredJson(String structuredJson) { this.structuredJson = structuredJson; }
    public String getRecordType() { return recordType; }
    public void setRecordType(String recordType) { this.recordType = recordType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAudioPath() { return audioPath; }
    public void setAudioPath(String audioPath) { this.audioPath = audioPath; }
    public Long getAudioDurationMs() { return audioDurationMs; }
    public void setAudioDurationMs(Long audioDurationMs) { this.audioDurationMs = audioDurationMs; }
    public boolean isOriginalAudio() { return originalAudio; }
    public void setOriginalAudio(boolean originalAudio) { this.originalAudio = originalAudio; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
}
