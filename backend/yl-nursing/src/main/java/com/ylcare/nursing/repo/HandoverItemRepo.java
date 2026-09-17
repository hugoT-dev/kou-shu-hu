package com.ylcare.nursing.repo;

import com.ylcare.nursing.domain.HandoverItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface HandoverItemRepo extends JpaRepository<HandoverItem, Long> {
    List<HandoverItem> findBySheetIdOrderByIdAsc(Long sheetId);

    @Modifying
    @Transactional
    void deleteBySheetId(Long sheetId);
}
