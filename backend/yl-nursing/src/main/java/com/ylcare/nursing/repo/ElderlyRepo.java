package com.ylcare.nursing.repo;

import com.ylcare.nursing.domain.Elderly;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ElderlyRepo extends JpaRepository<Elderly, Long> {
    List<Elderly> findByStatus(String status);
    Optional<Elderly> findByBedNoAndStatus(String bedNo, String status);
}
