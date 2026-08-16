package org.example.all_my_trip_project.domain.payment.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.payment.dto.IssuedTicketDTO;
import org.example.all_my_trip_project.domain.payment.dto.PayableReservationDTO;
import org.example.all_my_trip_project.domain.payment.dto.PaymentDTO;
import org.example.all_my_trip_project.domain.payment.mapper.PaymentMapper;
import org.example.all_my_trip_project.domain.ticket.dto.TicketReservationDTO;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class PaymentDAO {

    private final PaymentMapper paymentMapper;

    public Optional<PaymentDTO> findByIdempotencyKey(Long userId, String idempotencyKey) {
        return paymentMapper.findByIdempotencyKey(userId, idempotencyKey);
    }

    public Optional<PayableReservationDTO> lockPayableReservation(Long userId, Long reservationId) {
        return paymentMapper.lockPayableReservation(userId, reservationId);
    }

    public int insertPayment(PaymentDTO payment) {
        return paymentMapper.insertPayment(payment);
    }

    public Optional<PaymentDTO> findPayment(Long paymentId) {
        return paymentMapper.findPayment(paymentId);
    }

    public int confirmReservation(Long reservationId) {
        return paymentMapper.confirmReservation(reservationId);
    }

    public int insertIssuedTicket(IssuedTicketDTO ticket, String tokenHash) {
        return paymentMapper.insertIssuedTicket(ticket, tokenHash);
    }

    public List<IssuedTicketDTO> findTicketsByReservation(Long reservationId) {
        return paymentMapper.findTicketsByReservation(reservationId);
    }

    public Optional<TicketReservationDTO> findReservation(Long reservationId) {
        return paymentMapper.findReservation(reservationId);
    }
}
