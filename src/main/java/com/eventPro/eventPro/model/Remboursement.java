package com.eventPro.eventPro.model;

import com.eventPro.eventPro.model.enums.StatutRemboursement;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "remboursements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Remboursement{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montant ;

    @Column(nullable = false)
    private String motif ;

    @Column(nullable = false)
    private LocalDateTime dateDemande ;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StatutRemboursement statut ;

    @Column(length = 255)
    private String decision; // ??

    // paiment
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paiement_id", nullable = false)
    private Paiement paiement;




    public void approuver() { /////
        if (statut == StatutRemboursement.APPROVED) {
            throw new IllegalStateException("Ce remboursement est déjà approuvé");
        }
        statut = StatutRemboursement.APPROVED;
    }

    public void refuser() {   /////:
        if (statut == StatutRemboursement.APPROVED) {
            throw new IllegalStateException("Un remboursement approuvé ne peut pas être refusé");
        }
        statut = StatutRemboursement.REJECTED;
    }
}
