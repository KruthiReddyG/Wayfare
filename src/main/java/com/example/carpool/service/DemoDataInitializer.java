package com.example.carpool.service;

import com.example.carpool.entity.*;
import com.example.carpool.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Component
public class DemoDataInitializer implements CommandLineRunner {
    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final RideRepository rides;
    private final PasswordEncoder encoder;
    private final boolean enabled;
    public DemoDataInitializer(UserRepository users, VehicleRepository vehicles, RideRepository rides,
                               PasswordEncoder encoder, @Value("${app.seed-demo:true}") boolean enabled) {
        this.users = users; this.vehicles = vehicles; this.rides = rides; this.encoder = encoder; this.enabled = enabled;
    }
    @Override
    public void run(String... args) {
        if (!enabled || users.existsByEmailIgnoreCase("admin@smartcarpool.local")) return;
        User driver = addUser("Demo Driver", "driver@smartcarpool.local", User.Role.DRIVER, "+91 90000 11111");
        addUser("Demo Passenger", "passenger@smartcarpool.local", User.Role.PASSENGER, "+91 90000 22222");
        addUser("Platform Admin", "admin@smartcarpool.local", User.Role.ADMIN, "+91 90000 33333");
        Vehicle vehicle = new Vehicle(); vehicle.setOwner(driver); vehicle.setMakeModel("Hyundai i20");
        vehicle.setRegistrationNumber("TS09DEMO01"); vehicle.setSeatCapacity(4); vehicle.setColor("White"); vehicles.save(vehicle);
        Ride ride = new Ride(); ride.setDriver(driver); ride.setVehicle(vehicle); ride.setSource("Hyderabad");
        ride.setDestination("Warangal"); ride.setTravelDate(LocalDate.now().plusDays(2)); ride.setDepartureTime(LocalTime.of(8, 0));
        ride.setTotalSeats(3); ride.setAvailableSeats(3); ride.setEstimatedCost(new BigDecimal("900.00"));
        ride.setNotes("Pickup near the city center"); rides.save(ride);
    }
    private User addUser(String name, String email, User.Role role, String phone) {
        User user = new User(); user.setName(name); user.setEmail(email); user.setPassword(encoder.encode("Carpool123!"));
        user.setRole(role); user.setPhone(phone); return users.save(user);
    }
}