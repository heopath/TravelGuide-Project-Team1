package org.example.all_my_trip_project.domain.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(
                min = 8,
                max = 64,
                message = "비밀번호는 8자 이상 64자 이하여야 합니다."
        )
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(
                min = 2,
                max = 20,
                message = "닉네임은 2자 이상 20자 이하여야 합니다."
        )
        String nickname,

        @NotNull(message = "서비스 이용약관 동의 여부는 필수입니다.")
        @AssertTrue(message = "서비스 이용약관에 동의해야 합니다.")
        Boolean termsAgreed,

        @NotNull(message = "개인정보 수집·이용 동의 여부는 필수입니다.")
        @AssertTrue(message = "개인정보 수집·이용에 동의해야 합니다.")
        Boolean privacyAgreed,

        @Size(max = 2048, message = "사람 확인 토큰이 올바르지 않습니다.")
        String turnstileToken
) {
    public SignupRequest(String email, String password, String nickname) {
        this(email, password, nickname, true, true, null);
    }
}
