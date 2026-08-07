package org.example.all_my_trip_project.domain.ai.service;

import org.example.all_my_trip_project.domain.ai.dto.AiGuideDayResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideItemResponse;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideRequest;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideResponse;
import org.example.all_my_trip_project.domain.rag.dto.RagPlaceResult;
import org.example.all_my_trip_project.domain.ai.dto.AiConversationTurn;
import org.example.all_my_trip_project.domain.ai.dto.AiGuideContext;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Component
@Profile("!ai")
public class MockAiModelClient implements AiModelClient {

    @Override
    public AiGuideResponse generate(AiGuideRequest request, List<AiConversationTurn> conversationHistory,
                                    AiGuideContext context, List<RagPlaceResult> ragPlaces) {
        return new AiGuideResponse(
                "광안리에서 저녁을 먹고 해변 야경까지 즐길 수 있도록 DAY 1 일정을 추천해요.",
                List.of(new AiGuideDayResponse(
                        1,
                        "DAY 1 · 광안리 미식 산책",
                        List.of(
                                new AiGuideItemResponse("18:00", "민락회센터", "광안리에서 도보 10분 · 저녁 식사에 적합"),
                                new AiGuideItemResponse("20:00", "광안리 해변 산책", "식사 후 이동 부담이 적은 야경 코스")
                        )
                )),
                List.of(
                        new AiGuideResponse.ExternalLink("FLIGHT", "부산 항공권 검색", "https://www.google.com/travel/flights"),
                        new AiGuideResponse.ExternalLink("HOTEL", "광안리 숙소 검색", "https://www.google.com/travel/hotels")
                ),
                List.of("현재 일정", "부산 장소 데이터", "질문: " + request.question())
        );
    }
}
