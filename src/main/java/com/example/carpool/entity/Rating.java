package com.example.carpool.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "ratings", uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id", "passenger_id"}))
@Getter @Setter @NoArgsConstructor
public class Rating {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "booking_id") private Booking booking;
    @ManyToOne(optional = false) @JoinColumn(name = "passenger_id") private User passenger;
    @ManyToOne(optional = false) @JoinColumn(name = "driver_id") private User driver;
    @Column(nullable = false) private int stars;
    @Column(length = 1000) private String review;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
}