package org.example.all_my_trip_project.domain.flight.service;

import org.example.all_my_trip_project.domain.flight.dto.FlightOffer;
import org.example.all_my_trip_project.domain.flight.dto.FlightSearchQuery;

/**
 * 외부 예약 페이지 주소 생성.
 *
 * <p>붙일 API가 없다. URL 조합이 전부다.
 * 어필리에이트 승인 전에는 항공사 공식 예약 페이지로만 보내고,
 * 승인 후 파트너 ID를 쿼리스트링에 얹는다.
 */
public interface DeeplinkBuilder {
    String build(FlightOffer offer, FlightSearchQuery query);
}
