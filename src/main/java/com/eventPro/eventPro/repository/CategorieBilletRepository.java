package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.CategorieBillet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;


@Repository
public interface CategorieBilletRepository extends JpaRepository<CategorieBillet, UUID> {


    List<CategorieBillet> findByEventId(UUID eventId);
    boolean existsByEventId(UUID eventId);
    boolean existsByEventIdAndNomIgnoreCase(UUID eventId,String nom);
    Long countByEventId(UUID eventId);
}
