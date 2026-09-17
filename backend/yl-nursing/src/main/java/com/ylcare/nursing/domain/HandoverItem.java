package com.ylcare.nursing.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

@Entity
public class HandoverItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long sheetId;
    private String itemType;
    private Long elderlyId;
    private Long sourceRecordId;
    @Lob
    private String content;
    private String ackStatus = "pending";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSheetId() { return sheetId; }
    public void setSheetId(Long sheetId) { this.sheetId = sheetId; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public Long getElderlyId() { return elderlyId; }
    public void setElderlyId(Long elderlyId) { this.elderlyId = elderlyId; }
    public Long getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(Long sourceRecordId) { this.sourceRecordId = sourceRecordId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getAckStatus() { return ackStatus; }
    public void setAckStatus(String ackStatus) { this.ackStatus = ackStatus; }
}
