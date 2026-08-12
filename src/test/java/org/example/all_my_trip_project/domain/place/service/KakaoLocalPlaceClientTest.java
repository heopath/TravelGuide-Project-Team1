package org.example.all_my_trip_project.domain.place.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoLocalPlaceClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void convertsKakaoKeywordDocumentsToVerifiedPlaces() throws Exception {
        String response = """
                {"documents":[{
                  "id":"12345", "place_name":"Real Seongsu Cafe", "category_group_code":"CE7",
                  "road_address_name":"서울특별시 성동구 성수동1가 10",
                  "address_name":"서울특별시 성동구 성수동1가 10",
                  "x":"127.054", "y":"37.544", "phone":"02-123-4567",
                  "place_url":"https://place.map.kakao.com/12345"
                }]}
                """;

        List<PlaceDTO> places = KakaoLocalPlaceClient.parsePlaces(objectMapper.readTree(response));

        assertThat(places).singleElement().satisfies(place -> {
            assertThat(place.getExternalProvider()).isEqualTo("KAKAO");
            assertThat(place.getExternalPlaceId()).isEqualTo("12345");
            assertThat(place.getName()).isEqualTo("Real Seongsu Cafe");
            assertThat(place.getCategory()).isEqualTo("CAFE");
            assertThat(place.getRegion()).isEqualTo("서울특별시");
            assertThat(place.getLatitude()).hasToString("37.544");
            assertThat(place.getLongitude()).hasToString("127.054");
        });
    }

    @Test
    void ignoresDocumentsWithoutKakaoIdOrName() throws Exception {
        String response = "{" + "\"documents\":[{\"id\":\"\",\"place_name\":\"Missing id\"},{\"id\":\"1\",\"place_name\":\"\"}]}";

        assertThat(KakaoLocalPlaceClient.parsePlaces(objectMapper.readTree(response))).isEmpty();
    }

    @Test
    void mapsUngroupedKakaoPlacesToAllowedAttractionCategory() throws Exception {
        String response = "{\"documents\":[{\"id\":\"9\",\"place_name\":\"Shopping Place\",\"category_group_code\":\"\"}]}";

        assertThat(KakaoLocalPlaceClient.parsePlaces(objectMapper.readTree(response)))
                .singleElement()
                .extracting(PlaceDTO::getCategory)
                .isEqualTo("ATTRACTION");
    }
}
