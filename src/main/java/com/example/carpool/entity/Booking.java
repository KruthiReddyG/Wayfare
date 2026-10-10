package com.example.carpool.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "bookings")
@Getter @Setter @NoArgsConstructor
public class Booking {
    public enum Status { CONFIRMED, CANCELLED, COMPLETED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(optional = false) @JoinColumn(name = "request_id", unique = true) private RideRequest request;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status = Status.CONFIRMED;
    @Column(nullable = false) private LocalDateTime confirmedAt = LocalDateTime.now();
}