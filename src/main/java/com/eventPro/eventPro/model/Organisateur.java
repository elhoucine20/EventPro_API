package com.eventPro.eventPro.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "organisateurs")
@Data
public class Organisateur extends User{

    @Column(nullable = false)
    private String nomOrganisation;

    @OneToMany(mappedBy = "organisateur",cascade = CascadeType.ALL,orphanRemoval = true)
    private List<Event> events = new ArrayList<>();
}
