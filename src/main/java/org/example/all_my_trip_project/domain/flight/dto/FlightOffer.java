package org.example.all_my_trip_project.domain.flight.dto;

import org.example.all_my_trip_project.domain.flight.type.Badge;
import org.example.all_my_trip_project.domain.flight.type.PriceSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 검색 결과 항공편 1건.
 *
 * <p>기종(aircraft) 필드는 없다. TAGO가 주지 않는다.
 *
 * @param offerId     provider 접두어 필수. "tago:KE1201-20260815" / "mock:ke121-0815"
 *                    정렬이 바뀌어도 선택이 유지되려면 프론트가 인덱스가 아닌 이 값을 들고 있어야 한다.
 * @param priceSource null을 허용하지 않는다. 출처 없는 가격은 화면에 띄우지 않는다.
 * @param badges      전부 서버가 계산해서 채운다. API에서 오는 배지는 없다.
 * @param deeplinkUrl 외부 예약 페이지 주소
 */
public record FlightOffer(
        String offerId,
        String provider,
        String carrierCode,
        String carrierName,
        String flightNumber,
        String origin,
        String destination,
        LocalDateTime departureAt,
        LocalDateTime arrivalAt,
        Duration duration,
        BigDecimal pricePerAdult,
        BigDecimal totalPrice,
        String currency,
        PriceSource priceSource,
        List<Badge> badges,
        String deeplinkUrl
) {
    public FlightOffer {
        if (priceSource == null) {
            throw new IllegalArgumentException("priceSource는 비울 수 없습니다. offerId=" + offerId);
        }
        // 가격이 비어 있어도 되는 경우는 "운임 미제공"뿐이다.
        // 그 외에 null이 들어오면 출처만 있고 값이 없는 유령 가격이 만들어진다.
        if (totalPrice == null && priceSource.hasPrice()) {
            throw new IllegalArgumentException(
                    "가격 없는 offer는 priceSource=UNAVAILABLE이어야 합니다. offerId=" + offerId);
        }
        badges = badges == null ? List.of() : List.copyOf(badges);
    }

    /** 가격 비교·정렬에 참여할 수 있는지. */
    public boolean hasPrice() {
        return totalPrice != null && priceSource.hasPrice();
    }

    /** 딥링크는 offer가 만들어진 뒤에 조합하므로 나중에 채워 넣는다. */
    public FlightOffer withDeeplinkUrl(String url) {
        return copyWith(pricePerAdult, totalPrice, priceSource, badges, url);
    }

    /** PRICE provider가 가격을 덮어쓸 때 쓴다. 출처도 함께 바뀐다. */
    public FlightOffer withPrice(BigDecimal newPricePerAdult, BigDecimal newTotalPrice, PriceSource newSource) {
        return copyWith(newPricePerAdult, newTotalPrice, newSource, badges, deeplinkUrl);
    }

    public FlightOffer withBadges(List<Badge> newBadges) {
        return copyWith(pricePerAdult, totalPrice, priceSource, newBadges, deeplinkUrl);
    }

    /** 가격 병합의 매칭 키. 같은 편이라도 날짜가 다르면 다른 offer다. */
    public String matchKey() {
        return carrierCode + flightNumber + "@" + departureAt.toLocalDate();
    }

    private FlightOffer copyWith(BigDecimal perAdult, BigDecimal total,
                                 PriceSource source, List<Badge> newBadges, String url) {
        return new FlightOffer(offerId, provider, carrierCode, carrierName, flightNumber,
                origin, destination, departureAt, arrivalAt, duration,
                perAdult, total, currency, source, newBadges, url);
    }
}
