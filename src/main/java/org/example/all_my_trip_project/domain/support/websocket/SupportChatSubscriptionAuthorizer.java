package org.example.all_my_trip_project.domain.support.websocket;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.support.dao.SupportChatDAO;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@code /topic/support-chat/rooms/{roomId}} 구독 권한을 판단한다.
 *
 * <p>클라이언트가 STOMP 프레임에 실어 보내는 값은 아무것도 믿지 않는다. 판단 재료는 서버가
 * 세션에서 얻은 {@link AuthenticatedUser}(핸드셰이크 때 Spring Security 세션에서 채워진
 * 값)와, DB에서 다시 조회한 방의 실제 주인뿐이다(설계 문서 §4 "신뢰 경계").
 */
@Component
@Profile("!ui")
@RequiredArgsConstructor
public class SupportChatSubscriptionAuthorizer {

    private static final String ADMIN_ROLE = "ADMIN";

    private final SupportChatDAO supportChatDAO;

    public boolean canSubscribe(AuthenticatedUser principal, Long roomId) {
        if (principal == null || roomId == null) return false;
        if (ADMIN_ROLE.equals(principal.role())) return true;
        if (principal.userId() == null) return false;
        return supportChatDAO.findRoom(roomId)
                .map(room -> principal.userId().equals(room.getUserId()))
                .orElse(false);
    }
}
