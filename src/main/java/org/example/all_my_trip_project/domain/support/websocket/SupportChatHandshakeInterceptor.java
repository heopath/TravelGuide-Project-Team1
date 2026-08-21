package org.example.all_my_trip_project.domain.support.websocket;

import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Profile;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * SockJS 핸드셰이크 시점에 {@code CSRF-TOKEN} 쿠키 값을 WebSocket 세션 속성으로 옮겨 둔다.
 *
 * <p>핸드셰이크가 끝나면 원래의 {@link jakarta.servlet.http.HttpServletRequest}는 사라지고
 * 쿠키를 다시 읽을 방법이 없다. STOMP {@code CONNECT} 프레임 안의 CSRF 헤더를 검증하려면
 * (설계 문서 §2) 비교할 값을 미리 세션 속성에 심어 둬야 한다 — {@link SupportChatChannelInterceptor}
 * 가 이 값을 읽는다.
 */
@Component
@Profile("!ui")
public class SupportChatHandshakeInterceptor implements HandshakeInterceptor {

    public static final String CSRF_COOKIE_ATTRIBUTE = "supportChatCsrfCookieToken";
    private static final String CSRF_COOKIE_NAME = "CSRF-TOKEN";

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            Cookie[] cookies = servletRequest.getServletRequest().getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (CSRF_COOKIE_NAME.equals(cookie.getName())) {
                        attributes.put(CSRF_COOKIE_ATTRIBUTE, cookie.getValue());
                        break;
                    }
                }
            }
        }
        /*
         * 여기서 거부하지 않는다. 이 시점엔 아직 STOMP CONNECT 프레임을 못 봤으므로 비교할
         * CSRF 값이 없다 — 실제 검증은 SupportChatChannelInterceptor의 CONNECT 처리에서 한다.
         * 인증 자체는 SecurityConfig가 이 경로를 인증 필요로 이미 막아 둔다.
         */
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }
}
