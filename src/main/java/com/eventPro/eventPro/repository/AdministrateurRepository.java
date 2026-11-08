package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.Administrateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdministrateurRepository extends JpaRepository<Administrateur, UUID> {
}
