package org.example.all_my_trip_project.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;

/**
 * {@code @Async}를 켠다.
 *
 * <p>상담 챗봇(Gemini 호출)이 첫 사용처다 — 사용자 메시지를 저장하는 트랜잭션 안에서 외부
 * API 응답을 기다리면 커넥션을 오래 붙잡으므로, 트랜잭션이 커밋된 뒤 별도 스레드에서
 * 호출한다({@link org.example.all_my_trip_project.domain.support.service.SupportChatBotOrchestrator}).
 *
 * <p>{@code ui} 프로필에서는 켜지 않는다({@link SchedulingConfig}와 같은 이유).
 */
@Configuration
@Profile("!ui")
@EnableAsync
public class AsyncConfig {

    /** WebSocket 실행기와 구분되는 애플리케이션 {@code @Async} 기본 실행기. */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("support-chat-bot-");
        executor.initialize();
        return executor;
    }
}
