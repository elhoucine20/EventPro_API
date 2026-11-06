package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.AgentControle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AgentControleRepository extends JpaRepository<AgentControle, UUID> {
}
