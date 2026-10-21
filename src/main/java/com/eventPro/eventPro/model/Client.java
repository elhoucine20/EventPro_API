package com.eventPro.eventPro.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "clients")
public class Client extends User{

    @Column(nullable = false)
    private String phone;

    @OneToMany(mappedBy = "client")
    @Builder.Default
    private List<Reservation> reservations = new ArrayList<>();
}
