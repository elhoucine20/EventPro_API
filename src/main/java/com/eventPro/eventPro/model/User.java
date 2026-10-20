package com.eventPro.eventPro.model;

import com.eventPro.eventPro.model.enums.RoleUser;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    protected UUID id;

    @Column(nullable = false)
    protected String name;

    @Column(unique = true , nullable = false)
    protected String email ;

    @Column(nullable = false)
    protected String password;

    @Column(nullable = false)
    private String salt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RoleUser role;
}
