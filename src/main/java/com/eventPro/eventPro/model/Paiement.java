package com.eventPro.eventPro.model;

import com.eventPro.eventPro.model.enums.StatutPaiement;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "paiements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Paiement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true,nullable = false)
    private String reference;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false)
    private LocalDateTime datePaiement ;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StatutPaiement statut;

    @OneToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "reservation_id",nullable = false,unique = true)
    private Reservation reservation;
}
