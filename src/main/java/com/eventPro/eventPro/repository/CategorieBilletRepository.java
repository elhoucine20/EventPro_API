package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.CategorieBillet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;


@Repository
public interface CategorieBilletRepository extends JpaRepository<CategorieBillet, UUID> {


    List<CategorieBillet> findByEventId(UUID eventId);
    boolean existsByEventId(UUID eventId);
    boolean existsByEventIdAndNomIgnoreCase(UUID eventId,String nom);
    Long countByEventId(UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CategorieBillet c where c.id in :ids order by c.id")
    List<CategorieBillet> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);
}
