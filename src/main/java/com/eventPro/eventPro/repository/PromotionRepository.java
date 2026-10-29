package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, UUID> {

    List<Promotion> findByEventId(UUID eventId);
    Optional<Promotion> findByEventIdAndCode(UUID eventId, String code);
    boolean existsByEventIdAndCode(UUID eventId, String code);


    // reservation
    @Modifying(flushAutomatically = true) // indique a spring le query Update pas Select
    @Query("UPDATE Promotion p set p.nombreUtilisations = p.nombreUtilisations + 1 where p.id = :id and p.nombreUtilisations < p.maxUtilisations")
    int incrementUtulisation(@Param("id") UUID id);

    @Modifying(flushAutomatically = true) // synchronise les changements en attente avant cette requete
    @Query("update Promotion p set p.nombreUtilisations = p.nombreUtilisations - 1 where p.id = :id and p.nombreUtilisations>0")
    int devrementUtilisation(@Param("id") UUID id);
}
