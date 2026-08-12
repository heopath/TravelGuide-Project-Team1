package org.example.all_my_trip_project.domain.accommodation.service;

import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 숙소명·지역으로 검색 결과 주소를 만든다.
 *
 * <p>검색어에 "숙박 예약"을 붙이는 것은 결과를 실제로 예약할 수 있는 채널 쪽으로
 * 기울이기 위해서다. 숙소 소개 페이지만 나오면 사용자가 예약까지 가지 못한다.
 */
@Component
public class SearchAccommodationDeeplinkBuilder implements AccommodationDeeplinkBuilder {

    /** 검색 의도를 예약으로 좁힌다. 운영 중 바꿀 값이 아니라 코드에 둔다. */
    private static final String INTENT_KEYWORDS = "숙박 예약";

    private static final String QUERY_PLACEHOLDER = "{query}";

    private final AccommodationDeeplinkProperties properties;

    public SearchAccommodationDeeplinkBuilder(AccommodationDeeplinkProperties properties) {
        this.properties = properties;
    }

    @Override
    public String build(String name, String areaLabel) {
        String template = properties.getSearchTemplate();
        if (name == null || name.isBlank() || template == null || template.isBlank()) {
            return "";
        }
        return template.replace(QUERY_PLACEHOLDER, enc(keywords(name, areaLabel)));
    }

    /** 지역을 앞에 붙인다. "센텀 부티크 호텔"은 여러 지역에 있을 수 있다. */
    private String keywords(String name, String areaLabel) {
        String area = areaLabel == null ? "" : areaLabel.trim();
        return (area.isEmpty() ? "" : area + " ") + name.trim() + " " + INTENT_KEYWORDS;
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
