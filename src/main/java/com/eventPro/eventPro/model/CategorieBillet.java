package com.eventPro.eventPro.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "categorieBillets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategorieBillet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false,precision = 10,scale = 2)
    private BigDecimal prixUnitaire;

    @Column(nullable = false)
    private Long quantiteTotale;

    @Column(nullable = false)
    private Integer quotaMaxParClient;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "event_id",nullable = false)
    private Event event;

}
