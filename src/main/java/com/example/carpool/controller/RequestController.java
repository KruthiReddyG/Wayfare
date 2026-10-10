package com.example.carpool.controller;

import com.example.carpool.dto.ApiModels.*;
import com.example.carpool.entity.Booking;
import com.example.carpool.entity.RideRequest;
import com.example.carpool.service.CarpoolService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api")
public class RequestController {
    private final CarpoolService service;
    public RequestController(CarpoolService service) { this.service = service; }
    @PostMapping("/requests") @PreAuthorize("hasRole('PASSENGER')") public RideRequest request(@Valid @RequestBody RideRequestInput input, Authentication auth) { return service.createRequest(auth.getName(), input); }
    @GetMapping("/requests/inbox") @PreAuthorize("hasRole('DRIVER')") public List<RequestView> inbox(Authentication auth) { return service.inbox(auth.getName()); }
    @GetMapping("/requests/mine") @PreAuthorize("hasRole('PASSENGER')") public List<RequestView> mine(Authentication auth) { return service.passengerRequests(auth.getName()); }
    @PatchMapping("/requests/{id}") @PreAuthorize("hasRole('DRIVER')") public Map<String, String> decide(@PathVariable Long id, @Valid @RequestBody RequestDecision input, Authentication auth) {
        service.decideRequest(auth.getName(), id, input.accepted()); return Map.of("status", input.accepted() ? "ACCEPTED" : "REJECTED");
    }
    @DeleteMapping("/requests/{id}") @PreAuthorize("hasRole('PASSENGER')") public void cancelRequest(@PathVariable Long id, Authentication auth) { service.cancelRequest(auth.getName(), id); }
    @GetMapping("/bookings/mine") @PreAuthorize("hasAnyRole('PASSENGER','DRIVER')") public List<Booking> bookings(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_DRIVER"))
            ? service.driverBookings(auth.getName()) : service.passengerBookings(auth.getName());
    }
    @DeleteMapping("/bookings/{id}") @PreAuthorize("hasRole('PASSENGER')") public void cancelBooking(@PathVariable Long id, Authentication auth) { service.cancelBooking(auth.getName(), id); }
}