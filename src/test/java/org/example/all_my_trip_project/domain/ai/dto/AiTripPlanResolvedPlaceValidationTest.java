package org.example.all_my_trip_project.domain.ai.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AiTripPlanResolvedPlaceValidationTest {
    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = validatorFactory.getValidator();

    @Test
    void acceptsFullKakaoCategoryPath() {
        // 카카오 category_name은 30자를 넘는 전체 경로로 내려오며, 이 값이 거부되면
        // AI 여행 저장 요청 전체가 VALIDATION_ERROR로 막힌다.
        String kakaoCategory = "서비스,산업 > 여행,관광 > 관광,명소 > 유원지,테마파크";
        assertThat(kakaoCategory.length()).isGreaterThan(30);

        assertThat(validator.validate(resolvedPlaceWithCategory(kakaoCategory))).isEmpty();
    }

    @Test
    void rejectsCategoryLongerThanColumnSafeLimit() {
        assertThat(validator.validate(resolvedPlaceWithCategory("가".repeat(256))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("category");
    }

    private AiTripPlanResolvedPlace resolvedPlaceWithCategory(String category) {
        return new AiTripPlanResolvedPlace(
                1, 1, "1234567890", "감천문화마을", "부산 사하구 감내2로 203",
                new BigDecimal("35.0975000"), new BigDecimal("129.0106000"),
                "051-204-1444", "https://place.map.kakao.com/1234567890",
                category, "부산 대표 관광지"
        );
    }
}
