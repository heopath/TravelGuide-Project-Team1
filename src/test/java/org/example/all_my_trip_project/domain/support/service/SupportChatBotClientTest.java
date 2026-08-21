package org.example.all_my_trip_project.domain.support.service;

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
}
