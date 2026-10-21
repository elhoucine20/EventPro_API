package com.eventPro.eventPro.model;

import com.eventPro.eventPro.model.enums.StatutReservation;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true,nullable = false)
    private String reference;

    @Column(nullable = false)
    private LocalDateTime dateCreation ;

    @Column(nullable = false)
    private LocalDateTime dateExpiration ;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StatutReservation statut;

    @Column(nullable = false,precision = 10,scale = 2)
    private BigDecimal montantTotal;

    // client
    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "client_id",nullable = false)
    private Client client;

    // event
    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "event_id",nullable = false)
    private Event event;

    // promotion
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    // paiment
    @OneToOne(mappedBy = "reservation")
    private Paiement paiement;

    // ligne reservation
    @OneToMany(mappedBy = "reservation",cascade = CascadeType.ALL,orphanRemoval = true)
    @Builder.Default
    private List<LigneReservation> lignes = new ArrayList<>();
}
