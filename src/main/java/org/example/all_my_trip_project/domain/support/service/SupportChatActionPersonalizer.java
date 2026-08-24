package org.example.all_my_trip_project.domain.support.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.support.dao.SupportChatDAO;
import org.example.all_my_trip_project.domain.support.dto.SupportChatMessageDTO;
import org.example.all_my_trip_project.domain.trip.service.TripService;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;

/** 사용자의 실제 여행 보유 상태에 맞춰 봇의 다음 행동 선택지를 보정한다. */
@Component
@Profile("!ui")
@RequiredArgsConstructor
@Slf4j
public class SupportChatActionPersonalizer {

    private final SupportChatDAO supportChatDAO;
    private final TripService tripService;

    public List<String> personalize(
            Long roomId,
            List<SupportChatMessageDTO> conversation,
            List<String> proposedActions) {
        List<String> proposed = proposedActions == null
                ? List.of()
                : proposedActions.stream().distinct().limit(3).toList();
        String latestUserText = latestUserText(conversation);
        boolean asksTripPlanning = latestUserText.contains("여행")
                && List.of("만들", "계획", "일정", "짜").stream().anyMatch(latestUserText::contains);
        boolean proposesTripScreen = proposed.stream()
                .anyMatch(List.of("NEW_TRIP", "MY_TRIPS", "TRIP_SCHEDULE")::contains);
        if (!asksTripPlanning && !proposesTripScreen) return proposed;

        boolean hasTrip;
        try {
            hasTrip = supportChatDAO.findRoom(roomId)
                    .map(room -> !tripService.getByUser(room.getUserId()).isEmpty())
                    .orElse(false);
        } catch (RuntimeException exception) {
            /* 개인화 실패가 정상적인 봇 답변 저장까지 막아서는 안 된다. 원래 제안을 그대로 쓴다. */
            log.warn("상담 액션 개인화에 실패해 원래 액션을 사용합니다. roomId={}", roomId, exception);
            return proposed;
        }

        if (asksTripPlanning || proposed.contains("NEW_TRIP")) {
            return hasTrip
                    ? List.of("NEW_TRIP", "MY_TRIPS", "TRIP_SCHEDULE")
                    : List.of("NEW_TRIP", "RECOMMENDED_PLACES");
        }
        if (!hasTrip && (proposed.contains("MY_TRIPS") || proposed.contains("TRIP_SCHEDULE"))) {
            return List.of("NEW_TRIP", "RECOMMENDED_PLACES");
        }
        return new ArrayList<>(proposed);
    }

    private static String latestUserText(List<SupportChatMessageDTO> conversation) {
        if (conversation == null) return "";
        for (int i = conversation.size() - 1; i >= 0; i--) {
            SupportChatMessageDTO message = conversation.get(i);
            if ("USER".equals(message.getSenderType())) return String.valueOf(message.getContent());
        }
        return "";
    }
}
