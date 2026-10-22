package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.Organisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganisateurRepository extends JpaRepository<Organisateur, UUID> {

}
