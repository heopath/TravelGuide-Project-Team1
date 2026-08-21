package org.example.all_my_trip_project.global.config;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.support.websocket.SupportChatChannelInterceptor;
import org.example.all_my_trip_project.domain.support.websocket.SupportChatHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 상담 채팅 실시간 수신용 STOMP 엔드포인트(설계 문서 §2).
 *
 * <p>별도 서버나 외부 브로커를 두지 않는다 — 지금은 단일 EC2 인스턴스라 스프링 기본 내장
 * 심플 브로커로 시작한다("서버 구성" 절 확정 사항). 인스턴스를 늘릴 때 이 결정을 다시 본다.
 *
 * <p>발신 경로는 그대로 REST POST를 쓴다(§2 확정) — 그래서 {@code /app} 목적지로 오는 SEND를
 * 처리하는 {@code @MessageMapping} 컨트롤러가 없다. 클라이언트는 구독만 한다.
 */
@Configuration
@Profile("!ui")
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final SupportChatHandshakeInterceptor supportChatHandshakeInterceptor;
    private final SupportChatChannelInterceptor supportChatChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // setAllowedOrigins를 따로 주지 않는다 — 동일 출처만 허용한다는 확정 사항(§2)의 기본값이다.
        registry.addEndpoint("/ws/support-chat")
                .addInterceptors(supportChatHandshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(supportChatChannelInterceptor);
    }
}
