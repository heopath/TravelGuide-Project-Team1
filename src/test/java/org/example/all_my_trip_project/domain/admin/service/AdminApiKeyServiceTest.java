package org.example.all_my_trip_project.domain.admin.service;

import org.example.all_my_trip_project.domain.admin.dto.ApiKeyTestResultDTO;
import org.example.all_my_trip_project.global.apikey.ApiKeyCipher;
import org.example.all_my_trip_project.global.apikey.ApiKeyProvider;
import org.example.all_my_trip_project.global.apikey.ApiKeySetting;
import org.example.all_my_trip_project.global.apikey.ApiKeyStore;
import org.example.all_my_trip_project.global.apikey.ManagedApiKey;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminApiKeyServiceTest {

    @Mock private ApiKeyStore apiKeyStore;
    @Mock private ApiKeyProvider apiKeyProvider;
    @Mock private ApiKeyCipher apiKeyCipher;
    @Mock private ApiKeyConnectionTester apiKeyConnectionTester;
    @Mock private AdminAuditService adminAuditService;

    private AdminApiKeyService service;

    @BeforeEach
    void setUp() {
        service = new AdminApiKeyService(apiKeyStore, apiKeyProvider, apiKeyCipher,
                apiKeyConnectionTester, adminAuditService);
    }

    @Test
    @DisplayName("연결 테스트에 실패한 키는 기존 저장값을 덮어쓰지 않는다")
    void rejectsUnverifiedKeyBeforeSaving() {
        when(apiKeyCipher.isConfigured()).thenReturn(true);
        when(apiKeyConnectionTester.test(ManagedApiKey.OPENAI, "sk-invalid"))
                .thenReturn(new ApiKeyTestResultDTO(false, 401, "키가 거부되었습니다."));

        assertThatThrownBy(() -> service.update("OPENAI", "sk-invalid", 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.API_KEY_CONNECTION_TEST_FAILED);

        verify(apiKeyCipher, never()).encrypt("sk-invalid");
        verify(apiKeyStore, never()).save(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("연결 테스트를 통과한 키만 암호화해 저장한다")
    void savesVerifiedKey() {
        ApiKeySetting stored = new ApiKeySetting();
        stored.setApiKeyName("OPENAI");
        stored.setEncryptedValue("encrypted");
        stored.setUpdatedBy(7L);
        stored.setUpdatedAt(OffsetDateTime.now());

        when(apiKeyCipher.isConfigured()).thenReturn(true);
        when(apiKeyConnectionTester.test(ManagedApiKey.OPENAI, "sk-valid-1234567890"))
                .thenReturn(new ApiKeyTestResultDTO(true, 200, "정상 응답을 받았습니다."));
        when(apiKeyProvider.resolve(ManagedApiKey.OPENAI)).thenReturn("sk-old-1234567890");
        when(apiKeyCipher.encrypt("sk-valid-1234567890")).thenReturn("encrypted");
        when(apiKeyStore.find("OPENAI")).thenReturn(Optional.of(stored));
        when(apiKeyCipher.decrypt("encrypted")).thenReturn("sk-valid-1234567890");

        service.update("OPENAI", "sk-valid-1234567890", 7L);

        verify(apiKeyStore).save("OPENAI", "encrypted", 7L);
        verify(apiKeyProvider).evict(ManagedApiKey.OPENAI);
    }
}
