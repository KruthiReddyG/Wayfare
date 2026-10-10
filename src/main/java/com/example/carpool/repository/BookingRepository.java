package com.example.carpool.repository;

import com.example.carpool.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByRequestPassengerIdOrderByConfirmedAtDesc(Long passengerId);
    List<Booking> findByRequestRideDriverIdOrderByConfirmedAtDesc(Long driverId);
}