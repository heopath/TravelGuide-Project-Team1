package org.example.all_my_trip_project.domain.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.ai.dto.AiTripPlanDayResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiTripPlanItemResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiTripPlanPlaceResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiTripPlanRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiTripPlanResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class AiTripPlanService {
    private static final int MAX_TRIP_DAYS = 30;
    private static final DateTimeFormatter DAY_FORMATTER =
            DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-3.5-flash}")
    private String geminiModel;

    public AiTripPlanResponse generate(AiTripPlanRequest request) {
        int totalDays = validatePeriod(request.startDate(), request.endDate());
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            return generateWithGemini(request, totalDays);
        }
        return generateMockPlan(request, totalDays);
    }

    private AiTripPlanResponse generateMockPlan(AiTripPlanRequest request, int totalDays) {
        ThemePlan themePlan = themePlan(request.theme());
        List<String> times = paceTimes(request.pace());

        List<AiTripPlanDayResponse> days = java.util.stream.IntStream.range(0, totalDays)
                .mapToObj(index -> createDay(request, themePlan, times, index, totalDays))
                .toList();

        return new AiTripPlanResponse(
                request.destination() + " " + totalDays + "일 여행 초안",
                request.startDate().format(DAY_FORMATTER) + " ~ " + request.endDate().format(DAY_FORMATTER)
                        + " · " + request.travelers() + "명 · " + request.companion()
                        + " · " + request.theme() + " · " + request.pace() + " · " + request.budget(),
                recommendedPlaces(request, themePlan),
                days,
                "SERVER_MOCK"
        );
    }

    private AiTripPlanResponse generateWithGemini(AiTripPlanRequest request, int totalDays) {
        try {
            String responseBody = RestClient.create()
                    .post()
                    .uri("https://generativelanguage.googleapis.com/v1beta/models/"
                            + geminiModel + ":generateContent")
                    .header("x-goog-api-key", geminiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "contents", List.of(Map.of(
                                    "parts", List.of(Map.of("text", buildGeminiPrompt(request, totalDays)))
                            ))
                    ))
                    .retrieve()
                    .body(String.class);

            JsonNode response = responseBody == null ? null : objectMapper.readTree(responseBody);

            String generatedJson = response == null
                    ? ""
                    : response.at("/candidates/0/content/parts/0/text").asText();
            if (generatedJson.isBlank()) {
                throw new IllegalStateException("Gemini가 여행 초안을 반환하지 않았습니다.");
            }
            GeminiTripPlan generated = objectMapper.readValue(generatedJson, GeminiTripPlan.class);
            if (generated.days() == null || generated.days().size() != totalDays
                    || generated.recommendedPlaces() == null || generated.recommendedPlaces().size() < 4) {
                throw new IllegalStateException("Gemini 응답의 일정 형식이 올바르지 않습니다.");
            }
            return toResponse(generated);
        } catch (RestClientResponseException exception) {
            log.warn("Gemini API 응답 오류: status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new IllegalStateException(geminiErrorMessage(exception.getStatusCode()));
        } catch (Exception exception) {
            log.warn("Gemini 여행 초안 생성에 실패했습니다.", exception);
            throw new IllegalStateException("AI 여행 초안을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private String geminiErrorMessage(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 400 -> "Gemini 요청 형식 또는 선택한 모델 설정을 확인해 주세요.";
            case 401, 403 -> "Gemini API 키 또는 해당 Google 프로젝트의 API 사용 권한을 확인해 주세요.";
            case 404 -> "설정한 Gemini 모델을 사용할 수 없습니다. GEMINI_MODEL 설정을 확인해 주세요.";
            case 429 -> "Gemini API 사용 한도에 도달했습니다. 잠시 후 다시 시도하거나 AI Studio 할당량을 확인해 주세요.";
            default -> "AI 여행 초안을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.";
        };
    }

    private String buildGeminiPrompt(AiTripPlanRequest request, int totalDays) {
        return """
                당신은 한국어 여행 플래너입니다. 아래 여행 조건을 모두 반영해 여행 초안을 JSON만으로 작성하세요.

                목적지: %s
                여행 기간: %s ~ %s (%d일)
                여행 인원: %d명
                동행 유형: %s
                여행 테마: %s
                하루 일정 속도: %s
                예산 정도: %s

                응답 조건:
                - title, summary, recommendedPlaces, days 필드를 가진 JSON 객체만 반환합니다.
                - recommendedPlaces는 정확히 4개이며, 순서대로 추천 명소, 식사 장소, 추천 명소, 숙소입니다.
                - 각 장소에는 category, name, description을 넣습니다. name은 카카오 장소 검색으로 찾을 수 있는 구체적인 장소명으로 씁니다.
                - 숙소의 name도 권역이나 지역명이 아닌 실제 호텔, 리조트, 게스트하우스 등 구체적인 숙박시설명으로 씁니다.
                - days는 정확히 %d개이며, 각 day에는 title, items, places를 넣습니다.
                - 각 item에는 time(HH:mm), title, description을 넣고 하루에 3~5개를 제안합니다.
                - 마지막 날을 제외한 각 day의 places에는 지도에 표시할 장소를 추천 명소, 식사 장소, 추천 명소, 숙소 순서로 정확히 4개 넣습니다.
                - 마지막 날의 places는 추천 명소, 식사 장소, 추천 명소 순서로 정확히 3개만 넣고 숙소는 넣지 않습니다.
                - 마지막 날 일정의 마지막 item은 숙소가 아니라 귀가 일정으로 작성합니다.
                - 실제 영업시간, 가격, 예약 가능 여부를 확정적으로 말하지 않습니다.
                - 마크다운, 코드 블록, JSON 이외의 문장은 절대 넣지 않습니다.
                """.formatted(
                request.destination(), request.startDate(), request.endDate(), totalDays, request.travelers(),
                request.companion(), request.theme(), request.pace(), request.budget(), totalDays
        );
    }

    private AiTripPlanResponse toResponse(GeminiTripPlan generated) {
        List<AiTripPlanPlaceResponse> places = java.util.stream.IntStream
                .range(0, 4)
                .mapToObj(index -> {
                    GeminiPlace place = generated.recommendedPlaces().get(index);
                    return new AiTripPlanPlaceResponse(index + 1, place.category(), place.name(), place.description(), 0, 0);
                })
                .toList();
        List<AiTripPlanDayResponse> days = java.util.stream.IntStream
                .range(0, generated.days().size())
                .mapToObj(index -> {
                    GeminiDay day = generated.days().get(index);
                    return new AiTripPlanDayResponse(
                            index + 1,
                            day.title(),
                            day.items().stream()
                                    .map(item -> new AiTripPlanItemResponse(item.time(), item.title(), item.description()))
                                    .toList(),
                            toDayPlaces(
                                    day.places(),
                                    generated.recommendedPlaces(),
                                    index < generated.days().size() - 1
                            )
                    );
                })
                .toList();
        return new AiTripPlanResponse(generated.title(), generated.summary(), places, days, "GEMINI");
    }

    private int validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("여행 종료일은 시작일보다 빠를 수 없습니다.");
        }
        int totalDays = Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate)) + 1;
        if (totalDays > MAX_TRIP_DAYS) {
            throw new IllegalArgumentException("여행 기간은 최대 30일까지 선택할 수 있습니다.");
        }
        return totalDays;
    }

    private AiTripPlanDayResponse createDay(
            AiTripPlanRequest request,
            ThemePlan themePlan,
            List<String> times,
            int index,
            int totalDays
    ) {
        LocalDate date = request.startDate().plusDays(index);
        String companionDescription = companionDescription(request.companion());
        String budgetDescription = budgetDescription(request.budget());
        List<AiTripPlanItemResponse> items = List.of(
                new AiTripPlanItemResponse(
                        times.get(0),
                        request.destination() + " " + themePlan.first(),
                        companionDescription
                ),
                new AiTripPlanItemResponse(
                        times.get(1),
                        themePlan.second(),
                        request.travelers() + "명이 함께 즐기기 좋은 식사 시간이에요. " + budgetDescription
                ),
                new AiTripPlanItemResponse(
                        times.get(2),
                        themePlan.third(),
                        "이동 시간을 줄이고 " + request.pace() + " 둘러볼 수 있도록 구성했어요."
                ),
                new AiTripPlanItemResponse(
                        times.get(3),
                        index == totalDays - 1 ? "귀가" : "하루를 마무리하는 저녁 시간",
                        index == totalDays - 1
                                ? "짐을 챙기고 교통편 시간을 확인한 뒤 안전하게 귀가하세요."
                                : "숙소 주변 또는 야경 명소에서 여행의 여운을 즐겨 보세요."
                )
        );
        return new AiTripPlanDayResponse(
                index + 1,
                "DAY " + (index + 1) + " · " + date.format(DAY_FORMATTER),
                items,
                dayPlaces(request, themePlan, index, totalDays)
        );
    }

    private List<AiTripPlanPlaceResponse> dayPlaces(
            AiTripPlanRequest request,
            ThemePlan themePlan,
            int index,
            int totalDays
    ) {
        int day = index + 1;
        List<AiTripPlanPlaceResponse> places = new ArrayList<>(List.of(
                new AiTripPlanPlaceResponse(1, "추천 명소", request.destination() + " " + themePlan.first(),
                        day + "일차 오전에 들르기 좋은 추천 명소예요.", 0, 0),
                new AiTripPlanPlaceResponse(2, "식사 장소", request.destination() + " " + themePlan.second(),
                        request.budget() + " 예산에 맞춘 식사 장소예요.", 0, 0),
                new AiTripPlanPlaceResponse(3, "추천 명소", request.destination() + " " + themePlan.third(),
                        day + "일차 오후 동선에 맞춘 추천 명소예요.", 0, 0)
        ));
        if (day < totalDays) {
            places.add(new AiTripPlanPlaceResponse(4, "숙소", request.destination() + " 중심 숙소",
                    day + "일차 일정 후 이동하기 편한 숙소 권역이에요.", 0, 0));
        }
        return List.copyOf(places);
    }

    private List<AiTripPlanPlaceResponse> toDayPlaces(
            List<GeminiPlace> dayPlaces,
            List<GeminiPlace> fallbackPlaces,
            boolean includeAccommodation
    ) {
        int placeCount = includeAccommodation ? 4 : 3;
        return java.util.stream.IntStream.range(0, placeCount)
                .mapToObj(index -> {
                    GeminiPlace place = dayPlaces != null && dayPlaces.size() > index
                            ? dayPlaces.get(index)
                            : fallbackPlaces.get(index);
                    return new AiTripPlanPlaceResponse(
                            index + 1,
                            place.category(),
                            place.name(),
                            place.description(),
                            0,
                            0
                    );
                })
                .toList();
    }

    private List<AiTripPlanPlaceResponse> recommendedPlaces(AiTripPlanRequest request, ThemePlan themePlan) {
        return List.of(
                new AiTripPlanPlaceResponse(
                        1, "추천 명소", request.destination() + " " + themePlan.first(),
                        request.theme() + " 여행의 시작점으로 추천하는 대표 장소예요.", 24, 31
                ),
                new AiTripPlanPlaceResponse(
                        2, "식사 장소", "현지 인기 식당",
                        request.budget() + " 예산에 맞춘 점심 식사 추천 장소예요.", 61, 25
                ),
                new AiTripPlanPlaceResponse(
                        3, "추천 명소", themePlan.third(),
                        request.pace() + " 일정 속도에 맞춰 들르기 좋은 오후 장소예요.", 46, 67
                ),
                new AiTripPlanPlaceResponse(
                        4, "숙소", request.destination() + " 중심 숙소",
                        "저녁 일정 후 편하게 이동할 수 있는 숙소 권역을 추천해요.", 78, 60
                )
        );
    }

    private ThemePlan themePlan(String theme) {
        Map<String, ThemePlan> plans = Map.of(
                "도시 명소", new ThemePlan("대표 명소 둘러보기", "현지 인기 식당에서 점심", "골목과 쇼핑 거리 산책"),
                "맛집과 카페", new ThemePlan("현지 인기 브런치 즐기기", "대표 맛집에서 점심", "분위기 좋은 카페에서 휴식"),
                "자연과 휴식", new ThemePlan("자연 명소에서 가벼운 산책", "풍경을 즐기는 여유로운 점심", "휴식 명소에서 느긋한 오후"),
                "문화와 예술", new ThemePlan("대표 문화·예술 공간 관람", "현지 감성 식당에서 점심", "전시와 골목 문화 산책")
        );
        return plans.getOrDefault(theme, plans.get("도시 명소"));
    }

    private List<String> paceTimes(String pace) {
        return switch (pace) {
            case "알차게" -> List.of("09:00", "12:00", "14:30", "18:30");
            case "적당하게" -> List.of("09:30", "12:30", "15:00", "19:00");
            default -> List.of("10:00", "13:00", "15:30", "19:00");
        };
    }

    private String companionDescription(String companion) {
        return switch (companion) {
            case "커플" -> "함께 사진과 대화를 즐기기 좋은 장소를 중심으로 구성했어요.";
            case "가족" -> "모두가 편하게 쉬고 이동할 수 있도록 여유를 두었어요.";
            case "친구" -> "함께 즐기고 추억을 남기기 좋은 장소를 우선 추천해요.";
            default -> "혼자서도 편하게 머물 수 있는 장소와 동선을 우선으로 골랐어요.";
        };
    }

    private String budgetDescription(String budget) {
        return switch (budget) {
            case "알뜰하게" -> "대중교통과 무료·합리적인 체험을 우선으로 추천해요.";
            case "여유 있게" -> "예약형 체험과 편안한 이동을 고려해 추천해요.";
            default -> "이동 편의와 인기 장소를 균형 있게 담았어요.";
        };
    }

    private record ThemePlan(String first, String second, String third) {
    }

    private record GeminiTripPlan(
            String title,
            String summary,
            List<GeminiPlace> recommendedPlaces,
            List<GeminiDay> days
    ) {
    }

    private record GeminiPlace(String category, String name, String description) {
    }

    private record GeminiDay(String title, List<GeminiItem> items, List<GeminiPlace> places) {
    }

    private record GeminiItem(String time, String title, String description) {
    }
}
