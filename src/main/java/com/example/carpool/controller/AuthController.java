package com.example.carpool.controller;

import com.example.carpool.dto.ApiModels.*;
import com.example.carpool.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }
    @PostMapping("/register") public AuthResponse register(@Valid @RequestBody RegisterRequest input) { return auth.register(input); }
    @PostMapping("/login") public AuthResponse login(@Valid @RequestBody LoginRequest input) { return auth.login(input); }
}