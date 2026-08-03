package org.example.all_my_trip_project.domain.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.auth.service.AuthService;
import org.example.all_my_trip_project.domain.user.dto.MemberResponse;
import org.example.all_my_trip_project.domain.user.dto.UpdateMemberProfileRequest;
import org.example.all_my_trip_project.domain.user.dto.UpdatePreferencesRequest;
import org.example.all_my_trip_project.domain.user.dto.UserPreferenceResponse;
import org.example.all_my_trip_project.domain.user.service.MemberService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.response.ApiResponse;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!ui")
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final AuthService authService;
    private final MemberService memberService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> me(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        if (principal == null) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED
            );
        }

        MemberResponse response =
                authService.getCurrentMember(principal.userId());

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> updateProfile(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateMemberProfileRequest request
    ) {
        validatePrincipal(principal);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "회원정보가 수정되었습니다.",
                        memberService.updateProfile(
                                principal.userId(),
                                request
                        )
                )
        );
    }

    @GetMapping("/me/preferences")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> preferences(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        validatePrincipal(principal);

        return ResponseEntity.ok(
                ApiResponse.success(
                        memberService.getPreferences(principal.userId())
                )
        );
    }

    @PutMapping("/me/preferences")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> replacePreferences(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdatePreferencesRequest request
    ) {
        validatePrincipal(principal);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "여행 선호가 저장되었습니다.",
                        memberService.replacePreferences(
                                principal.userId(),
                                request
                        )
                )
        );
    }

    private void validatePrincipal(AuthenticatedUser principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
