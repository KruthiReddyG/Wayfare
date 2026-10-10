package com.example.carpool.controller;

import com.example.carpool.dto.ApiModels.DashboardView;
import com.example.carpool.dto.ApiModels.RideView;
import com.example.carpool.entity.*;
import com.example.carpool.service.CarpoolService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/admin") @PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final CarpoolService service;
    public AdminController(CarpoolService service) { this.service = service; }
    @GetMapping("/dashboard") public DashboardView dashboard() { return service.dashboard(); }
    @GetMapping("/users") public List<User> users() { return service.adminUsers(); }
    @PatchMapping("/users/{id}/active") public Map<String, Boolean> setActive(@PathVariable Long id, @RequestBody Map<String, Boolean> input) {
        service.setUserActive(id, Boolean.TRUE.equals(input.get("active"))); return Map.of("active", Boolean.TRUE.equals(input.get("active")));
    }
    @GetMapping("/rides") public List<RideView> rides() { return service.adminRides(); }
    @DeleteMapping("/rides/{id}") public void cancelRide(@PathVariable Long id) { service.adminCancelRide(id); }
    @GetMapping("/bookings") public List<Booking> bookings() { return service.adminBookings(); }
    @GetMapping("/complaints") public List<Complaint> complaints() { return service.adminComplaints(); }
    @PatchMapping("/complaints/{id}/status") public Complaint setComplaintStatus(@PathVariable Long id, @RequestBody Map<String, String> input) {
        return service.setComplaintStatus(id, input.get("status"));
    }
    @GetMapping("/ratings") public List<Rating> ratings() { return service.adminRatings(); }
}