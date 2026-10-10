package com.example.carpool.repository;

import com.example.carpool.entity.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;

public interface RideRepository extends JpaRepository<Ride, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Ride r where r.id = :id")
    java.util.Optional<Ride> findByIdForUpdate(@Param("id") Long id);
    List<Ride> findByTravelDateAndStatusAndAvailableSeatsGreaterThanEqual(LocalDate date, Ride.Status status, int seats);
    List<Ride> findByDriverIdOrderByTravelDateDescDepartureTimeDesc(Long driverId);
    List<Ride> findByStatusOrderByTravelDateAscDepartureTimeAsc(Ride.Status status);
}