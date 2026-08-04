package org.example.all_my_trip_project.domain.ai.service;

import org.example.all_my_trip_project.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiGuideRequestGuardTest {

    @Test
    void rejectsTheEleventhRequestFromTheSameUserWithinOneMinute() {
        AiGuideRequestGuard guard = new AiGuideRequestGuard();

        for (int index = 0; index < 10; index++) {
            guard.acquire(1L);
            guard.release();
        }

        assertThatThrownBy(() -> guard.acquire(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsTheFourthConcurrentRequest() {
        AiGuideRequestGuard guard = new AiGuideRequestGuard();
        guard.acquire(1L);
        guard.acquire(2L);
        guard.acquire(3L);

        assertThatThrownBy(() -> guard.acquire(4L))
                .isInstanceOf(BusinessException.class);

        guard.release();
        guard.release();
        guard.release();
    }
}
