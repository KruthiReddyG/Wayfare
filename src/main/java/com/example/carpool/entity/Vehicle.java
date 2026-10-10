package com.example.carpool.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "vehicles")
@Getter @Setter @NoArgsConstructor
public class Vehicle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "owner_id") private User owner;
    @Column(nullable = false, length = 80) private String makeModel;
    @Column(nullable = false, unique = true, length = 24) private String registrationNumber;
    @Column(nullable = false) private int seatCapacity;
    private String color;
}