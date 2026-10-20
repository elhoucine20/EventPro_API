package com.eventPro.eventPro.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "clients")
public class Client extends User{

    @Column(nullable = false)
    private String phone;

    @OneToMany(mappedBy = "client")
    private List<Reservation> reservations = new ArrayList<>();
}
