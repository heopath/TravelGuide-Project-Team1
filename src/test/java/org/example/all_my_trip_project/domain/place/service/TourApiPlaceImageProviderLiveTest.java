package org.example.all_my_trip_project.domain.place.service;

import org.example.all_my_trip_project.domain.accommodation.provider.TourApiProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 진짜 TourAPI에 대고 매칭이 의도대로 도는지 본다.
 *
 * <p>가짜 서버로는 "우리가 짠 규칙이 우리가 넣은 응답에 대해 동작한다"까지만 알 수 있다.
 * 실제로 어떤 이름과 좌표가 돌아오는지는 여기서만 드러난다. 반경 300m만 보던 시절에
 * 해운대해수욕장·경복궁 같은 넓은 장소가 통째로 빠졌던 것도 이렇게 재고 나서 알았다.
 *
 * <p>서비스키가 필요하고 바깥으로 나가므로 평소에는 돌지 않는다.
 * {@code TOUR_API_LIVE_TEST=true}와 {@code TOUR_API_SERVICE_KEY}를 주면 돈다.
 */
@EnabledIfEnvironmentVariable(named = "TOUR_API_LIVE_TEST", matches = "true")
class TourApiPlaceImageProviderLiveTest {

    private record Place(String name, String latitude, String longitude) {
    }

    /** 좌표가 떨어져 있어 1단계만으로는 못 찾던 것들이다. */
    private static final List<Place> WIDE_PLACES = List.of(
            new Place("해운대해수욕장", "35.1585232", "129.1598547"),
            new Place("경복궁", "37.5788894", "126.9770069"),
            new Place("북촌한옥마을", "37.5826113", "126.9834740"),
            new Place("광안리해수욕장", "35.1531933", "129.1189761"));

    /** 관광정보에 없는 장소들이다. 억지로 채우면 안 된다. */
    private static final List<Place> UNLISTED_PLACES = List.of(
            new Place("무신사 스토어 강남", "37.4979502", "127.0276368"),
            new Place("동탄ㅇㅇ스팀", "37.1550953", "127.1160254"));

    @Test
    @DisplayName("좌표가 떨어져 있는 넓은 장소도 이름 검색으로 찾는다")
    void findsWidePlacesByKeyword() {
        for (Place place : WIDE_PLACES) {
            assertThat(imageOf(place))
                    .as("%s 사진을 찾지 못했다", place.name())
                    .isPresent();
        }
    }

    @Test
    @DisplayName("관광정보에 없는 장소에는 아무 사진도 붙이지 않는다")
    void leavesUnlistedPlacesEmpty() {
        for (Place place : UNLISTED_PLACES) {
            assertThat(imageOf(place))
                    .as("%s 에 엉뚱한 사진이 붙었다", place.name())
                    .isEmpty();
        }
    }

    private Optional<String> imageOf(Place place) {
        TourApiProperties properties = new TourApiProperties();
        properties.setServiceKey(System.getenv("TOUR_API_SERVICE_KEY"));
        return new TourApiPlaceImageProvider(properties).findImageUrl(
                place.name(), new BigDecimal(place.latitude()), new BigDecimal(place.longitude()));
    }
}
