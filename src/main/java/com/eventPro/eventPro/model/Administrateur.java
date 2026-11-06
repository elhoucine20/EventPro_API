package com.eventPro.eventPro.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "administrateurs")
@SuperBuilder
@NoArgsConstructor
public class Administrateur extends User{
}
