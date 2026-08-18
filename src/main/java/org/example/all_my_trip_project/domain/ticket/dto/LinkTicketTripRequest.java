package org.example.all_my_trip_project.domain.ticket.dto;

/**
 * 산 티켓을 여행에 붙이거나 떼는 요청.
 *
 * <p>{@code tripId}가 {@code null}이면 연결을 푼다. 필수로 두지 않는 이유가 그것이다 —
 * "없음"이 유효한 값이라 {@code @NotNull}을 걸면 뗄 방법이 사라진다.
 */
public record LinkTicketTripRequest(Long tripId) {}
