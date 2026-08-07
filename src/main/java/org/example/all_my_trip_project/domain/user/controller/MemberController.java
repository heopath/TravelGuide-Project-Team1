package org.example.all_my_trip_project.domain.user.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.auth.service.AuthService;
import org.example.all_my_trip_project.domain.user.dto.MemberDeleteRequest;
import org.example.all_my_trip_project.domain.user.dto.MemberPasswordChangeRequest;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
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
            @AuthenticationPrincipal
            AuthenticatedUser principal
    ) {
        validatePrincipal(principal);

        MemberResponse response =
                authService.getCurrentMember(
                        principal.userId()
                );

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> updateProfile(
            @AuthenticationPrincipal
            AuthenticatedUser principal,
            @Valid
            @RequestBody
            UpdateMemberProfileRequest request
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

    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal
            AuthenticatedUser principal,
            @Valid
            @RequestBody
            MemberPasswordChangeRequest request
    ) {
        validatePrincipal(principal);

        memberService.changePassword(
                principal.userId(),
                request
        );

        return ResponseEntity.ok(
                ApiResponse.<Void>success(
                        "비밀번호가 변경되었습니다.",
                        null
                )
        );
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal
            AuthenticatedUser principal,
            @Valid
            @RequestBody
            MemberDeleteRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        validatePrincipal(principal);

        memberService.withdraw(
                principal.userId(),
                request
        );

        clearAuthentication(
                httpRequest,
                httpResponse
        );

        return ResponseEntity.ok(
                ApiResponse.<Void>success(
                        "회원 탈퇴가 완료되었습니다.",
                        null
                )
        );
    }

    @GetMapping("/me/preferences")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> preferences(
            @AuthenticationPrincipal
            AuthenticatedUser principal
    ) {
        validatePrincipal(principal);

        return ResponseEntity.ok(
                ApiResponse.success(
                        memberService.getPreferences(
                                principal.userId()
                        )
                )
        );
    }

    @PutMapping("/me/preferences")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> replacePreferences(
            @AuthenticationPrincipal
            AuthenticatedUser principal,
            @Valid
            @RequestBody
            UpdatePreferencesRequest request
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

    private void validatePrincipal(
            AuthenticatedUser principal
    ) {
        if (principal == null) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED
            );
        }
    }

    private void clearAuthentication(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        HttpSession session =
                request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();

        Cookie cookie =
                new Cookie("JSESSIONID", "");

        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);

        response.addCookie(cookie);
    }
}