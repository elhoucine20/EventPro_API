package com.eventPro.eventPro.model;

import com.eventPro.eventPro.model.enums.TypeReduction;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "promotions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true,nullable = false)
    private String code;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TypeReduction typeReduction;

    @Column(nullable = false,precision = 10, scale = 2)
    private BigDecimal valeurReduction;

    @Column(nullable = false)
    private LocalDate dateDebut;

    @Column(nullable = false)
    private LocalDate dateFin ;

    @Column(nullable = false)
    private Integer maxUtilisations ;

    @Column(nullable = false)
    @Builder.Default
    private Integer nombreUtilisations = 0;

    @OneToMany(mappedBy = "promotion")
    @Builder.Default
    private List<Event> events = new ArrayList<>();
}
