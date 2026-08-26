package org.example.all_my_trip_project.domain.ai.service;

import lombok.RequiredArgsConstructor;
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
import org.example.all_my_trip_project.domain.place.service.KoreanAddress;
import org.example.all_my_trip_project.domain.trip.dao.TripDAO;
import org.example.all_my_trip_project.domain.trip.dao.TripDayDAO;
import org.example.all_my_trip_project.domain.trip.dto.ItineraryItemDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDTO;
import org.example.all_my_trip_project.domain.trip.dto.TripDayDTO;
import org.example.all_my_trip_project.domain.trip.policy.TripPolicy;
import org.example.all_my_trip_project.domain.trip.service.ItineraryItemValidator;
import org.example.all_my_trip_project.domain.user.service.ActiveMemberGuard;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Profile("!ui")
@RequiredArgsConstructor
public class AiTripPlanPersistenceService {
    private static final int MAX_DAYS = 30;

    private final TripDAO tripDAO;
    private final TripDayDAO tripDayDAO;
    private final ItineraryItemDAO itineraryItemDAO;
    private final PlaceDAO placeDAO;
    private final ActiveMemberGuard activeMemberGuard;
    private final ItineraryItemValidator itineraryItemValidator;

    @Transactional
    public AiTripPlanSaveResult save(Long userId, AiTripPlanSaveRequest saveRequest) {
        requireValidUserId(userId);
        activeMemberGuard.requireActiveMember(userId);
        AiTripPlanRequest conditions = saveRequest.conditions();
        AiTripPlanResponse plan = saveRequest.plan();
        int expectedDays = validate(conditions, plan);
        Map<String, AiTripPlanResolvedPlace> resolvedPlaces = (saveRequest.resolvedPlaces() == null
                ? List.<AiTripPlanResolvedPlace>of()
                : saveRequest.resolvedPlaces()).stream()
                .collect(Collectors.toMap(
                        place -> place.day() + ":" + place.number(),
                        Function.identity(),
                        (first, ignored) -> first
                ));
        validatePlaceLinks(plan, resolvedPlaces);

        TripDTO trip = TripDTO.builder()
                .userId(userId)
                .title(requiredText(
                        saveRequest.title() == null || saveRequest.title().isBlank()
                                ? plan.title()
                                : saveRequest.title(),
                        "여행 제목",
                        150
                ))
                .destinationName(requiredText(conditions.destination(), "목적지", 150))
                .startDate(conditions.startDate())
                .endDate(conditions.endDate())
                .companionType(companionType(conditions.companion()))
                .companionCount(conditions.travelers())
                .purpose(requiredText(conditions.purpose(), "여행 목적", 500))
                .currencyCode("KRW")
                .transportPreference(requiredText(conditions.transportPreference(), "이동 선호", 100))
                .foodPreference(requiredText(conditions.foodPreference(), "음식 선호", 255))
                .pace(pace(conditions.pace()))
                .accommodationStyle(requiredText(conditions.accommodationStyle(), "숙박 형태", 100))
                .status("CONFIRMED")
                .source("AI")
                .build();
        if (tripDAO.insert(trip) != 1 || trip.getTripId() == null) {
            throw new BusinessException(ErrorCode.TRIP_CREATE_FAILED);
        }

        int savedItems = 0;
        for (int index = 0; index < expectedDays; index++) {
            AiTripPlanDayResponse planDay = plan.days().get(index);
            TripDayDTO day = TripDayDTO.builder()
                    .tripId(trip.getTripId())
                    .dayNumber(index + 1)
                    .tripDate(conditions.startDate().plusDays(index))
                    .title(requiredText(planDay.title(), "일차 제목", 150))
                    .memo("AI 추천 일정")
                    .build();
            if (tripDayDAO.insert(day) != 1 || day.getTripDayId() == null) {
                throw new BusinessException(ErrorCode.TRIP_CREATE_FAILED);
            }
            savedItems += saveItems(day.getTripDayId(), planDay, resolvedPlaces);
        }
        return new AiTripPlanSaveResult(trip.getTripId(), expectedDays, savedItems);
    }

    // AI 일정은 자유 텍스트 장소명을 카카오 장소와 매칭해야 한다. 매칭에 실패한 장소를
    // place_id=null로 저장하면 스케줄 페이지의 지도·이동수단 기능이 동작하지 않으므로,
    // 여행을 insert하기 전에 모든 장소 연결을 먼저 확인하고 하나라도 실패하면 저장을 중단한다.
    private void validatePlaceLinks(AiTripPlanResponse plan,
                                    Map<String, AiTripPlanResolvedPlace> resolvedPlaces) {
        for (AiTripPlanDayResponse day : plan.days()) {
            List<AiTripPlanItemResponse> schedules = day.items() == null ? List.of() : day.items();
            List<AiTripPlanPlaceResponse> places = day.places() == null ? List.of() : day.places();
            if (schedules.size() != places.size()) {
                throw new IllegalArgumentException("DAY " + day.day() + "의 일정과 장소 개수가 일치하지 않습니다.");
            }
            if (places.isEmpty() || places.size() > TripPolicy.MAX_ITINERARY_ITEMS_PER_DAY) {
                throw new IllegalArgumentException(
                        "하루 일정은 1개부터 " + TripPolicy.MAX_ITINERARY_ITEMS_PER_DAY + "개까지 저장할 수 있습니다.");
            }
            // 수동 일정 추가와 동일하게 같은 DAY에 동일한 장소가 중복 연결되는 것을 막는다.
            Set<String> usedExternalIds = new HashSet<>();
            for (AiTripPlanPlaceResponse place : places) {
                AiTripPlanResolvedPlace resolved = resolvedPlaces.get(day.day() + ":" + place.number());
                if (resolved == null || resolved.externalPlaceId() == null || resolved.externalPlaceId().isBlank()
                        || resolved.latitude() == null || resolved.longitude() == null) {
                    throw new IllegalArgumentException(
                            "카카오 장소를 찾지 못했습니다: " + place.name() + ". 일정을 다시 생성해 주세요.");
                }
                if (!usedExternalIds.add(resolved.externalPlaceId())) {
                    throw new BusinessException(ErrorCode.ITINERARY_PLACE_ALREADY_ADDED);
                }
            }
        }
    }

    private int saveItems(Long tripDayId, AiTripPlanDayResponse day,
                          Map<String, AiTripPlanResolvedPlace> resolvedPlaces) {
        List<AiTripPlanItemResponse> schedules = day.items();
        List<AiTripPlanPlaceResponse> places = day.places();
        for (int index = 0; index < places.size(); index++) {
            AiTripPlanItemResponse schedule = schedules.get(index);
            AiTripPlanPlaceResponse place = places.get(index);
            AiTripPlanResolvedPlace resolved = resolvedPlaces.get(day.day() + ":" + place.number());
            ItineraryItemDTO item = ItineraryItemDTO.builder()
                    .tripDayId(tripDayId)
                    .placeId(savePlace(resolved, place))
                    .itemType(itemType(place, schedule))
                    .title(requiredText(place.name(), "일정 제목", 150))
                    .startTime(parseTime(schedule.time()))
                    .sortOrder(index + 1)
                    .memo(joinDescriptions(place.description(), schedule.description()))
                    .currencyCode("KRW")
                    .source("AI")
                    .build();
            itineraryItemValidator.validate(item);
            if (itineraryItemDAO.insert(item) != 1) {
                throw new BusinessException(ErrorCode.TRIP_CREATE_FAILED);
            }
        }
        return places.size();
    }

    private Long savePlace(AiTripPlanResolvedPlace resolved, AiTripPlanPlaceResponse recommendation) {
        return placeDAO.upsert(PlaceDTO.builder()
                .externalProvider("KAKAO")
                .externalPlaceId(resolved.externalPlaceId())
                .category(placeCategory(resolved.category(), recommendation.category()))
                .name(requiredText(resolved.name(), "장소명", 150))
                .countryCode("KR")
                // 카카오 검색 경로와 같은 규칙으로 끊는다. 여기서 비워 보내면 AI 일정으로
                // 들어온 장소만 지역 필터 검색에서 빠진다.
                .region(KoreanAddress.region(resolved.address()))
                .city(KoreanAddress.city(resolved.address()))
                .address(resolved.address())
                .latitude(resolved.latitude())
                .longitude(resolved.longitude())
                .description(resolved.description())
                .phone(resolved.phone())
                .websiteUrl(resolved.websiteUrl())
                .active(true)
                .build());
    }

    private String placeCategory(String kakaoCategory, String recommendationCategory) {
        String combined = String.valueOf(kakaoCategory) + " " + String.valueOf(recommendationCategory);
        if (combined.contains("숙박") || combined.contains("숙소") || combined.contains("호텔")) return "ACCOMMODATION";
        if (combined.contains("카페")) return "CAFE";
        if (combined.contains("음식") || combined.contains("식사") || combined.contains("맛집")) return "RESTAURANT";
        if (combined.contains("교통") || combined.contains("역") || combined.contains("터미널")) return "TRANSPORT";
        return "ATTRACTION";
    }

    private int validate(AiTripPlanRequest conditions, AiTripPlanResponse plan) {
        if (conditions.endDate().isBefore(conditions.startDate())) {
            throw new IllegalArgumentException("여행 종료일은 시작일보다 빠를 수 없습니다.");
        }
        int days = Math.toIntExact(ChronoUnit.DAYS.between(conditions.startDate(), conditions.endDate())) + 1;
        if (days > MAX_DAYS || plan.days() == null || plan.days().size() != days) {
            throw new IllegalArgumentException("AI 일정 일수와 여행 기간이 일치하지 않습니다.");
        }
        return days;
    }

    private void requireValidUserId(Long userId) {
        if (userId == null || userId < 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    private String companionType(String companion) {
        if (companion == null) return "OTHER";
        if (companion.contains("혼자")) return "SOLO";
        if (companion.contains("친구")) return "FRIENDS";
        if (companion.contains("커플") || companion.contains("연인")) return "COUPLE";
        if (companion.contains("가족") || companion.contains("부모") || companion.contains("아이")) return "FAMILY";
        if (companion.contains("단체") || companion.contains("그룹")) return "GROUP";
        return "OTHER";
    }

    private String pace(String value) {
        if (value == null) return "NORMAL";
        if (value.contains("아주") || value.contains("여유")) return "RELAXED";
        if (value.contains("알찬") || value.contains("테마")) return "PACKED";
        return "NORMAL";
    }

    private String itemType(AiTripPlanPlaceResponse place, AiTripPlanItemResponse schedule) {
        String category = place == null ? "" : String.valueOf(place.category());
        String title = schedule == null ? "" : String.valueOf(schedule.title());
        if (category.contains("숙소")) return "ACCOMMODATION";
        if (category.contains("식사") || category.contains("맛집")) return "MEAL";
        if (title.contains("귀가") || title.contains("이동")) return "TRANSPORT";
        return place == null ? "ACTIVITY" : "PLACE";
    }

    private LocalTime parseTime(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalTime.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String joinDescriptions(String first, String second) {
        if (first == null || first.isBlank()) return second;
        if (second == null || second.isBlank() || first.equals(second)) return first;
        return first + "\n" + second;
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "은 필수입니다.");
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) throw new IllegalArgumentException(fieldName + "이 너무 깁니다.");
        return trimmed;
    }
}
