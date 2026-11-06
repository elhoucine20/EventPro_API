package com.eventPro.eventPro.model;


import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "agents_controle")
@SuperBuilder
@NoArgsConstructor
public class AgentControle extends User{
}
