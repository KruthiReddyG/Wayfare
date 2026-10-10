package com.example.carpool;

import com.example.carpool.dto.ApiModels.*;
import com.example.carpool.entity.*;
import com.example.carpool.exception.ApiException;
import com.example.carpool.repository.*;
import com.example.carpool.service.CarpoolService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarpoolServiceTest {
    @Mock private UserRepository users;
    @Mock private VehicleRepository vehicles;
    @Mock private RideRepository rides;
    @Mock private RideRequestRepository requests;
    @Mock private BookingRepository bookings;
    @Mock private PaymentRepository payments;
    @Mock private RatingRepository ratings;
    @Mock private ComplaintRepository complaints;
    @Mock private NotificationRepository notifications;

    @InjectMocks private CarpoolService service;

    @Test
    void decideRequest_acceptsRequestAndReducesSeatCount() {
        User driver = user(1L, "Driver One", "driver@test.com", User.Role.DRIVER);
        User passenger = user(2L, "Passenger One", "passenger@test.com", User.Role.PASSENGER);
        Ride ride = ride(10L, driver, 2, Ride.Status.OPEN);
        RideRequest request = new RideRequest();
        request.setId(50L);
        request.setRide(ride);
        request.setPassenger(passenger);
        request.setStatus(RideRequest.Status.PENDING);

        when(users.findByEmailIgnoreCase("driver@test.com")).thenReturn(Optional.of(driver));
        when(requests.findById(50L)).thenReturn(Optional.of(request));
        when(rides.findByIdForUpdate(10L)).thenReturn(Optional.of(ride));
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notifications.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.decideRequest("driver@test.com", 50L, true);

        assertEquals(RideRequest.Status.ACCEPTED, request.getStatus());
        assertEquals(1, ride.getAvailableSeats());
        verify(bookings).save(any(Booking.class));
        verify(payments).save(any(Payment.class));
    }

    @Test
    void createRide_allowsDriverToPublishRideWithOwnedVehicle() {
        User driver = user(1L, "Driver One", "driver@test.com", User.Role.DRIVER);
        Vehicle vehicle = new Vehicle();
        vehicle.setId(4L);
        vehicle.setOwner(driver);
        vehicle.setMakeModel("Toyota Innova");
        vehicle.setRegistrationNumber("TS09XY1234");
        vehicle.setSeatCapacity(6);

        when(users.findByEmailIgnoreCase("driver@test.com")).thenReturn(Optional.of(driver));
        when(vehicles.findById(4L)).thenReturn(Optional.of(vehicle));
        when(rides.save(any(Ride.class))).thenAnswer(invocation -> {
            Ride saved = invocation.getArgument(0);
            saved.setId(77L);
            return saved;
        });

        RideView result = service.createRide("driver@test.com", new CreateRideRequest(
                4L, "Hyderabad", "Warangal", LocalDate.now().plusDays(1), LocalTime.of(18, 30), 4,
                new BigDecimal("650.00"), "Evening ride"));

        assertEquals(77L, result.id());
        assertEquals("Hyderabad", result.source());
        assertEquals(4, result.availableSeats());
    }

    @Test
    void createRequest_rejectsWhenRideHasNoSeatsAvailable() {
        User driver = user(1L, "Driver One", "driver@test.com", User.Role.DRIVER);
        User passenger = user(2L, "Passenger One", "passenger@test.com", User.Role.PASSENGER);
        Ride ride = ride(11L, driver, 0, Ride.Status.OPEN);

        when(users.findByEmailIgnoreCase("passenger@test.com")).thenReturn(Optional.of(passenger));
        when(rides.findById(11L)).thenReturn(Optional.of(ride));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.createRequest("passenger@test.com", new RideRequestInput(11L, "Please accept")));

        assertEquals("This ride has no available seats", ex.getMessage());
    }

    @Test
    void cancelRequest_marksPendingRequestCancelled() {
        User passenger = user(2L, "Passenger One", "passenger@test.com", User.Role.PASSENGER);
        RideRequest request = new RideRequest();
        request.setId(81L);
        request.setPassenger(passenger);
        request.setStatus(RideRequest.Status.PENDING);

        when(users.findByEmailIgnoreCase("passenger@test.com")).thenReturn(Optional.of(passenger));
        when(requests.findById(81L)).thenReturn(Optional.of(request));

        service.cancelRequest("passenger@test.com", 81L);

        assertEquals(RideRequest.Status.CANCELLED, request.getStatus());
    }

    @Test
    void cancelBooking_restoresSeatAndRefundsPayment() {
        User driver = user(1L, "Driver One", "driver@test.com", User.Role.DRIVER);
        User passenger = user(2L, "Passenger One", "passenger@test.com", User.Role.PASSENGER);
        Ride ride = ride(12L, driver, 1, Ride.Status.OPEN);
        RideRequest request = new RideRequest();
        request.setId(70L);
        request.setRide(ride);
        request.setPassenger(passenger);
        Booking booking = new Booking();
        booking.setId(88L);
        booking.setRequest(request);
        booking.setStatus(Booking.Status.CONFIRMED);
        Payment payment = new Payment();
        payment.setId(99L);
        payment.setBooking(booking);
        payment.setStatus(Payment.Status.PENDING);

        when(users.findByEmailIgnoreCase("passenger@test.com")).thenReturn(Optional.of(passenger));
        when(bookings.findById(88L)).thenReturn(Optional.of(booking));
        when(rides.findByIdForUpdate(12L)).thenReturn(Optional.of(ride));
        when(payments.findByBookingId(88L)).thenReturn(Optional.of(payment));

        service.cancelBooking("passenger@test.com", 88L);

        assertEquals(Booking.Status.CANCELLED, booking.getStatus());
        assertEquals(2, ride.getAvailableSeats());
        assertEquals(Payment.Status.REFUNDED, payment.getStatus());
    }

    @Test
    void adminCancelRide_cancelsConfirmedBookingsAndRefundsPayments() {
        User driver = user(1L, "Driver One", "driver@test.com", User.Role.DRIVER);
        User passenger = user(2L, "Passenger One", "passenger@test.com", User.Role.PASSENGER);
        Ride ride = ride(18L, driver, 1, Ride.Status.OPEN);
        RideRequest request = new RideRequest();
        request.setId(91L);
        request.setRide(ride);
        request.setPassenger(passenger);
        Booking booking = new Booking();
        booking.setId(92L);
        booking.setRequest(request);
        booking.setStatus(Booking.Status.CONFIRMED);
        Payment payment = new Payment();
        payment.setId(93L);
        payment.setBooking(booking);
        payment.setStatus(Payment.Status.PENDING);

        when(rides.findById(18L)).thenReturn(Optional.of(ride));
        when(bookings.findByRequestRideDriverIdOrderByConfirmedAtDesc(1L)).thenReturn(java.util.List.of(booking));
        when(payments.findByBookingId(92L)).thenReturn(Optional.of(payment));
        when(notifications.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.adminCancelRide(18L);

        assertEquals(Ride.Status.CANCELLED, ride.getStatus());
        assertEquals(Booking.Status.CANCELLED, booking.getStatus());
        assertEquals(Payment.Status.REFUNDED, payment.getStatus());
    }

    @Test
    void rate_rejectsNonCompletedBookings() {
        User driver = user(1L, "Driver One", "driver@test.com", User.Role.DRIVER);
        User passenger = user(2L, "Passenger One", "passenger@test.com", User.Role.PASSENGER);
        Ride ride = ride(13L, driver, 0, Ride.Status.COMPLETED);
        RideRequest request = new RideRequest();
        request.setId(75L);
        request.setRide(ride);
        request.setPassenger(passenger);
        Booking booking = new Booking();
        booking.setId(90L);
        booking.setRequest(request);
        booking.setStatus(Booking.Status.CONFIRMED);

        when(users.findByEmailIgnoreCase("passenger@test.com")).thenReturn(Optional.of(passenger));
        when(bookings.findById(90L)).thenReturn(Optional.of(booking));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.rate("passenger@test.com", new RatingInput(90L, 5, "Great ride")));

        assertEquals("You can rate a ride after it is completed", ex.getMessage());
    }

    private User user(Long id, String name, String email, User.Role role) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setPassword("secret");
        user.setRole(role);
        user.setActive(true);
        return user;
    }

    private Ride ride(Long id, User driver, int seats, Ride.Status status) {
        Ride ride = new Ride();
        ride.setId(id);
        ride.setDriver(driver);
        ride.setVehicle(new Vehicle());
        ride.getVehicle().setMakeModel("Hyundai i20");
        ride.getVehicle().setRegistrationNumber("TS09AB1234");
        ride.setSource("Hyderabad");
        ride.setDestination("Warangal");
        ride.setTravelDate(LocalDate.now().plusDays(2));
        ride.setDepartureTime(LocalTime.of(8, 30));
        ride.setTotalSeats(2);
        ride.setAvailableSeats(seats);
        ride.setEstimatedCost(new BigDecimal("500.00"));
        ride.setStatus(status);
        return ride;
    }
}
