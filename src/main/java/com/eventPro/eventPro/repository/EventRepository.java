package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.Event;
import com.eventPro.eventPro.model.enums.StatusEvenment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    @EntityGraph(attributePaths = "organisateur")
    Page<Event> findByStatut(StatusEvenment statu,Pageable pageable);

    @EntityGraph(attributePaths = "organisateur") // hibernate utilise join pour effectue ine seul request (Lazy)
    Page<Event> findByOrganisateurId(UUID id, Pageable pageable);  // Page => output, Pageable => input
}
