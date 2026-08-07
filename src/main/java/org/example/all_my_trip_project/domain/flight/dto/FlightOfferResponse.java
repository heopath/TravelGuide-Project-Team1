package org.example.all_my_trip_project.domain.flight.dto;

import org.example.all_my_trip_project.domain.flight.type.Badge;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 검색 결과 카드 1건의 화면용 표현.
 *
 * <p>시각 문자열·소요시간 문구·출처 라벨을 서버가 만들어 내려준다.
 * 프론트에서 다시 포맷하면 같은 규칙이 두 곳에 생긴다.
 *
 * <p>기종 필드는 없다. TAGO가 주지 않으므로 화면에서도 뺐다.
 *
 * @param provider 화면에 노출하지 않는다. 어느 소스가 준 결과인지 로그·디버깅에서 가리기 위해 보존한다.
 * @param ribbons  카드 상단 리본(AI 추천 / 최저가). 최대 2개.
 * @param badges   카드 배지(일정 충돌 등). 최대 2개.
 */
public record FlightOfferResponse(
        String offerId,
        String provider,
        String carrierCode,
        String carrierName,
        String flightNumber,
        String origin,
        String destination,
        LocalDateTime departureAt,
        LocalDateTime arrivalAt,
        String departureTime,
        String arrivalTime,
        String durationLabel,
        BigDecimal pricePerAdult,
        BigDecimal totalPrice,
        String currency,
        String priceSource,
        String priceSourceLabel,
        List<String> ribbons,
        List<BadgeResponse> badges,
        String deeplinkUrl
) implements Serializable {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** 리본과 배지 각각 최대 2개. */
    private static final int MAX_PER_PLACEMENT = 2;

    /**
     * 검색 결과 캐시는 Redis에 JDK 직렬화로 들어간다.
     * Serializable을 빼면 캐시 저장이 매번 조용히 실패하고 경고 로그만 쌓인다.
     */
    public record BadgeResponse(String code, String label, String tone) implements Serializable {}

    public static FlightOfferResponse from(FlightOffer offer) {
        return new FlightOfferResponse(
                offer.offerId(),
                offer.provider(),
                offer.carrierCode(),
                offer.carrierName(),
                offer.flightNumber(),
                offer.origin(),
                offer.destination(),
                offer.departureAt(),
                offer.arrivalAt(),
                offer.departureAt().format(TIME),
                offer.arrivalAt().format(TIME),
                durationLabel(offer.duration()),
                offer.pricePerAdult(),
                offer.totalPrice(),
                offer.currency(),
                offer.priceSource().name(),
                offer.priceSource().getLabel(),
                offer.badges().stream()
                        .filter(b -> b.getPlacement() == Badge.Placement.RIBBON)
                        .limit(MAX_PER_PLACEMENT)
                        .map(Badge::getLabel)
                        .toList(),
                offer.badges().stream()
                        .filter(b -> b.getPlacement() == Badge.Placement.CARD)
                        .limit(MAX_PER_PLACEMENT)
                        .map(b -> new BadgeResponse(b.name(), b.getLabel(), b.getTone()))
                        .toList(),
                offer.deeplinkUrl()
        );
    }

    private static String durationLabel(Duration duration) {
        if (duration == null) {
            return "";
        }
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours == 0) {
            return minutes + "분";
        }
        if (minutes == 0) {
            return hours + "시간";
        }
        return hours + "시간 " + minutes + "분";
    }
}
