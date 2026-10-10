package com.example.carpool.repository;

import com.example.carpool.entity.RideRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RideRequestRepository extends JpaRepository<RideRequest, Long> {
    List<RideRequest> findByRideDriverIdOrderByCreatedAtDesc(Long driverId);
    List<RideRequest> findByPassengerIdOrderByCreatedAtDesc(Long passengerId);
    boolean existsByRideIdAndPassengerId(Long rideId, Long passengerId);
}