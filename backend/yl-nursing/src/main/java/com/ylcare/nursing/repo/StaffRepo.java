package com.ylcare.nursing.repo;

import com.ylcare.nursing.domain.Staff;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRepo extends JpaRepository<Staff, Long> {
}
