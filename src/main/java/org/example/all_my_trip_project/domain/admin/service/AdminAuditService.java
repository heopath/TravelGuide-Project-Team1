package org.example.all_my_trip_project.domain.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.admin.dao.AdminAuditDAO;
import org.example.all_my_trip_project.domain.admin.dto.AdminAuditLogDTO;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 관리자 조작 이력을 남긴다.
 *
 * <p>기록 실패가 관리 동작 자체를 막지 않는다. 감사 기록이 안 됐다고 신고 처리나 판매 상태
 * 변경이 되돌아가면, 관리자는 이유를 알 수 없는 실패를 보게 되고 운영이 멈춘다. 실패는
 * {@code WARN}으로만 남기고 원래 동작은 그대로 끝낸다. 대신 기록이 비는 구간이 생길 수 있어
 * 로그로 추적할 수 있게 대상과 종류를 함께 남긴다.
 *
 * <p>호출부가 관리자 ID·IP·User-Agent를 넘기지 않는다. 서비스 계층까지 그 값을 끌고 내려가면
 * 시그니처가 오염되고, 빠뜨리면 조용히 {@code null}이 된다. 여기서 보안 컨텍스트와 요청에서
 * 직접 꺼낸다.
 *
 * <p>{@link ObjectMapper}는 주입받지 않고 직접 만든다. 이 프로젝트의 다른 서비스와 같은
 * 방식이다. Spring Boot 4는 Jackson 3이 기본이라 Jackson 2의 {@code ObjectMapper}를 빈으로
 * 주입받으면 컨텍스트가 뜨지 않는다.
 */
@Slf4j
@Service
@Profile("!ui")
@RequiredArgsConstructor
public class AdminAuditService {

    /** {@code admin_audit_logs.user_agent}가 VARCHAR(500)이다. 넘치면 잘라서라도 남긴다. */
    private static final int MAX_USER_AGENT = 500;

    private final AdminAuditDAO adminAuditDAO;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 감사 본문을 만든다. {@code Map.of}를 직접 쓰지 않기 위한 것이다.
     *
     * <p>{@code Map.of}는 값이 하나라도 {@code null}이면 즉시 {@code NullPointerException}을
     * 던진다. 그 예외는 {@link #record} 안이 아니라 <b>호출부에서</b> 터지므로 여기서 감싸도
     * 소용이 없고, 이름 없는 장소 하나 때문에 판매 상태 변경이 실패한다.
     *
     * <p>그래서 값이 비면 그 항목만 빼고 나머지는 남긴다. 기록이 조금 부실한 것이
     * 관리 동작이 막히는 것보다 낫다.
     *
     * @param keyValues 키와 값을 번갈아 넘긴다
     */
    public static Map<String, String> payload(Object... keyValues) {
        Map<String, String> data = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            Object key = keyValues[index];
            Object value = keyValues[index + 1];
            if (key == null || value == null) continue;
            data.put(String.valueOf(key), String.valueOf(value));
        }
        return data;
    }

    public void record(String actionType, String targetType, Object targetId,
                       Map<String, ?> before, Map<String, ?> after) {
        try {
            AdminAuditLogDTO entry = AdminAuditLogDTO.builder()
                    .adminUserId(currentAdminUserId())
                    .actionType(actionType)
                    .targetType(targetType)
                    .targetId(targetId == null ? null : String.valueOf(targetId))
                    .beforeData(toJson(before))
                    .afterData(toJson(after))
                    .requestId(header("X-Request-Id"))
                    .ipAddress(clientIp())
                    .userAgent(truncate(header("User-Agent")))
                    .build();
            adminAuditDAO.insert(entry);
        } catch (Exception exception) {
            log.warn("Failed to write admin audit log. actionType={}, targetType={}, targetId={}",
                    actionType, targetType, targetId, exception);
        }
    }

    private Long currentAdminUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        Object principal = authentication.getPrincipal();
        return principal instanceof AuthenticatedUser user ? user.userId() : null;
    }

    private String toJson(Map<String, ?> value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            /* 본문 직렬화가 실패해도 "누가 무엇을 했다"는 남겨야 한다. 값만 포기한다. */
            log.warn("Failed to serialize admin audit payload", exception);
            return null;
        }
    }

    /**
     * 프록시 뒤에서는 원격 주소가 로드밸런서가 된다. 운영이 EC2 앞단을 거치므로
     * {@code X-Forwarded-For}를 먼저 본다. 여러 개면 첫 번째가 최초 클라이언트다.
     *
     * <p>{@code ip_address}가 INET이라 형식이 어긋나면 INSERT 자체가 실패한다. 값이
     * 이상하면 넣지 않고 비운다. IP 하나 때문에 이력 전체를 잃는 편이 더 나쁘다.
     */
    private String clientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        String candidate = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        if (candidate == null || candidate.isBlank()) return null;
        return candidate.matches("[0-9A-Fa-f:.]+") ? candidate : null;
    }

    private String header(String name) {
        HttpServletRequest request = currentRequest();
        if (request == null) return null;
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? null : value;
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() <= MAX_USER_AGENT ? value : value.substring(0, MAX_USER_AGENT);
    }

    /** 스케줄러나 테스트처럼 요청 밖에서 불릴 수 있다. 그때는 IP·UA 없이 남긴다. */
    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }
}
