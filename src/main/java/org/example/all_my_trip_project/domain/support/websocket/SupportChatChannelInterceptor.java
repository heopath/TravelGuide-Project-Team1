package org.example.all_my_trip_project.domain.support.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.support.dto.SupportChatSocketError;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;

/**
 * 상담 채팅 WebSocket 연결의 CONNECT·SUBSCRIBE를 검사한다(설계 문서 §2, §4).
 *
 * <ul>
 *   <li>{@code CONNECT}: STOMP 헤더에 실린 CSRF 토큰을, 핸드셰이크 때 쿠키에서 미리 읽어 둔
 *   값({@link SupportChatHandshakeInterceptor})과 비교한다. 다르면 연결 자체를 거부한다
 *   ({@code preSend}가 {@code null}을 돌려주면 스프링이 프레임을 버리고 세션을 정리한다).</li>
 *   <li>{@code SUBSCRIBE}: {@link SupportChatSubscriptionAuthorizer}로 판단한다. 거부해도
 *   연결은 끊지 않는다 — 구독만 등록하지 않고, 복구 가능한 오류로
 *   {@code /user/queue/support-chat/errors}에 알린다(설계 문서 §3).</li>
 * </ul>
 */
@Component
@Profile("!ui")
@RequiredArgsConstructor
@Slf4j
public class SupportChatChannelInterceptor implements ChannelInterceptor {

    private static final String ROOM_TOPIC_PREFIX = "/topic/support-chat/rooms/";
    private static final String CSRF_HEADER = "X-CSRF-TOKEN";
    private static final String ERROR_QUEUE = "/queue/support-chat/errors";

    private final SupportChatSubscriptionAuthorizer authorizer;
    /*
     * SimpMessagingTemplate을 바로 주입하지 않는다. 이 인터셉터는 WebSocketConfig(메시지
     * 브로커 설정 그 자체)의 생성자 의존성이라, brokerMessagingTemplate 빈이 만들어지는
     * 과정 안에서 다시 이 빈을 필요로 하게 되어 순환 참조가 생긴다. ObjectProvider로 받으면
     * 생성 시점에는 아무것도 조회하지 않고, 실제로 오류를 보낼 때만 조회한다.
     */
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            return authorizeConnect(accessor) ? message : null;
        }
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return authorizeSubscribe(accessor) ? message : null;
        }
        return message;
    }

    private boolean authorizeConnect(StompHeaderAccessor accessor) {
        String presented = accessor.getFirstNativeHeader(CSRF_HEADER);
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        Object expected = sessionAttributes == null
                ? null : sessionAttributes.get(SupportChatHandshakeInterceptor.CSRF_COOKIE_ATTRIBUTE);
        if (presented == null || !presented.equals(expected)) {
            log.warn("상담 채팅 WebSocket CONNECT의 CSRF 토큰이 일치하지 않아 연결을 거부합니다.");
            return false;
        }
        return true;
    }

    private boolean authorizeSubscribe(StompHeaderAccessor accessor) {
        Long roomId = extractRoomId(accessor.getDestination());
        AuthenticatedUser principal = resolvePrincipal(accessor);
        if (roomId != null && authorizer.canSubscribe(principal, roomId)) {
            return true;
        }
        sendForbidden(accessor);
        return false;
    }

    private Long extractRoomId(String destination) {
        if (destination == null || !destination.startsWith(ROOM_TOPIC_PREFIX)) return null;
        try {
            return Long.valueOf(destination.substring(ROOM_TOPIC_PREFIX.length()));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private AuthenticatedUser resolvePrincipal(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (!(user instanceof Authentication authentication)) return null;
        if (!(authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser)) return null;
        return authenticatedUser;
    }

    private void sendForbidden(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        SimpMessagingTemplate messagingTemplate = messagingTemplateProvider.getIfAvailable();
        if (user == null || messagingTemplate == null) return;
        messagingTemplate.convertAndSendToUser(
                user.getName(),
                ERROR_QUEUE,
                SupportChatSocketError.validation(
                        ErrorCode.SUPPORT_CHAT_SUBSCRIBE_FORBIDDEN.name(),
                        ErrorCode.SUPPORT_CHAT_SUBSCRIBE_FORBIDDEN.getMessage()));
    }
}
