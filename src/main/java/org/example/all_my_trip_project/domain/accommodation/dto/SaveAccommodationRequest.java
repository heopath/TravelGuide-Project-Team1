package org.example.all_my_trip_project.domain.accommodation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 숙소 선택을 여행에 저장할 때 보내는 요청.
 *
 * <p>숙소 정보와 요금을 클라이언트가 보내는 게 이상해 보일 수 있지만, 저장해야 하는 값은
 * "지금 사용자가 화면에서 보고 고른 것"이다. 서버가 다시 조회하면 그 사이 바뀐 값이 박제된다.
 * 항공의 {@code OutboundClickRequest}와 같은 판단이다.
 */
public record SaveAccommodationRequest(
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @NotBlank String offerId,
        @NotBlank String provider,
        @NotBlank String name,
        @NotBlank String accommodationType,
        String areaLabel,
        String address,
        Double rating,
        Double latitude,
        Double longitude,
        /** 요금 미제공(priceSource=UNAVAILABLE) 숙소는 비어 있을 수 있다. */
        @PositiveOrZero BigDecimal nightlyPrice,
        @PositiveOrZero BigDecimal totalPrice,
        String currency,
        /** RACK / PARTNER / SANDBOX / MOCK / UNAVAILABLE. 어떤 가격을 보고 골랐는지 함께 박제한다. */
        @NotBlank String priceSource,
        Integer rooms,
        Integer adults
) {
    public SaveAccommodationRequest {
        if (currency == null || currency.isBlank()) {
            currency = "KRW";
        }
        if (rooms == null || rooms < 1) {
            rooms = 1;
        }
        if (adults == null || adults < 1) {
            adults = 2;
        }
    }
}
