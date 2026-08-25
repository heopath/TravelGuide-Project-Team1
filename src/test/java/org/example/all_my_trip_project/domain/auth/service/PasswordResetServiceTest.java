package org.example.all_my_trip_project.domain.auth.service;

import org.example.all_my_trip_project.domain.auth.entity.PasswordResetTokenEntity;
import org.example.all_my_trip_project.domain.auth.repository.PasswordResetTokenRepository;
import org.example.all_my_trip_project.domain.user.entity.UserEntity;
import org.example.all_my_trip_project.domain.user.repository.UserRepository;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordResetTokenRepository tokenRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    PasswordResetMailer mailer;

    PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(
                userRepository, tokenRepository, passwordEncoder, mailer);
        ReflectionTestUtils.setField(passwordResetService, "baseUrl", "http://localhost:8080");
    }

    @Test
    @DisplayName("없는 계정으로 요청해도 조용히 끝난다 — 가입 여부가 드러나면 안 된다")
    void requestForUnknownEmailStaysSilent() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com"))
                .thenReturn(Optional.empty());

        assertThatCode(() -> passwordResetService.requestReset("nobody@example.com"))
                .doesNotThrowAnyException();

        verify(tokenRepository, never()).save(any());
        verify(mailer, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("링크를 새로 보내기 전에 이전 토큰을 먼저 무른다")
    void requestExpiresPreviousTokensBeforeIssuingNewOne() {
        UserEntity user = user(7L, "member@example.com");
        when(userRepository.findByEmailIgnoreCase("member@example.com"))
                .thenReturn(Optional.of(user));

        passwordResetService.requestReset("  Member@Example.com  ");

        var order = inOrder(tokenRepository, mailer);
        order.verify(tokenRepository).expireAllFor(anyLong(), any(OffsetDateTime.class));
        order.verify(tokenRepository).save(any(PasswordResetTokenEntity.class));
        order.verify(mailer).send(anyString(), anyString());
    }

    @Test
    @DisplayName("토큰 원문은 저장하지 않는다 — 메일에 실린 값과 저장된 값이 달라야 한다")
    void storedTokenIsHashedNotTheOneInTheMail() {
        UserEntity user = user(7L, "member@example.com");
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(user));

        passwordResetService.requestReset("member@example.com");

        ArgumentCaptor<PasswordResetTokenEntity> saved =
                ArgumentCaptor.forClass(PasswordResetTokenEntity.class);
        verify(tokenRepository).save(saved.capture());

        ArgumentCaptor<String> sentUrl = ArgumentCaptor.forClass(String.class);
        verify(mailer).send(anyString(), sentUrl.capture());

        String plainToken = sentUrl.getValue().substring(sentUrl.getValue().indexOf("token=") + 6);
        assertThat(plainToken).isNotBlank();
        assertThat(saved.getValue().getTokenHash())
                .isNotEqualTo(plainToken)
                .isEqualTo(sha256(plainToken));
    }

    @Test
    @DisplayName("만료된 토큰은 통하지 않는다")
    void expiredTokenIsRejected() {
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        when(tokenRepository.findByTokenHash(sha256("token")))
                .thenReturn(Optional.of(PasswordResetTokenEntity.issue(7L, sha256("token"), past)));

        assertThatThrownBy(() -> passwordResetService.verifyToken("token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
    }

    @Test
    @DisplayName("이미 쓴 토큰은 다시 통하지 않는다")
    void usedTokenIsRejected() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        PasswordResetTokenEntity entity =
                PasswordResetTokenEntity.issue(7L, sha256("token"), now.plusMinutes(30));
        entity.markUsed(now);
        when(tokenRepository.findByTokenHash(sha256("token"))).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> passwordResetService.verifyToken("token"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("짧은 비밀번호는 토큰을 쓰기 전에 막는다")
    void shortPasswordIsRejectedBeforeSpendingTheToken() {
        assertThatThrownBy(() -> passwordResetService.confirmReset("token", "1234567"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_TOO_SHORT);

        verify(tokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("재설정에 성공하면 비밀번호가 바뀌고 토큰은 그 자리에서 소진된다")
    void confirmChangesPasswordAndConsumesToken() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        PasswordResetTokenEntity entity =
                PasswordResetTokenEntity.issue(7L, sha256("token"), now.plusMinutes(30));
        UserEntity user = user(7L, "member@example.com");

        when(tokenRepository.findByTokenHash(sha256("token"))).thenReturn(Optional.of(entity));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpassword1")).thenReturn("encoded");

        passwordResetService.confirmReset("token", "newpassword1");

        assertThat(user.getPasswordHash()).isEqualTo("encoded");
        assertThat(entity.isUsable(OffsetDateTime.now(ZoneOffset.UTC))).isFalse();
    }

    private UserEntity user(Long userId, String email) {
        UserEntity user = UserEntity.create(email, "old-hash", "닉네임");
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
