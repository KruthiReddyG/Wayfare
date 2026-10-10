package com.example.carpool;

import com.example.carpool.dto.ApiModels.SearchRideRequest;
import com.example.carpool.dto.ApiModels.RideView;
import com.example.carpool.entity.*;
import com.example.carpool.repository.RideRepository;
import com.example.carpool.service.MatchingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {
    @Mock RideRepository rides;
    @InjectMocks MatchingService matching;

    @Test
    void nearbyRouteAndTimeReturnsPossibleMatchWithWeightedScore() {
        LocalDate date = LocalDate.now().plusDays(2);
        Ride ride = ride("Hyderabad", "Warangal", date, LocalTime.of(8, 0), 3);
        when(rides.findByTravelDateAndStatusAndAvailableSeatsGreaterThanEqual(date, Ride.Status.OPEN, 1)).thenReturn(List.of(ride));

        List<RideView> results = matching.search(new SearchRideRequest("Hyderabad", "Hanamkonda", date, LocalTime.of(8, 30), 1));

        assertEquals(1, results.size());
        assertEquals(60.0, results.get(0).matchScore());
    }

    @Test
    void resultsAreSortedByMatchScoreDescending() {
        LocalDate date = LocalDate.now().plusDays(1);
        Ride close = ride("Hyderabad", "Warangal", date, LocalTime.of(8, 0), 3);
        Ride farther = ride("Hyderabad", "Karimnagar", date, LocalTime.of(10, 0), 3);
        when(rides.findByTravelDateAndStatusAndAvailableSeatsGreaterThanEqual(date, Ride.Status.OPEN, 1)).thenReturn(List.of(farther, close));

        List<RideView> results = matching.search(new SearchRideRequest("Hyderabad", "Warangal", date, LocalTime.of(8, 0), 1));

        assertEquals(2, results.size());
        assertTrue(results.get(0).matchScore() > results.get(1).matchScore());
    }

    private Ride ride(String source, String destination, LocalDate date, LocalTime time, int seats) {
        User driver = new User(); driver.setId(7L); driver.setName("Demo Driver");
        Vehicle vehicle = new Vehicle(); vehicle.setMakeModel("Hatchback"); vehicle.setRegistrationNumber("TEST123");
        Ride ride = new Ride(); ride.setId(4L); ride.setDriver(driver); ride.setVehicle(vehicle); ride.setSource(source);
        ride.setDestination(destination); ride.setTravelDate(date); ride.setDepartureTime(time); ride.setAvailableSeats(seats);
        ride.setEstimatedCost(new BigDecimal("600.00")); ride.setStatus(Ride.Status.OPEN);
        return ride;
    }
}