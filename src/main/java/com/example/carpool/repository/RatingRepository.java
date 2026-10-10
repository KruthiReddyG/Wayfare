package com.example.carpool.repository;

import com.example.carpool.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RatingRepository extends JpaRepository<Rating, Long> {
    List<Rating> findByDriverIdOrderByCreatedAtDesc(Long driverId);
    boolean existsByBookingIdAndPassengerId(Long bookingId, Long passengerId);
}