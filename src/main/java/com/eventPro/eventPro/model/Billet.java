package com.eventPro.eventPro.model;

import com.eventPro.eventPro.model.enums.StatutBillet;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "billets")
@Getter
@Setter
public class Billet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false,unique = true)
    private String code ;

    @Column(nullable = false)
    private LocalDateTime dateEmission ;

    private LocalDateTime dateControle ;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StatutBillet statut;

    // ligneReservation
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ligne_reservation_id", nullable = false)
    private LigneReservation ligneReservation;

}
