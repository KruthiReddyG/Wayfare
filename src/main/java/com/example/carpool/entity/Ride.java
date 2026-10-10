package com.example.carpool.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity @Table(name = "rides", indexes = @Index(name = "idx_ride_schedule", columnList = "travel_date,departure_time"))
@Getter @Setter @NoArgsConstructor
public class Ride {
    public enum Status { OPEN, FULL, CANCELLED, COMPLETED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "driver_id") private User driver;
    @ManyToOne(optional = false) @JoinColumn(name = "vehicle_id") private Vehicle vehicle;
    @Column(nullable = false, length = 120) private String source;
    @Column(nullable = false, length = 120) private String destination;
    @Column(name = "travel_date", nullable = false) private LocalDate travelDate;
    @Column(name = "departure_time", nullable = false) private LocalTime departureTime;
    @Column(nullable = false) private int totalSeats;
    @Column(nullable = false) private int availableSeats;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal estimatedCost;
    @Column(length = 500) private String notes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status = Status.OPEN;
}