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
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer flightSearchCacheCustomizer() {
        return builder -> builder.withCacheConfiguration("flightSearch",
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(6)));
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
