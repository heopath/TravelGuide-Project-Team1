package org.example.all_my_trip_project.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;

@Configuration
@Profile("!ui")
@EnableCaching
@Slf4j
public class CacheConfig implements CachingConfigurer {

    /**
     * 항공편 검색 캐시만 6시간으로 늘린다.
     *
     * <p>국내선 스케줄은 하루 단위로 거의 바뀌지 않는데, TAGO는 일일 트래픽 제한이 있다.
     * 기본 TTL(10분)로는 같은 날짜를 반복 조회하는 사용자 몇 명만으로 개발계정 한도를 쓴다.
     *
     * <p><b>설정을 새로 만들지 말고 {@code builder.cacheDefaults()}에서 TTL만 덮어쓴다.</b>
     * {@code RedisCacheConfiguration.defaultCacheConfig()}로 새로 만들면 Spring Boot가
     * {@code spring.cache.redis.*}로 구성해 둔 값을 전부 잃는다. 실제로 잃었던 것은 셋이다.
     *
     * <ul>
     *   <li>클래스 로더 — devtools는 애플리케이션 클래스를 RestartClassLoader로 읽는데
     *       캐시는 기본 클래스 로더로 읽어, 이름만 같고 서로 다른 클래스가 된다.
     *       두 번째 조회부터 ClassCastException이 났다(#139).</li>
     *   <li>키 접두어 {@code all-my-trips:} — 이 캐시만 접두어 없이 저장돼
     *       Redis를 다른 용도와 함께 쓸 때 우리 키만 골라내지 못한다.</li>
     *   <li>{@code cache-null-values=false}</li>
     * </ul>
     *
     * <p>숙박·관광티켓처럼 외부 API 캐시를 추가할 때도 이 블록을 복사하게 된다.
     * 캐시 이름과 TTL만 바꾸고 나머지는 반드시 {@code cacheDefaults()}에서 물려받는다.
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer flightSearchCacheCustomizer() {
        return builder -> builder.withCacheConfiguration("flightSearch",
                builder.cacheDefaults().entryTtl(Duration.ofHours(6)));
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("캐시 조회 실패로 DB 조회를 계속합니다. cache={}, key={}",
                        cache.getName(), key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache,
                                            Object key, Object value) {
                log.warn("캐시 저장에 실패했습니다. cache={}, key={}",
                        cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("캐시 제거에 실패했습니다. cache={}, key={}",
                        cache.getName(), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("캐시 전체 제거에 실패했습니다. cache={}", cache.getName(), exception);
            }
        };
    }
}
