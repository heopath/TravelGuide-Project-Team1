package org.example.all_my_trip_project.domain.auth.service;

import org.example.all_my_trip_project.domain.auth.dto.SignupRequest;
import org.example.all_my_trip_project.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    AuthService authService;

    @Test
    void signupValidatesNicknameAfterTrimming() {
        SignupRequest request = new SignupRequest(
                "member@example.com",
                "password123",
                " a "
        );

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("닉네임은 2자 이상 20자 이하여야 합니다.");

        verify(userRepository, never()).existsByNickname("a");
        verify(passwordEncoder, never()).encode("password123");
    }
}