package org.example.all_my_trip_project.domain.support.websocket;

import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SUBSCRIBE 목적지별 인가.
 *
 * <p>목적지를 전부 방 토픽으로만 해석하면, 설계 문서 §3에서 확정한 본인 오류 큐 구독까지
 * "roomId가 없다"는 이유로 거부된다 — 서버가 오류를 보내도 받을 곳이 없어진다.
 */
class SupportChatChannelInterceptorTest {

    private static final String ROOM_TOPIC = "/topic/support-chat/rooms/5";
    private static final String ADMIN_ROOMS_TOPIC = "/topic/support-chat/admin/rooms";
    private static final String ERROR_QUEUE = "/user/queue/support-chat/errors";

    private SupportChatSubscriptionAuthorizer authorizer;
    private SupportChatChannelInterceptor interceptor;
    private MessageChannel channel;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        authorizer = mock(SupportChatSubscriptionAuthorizer.class);
        ObjectProvider<SimpMessagingTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(SimpMessagingTemplate.class));
        interceptor = new SupportChatChannelInterceptor(authorizer, provider);
        channel = mock(MessageChannel.class);
    }

    private Message<?> subscribe(String destination, AuthenticatedUser principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (principal != null) {
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, AuthorityUtils.createAuthorityList("ROLE_" + principal.role()));
            accessor.setUser(authentication);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private final AuthenticatedUser user = new AuthenticatedUser(7L, "user@example.com", "USER");
    private final AuthenticatedUser admin = new AuthenticatedUser(90L, "admin@example.com", "ADMIN");

    @Test
    @DisplayName("로그인한 사용자는 본인 오류 큐를 구독할 수 있다")
    void allowsOwnErrorQueue() {
        when(authorizer.canSubscribeUserErrors(any())).thenReturn(true);

        assertThat(interceptor.preSend(subscribe(ERROR_QUEUE, user), channel)).isNotNull();
    }

    @Test
    @DisplayName("로그인하지 않으면 오류 큐도 구독할 수 없다")
    void rejectsErrorQueueWithoutPrincipal() {
        when(authorizer.canSubscribeUserErrors(any())).thenReturn(false);

        assertThat(interceptor.preSend(subscribe(ERROR_QUEUE, null), channel)).isNull();
    }

    @Test
    @DisplayName("관리자 대기열 토픽은 관리자만 구독할 수 있다")
    void allowsAdminQueueForAdminOnly() {
        when(authorizer.canSubscribeAdminRooms(admin)).thenReturn(true);
        when(authorizer.canSubscribeAdminRooms(user)).thenReturn(false);

        assertThat(interceptor.preSend(subscribe(ADMIN_ROOMS_TOPIC, admin), channel)).isNotNull();
        assertThat(interceptor.preSend(subscribe(ADMIN_ROOMS_TOPIC, user), channel)).isNull();
    }

    @Test
    @DisplayName("방 토픽은 방 권한 검사를 그대로 따른다")
    void delegatesRoomTopicToAuthorizer() {
        when(authorizer.canSubscribe(user, 5L)).thenReturn(true);
        assertThat(interceptor.preSend(subscribe(ROOM_TOPIC, user), channel)).isNotNull();

        when(authorizer.canSubscribe(user, 5L)).thenReturn(false);
        assertThat(interceptor.preSend(subscribe(ROOM_TOPIC, user), channel)).isNull();
    }

    @Test
    @DisplayName("알 수 없는 목적지는 거부한다")
    void rejectsUnknownDestination() {
        assertThat(interceptor.preSend(subscribe("/topic/anything-else", admin), channel)).isNull();
    }
}
