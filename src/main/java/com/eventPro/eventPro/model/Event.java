package com.eventPro.eventPro.model;

import com.eventPro.eventPro.model.enums.StatusEvenment;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "events")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Event{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id ;

    @Column(nullable = false)
    private String titre;

    @Column(nullable = false,length = 1000)
    private String description ;

    @Column(nullable = false)
    private String lieu ;

    @Column(nullable = false)
    private LocalDateTime dateDebut;

    @Column(nullable = false)
    private LocalDateTime dateFin;

    @Column(nullable = false)
    private LocalDateTime dateOuvertureVente;

    @Column(nullable = false)
    private LocalDateTime dateClotureVente;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StatusEvenment statut;

    // organisateur
    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "organisateur_id",nullable = false)
    private Organisateur organisateur;

    // categorieBillet
    @OneToMany(mappedBy = "event",cascade = CascadeType.ALL,orphanRemoval = true)
    @Builder.Default
    private List<CategorieBillet> categoriesBillet = new ArrayList<>();


    //promotion
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;



}
