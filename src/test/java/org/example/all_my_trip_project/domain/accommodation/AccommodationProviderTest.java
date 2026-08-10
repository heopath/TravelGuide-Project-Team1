package org.example.all_my_trip_project.domain.accommodation;

import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationOffer;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationSearchQuery;
import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationSearchResult;
import org.example.all_my_trip_project.domain.accommodation.provider.AccommodationSearchProvider;
import org.example.all_my_trip_project.domain.accommodation.provider.CompositeAccommodationSearchProvider;
import org.example.all_my_trip_project.domain.accommodation.provider.MockAccommodationSearchProvider;
import org.example.all_my_trip_project.domain.accommodation.provider.TourApiAccommodationSearchProvider;
import org.example.all_my_trip_project.domain.accommodation.provider.TourApiProperties;
import org.example.all_my_trip_project.domain.accommodation.service.AccommodationRecommendationScorer;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationPriceSource;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationProviderRole;
import org.example.all_my_trip_project.domain.accommodation.type.AccommodationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AccommodationProviderTest {

    private static final String TOUR_API_BASE = "https://tour.test/KorService2";

    private record TourFixture(TourApiAccommodationSearchProvider provider,
                               MockRestServiceServer server) {}

    private AccommodationSearchQuery query(String destination) {
        return new AccommodationSearchQuery(destination,
                LocalDate.of(2027, 2, 10), LocalDate.of(2027, 2, 13), 2, 1, "KRW");
    }

    private TourFixture tourApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TourApiProperties properties = new TourApiProperties();
        properties.setBaseUrl(TOUR_API_BASE);
        properties.setServiceKey("test%2Fkey%3D%3D");
        return new TourFixture(new TourApiAccommodationSearchProvider(properties, builder), server);
    }

    @Test
    @DisplayName("TourAPI 숙박정보를 주소·사진·좌표가 있는 offer로 매핑한다")
    void mapsTourApiStay() {
        TourFixture fixture = tourApi();
        fixture.server().expect(requestTo(org.hamcrest.Matchers.containsString("/areaCode2")))
                .andRespond(withSuccess(areaResponse(), MediaType.APPLICATION_JSON));
        fixture.server().expect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("/searchStay2"),
                        org.hamcrest.Matchers.containsString("areaCode=6"),
                        org.hamcrest.Matchers.containsString("sigunguCode=16"))))
                .andRespond(withSuccess(stayResponse(), MediaType.APPLICATION_JSON));

        List<AccommodationOffer> offers = fixture.provider().search(query("부산 해운대"));

        assertThat(offers).hasSize(1);
        AccommodationOffer offer = offers.get(0);
        assertThat(offer.offerId()).isEqualTo("tourapi:12345");
        assertThat(offer.provider()).isEqualTo("tourapi");
        assertThat(offer.name()).isEqualTo("해운대 테스트 호텔");
        assertThat(offer.type()).isEqualTo(AccommodationType.HOTEL);
        assertThat(offer.areaLabel()).isEqualTo("부산광역시 해운대구");
        assertThat(offer.address()).isEqualTo("부산광역시 해운대구 해운대로 1 101호");
        assertThat(offer.imageUrl()).isEqualTo("https://image.test/hotel.jpg");
        assertThat(offer.latitude()).isEqualTo(35.1587);
        assertThat(offer.longitude()).isEqualTo(129.1604);
        assertThat(offer.priceSource()).isEqualTo(AccommodationPriceSource.UNAVAILABLE);
        assertThat(offer.hasPrice()).isFalse();
        fixture.server().verify();
    }

    @Test
    @DisplayName("광역 지역이 없는 검색어는 숙박 콘텐츠 키워드 검색을 사용한다")
    void searchesUnknownAreaByKeyword() {
        TourFixture fixture = tourApi();
        fixture.server().expect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("/searchKeyword2"),
                        org.hamcrest.Matchers.containsString("contentTypeId=32"))))
                .andRespond(withSuccess(stayResponse(), MediaType.APPLICATION_JSON));

        assertThat(fixture.provider().search(query("강릉"))).hasSize(1);
        fixture.server().verify();
    }

    @Test
    @DisplayName("서비스키가 없으면 TourAPI provider가 비활성화된다")
    void disablesTourApiWithoutKey() {
        TourApiAccommodationSearchProvider provider = new TourApiAccommodationSearchProvider(
                new TourApiProperties(), RestClient.builder());

        assertThat(provider.supports(query("부산"))).isFalse();
    }

    @Test
    @DisplayName("TourAPI 결과가 없으면 Mock 목록으로 폴백한다")
    void fallsBackToMockListing() {
        AccommodationSearchProvider emptyTourApi = new AccommodationSearchProvider() {
            @Override public String name() { return "tourapi"; }
            @Override public AccommodationProviderRole role() { return AccommodationProviderRole.LISTING; }
            @Override public boolean supports(AccommodationSearchQuery query) { return true; }
            @Override public List<AccommodationOffer> search(AccommodationSearchQuery query) { return List.of(); }
        };
        CompositeAccommodationSearchProvider composite = new CompositeAccommodationSearchProvider(
                List.of(emptyTourApi, new MockAccommodationSearchProvider()),
                new AccommodationRecommendationScorer());

        AccommodationSearchResult result = composite.search(query("부산"));

        assertThat(result.listingProvider()).isEqualTo(MockAccommodationSearchProvider.NAME);
        assertThat(result.offers()).isNotEmpty();
    }

    private String areaResponse() {
        return """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":{"item":[{"code":"16","name":"해운대구"}]}}}}
                """;
    }

    private String stayResponse() {
        return """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":{"item":[{
                  "contentid":"12345",
                  "title":"해운대 테스트 호텔",
                  "addr1":"부산광역시 해운대구 해운대로 1",
                  "addr2":"101호",
                  "firstimage":"https://image.test/hotel.jpg",
                  "mapx":"129.1604",
                  "mapy":"35.1587",
                  "cat3":"B02010100"
                }]},"totalCount":1}}}
                """;
    }
}
