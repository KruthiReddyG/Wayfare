package com.example.carpool.service;

import com.example.carpool.dto.ApiModels.*;
import com.example.carpool.entity.Ride;
import com.example.carpool.repository.RideRepository;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.*;

@Service
public class MatchingService {
    private final RideRepository rides;
    public MatchingService(RideRepository rides) { this.rides = rides; }
    public List<RideView> search(SearchRideRequest request) {
        return rides.findByTravelDateAndStatusAndAvailableSeatsGreaterThanEqual(request.travelDate(), Ride.Status.OPEN, request.seats())
            .stream().map(ride -> Map.entry(ride, score(request, ride)))
            .filter(entry -> entry.getValue() >= 35)
            .sorted(Map.Entry.<Ride, Double>comparingByValue().reversed())
            .map(entry -> RideView.from(entry.getKey(), entry.getValue())).toList();
    }
    private double score(SearchRideRequest request, Ride ride) {
        String requestedSource = normalize(request.source()), rideSource = normalize(ride.getSource());
        String requestedDestination = normalize(request.destination()), rideDestination = normalize(ride.getDestination());
        double route = (similarity(requestedSource, rideSource) + similarity(requestedDestination, rideDestination)) / 2;
        double minutes = Math.abs(Duration.between(request.preferredTime(), ride.getDepartureTime()).toMinutes());
        double time = Math.max(0, 1 - minutes / 180.0);
        return Math.round((route * 70 + time * 30) * 10.0) / 10.0;
    }
    private String normalize(String place) { return place.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " "); }
    private double similarity(String requested, String offered) {
        if (requested.equals(offered)) return 1;
        if (requested.contains(offered) || offered.contains(requested)) return 0.85;
        if (requested.startsWith(offered) || offered.startsWith(requested)) return 0.75;
        Set<String> requestedWords = new HashSet<>(Arrays.asList(requested.split("[ ,]+")));
        Set<String> offeredWords = new HashSet<>(Arrays.asList(offered.split("[ ,]+")));
        Set<String> common = new HashSet<>(requestedWords); common.retainAll(offeredWords);
        Set<String> all = new HashSet<>(requestedWords); all.addAll(offeredWords);
        return all.isEmpty() ? 0 : (double) common.size() / all.size();
    }
}