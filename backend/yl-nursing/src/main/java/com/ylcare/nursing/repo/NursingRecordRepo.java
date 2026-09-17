package com.ylcare.nursing.repo;

import com.ylcare.nursing.domain.NursingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NursingRecordRepo extends JpaRepository<NursingRecord, Long> {
    List<NursingRecord> findByStaffIdOrderByCreatedAtAsc(Long staffId);
    List<NursingRecord> findByStatusOrderByCreatedAtDesc(String status);
    List<NursingRecord> findAllByOrderByCreatedAtDesc();
}
