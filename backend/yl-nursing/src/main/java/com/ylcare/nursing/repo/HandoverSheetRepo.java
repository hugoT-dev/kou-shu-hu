package com.ylcare.nursing.repo;

import com.ylcare.nursing.domain.HandoverSheet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface HandoverSheetRepo extends JpaRepository<HandoverSheet, Long> {
    Optional<HandoverSheet> findFirstByShiftDateAndShiftNameAndStaffId(LocalDate shiftDate, String shiftName, Long staffId);
}
