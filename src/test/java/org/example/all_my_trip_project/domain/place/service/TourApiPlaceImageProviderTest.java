package org.example.all_my_trip_project.domain.place.service;

import org.example.all_my_trip_project.domain.accommodation.provider.TourApiProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이미지 매칭은 "엉뚱한 사진을 붙이지 않는 것"이 목적이라 경계를 고정해 둔다.
 * 좌표 반경 안에 있어도 이름이 닮지 않으면 넣지 않는다.
 */
class TourApiPlaceImageProviderTest {

    private final TourApiPlaceImageProvider provider =
            new TourApiPlaceImageProvider(new TourApiProperties());

    private Optional<String> bestImage(String body, String placeName) throws Exception {
        Method method = TourApiPlaceImageProvider.class
                .getDeclaredMethod("bestImage", String.class, String.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Optional<String> result = (Optional<String>) method.invoke(provider, body, placeName);
        return result;
    }

    private String body(String... titleAndImagePairs) {
        StringBuilder items = new StringBuilder();
        for (int index = 0; index < titleAndImagePairs.length; index += 2) {
            if (index > 0) items.append(',');
            items.append("{\"title\":\"").append(titleAndImagePairs[index])
                 .append("\",\"firstimage\":\"").append(titleAndImagePairs[index + 1]).append("\"}");
        }
        return "{\"response\":{\"body\":{\"items\":{\"item\":[" + items + "]}}}}";
    }

    @Test
    void 이름이_같으면_이미지를_쓴다() throws Exception {
        assertThat(bestImage(body("경복궁", "https://img/gyeongbok.jpg"), "경복궁"))
                .contains("https://img/gyeongbok.jpg");
    }

    @Test
    void 표기가_조금_달라도_충분히_닮으면_쓴다() throws Exception {
        // "석굴암"(3자)이 "석굴암석굴"(5자)에 들어 있어 비율 0.6으로 임계값을 만족한다.
        assertThat(bestImage(body("석굴암석굴", "https://img/seokguram.jpg"), "석굴암"))
                .contains("https://img/seokguram.jpg");
    }

    @Test
    void 이름이_많이_길어지면_같은_장소로_보지_않는다() throws Exception {
        /*
         * "경복궁"과 "경복궁근정전"은 비율 0.5라 넣지 않는다.
         * 근정전은 경복궁 안의 건물이라 사람이 보기엔 관련 있지만, 같은 판정 폭을 넓히면
         * "경복궁"과 "경복궁역"처럼 다른 장소까지 통과한다. 놓치는 쪽을 택한다.
         */
        assertThat(bestImage(body("경복궁근정전", "https://img/geunjeongjeon.jpg"), "경복궁"))
                .isEmpty();
    }

    @Test
    void 반경_안에_있어도_이름이_다르면_넣지_않는다() throws Exception {
        // 근처 다른 관광지가 잡히는 경우다. 사진이 있어도 쓰지 않는다.
        assertThat(bestImage(body("국립민속박물관", "https://img/folk.jpg"), "스타벅스 경복궁점"))
                .isEmpty();
    }

    @Test
    void 사진이_없는_항목은_후보에서_빠진다() throws Exception {
        assertThat(bestImage(body("경복궁", ""), "경복궁")).isEmpty();
    }

    @Test
    void 후보가_없으면_비어_있다() throws Exception {
        assertThat(bestImage("{\"response\":{\"body\":{\"items\":\"\"}}}", "경복궁")).isEmpty();
    }

    @Test
    void 서비스키가_없으면_호출하지_않는다() {
        // 키가 비어 있으면 네트워크를 타지 않고 곧바로 빈 값을 준다.
        assertThat(provider.findImageUrl("경복궁", new BigDecimal("37.5796"), new BigDecimal("126.9770")))
                .isEmpty();
    }
}
