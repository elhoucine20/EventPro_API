package com.eventPro.eventPro.model;

import com.eventPro.eventPro.model.enums.StatusEvenment;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "events")
@Getter
@Setter
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
    private LocalDate dateDebut;

    @Column(nullable = false)
    private LocalDate dateFin;

    @Column(nullable = false)
    private LocalDate dateOuvertureVente;

    @Column(nullable = false)
    private LocalDate dateClotureVente;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StatusEvenment statut;

    // organisateur
    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "organisateur_id",nullable = false)
    private Organisateur organisateur;

    // categorieBillet
    @OneToMany(mappedBy = "event",cascade = CascadeType.ALL,orphanRemoval = true)
    private List<CategorieBillet> categoriesBillet = new ArrayList<>();


    //promotion
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;



}
