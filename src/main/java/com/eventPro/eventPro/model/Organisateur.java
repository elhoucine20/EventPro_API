package com.eventPro.eventPro.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "organisateurs")
@Setter
@Getter
@SuperBuilder
@NoArgsConstructor
public class Organisateur extends User{

    @Column(nullable = false)
    private String nomOrganisation;

    @OneToMany(mappedBy = "organisateur",cascade = CascadeType.ALL,orphanRemoval = true)
    @Builder.Default
    private List<Event> events = new ArrayList<>();
}
