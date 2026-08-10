package org.example.all_my_trip_project.domain.accommodation;

import org.example.all_my_trip_project.domain.accommodation.service.AccommodationDeeplinkProperties;
import org.example.all_my_trip_project.domain.accommodation.service.SearchAccommodationDeeplinkBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccommodationDeeplinkBuilderTest {

    private SearchAccommodationDeeplinkBuilder builder(String template) {
        AccommodationDeeplinkProperties properties = new AccommodationDeeplinkProperties();
        if (template != null) {
            properties.setSearchTemplate(template);
        }
        return new SearchAccommodationDeeplinkBuilder(properties);
    }

    @Test
    @DisplayName("지역과 숙소명, 예약 의도를 한 검색어로 묶어 인코딩한다")
    void buildsEncodedSearchUrl() {
        String url = builder("https://search.test/?q={query}").build("센텀 부티크 호텔", "부산");

        assertThat(url).isEqualTo(
                "https://search.test/?q=%EB%B6%80%EC%82%B0+%EC%84%BC%ED%85%80+%EB%B6%80%ED%8B%B0%ED%81%AC+"
                        + "%ED%98%B8%ED%85%94+%EC%88%99%EB%B0%95+%EC%98%88%EC%95%BD");
    }

    @Test
    @DisplayName("지역을 모르면 숙소명만으로 만든다")
    void buildsWithoutArea() {
        String url = builder("https://search.test/?q={query}").build("제주 오션 스테이", null);

        assertThat(url).doesNotContain("null").contains("q=%EC%A0%9C%EC%A3%BC");
    }

    /* 숙소명이 없으면 검색어가 "숙박 예약"만 남아 엉뚱한 결과로 보낸다. 그럴 바엔 버튼을 안 내는 게 낫다. */
    @Test
    @DisplayName("숙소명이 없으면 주소를 만들지 않는다")
    void returnsEmptyWithoutName() {
        assertThat(builder(null).build(" ", "제주")).isEmpty();
        assertThat(builder(null).build(null, "제주")).isEmpty();
    }

    /* 템플릿을 비우면 딥링크 기능을 끌 수 있다. 목적지가 확정되지 않은 동안의 안전장치다. */
    @Test
    @DisplayName("템플릿이 비어 있으면 주소를 만들지 않는다")
    void returnsEmptyWithoutTemplate() {
        assertThat(builder("").build("제주 오션 스테이", "제주")).isEmpty();
    }

    @Test
    @DisplayName("기본 템플릿은 구글 검색이다")
    void defaultsToGoogleSearch() {
        assertThat(builder(null).build("제주 오션 스테이", "제주"))
                .startsWith("https://www.google.com/search?q=");
    }
}
