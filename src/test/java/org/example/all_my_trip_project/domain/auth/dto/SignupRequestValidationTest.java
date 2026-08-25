package org.example.all_my_trip_project.domain.auth.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SignupRequestValidationTest {

    private static final ValidatorFactory VALIDATOR_FACTORY =
            Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void signupRequiresBothMandatoryAgreements() {
        SignupRequest request = new SignupRequest(
                "member@example.com",
                "password123",
                "여행자",
                false,
                null,
                null
        );

        Set<String> messages = VALIDATOR.validate(request).stream()
                .map(violation -> violation.getMessage())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(messages).contains(
                "서비스 이용약관에 동의해야 합니다.",
                "개인정보 수집·이용 동의 여부는 필수입니다."
        );
    }

    @Test
    void signupAcceptsExplicitMandatoryAgreements() {
        SignupRequest request = new SignupRequest(
                "member@example.com",
                "password123",
                "여행자",
                true,
                true,
                null
        );

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }
}
