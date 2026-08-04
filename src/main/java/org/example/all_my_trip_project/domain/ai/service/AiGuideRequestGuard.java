package org.example.all_my_trip_project.domain.ai.service;

import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Component
public class AiGuideRequestGuard {

    private static final int MAX_REQUESTS_PER_MINUTE = 10;
    private static final int MAX_CONCURRENT_REQUESTS = 3;
    private static final long WINDOW_SECONDS = 60;

    private final Map<Long, Deque<Instant>> requestTimes = new ConcurrentHashMap<>();
    private final Semaphore concurrentRequests = new Semaphore(MAX_CONCURRENT_REQUESTS, true);

    public void acquire(Long userId) {
        validateRateLimit(userId);
        if (!concurrentRequests.tryAcquire()) {
            throw new BusinessException(ErrorCode.AI_REQUEST_LIMIT_EXCEEDED);
        }
    }

    public void release() {
        concurrentRequests.release();
    }

    private void validateRateLimit(Long userId) {
        Deque<Instant> times = requestTimes.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (times) {
            Instant cutoff = Instant.now().minusSeconds(WINDOW_SECONDS);
            while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
                times.removeFirst();
            }
            if (times.size() >= MAX_REQUESTS_PER_MINUTE) {
                throw new BusinessException(ErrorCode.AI_REQUEST_LIMIT_EXCEEDED);
            }
            times.addLast(Instant.now());
        }
    }
}
