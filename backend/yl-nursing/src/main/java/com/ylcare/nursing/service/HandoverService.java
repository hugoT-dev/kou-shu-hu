package com.ylcare.nursing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylcare.common.StructuredRecord;
import com.ylcare.nursing.domain.HandoverItem;
import com.ylcare.nursing.domain.HandoverSheet;
import com.ylcare.nursing.domain.NursingRecord;
import com.ylcare.nursing.repo.HandoverItemRepo;
import com.ylcare.nursing.repo.HandoverSheetRepo;
import com.ylcare.nursing.repo.MediaAssetRepo;
import com.ylcare.nursing.repo.NursingRecordRepo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HandoverService {
    private final HandoverSheetRepo sheets;
    private final HandoverItemRepo items;
    private final NursingRecordRepo records;
    private final MediaAssetRepo mediaRepo;
    private final ObjectMapper mapper;

    public HandoverService(HandoverSheetRepo sheets, HandoverItemRepo items, NursingRecordRepo records,
                           MediaAssetRepo mediaRepo, ObjectMapper mapper) {
        this.sheets = sheets;
        this.items = items;
        this.records = records;
        this.mediaRepo = mediaRepo;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> current(Long staffId) throws Exception {
        LocalDate today = LocalDate.now();
        HandoverSheet sheet = sheets.findFirstByShiftDateAndShiftNameAndStaffId(today, "白班", staffId)
                .orElseGet(() -> {
                    HandoverSheet s = new HandoverSheet();
                    s.setShiftDate(today);
                    s.setShiftName("白班");
                    s.setStaffId(staffId);
                    s.setStatus("draft");
                    return sheets.save(s);
                });
        if (!"submitted".equals(sheet.getStatus())) {
            rebuildItems(sheet);
        }
        return toView(sheet);
    }

    @Transactional
    public Map<String, Object> submit(Long id) throws Exception {
        HandoverSheet sheet = sheets.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "清单不存在"));
        sheet.setStatus("submitted");
        sheet.setSubmittedAt(Instant.now());
        sheets.save(sheet);
        return toView(sheet);
    }

    @Transactional
    public HandoverItem ack(Long itemId) {
        HandoverItem item = items.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "条目不存在"));
        item.setAckStatus("understood");
        return items.save(item);
    }

    private void rebuildItems(HandoverSheet sheet) throws Exception {
        items.deleteBySheetId(sheet.getId());
        List<NursingRecord> confirmed = records.findByStaffIdOrderByCreatedAtAsc(sheet.getStaffId())
                .stream().filter(r -> "confirmed".equals(r.getStatus())).toList();
        int focus = 0;
        for (NursingRecord rec : confirmed) {
            StructuredRecord s = mapper.readValue(rec.getStructuredJson(), StructuredRecord.class);
            String type = rec.getRecordType();
            if ("abnormal".equals(type)) {
                saveItem(sheet, "focus", rec, content(s, rec));
                focus++;
            } else if ("leftover".equals(type)) {
                saveItem(sheet, "leftover", rec, s.getFamilyMessage() == null ? rec.getAsrText() : s.getFamilyMessage());
            } else if ("supply".equals(type)) {
                saveItem(sheet, "supply", rec, rec.getAsrText());
            }
        }
        sheet.setSummary("本班已确认 " + confirmed.size() + " 条记录，" + focus + " 位需重点关注。");
        sheets.save(sheet);
    }

    private void saveItem(HandoverSheet sheet, String type, NursingRecord rec, String content) {
        HandoverItem item = new HandoverItem();
        item.setSheetId(sheet.getId());
        item.setItemType(type);
        item.setElderlyId(rec.getElderlyId());
        item.setSourceRecordId(rec.getId());
        item.setContent(content);
        items.save(item);
    }

    private String content(StructuredRecord s, NursingRecord rec) {
        StringBuilder b = new StringBuilder();
        if (s.getElderlyName() != null) {
            b.append(s.getBedNo() == null ? "" : s.getBedNo() + "床 ").append(s.getElderlyName()).append(" ");
        }
        if (s.getAbnormalType() != null) {
            b.append(s.getAbnormalType());
        }
        if (s.getHandoverFocus() != null) {
            b.append("。").append(s.getHandoverFocus());
        }
        if (b.isEmpty()) {
            b.append(rec.getAsrText());
        }
        return b.toString();
    }

    private Map<String, Object> toView(HandoverSheet sheet) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (HandoverItem item : items.findBySheetIdOrderByIdAsc(sheet.getId())) {
            Map<String, Object> row = new HashMap<>();
            row.put("item", item);
            if (item.getSourceRecordId() != null) {
                row.put("media", mediaRepo.findByBizTypeAndBizId("record", item.getSourceRecordId()));
            }
            rows.add(row);
        }
        Map<String, Object> view = new HashMap<>();
        view.put("sheet", sheet);
        view.put("items", rows);
        return view;
    }
}
