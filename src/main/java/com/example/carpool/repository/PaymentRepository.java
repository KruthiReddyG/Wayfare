package com.example.carpool.repository;

import com.example.carpool.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
	java.util.Optional<Payment> findByBookingId(Long bookingId);
}