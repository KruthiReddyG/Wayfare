package com.example.carpool.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "ride_requests", uniqueConstraints = @UniqueConstraint(columnNames = {"ride_id", "passenger_id"}))
@Getter @Setter @NoArgsConstructor
public class RideRequest {
    public enum Status { PENDING, ACCEPTED, REJECTED, CANCELLED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "ride_id") private Ride ride;
    @ManyToOne(optional = false) @JoinColumn(name = "passenger_id") private User passenger;
    @Column(length = 300) private String message;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status = Status.PENDING;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
}