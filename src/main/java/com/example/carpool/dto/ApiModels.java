package com.example.carpool.dto;

import com.example.carpool.entity.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class ApiModels {
    private ApiModels() { }
    public record RegisterRequest(@NotBlank @Size(max = 100) String name, @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password, @NotNull User.Role role, @Size(max = 24) String phone) { }
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) { }
    public record AuthResponse(String token, Long id, String name, String email, User.Role role) { }
    public record CreateVehicleRequest(@NotBlank String makeModel, @NotBlank String registrationNumber,
        @Min(1) @Max(12) int seatCapacity, String color) { }
    public record CreateRideRequest(@NotNull Long vehicleId, @NotBlank String source, @NotBlank String destination,
        @NotNull @FutureOrPresent LocalDate travelDate, @NotNull LocalTime departureTime,
        @Min(1) @Max(12) int availableSeats, @NotNull @DecimalMin("0.01") BigDecimal estimatedCost, String notes) { }
    public record SearchRideRequest(@NotBlank String source, @NotBlank String destination,
        @NotNull @FutureOrPresent LocalDate travelDate, @NotNull LocalTime preferredTime, @Min(1) @Max(12) int seats) { }
    public record RideView(Long id, Long driverId, String driverName, String source, String destination,
        LocalDate travelDate, LocalTime departureTime, int availableSeats, BigDecimal estimatedCost,
        String vehicle, String vehicleRegistration, String status, double matchScore) {
        public static RideView from(Ride ride, double score) {
            return new RideView(ride.getId(), ride.getDriver().getId(), ride.getDriver().getName(), ride.getSource(),
                ride.getDestination(), ride.getTravelDate(), ride.getDepartureTime(), ride.getAvailableSeats(),
                ride.getEstimatedCost(), ride.getVehicle().getMakeModel(), ride.getVehicle().getRegistrationNumber(),
                ride.getStatus().name(), score);
        }
    }
    public record RideRequestInput(@NotNull Long rideId, @Size(max = 300) String message) { }
    public record RequestDecision(@NotNull Boolean accepted) { }
    public record RequestView(Long id, Long rideId, String passengerName, String rideRoute,
        String message, String status, LocalDateTime createdAt) { }
    public record RatingInput(@NotNull Long bookingId, @Min(1) @Max(5) int stars, @Size(max = 1000) String review) { }
    public record RatingView(Long id, String passengerName, int stars, String review, LocalDateTime createdAt) { }
    public record ComplaintInput(@NotBlank @Size(max = 1200) String description, Long rideId) { }
    public record ProfileUpdate(@NotBlank @Size(max = 100) String name, @Size(max = 24) String phone) { }
    public record DashboardView(long users, long rides, long bookings, long openRequests) { }
}