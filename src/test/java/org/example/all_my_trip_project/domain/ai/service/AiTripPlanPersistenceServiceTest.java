package org.example.all_my_trip_project.domain.ai.service;

import org.example.all_my_trip_project.domain.ai.dto.AiTripPlanDayResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiTripPlanItemResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiTripPlanPlaceResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiTripPlanRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiTripPlanResolvedPlace;
import org.example.all_my_trip_project.domain.ai.dto.AiTripPlanResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiTripPlanSaveRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiTripPlanSaveResult;
import org.example.all_my_trip_project.domain.trip.dao.ItineraryItemDAO;
import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDayDAO;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.domain.user.service.ActiveMemberGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTripPlanPersistenceServiceTest {
    @Mock TripDAO tripDAO;
    @Mock TripDayDAO tripDayDAO;
    @Mock ItineraryItemDAO itineraryItemDAO;
    @Mock PlaceDAO placeDAO;
    @Mock ActiveMemberGuard activeMemberGuard;

    private AiTripPlanPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new AiTripPlanPersistenceService(tripDAO, tripDayDAO, itineraryItemDAO, placeDAO, activeMemberGuard);
    }

    private void stubSuccessfulPersistence() {
        doAnswer(invocation -> {
            ((TripDTO) invocation.getArgument(0)).setTripId(100L);
            return 1;
        }).when(tripDAO).insert(any(TripDTO.class));
        AtomicLong dayIds = new AtomicLong(200L);
        doAnswer(invocation -> {
            ((TripDayDTO) invocation.getArgument(0)).setTripDayId(dayIds.getAndIncrement());
            return 1;
        }).when(tripDayDAO).insert(any(TripDayDTO.class));
        when(itineraryItemDAO.insert(any(ItineraryItemDTO.class))).thenReturn(1);
        when(placeDAO.upsert(any(PlaceDTO.class))).thenReturn(300L);
    }

    @Test
    void savesConditionsDaysAndAiItemsTogether() {
        stubSuccessfulPersistence();
        AiTripPlanSaveResult result = service.save(42L, request());

        assertThat(result).isEqualTo(new AiTripPlanSaveResult(100L, 2, 8));

        ArgumentCaptor<TripDTO> tripCaptor = ArgumentCaptor.forClass(TripDTO.class);
        verify(tripDAO).insert(tripCaptor.capture());
        TripDTO trip = tripCaptor.getValue();
        assertThat(trip.getSource()).isEqualTo("AI");
        assertThat(trip.getStatus()).isEqualTo("CONFIRMED");
        assertThat(trip.getTitle()).isEqualTo("여름 부산 여행");
        assertThat(trip.getPurpose()).isEqualTo("관광, 맛집");
        assertThat(trip.getPace()).isEqualTo("PACKED");
        assertThat(trip.getTransportPreference()).isEqualTo("대중교통");
        assertThat(trip.getFoodPreference()).isEqualTo("로컬 맛집");
        assertThat(trip.getAccommodationStyle()).isEqualTo("호텔");

        ArgumentCaptor<ItineraryItemDTO> itemCaptor = ArgumentCaptor.forClass(ItineraryItemDTO.class);
        verify(itineraryItemDAO, org.mockito.Mockito.times(8)).insert(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues()).extracting(ItineraryItemDTO::getSource).containsOnly("AI");
        assertThat(itemCaptor.getAllValues()).extracting(ItineraryItemDTO::getItemType)
                .contains("PLACE", "MEAL", "ACCOMMODATION", "TRANSPORT");
        assertThat(itemCaptor.getAllValues()).extracting(ItineraryItemDTO::getTitle)
                .contains("해운대", "돼지국밥", "부산 호텔", "부산역");
        assertThat(itemCaptor.getAllValues()).extracting(ItineraryItemDTO::getSortOrder)
                .containsOnly(1, 2, 3, 4);
        assertThat(itemCaptor.getAllValues()).extracting(ItineraryItemDTO::getPlaceId)
                .containsOnly(300L);
        verify(placeDAO, org.mockito.Mockito.times(8)).upsert(any(PlaceDTO.class));
    }

    @Test
    void rejectsPlanWhenAnyRecommendationIsNotResolved() {
        AiTripPlanSaveRequest request = request();
        AiTripPlanSaveRequest missingResolvedPlace = new AiTripPlanSaveRequest(
                request.title(), request.conditions(), request.plan(),
                request.resolvedPlaces().subList(0, request.resolvedPlaces().size() - 1)
        );

        assertThatThrownBy(() -> service.save(42L, missingResolvedPlace))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("카카오 장소와 연결하지 못했습니다");
        verify(tripDAO, never()).insert(any(TripDTO.class));
    }

    @Test
    void rejectsPlanWithMoreThanFivePlacesInOneDay() {
        AiTripPlanSaveRequest request = request();
        AiTripPlanDayResponse firstDay = request.plan().days().get(0);
        List<AiTripPlanItemResponse> items = new ArrayList<>(firstDay.items());
        List<AiTripPlanPlaceResponse> places = new ArrayList<>(firstDay.places());
        items.add(item("21:00", "야간 명소"));
        places.add(place(5, "추천 명소", "더베이101"));
        items.add(item("22:00", "야경 명소"));
        places.add(place(6, "추천 명소", "황령산"));
        AiTripPlanResponse plan = new AiTripPlanResponse(
                request.plan().title(), request.plan().summary(), places,
                List.of(
                        new AiTripPlanDayResponse(firstDay.day(), firstDay.title(), items, places),
                        request.plan().days().get(1)
                ),
                request.plan().generatedBy()
        );

        assertThatThrownBy(() -> service.save(42L, new AiTripPlanSaveRequest(
                request.title(), request.conditions(), plan, request.resolvedPlaces()
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1개부터 5개");
        verify(tripDAO, never()).insert(any(TripDTO.class));
    }

    private AiTripPlanSaveRequest request() {
        AiTripPlanRequest conditions = new AiTripPlanRequest(
                "부산", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 11), 2,
                "커플", "관광, 맛집", "알찬", "대중교통", "로컬 맛집", "호텔",
                new BigDecimal("800000")
        );
        List<AiTripPlanItemResponse> firstItems = List.of(
                item("09:00", "오전 명소"), item("12:00", "점심"),
                item("15:00", "오후 명소"), item("19:00", "숙소")
        );
        List<AiTripPlanPlaceResponse> firstPlaces = List.of(
                place(1, "추천 명소", "해운대"), place(2, "식사 장소", "돼지국밥"),
                place(3, "추천 명소", "광안리"), place(4, "숙소", "부산 호텔")
        );
        List<AiTripPlanItemResponse> lastItems = List.of(
                item("09:00", "오전 명소"), item("12:00", "점심"),
                item("15:00", "오후 명소"), item("18:00", "귀가")
        );
        List<AiTripPlanPlaceResponse> lastPlaces = List.of(
                place(1, "추천 명소", "감천문화마을"), place(2, "식사 장소", "밀면"),
                place(3, "추천 명소", "부산역"), place(4, "교통", "부산역")
        );
        AiTripPlanResponse plan = new AiTripPlanResponse(
                "부산 2일 여행", "부산 추천 일정", firstPlaces,
                List.of(
                        new AiTripPlanDayResponse(1, "DAY 1", firstItems, firstPlaces),
                        new AiTripPlanDayResponse(2, "DAY 2", lastItems, lastPlaces)
                ),
                "GEMINI"
        );
        List<AiTripPlanResolvedPlace> resolvedPlaces = List.of(
                resolved(1, 1, "101", "해운대"), resolved(1, 2, "102", "돼지국밥"),
                resolved(1, 3, "103", "광안리"), resolved(1, 4, "104", "부산 호텔"),
                resolved(2, 1, "105", "감천문화마을"), resolved(2, 2, "106", "밀면"),
                resolved(2, 3, "107", "부산역"), resolved(2, 4, "108", "부산역")
        );
        return new AiTripPlanSaveRequest("여름 부산 여행", conditions, plan, resolvedPlaces);
    }

    private AiTripPlanItemResponse item(String time, String title) {
        return new AiTripPlanItemResponse(time, title, title + " 설명");
    }

    private AiTripPlanPlaceResponse place(int number, String category, String name) {
        return new AiTripPlanPlaceResponse(number, category, name, name + " 추천 이유", 0, 0);
    }

    private AiTripPlanResolvedPlace resolved(int day, int number, String id, String name) {
        return new AiTripPlanResolvedPlace(
                day, number, id, name, "부산 주소",
                new BigDecimal("35.1234567"), new BigDecimal("129.1234567"),
                "", "https://place.map.kakao.com/" + id, "관광명소", name + " 추천 이유"
        );
    }
}
