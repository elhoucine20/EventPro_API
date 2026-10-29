package com.eventPro.eventPro.model;

import com.eventPro.eventPro.model.enums.TypeReduction;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import java.util.UUID;

@Entity
//@Table(name = "promotions")
// unique sur event_id et code //if promotion deha exist dans
@Table(name = "promotions",uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "code"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TypeReduction typeReduction;

    @Column(nullable = false,precision = 10, scale = 2)
    private BigDecimal valeurReduction;

    @Column(nullable = false)
    //@JsonFormat(shape = JsonFormat.Shape.STRING,pattern = "dd-MM-yyyy")
    private LocalDate dateDebut;

    @Column(nullable = false)
    private LocalDate dateFin ;

    @Column(nullable = false)
    private Integer maxUtilisations ;

    @Column(nullable = false)
    @Builder.Default
    private Integer nombreUtilisations = 0;

   // @OneToMany(mappedBy = "promotion")
    //@Builder.Default
    //private List<Event> events = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "event_id",nullable = false)
    private Event event;


    public boolean estValid(){
        LocalDate today = LocalDate.now();

        // est-ce que la promo est rest valide
        return (   !today.isBefore(dateDebut) &&
                   !today.isAfter(dateFin) &&
                   nombreUtilisations < maxUtilisations
        );
    }

    public BigDecimal calculerReduction(BigDecimal montant){
        BigDecimal reduction = (typeReduction == TypeReduction.PERCENTAGE) ?
                montant.multiply(valeurReduction).divide(BigDecimal.valueOf(100),2, RoundingMode.HALF_UP) : valeurReduction;

        return reduction.min(montant);
    }
}
