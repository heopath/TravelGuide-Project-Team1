package org.example.all_my_trip_project.domain.trip.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.mapper.TripMapper;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class TripDAO {
    private final TripMapper tripMapper;

    public int insert(TripDTO trip) { return tripMapper.insert(trip); }
    public Optional<TripDTO> findById(Long tripId) { return tripMapper.findById(tripId); }
    public List<TripDTO> findByUserId(Long userId) { return tripMapper.findByUserId(userId); }
    public int update(TripDTO trip) { return tripMapper.update(trip); }
    public int confirmBooking(Long tripId) { return tripMapper.confirmBooking(tripId); }
    public int clearBookingConfirmation(Long tripId) { return tripMapper.clearBookingConfirmation(tripId); }
    public int softDelete(Long tripId) { return tripMapper.softDelete(tripId); }
}
