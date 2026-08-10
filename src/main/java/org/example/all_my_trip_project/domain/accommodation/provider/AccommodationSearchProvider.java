package org.example.all_my_trip_project.domain.accommodation.provider;

import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationOffer;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationSearchQuery;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationProviderRole;

import java.util.List;

/**
 * 숙소 데이터 소스.
 *
 * <p>{@link AccommodationProviderRole#LISTING}이 목록을 만들고
 * {@link AccommodationProviderRole#PRICE}가 요금만 덮어쓴다.
 *
 * <p>현재 구현은 Mock 하나뿐이다. Travelpayouts 호텔 API는 계정 권한이 없어 못 쓴다(#147).
 * 실 provider가 붙을 때 화면과 서비스를 손대지 않으려고 인터페이스를 먼저 둔다.
 */
public interface AccommodationSearchProvider {

    /** "mock" | "tourapi" — offerId 접두어와 로그에 그대로 쓰인다. */
    String name();

    AccommodationProviderRole role();

    /** 지역 커버리지 판정. 거짓이면 호출되지 않는다. */
    boolean supports(AccommodationSearchQuery query);

    List<AccommodationOffer> search(AccommodationSearchQuery query);
}
