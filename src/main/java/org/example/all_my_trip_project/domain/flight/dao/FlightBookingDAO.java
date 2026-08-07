package org.example.all_my_trip_project.domain.flight.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.flight.dto.FlightBookingDTO;
import org.example.all_my_trip_project.domain.flight.dto.OutboundClickDTO;
import org.example.all_my_trip_project.domain.flight.mapper.FlightBookingMapper;
import org.example.all_my_trip_project.domain.flight.mapper.OutboundClickMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class FlightBookingDAO {

    private final FlightBookingMapper flightBookingMapper;
    private final OutboundClickMapper outboundClickMapper;

    public int upsertSelection(FlightBookingDTO booking) {
        return flightBookingMapper.upsertSelection(booking);
    }

    public Optional<FlightBookingDTO> findByTripAndLeg(Long tripId, int leg) {
        return flightBookingMapper.findByTripAndLeg(tripId, leg);
    }

    public List<FlightBookingDTO> findByTrip(Long tripId) {
        return flightBookingMapper.findByTrip(tripId);
    }

    public int updateUserReported(Long tripId, int leg, boolean userReportedBooked) {
        return flightBookingMapper.updateUserReported(tripId, leg, userReportedBooked);
    }

    public int updateBookingRef(Long tripId, int leg, String bookingRef) {
        return flightBookingMapper.updateBookingRef(tripId, leg, bookingRef);
    }

    public int delete(Long tripId, int leg) {
        return flightBookingMapper.delete(tripId, leg);
    }

    public Long insertOutboundClick(OutboundClickDTO click) {
        outboundClickMapper.insert(click);
        return click.getFlightOutboundClickId();
    }

    public int updateOutboundClickOutcome(Long clickId, String outcome) {
        return outboundClickMapper.updateOutcome(clickId, outcome);
    }

    public List<OutboundClickDTO> findUnresolvedClicks(Long tripId) {
        return outboundClickMapper.findUnresolvedByTrip(tripId);
    }
}
