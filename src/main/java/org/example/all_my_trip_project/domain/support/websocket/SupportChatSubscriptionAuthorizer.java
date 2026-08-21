package org.example.all_my_trip_project.domain.support.websocket;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.support.dao.SupportChatDAO;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 상담 채팅 구독 권한을 판단한다.
 *
 * <p>클라이언트가 STOMP 프레임에 실어 보내는 값은 아무것도 믿지 않는다. 판단 재료는 서버가
 * 세션에서 얻은 {@link AuthenticatedUser}(핸드셰이크 때 Spring Security 세션에서 채워진
 * 값)와, DB에서 다시 조회한 방의 실제 주인뿐이다(설계 문서 §4 "신뢰 경계").
 *
 * <p>목적지는 세 가지다 — 방 토픽({@code /topic/support-chat/rooms/{roomId}}), 관리자
 * 대기열 토픽({@code /topic/support-chat/admin/rooms}), 그리고 본인 전용 오류 큐
 * ({@code /user/queue/support-chat/errors}). 오류 큐는 스프링이 세션별로 목적지를 갈라 주므로
 * 로그인 여부만 보면 되고, 남의 큐를 지정해 받아 갈 방법은 없다.
 */
@Component
@Profile("!ui")
@RequiredArgsConstructor
public class SupportChatSubscriptionAuthorizer {

    private static final String ADMIN_ROLE = "ADMIN";

    private final SupportChatDAO supportChatDAO;

    /** 관리자 대기열 토픽. 방 하나가 아니라 목록 전체의 변화를 받는 자리라 관리자만 허용한다. */
    public boolean canSubscribeAdminRooms(AuthenticatedUser principal) {
        return principal != null && ADMIN_ROLE.equals(principal.role());
    }

    /** 본인 전용 오류 큐. 목적지를 스프링이 세션으로 갈라 주므로 로그인 여부만 확인한다. */
    public boolean canSubscribeUserErrors(AuthenticatedUser principal) {
        return principal != null;
    }

    public boolean canSubscribe(AuthenticatedUser principal, Long roomId) {
        if (principal == null || roomId == null) return false;
        if (ADMIN_ROLE.equals(principal.role())) return true;
        if (principal.userId() == null) return false;
        return supportChatDAO.findRoom(roomId)
                .map(room -> principal.userId().equals(room.getUserId()))
                .orElse(false);
    }
}
