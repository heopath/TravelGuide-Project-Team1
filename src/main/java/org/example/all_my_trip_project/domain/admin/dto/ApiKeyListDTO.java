package org.example.all_my_trip_project.domain.admin.dto;

import java.util.List;

/**
 * @param encryptionReady 서버에 마스터 키가 설정돼 있는지. 화면은 이 값이 거짓이면 저장 버튼을
 *                        잠그고 이유를 안내한다. 눌러 본 뒤에 실패로 알려주면 관리자는 자기
 *                        입력이 잘못된 줄 안다
 */
public record ApiKeyListDTO(boolean encryptionReady, List<ApiKeyDTO> keys) {
}
