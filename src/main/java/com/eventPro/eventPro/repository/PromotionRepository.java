package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, UUID> {

    List<Promotion> findByEventId(UUID eventId);
    Optional<Promotion> findByEventIdAndCode(UUID eventId, String code);
    boolean existsByEventIdAndCode(UUID eventId, String code);
}
