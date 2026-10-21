package com.eventPro.eventPro.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ligne_reservations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LigneReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Integer quantite;

    @Column(nullable = false,precision = 10, scale = 2)
    private BigDecimal prixUnitaire;

    @Column(nullable = false,precision = 10, scale = 2)
    private BigDecimal sousTotal ;

    // reservation
    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "reservation_id",nullable = false)
    private Reservation reservation;

    //
    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "categorie_billet_id",nullable = false)
    private CategorieBillet categorieBillet;
}
