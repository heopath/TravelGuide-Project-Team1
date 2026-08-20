package org.example.all_my_trip_project.domain.admin.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * admin_audit_logs.action_type은 VARCHAR(30)이다. 넘치면 감사 로그 INSERT가 터지고,
 * 감사 로그는 기능 트랜잭션 안에서 남기므로 기능 자체가 롤백된다.
 *
 * <p>실제로 32자짜리 이름을 넣었다가 관리자 추천 일괄 처리가 통째로 실패했다.
 * 컴파일도 통과하고 단위 테스트도 통과해서 실행해 봐야만 드러났다. 여기서 막는다.
 */
class AdminAuditActionTypeLengthTest {

    private static final int MAX_ACTION_TYPE_LENGTH = 30;
    private static final Pattern RECORD_CALL = Pattern.compile("record\\(\\s*\"([A-Z0-9_]+)\"");

    @Test
    void 감사_로그_이름은_컬럼_길이를_넘지_않는다() throws IOException {
        List<String> tooLong = new ArrayList<>();

        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            sources.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    Matcher matcher = RECORD_CALL.matcher(Files.readString(path));
                    while (matcher.find()) {
                        String actionType = matcher.group(1);
                        if (actionType.length() > MAX_ACTION_TYPE_LENGTH) {
                            tooLong.add(actionType + "(" + actionType.length() + "자) in " + path.getFileName());
                        }
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException(path.toString(), exception);
                }
            });
        }

        assertThat(tooLong)
                .as("action_type은 %d자 이하여야 한다. 넘으면 감사 로그 INSERT가 실패해 기능이 롤백된다.",
                        MAX_ACTION_TYPE_LENGTH)
                .isEmpty();
    }
}
