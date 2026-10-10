package com.example.carpool.service;

import com.example.carpool.dto.ApiModels.*;
import com.example.carpool.entity.User;
import com.example.carpool.exception.ApiException;
import com.example.carpool.repository.UserRepository;
import com.example.carpool.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    public AuthService(UserRepository users, PasswordEncoder encoder, AuthenticationManager authenticationManager, JwtService jwtService) {
        this.users = users; this.encoder = encoder; this.authenticationManager = authenticationManager; this.jwtService = jwtService;
    }
    @Transactional
    public AuthResponse register(RegisterRequest input) {
        if (input.role() == User.Role.ADMIN) throw new ApiException(HttpStatus.FORBIDDEN, "Admin accounts cannot be self-registered");
        if (users.existsByEmailIgnoreCase(input.email())) throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        User user = new User(); user.setName(input.name().trim()); user.setEmail(input.email().trim().toLowerCase());
        user.setPassword(encoder.encode(input.password())); user.setPhone(input.phone()); user.setRole(input.role());
        users.save(user);
        return response(user);
    }
    public AuthResponse login(LoginRequest input) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(input.email(), input.password()));
        return response(users.findByEmailIgnoreCase(input.email()).orElseThrow());
    }
    private AuthResponse response(User user) { return new AuthResponse(jwtService.generate(user), user.getId(), user.getName(), user.getEmail(), user.getRole()); }
}