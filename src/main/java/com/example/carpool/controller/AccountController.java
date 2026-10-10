package com.example.carpool.controller;

import com.example.carpool.dto.ApiModels.*;
import com.example.carpool.entity.*;
import com.example.carpool.service.CarpoolService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api")
public class AccountController {
    private final CarpoolService service;
    public AccountController(CarpoolService service) { this.service = service; }
    @GetMapping("/profile") public User profile(Authentication auth) { return service.account(auth.getName()); }
    @PutMapping("/profile") public User updateProfile(@Valid @RequestBody ProfileUpdate input, Authentication auth) { return service.updateProfile(auth.getName(), input); }
    @GetMapping("/notifications") public List<Notification> notifications(Authentication auth) { return service.notifications(auth.getName()); }
    @PostMapping("/complaints") public Complaint complaint(@Valid @RequestBody ComplaintInput input, Authentication auth) { return service.createComplaint(auth.getName(), input); }
    @PostMapping("/ratings") @PreAuthorize("hasRole('PASSENGER')") public RatingView rate(@Valid @RequestBody RatingInput input, Authentication auth) { return service.rate(auth.getName(), input); }
    @GetMapping("/ratings/mine") @PreAuthorize("hasRole('DRIVER')") public List<RatingView> ratings(Authentication auth) { return service.driverRatings(auth.getName()); }
}