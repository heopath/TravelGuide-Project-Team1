package org.example.all_my_trip_project.global.apikey;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * {@code api_key_settings} 한 행.
 *
 * <p>record가 아니라 setter가 있는 클래스인 이유는 MyBatis 매핑 때문이다. 이 프로젝트의 다른
 * 조회는 {@code map-underscore-to-camel-case}로 setter에 채워 넣는다. 같은 방식을 따른다.
 *
 * <p>{@code encryptedValue}는 암호문이다. <b>이 객체를 그대로 응답으로 내보내면 안 된다.</b>
 * 화면에 나가는 형태는 별도 DTO로 만든다.
 */
@Getter
@Setter
public class ApiKeySetting {
    private String apiKeyName;
    private String encryptedValue;
    private Long updatedBy;
    private OffsetDateTime updatedAt;
}
