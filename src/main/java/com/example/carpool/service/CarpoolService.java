package com.example.carpool.service;

import com.example.carpool.dto.ApiModels.*;
import com.example.carpool.entity.*;
import com.example.carpool.exception.ApiException;
import com.example.carpool.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
public class CarpoolService {
    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final RideRepository rides;
    private final RideRequestRepository requests;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final RatingRepository ratings;
    private final ComplaintRepository complaints;
    private final NotificationRepository notifications;

    public CarpoolService(UserRepository users, VehicleRepository vehicles, RideRepository rides, RideRequestRepository requests,
                          BookingRepository bookings, PaymentRepository payments, RatingRepository ratings,
                          ComplaintRepository complaints, NotificationRepository notifications) {
        this.users = users; this.vehicles = vehicles; this.rides = rides; this.requests = requests;
        this.bookings = bookings; this.payments = payments; this.ratings = ratings;
        this.complaints = complaints; this.notifications = notifications;
    }

    public User account(String email) { return users.findByEmailIgnoreCase(email).orElseThrow(() -> missing("Account not found")); }
    public List<Vehicle> vehicles(String email) { return vehicles.findByOwnerId(account(email).getId()); }
    @Transactional
    public Vehicle addVehicle(String email, CreateVehicleRequest input) {
        User owner = requireRole(email, User.Role.DRIVER);
        Vehicle vehicle = new Vehicle(); vehicle.setOwner(owner); vehicle.setMakeModel(input.makeModel());
        vehicle.setRegistrationNumber(input.registrationNumber().trim().toUpperCase());
        vehicle.setSeatCapacity(input.seatCapacity()); vehicle.setColor(input.color());
        return vehicles.save(vehicle);
    }
    @Transactional
    public RideView createRide(String email, CreateRideRequest input) {
        User driver = requireRole(email, User.Role.DRIVER);
        Vehicle vehicle = vehicles.findById(input.vehicleId()).orElseThrow(() -> missing("Vehicle not found"));
        if (!vehicle.getOwner().getId().equals(driver.getId())) throw forbidden("Vehicle does not belong to this driver");
        if (input.availableSeats() > vehicle.getSeatCapacity()) throw bad("Available seats exceed vehicle capacity");
        Ride ride = new Ride(); ride.setDriver(driver); ride.setVehicle(vehicle); ride.setSource(input.source().trim());
        ride.setDestination(input.destination().trim()); ride.setTravelDate(input.travelDate()); ride.setDepartureTime(input.departureTime());
        ride.setTotalSeats(input.availableSeats()); ride.setAvailableSeats(input.availableSeats());
        ride.setEstimatedCost(input.estimatedCost()); ride.setNotes(input.notes());
        return RideView.from(rides.save(ride), 0);
    }
    public List<RideView> openRides() {
        return rides.findByStatusOrderByTravelDateAscDepartureTimeAsc(Ride.Status.OPEN).stream()
            .filter(ride -> ride.getAvailableSeats() > 0).map(ride -> RideView.from(ride, 0)).toList();
    }
    public RideView ride(Long id) { return RideView.from(findRide(id), 0); }
    public List<RideView> driverRides(String email) {
        return rides.findByDriverIdOrderByTravelDateDescDepartureTimeDesc(account(email).getId()).stream()
            .map(ride -> RideView.from(ride, 0)).toList();
    }
    @Transactional
    public void cancelRide(String email, Long id) {
        User driver = requireRole(email, User.Role.DRIVER); Ride ride = findRide(id);
        if (!ride.getDriver().getId().equals(driver.getId())) throw forbidden("This ride belongs to another driver");
        if (ride.getStatus() == Ride.Status.COMPLETED) throw bad("Completed rides cannot be cancelled");
        ride.setStatus(Ride.Status.CANCELLED);
        bookings.findByRequestRideDriverIdOrderByConfirmedAtDesc(driver.getId()).stream()
            .filter(booking -> booking.getRequest().getRide().getId().equals(id) && booking.getStatus() == Booking.Status.CONFIRMED)
            .forEach(booking -> {
                booking.setStatus(Booking.Status.CANCELLED);
                payments.findByBookingId(booking.getId()).ifPresent(payment -> payment.setStatus(Payment.Status.REFUNDED));
                notify(booking.getRequest().getPassenger(), "A ride you booked was cancelled by the driver.");
            });
    }
    @Transactional
    public void completeRide(String email, Long id) {
        User driver = requireRole(email, User.Role.DRIVER); Ride ride = findRide(id);
        if (!ride.getDriver().getId().equals(driver.getId())) throw forbidden("This ride belongs to another driver");
        if (ride.getStatus() == Ride.Status.CANCELLED) throw bad("Cancelled rides cannot be completed");
        ride.setStatus(Ride.Status.COMPLETED);
        bookings.findByRequestRideDriverIdOrderByConfirmedAtDesc(driver.getId()).stream()
            .filter(booking -> booking.getRequest().getRide().getId().equals(id) && booking.getStatus() == Booking.Status.CONFIRMED)
            .forEach(booking -> booking.setStatus(Booking.Status.COMPLETED));
    }
    @Transactional
    public RideRequest createRequest(String email, RideRequestInput input) {
        User passenger = requireRole(email, User.Role.PASSENGER); Ride ride = findRide(input.rideId());
        if (ride.getStatus() != Ride.Status.OPEN || ride.getAvailableSeats() < 1) throw bad("This ride has no available seats");
        if (ride.getDriver().getId().equals(passenger.getId())) throw bad("You cannot request your own ride");
        if (requests.existsByRideIdAndPassengerId(ride.getId(), passenger.getId())) throw new ApiException(HttpStatus.CONFLICT, "You already requested this ride");
        RideRequest request = new RideRequest(); request.setRide(ride); request.setPassenger(passenger); request.setMessage(input.message());
        RideRequest saved = requests.save(request); notify(ride.getDriver(), passenger.getName() + " requested a seat on your ride.");
        return saved;
    }
    public List<RequestView> inbox(String email) {
        return requests.findByRideDriverIdOrderByCreatedAtDesc(account(email).getId()).stream().map(this::requestView).toList();
    }
    public List<RequestView> passengerRequests(String email) {
        return requests.findByPassengerIdOrderByCreatedAtDesc(account(email).getId()).stream().map(this::requestView).toList();
    }
    @Transactional
    public void decideRequest(String email, Long requestId, boolean accepted) {
        User driver = requireRole(email, User.Role.DRIVER);
        RideRequest request = requests.findById(requestId).orElseThrow(() -> missing("Ride request not found"));
        Ride ride = rides.findByIdForUpdate(request.getRide().getId()).orElseThrow(() -> missing("Ride not found"));
        if (!ride.getDriver().getId().equals(driver.getId())) throw forbidden("This request belongs to another driver");
        if (request.getStatus() != RideRequest.Status.PENDING) throw bad("This request has already been handled");
        if (accepted && (ride.getStatus() != Ride.Status.OPEN || ride.getAvailableSeats() < 1)) throw bad("No seats remain on this ride");
        if (accepted) {
            request.setStatus(RideRequest.Status.ACCEPTED);
            ride.setAvailableSeats(ride.getAvailableSeats() - 1);
            if (ride.getAvailableSeats() == 0) ride.setStatus(Ride.Status.FULL);
            Booking booking = new Booking(); booking.setRequest(request); bookings.save(booking);
            Payment payment = new Payment(); payment.setBooking(booking);
            payment.setAmount(ride.getEstimatedCost().divide(BigDecimal.valueOf(Math.max(ride.getTotalSeats(), 1)), 2, java.math.RoundingMode.HALF_UP));
            payments.save(payment);
        } else request.setStatus(RideRequest.Status.REJECTED);
        notify(request.getPassenger(), accepted ? "Your ride request was accepted." : "Your ride request was declined.");
    }
    @Transactional
    public void cancelRequest(String email, Long requestId) {
        User passenger = requireRole(email, User.Role.PASSENGER);
        RideRequest request = requests.findById(requestId).orElseThrow(() -> missing("Ride request not found"));
        if (!request.getPassenger().getId().equals(passenger.getId())) throw forbidden("This request belongs to another passenger");
        if (request.getStatus() != RideRequest.Status.PENDING) throw bad("Only pending requests can be cancelled");
        request.setStatus(RideRequest.Status.CANCELLED);
    }
    public List<Booking> passengerBookings(String email) { return bookings.findByRequestPassengerIdOrderByConfirmedAtDesc(account(email).getId()); }
    public List<Booking> driverBookings(String email) { return bookings.findByRequestRideDriverIdOrderByConfirmedAtDesc(account(email).getId()); }
    @Transactional
    public void cancelBooking(String email, Long bookingId) {
        User passenger = requireRole(email, User.Role.PASSENGER);
        Booking booking = bookings.findById(bookingId).orElseThrow(() -> missing("Booking not found"));
        if (!booking.getRequest().getPassenger().getId().equals(passenger.getId())) throw forbidden("This booking belongs to another passenger");
        if (booking.getStatus() != Booking.Status.CONFIRMED) throw bad("This booking is no longer active");
        Ride ride = rides.findByIdForUpdate(booking.getRequest().getRide().getId()).orElseThrow(() -> missing("Ride not found"));
        booking.setStatus(Booking.Status.CANCELLED);
        if (ride.getStatus() != Ride.Status.CANCELLED && ride.getStatus() != Ride.Status.COMPLETED) {
            ride.setAvailableSeats(ride.getAvailableSeats() + 1); ride.setStatus(Ride.Status.OPEN);
        }
        payments.findByBookingId(bookingId).ifPresent(payment -> payment.setStatus(Payment.Status.REFUNDED));
    }
    @Transactional
    public RatingView rate(String email, RatingInput input) {
        User passenger = requireRole(email, User.Role.PASSENGER);
        Booking booking = bookings.findById(input.bookingId()).orElseThrow(() -> missing("Booking not found"));
        if (!booking.getRequest().getPassenger().getId().equals(passenger.getId())) throw forbidden("This booking belongs to another passenger");
        if (booking.getStatus() != Booking.Status.COMPLETED) throw bad("You can rate a ride after it is completed");
        if (ratings.existsByBookingIdAndPassengerId(booking.getId(), passenger.getId())) throw new ApiException(HttpStatus.CONFLICT, "This booking has already been rated");
        Rating rating = new Rating(); rating.setBooking(booking); rating.setPassenger(passenger);
        rating.setDriver(booking.getRequest().getRide().getDriver()); rating.setStars(input.stars()); rating.setReview(input.review());
        Rating saved = ratings.save(rating); return new RatingView(saved.getId(), passenger.getName(), saved.getStars(), saved.getReview(), saved.getCreatedAt());
    }
    public List<RatingView> driverRatings(String email) {
        User driver = users.findByEmailIgnoreCase(email).orElseThrow();
        return ratings.findByDriverIdOrderByCreatedAtDesc(driver.getId()).stream()
            .map(rating -> new RatingView(rating.getId(), rating.getPassenger().getName(), rating.getStars(), rating.getReview(), rating.getCreatedAt())).toList();
    }
    @Transactional
    public User updateProfile(String email, ProfileUpdate input) {
        User user = account(email); user.setName(input.name().trim()); user.setPhone(input.phone()); return users.save(user);
    }
    public List<Notification> notifications(String email) { return notifications.findByUserIdOrderByCreatedAtDesc(account(email).getId()); }
    @Transactional
    public Complaint createComplaint(String email, ComplaintInput input) {
        Complaint complaint = new Complaint(); complaint.setUser(account(email)); complaint.setDescription(input.description());
        if (input.rideId() != null) complaint.setRide(findRide(input.rideId()));
        return complaints.save(complaint);
    }
    public List<User> adminUsers() { return users.findAll(); }
    public List<RideView> adminRides() { return rides.findAll().stream().map(ride -> RideView.from(ride, 0)).toList(); }
    public List<Booking> adminBookings() { return bookings.findAll(); }
    public List<Complaint> adminComplaints() { return complaints.findAllByOrderByCreatedAtDesc(); }
    public List<Rating> adminRatings() { return ratings.findAll(); }
    @Transactional
    public void adminCancelRide(Long id) {
        Ride ride = findRide(id);
        if (ride.getStatus() == Ride.Status.COMPLETED) throw bad("Completed rides cannot be cancelled");
        ride.setStatus(Ride.Status.CANCELLED);
        bookings.findByRequestRideDriverIdOrderByConfirmedAtDesc(ride.getDriver().getId()).stream()
            .filter(booking -> booking.getRequest().getRide().getId().equals(id) && booking.getStatus() == Booking.Status.CONFIRMED)
            .forEach(booking -> {
                booking.setStatus(Booking.Status.CANCELLED);
                payments.findByBookingId(booking.getId()).ifPresent(payment -> payment.setStatus(Payment.Status.REFUNDED));
                notify(booking.getRequest().getPassenger(), "A ride you booked was cancelled by the platform.");
            });
    }
    @Transactional
    public Complaint setComplaintStatus(Long id, String status) {
        Complaint complaint = complaints.findById(id).orElseThrow(() -> missing("Complaint not found"));
        try { complaint.setStatus(Complaint.Status.valueOf(status)); }
        catch (IllegalArgumentException | NullPointerException exception) { throw bad("Status must be OPEN, IN_REVIEW, or RESOLVED"); }
        return complaint;
    }
    @Transactional public User setUserActive(Long id, boolean active) {
        User user = users.findById(id).orElseThrow(() -> missing("User not found")); user.setActive(active); return users.save(user);
    }
    public DashboardView dashboard() {
        return new DashboardView(users.count(), rides.count(), bookings.count(),
            requests.findAll().stream().filter(request -> request.getStatus() == RideRequest.Status.PENDING).count());
    }
    private User requireRole(String email, User.Role role) {
        User user = account(email); if (user.getRole() != role) throw forbidden("This action requires the " + role + " role"); return user;
    }
    private Ride findRide(Long id) { return rides.findById(id).orElseThrow(() -> missing("Ride not found")); }
    private RequestView requestView(RideRequest request) {
        Ride ride = request.getRide(); return new RequestView(request.getId(), ride.getId(), request.getPassenger().getName(),
            ride.getSource() + " to " + ride.getDestination(), request.getMessage(), request.getStatus().name(), request.getCreatedAt());
    }
    private void notify(User user, String message) { Notification n = new Notification(); n.setUser(user); n.setMessage(message); notifications.save(n); }
    private ApiException missing(String message) { return new ApiException(HttpStatus.NOT_FOUND, message); }
    private ApiException forbidden(String message) { return new ApiException(HttpStatus.FORBIDDEN, message); }
    private ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
}