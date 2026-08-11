package org.example.all_my_trip_project.domain.accommodation;

import org.example.all_my_trip_project.domain.accommodation.dto.AccommodationSearchQuery;
import org.example.all_my_trip_project.domain.accommodation.provider.AccommodationProviderException;
import org.example.all_my_trip_project.domain.accommodation.provider.CompositeAccommodationSearchProvider;
import org.example.all_my_trip_project.domain.accommodation.service.AccommodationSearchService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccommodationSearchServiceTest {

    @Test
    @DisplayName("숙소 공급자 장애를 캐시 가능한 빈 응답 대신 503 업무 오류로 바꾼다")
    void mapsProviderFailureToServiceUnavailable() {
        CompositeAccommodationSearchProvider provider = mock(CompositeAccommodationSearchProvider.class);
        AccommodationSearchQuery query = new AccommodationSearchQuery(
                "제주", LocalDate.of(2027, 2, 10), LocalDate.of(2027, 2, 13), 2, 1, "KRW");
        when(provider.search(query))
                .thenThrow(new AccommodationProviderException("tourapi", "HTTP_403", null));
        AccommodationSearchService service = new AccommodationSearchService(provider, new MockEnvironment());

        assertThatThrownBy(() -> service.search(query))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ACCOMMODATION_PROVIDER_UNAVAILABLE));
    }
}
