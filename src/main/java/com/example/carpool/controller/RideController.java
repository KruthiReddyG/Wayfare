package com.example.carpool.controller;

import com.example.carpool.dto.ApiModels.*;
import com.example.carpool.entity.Vehicle;
import com.example.carpool.service.CarpoolService;
import com.example.carpool.service.MatchingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/rides")
public class RideController {
    private final CarpoolService service;
    private final MatchingService matching;
    public RideController(CarpoolService service, MatchingService matching) { this.service = service; this.matching = matching; }
    @GetMapping public List<RideView> openRides() { return service.openRides(); }
    @GetMapping("/{id}") public RideView getRide(@PathVariable Long id) { return service.ride(id); }
    @PostMapping("/search") public List<RideView> search(@Valid @RequestBody SearchRideRequest request) { return matching.search(request); }
    @GetMapping("/mine") @PreAuthorize("hasRole('DRIVER')") public List<RideView> mine(Authentication auth) { return service.driverRides(auth.getName()); }
    @PostMapping @PreAuthorize("hasRole('DRIVER')") public RideView create(@Valid @RequestBody CreateRideRequest input, Authentication auth) { return service.createRide(auth.getName(), input); }
    @PostMapping("/vehicles") @PreAuthorize("hasRole('DRIVER')") public Vehicle addVehicle(@Valid @RequestBody CreateVehicleRequest input, Authentication auth) { return service.addVehicle(auth.getName(), input); }
    @GetMapping("/vehicles/mine") @PreAuthorize("hasRole('DRIVER')") public List<Vehicle> vehicles(Authentication auth) { return service.vehicles(auth.getName()); }
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('DRIVER')") public void cancel(@PathVariable Long id, Authentication auth) { service.cancelRide(auth.getName(), id); }
    @PostMapping("/{id}/complete") @PreAuthorize("hasRole('DRIVER')") public void complete(@PathVariable Long id, Authentication auth) { service.completeRide(auth.getName(), id); }
}