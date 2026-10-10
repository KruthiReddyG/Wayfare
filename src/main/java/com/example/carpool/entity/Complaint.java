package com.example.carpool.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "complaints")
@Getter @Setter @NoArgsConstructor
public class Complaint {
    public enum Status { OPEN, IN_REVIEW, RESOLVED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "user_id") private User user;
    @ManyToOne @JoinColumn(name = "ride_id") private Ride ride;
    @Column(nullable = false, length = 1200) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status = Status.OPEN;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
}