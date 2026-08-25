package org.example.all_my_trip_project.global.apikey;

import java.util.List;
import java.util.Optional;

/**
 * 저장된 API 키를 읽고 쓰는 창구.
 *
 * <p>구현({@code ApiKeySettingDAO})은 admin 도메인에 있는데 인터페이스만 global에 둔 이유는,
 * 키를 실제로 쓰는 쪽(AI·장소·경로 서비스)이 관리자 도메인을 알 필요가 없기 때문이다.
 * 여기서 방향을 끊어 두지 않으면 장소 검색이 관리자 패키지에 의존하게 된다.
 */
public interface ApiKeyStore {

    Optional<ApiKeySetting> find(String apiKeyName);

    List<ApiKeySetting> findAll();

    void save(String apiKeyName, String encryptedValue, Long adminUserId);

    /** 저장값을 지운다. 지우면 환경변수 값으로 되돌아간다. */
    int delete(String apiKeyName);
}
