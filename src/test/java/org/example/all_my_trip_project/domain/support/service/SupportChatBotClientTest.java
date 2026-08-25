package org.example.all_my_trip_project.domain.support.service;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code reply()}는 실제 Gemini 호출이라 여기서 다루지 않는다({@code AiTripPlanService}도 이
 * 저장소에서 같은 이유로 테스트가 없다). 여기서는 응답 끝의 {@code [HANDOFF]} 표시를 걷어내는
 * 순수 파싱 로직만 검증한다.
 */
class SupportChatBotClientTest {

    private final SupportChatBotClient client = new SupportChatBotClient();

    @Test
    @DisplayName("표시가 없으면 그대로 손님에게 보여줄 답이다")
    void parsesPlainReply() {
        SupportChatBotReply reply = client.parse("환불은 마이페이지 > 예약 내역에서 신청할 수 있어요.");

        assertThat(reply.content()).isEqualTo("환불은 마이페이지 > 예약 내역에서 신청할 수 있어요.");
        assertThat(reply.handoff()).isFalse();
    }

    @Test
    @DisplayName("[HANDOFF] 표시는 걷어내고 넘김 신호로만 쓴다")
    void parsesHandoffMarker() {
        SupportChatBotReply reply = client.parse(
                "죄송해요, 그 부분은 상담원에게 안내해 드리는 게 정확해요.\n[HANDOFF]");

        assertThat(reply.content()).isEqualTo("죄송해요, 그 부분은 상담원에게 안내해 드리는 게 정확해요.");
        assertThat(reply.handoff()).isTrue();
    }

    @Test
    @DisplayName("표시만 있고 본문이 비면 기본 안내 문구를 쓴다")
    void fallsBackToDefaultTextWhenOnlyMarkerPresent() {
        SupportChatBotReply reply = client.parse("[HANDOFF]");

        assertThat(reply.content()).isNotBlank();
        assertThat(reply.handoff()).isTrue();
    }

    @Test
    @DisplayName("상담원 연결 재확인 표시는 실제 연결과 구분해 파싱한다")
    void parsesHandoffConfirmation() {
        SupportChatBotReply reply = client.parse("상담원을 연결해 드릴까요?\n[CONFIRM_HANDOFF]");

        assertThat(reply.handoffDecision()).isEqualTo(SupportChatHandoffDecision.CONFIRM);
        assertThat(reply.content()).isEqualTo("상담원을 연결해 드릴까요?");
    }

    @Test
    @DisplayName("허용된 화면 액션은 본문에서 걷어내고 구조화한다")
    void parsesAllowedActionMarker() {
        SupportChatBotReply reply = client.parse("여행 만들기 화면에서 시작할 수 있어요.\n[ACTION:NEW_TRIP]");

        assertThat(reply.content()).isEqualTo("여행 만들기 화면에서 시작할 수 있어요.");
        assertThat(reply.actionKey()).isEqualTo("NEW_TRIP");
    }

    @Test
    @DisplayName("허용 목록 밖의 액션은 링크로 만들지 않는다")
    void rejectsUnknownActionMarker() {
        SupportChatBotReply reply = client.parse("여기로 이동하세요.\n[ACTION:EXTERNAL_SITE]");

        assertThat(reply.content()).isEqualTo("여기로 이동하세요.");
        assertThat(reply.actionKey()).isNull();
    }

    @Test
    @DisplayName("상담원 이관과 화면 액션을 함께 반환할 수 있다")
    void parsesActionAlongsideHandoff() {
        SupportChatBotReply reply = client.parse(
                "여행 만들기 화면으로 안내할게요.\n[ACTION:NEW_TRIP]\n[HANDOFF]");

        assertThat(reply.content()).isEqualTo("여행 만들기 화면으로 안내할게요.");
        assertThat(reply.handoff()).isTrue();
        assertThat(reply.actionKey()).isEqualTo("NEW_TRIP");
    }

    @Test
    @DisplayName("복수 액션은 순서대로 중복 없이 최대 세 개만 받는다")
    void parsesUpToThreeDistinctActions() {
        SupportChatBotReply reply = client.parse("선택해 주세요.\n"
                + "[ACTION:BOOK_FLIGHT]\n[ACTION:BOOK_HOTEL]\n"
                + "[ACTION:BOOK_FLIGHT]\n[ACTION:BOOK_TICKET]\n[ACTION:MY_BOOKINGS]");

        assertThat(reply.actionKeys()).containsExactly("BOOK_FLIGHT", "BOOK_HOTEL", "BOOK_TICKET");
    }

    @Test
    @DisplayName("표시 두 개가 같은 줄에 붙어 있어도 걷어내고 파싱한다(실사용 중 발견한 노출 버그)")
    void parsesMarkersOnTheSameLine() {
        SupportChatBotReply reply = client.parse(
                "혹시 서비스 이용에 불편함이 있으셨나요? 전문 상담원에게 연결해 드릴까요?\n"
                        + "[CONFIRM_HANDOFF] [ACTION:SUPPORT]");

        assertThat(reply.content())
                .isEqualTo("혹시 서비스 이용에 불편함이 있으셨나요? 전문 상담원에게 연결해 드릴까요?");
        assertThat(reply.handoffDecision()).isEqualTo(SupportChatHandoffDecision.CONFIRM);
        assertThat(reply.actionKeys()).containsExactly("SUPPORT");
    }

    @Test
    @DisplayName("액션 표시가 상담원 표시보다 먼저 나와도 정상 파싱한다")
    void parsesActionBeforeConfirmMarkerOnSeparateLines() {
        SupportChatBotReply reply = client.parse(
                "고객센터에서도 도움을 받을 수 있어요.\n[ACTION:SUPPORT]\n[CONFIRM_HANDOFF]");

        assertThat(reply.content()).isEqualTo("고객센터에서도 도움을 받을 수 있어요.");
        assertThat(reply.handoffDecision()).isEqualTo(SupportChatHandoffDecision.CONFIRM);
        assertThat(reply.actionKeys()).containsExactly("SUPPORT");
    }

    @Test
    @DisplayName("DB 검색 후보에 있는 장소만 카드 선택으로 받는다")
    void parsesOnlyKnownPlaceCandidates() {
        List<SupportChatPlaceCandidate> candidates = List.of(
                new SupportChatPlaceCandidate(10L, "남산서울타워", "명소", "서울", "야경 명소"));

        SupportChatBotReply reply = client.parse("야경을 보기 좋아요.\n"
                + "[PLACE:10|서울 야경을 한눈에 볼 수 있어요]\n"
                + "[PLACE:999|존재하지 않는 장소]", candidates);

        assertThat(reply.content()).isEqualTo("야경을 보기 좋아요.");
        assertThat(reply.placeSelections()).containsExactly(
                new SupportChatPlaceSelection(10L, "서울 야경을 한눈에 볼 수 있어요"));
    }
}
