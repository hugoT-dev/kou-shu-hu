package com.ylcare.common;

import java.util.ArrayList;
import java.util.List;

public class StructuredRecord {
    private String bedNo;
    private String elderlyName;
    private String recordType = "routine";
    private String bp;
    private String food;
    private String amount;
    private String mentalStatus;
    private String mentalLabel;
    private String abnormalType;
    private String startTime;
    private String accompanying;
    private String treatment;
    private String handoverFocus;
    private String familyMessage;
    private String remark;
    private List<String> flags = new ArrayList<>();

    public String getBedNo() { return bedNo; }
    public void setBedNo(String bedNo) { this.bedNo = bedNo; }
    public String getElderlyName() { return elderlyName; }
    public void setElderlyName(String elderlyName) { this.elderlyName = elderlyName; }
    public String getRecordType() { return recordType; }
    public void setRecordType(String recordType) { this.recordType = recordType; }
    public String getBp() { return bp; }
    public void setBp(String bp) { this.bp = bp; }
    public String getFood() { return food; }
    public void setFood(String food) { this.food = food; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getMentalStatus() { return mentalStatus; }
    public void setMentalStatus(String mentalStatus) { this.mentalStatus = mentalStatus; }
    public String getMentalLabel() { return mentalLabel; }
    public void setMentalLabel(String mentalLabel) { this.mentalLabel = mentalLabel; }
    public String getAbnormalType() { return abnormalType; }
    public void setAbnormalType(String abnormalType) { this.abnormalType = abnormalType; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getAccompanying() { return accompanying; }
    public void setAccompanying(String accompanying) { this.accompanying = accompanying; }
    public String getTreatment() { return treatment; }
    public void setTreatment(String treatment) { this.treatment = treatment; }
    public String getHandoverFocus() { return handoverFocus; }
    public void setHandoverFocus(String handoverFocus) { this.handoverFocus = handoverFocus; }
    public String getFamilyMessage() { return familyMessage; }
    public void setFamilyMessage(String familyMessage) { this.familyMessage = familyMessage; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public List<String> getFlags() { return flags; }
    public void setFlags(List<String> flags) { this.flags = flags; }
}
