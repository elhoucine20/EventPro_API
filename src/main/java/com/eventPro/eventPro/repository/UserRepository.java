package com.eventPro.eventPro.repository;

import com.eventPro.eventPro.model.User;
import com.eventPro.eventPro.model.enums.RoleUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    public boolean existsByEmail(String email);
    public Optional<User> findByEmail(String email);
    Page<User> findByRole(RoleUser role, Pageable pageable);
}
