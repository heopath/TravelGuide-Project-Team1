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
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager.RedisCacheManagerBuilder;

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
                withTtl(builder, Duration.ofHours(6)));
    }

    /**
     * 숙소 검색 캐시는 1시간으로 둔다.
     *
     * <p><b>요금 보강이 실제로 붙으면 5분으로 되돌려야 한다.</b> 판단 기준은 검색 응답의
     * {@code meta.matchedPriceCount}다. 이 값이 0보다 커지는 순간 아래 이유가 다시 살아난다.
     *
     * <p>원래 5분이었던 이유: 국내선 스케줄은 하루 단위로 거의 안 바뀌지만 숙소 요금과
     * 잔여 객실은 하루 안에도 움직인다. 길게 잡으면 이미 매진된 숙소를 계속 보여주게 되고,
     * LiteAPI Sandbox의 초당 호출 제한도 걸린다.
     *
     * <p>지금 늘리는 이유: 현재 캐시에 담기는 것은 TourAPI가 준 숙소명·주소·사진·좌표뿐이고
     * 요금은 전부 UNAVAILABLE이다. 한 달에 한 번 바뀔까 말까 한 공공데이터를 위해 5분마다
     * TourAPI를 다시 부르고 있었다. 그 왕복이 5.5초라 사용자가 5분마다 그 시간을 다시 기다렸다.
     * 캐시 적중은 0.03초다. (#147)
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer accommodationSearchCacheCustomizer() {
        return builder -> builder.withCacheConfiguration("accommodationSearch",
                withTtl(builder, Duration.ofHours(1)));
    }

    /**
     * TTL만 바꾸고 나머지 설정은 부트가 만든 것을 그대로 물려받는다.
     *
     * <p>캐시가 늘어날 때마다 {@code builder.cacheDefaults()}를 쓰는 걸 잊지 않도록
     * 한 곳으로 모았다. #139는 이 한 줄을 빠뜨려서 났다.
     */
    private RedisCacheConfiguration withTtl(RedisCacheManagerBuilder builder, Duration ttl) {
        return builder.cacheDefaults().entryTtl(ttl);
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
