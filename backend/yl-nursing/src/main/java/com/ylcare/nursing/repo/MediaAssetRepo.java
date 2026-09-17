package com.ylcare.nursing.repo;

import com.ylcare.nursing.domain.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaAssetRepo extends JpaRepository<MediaAsset, Long> {
    List<MediaAsset> findByBizTypeAndBizId(String bizType, Long bizId);
}
