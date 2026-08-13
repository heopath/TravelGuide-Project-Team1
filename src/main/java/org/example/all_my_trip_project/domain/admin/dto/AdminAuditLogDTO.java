package org.example.all_my_trip_project.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 관리자 조작 이력 한 건.
 *
 * <p>{@code admin_audit_logs}는 V6부터 있었지만 기록하는 코드가 없었다. #163에서 공유 계정을
 * 쓰지 않고 각자 승격하기로 한 근거가 "누가 했는지 남긴다"였는데, 정작 남기는 곳이 없어
 * 그 결정이 값을 갖지 못하던 상태다.
 *
 * <p>{@code beforeData}/{@code afterData}는 JSON 문자열로 담아 {@code ::jsonb}로 넣는다.
 * 스키마를 고정하지 않는 이유는 대상 테이블마다 의미 있는 필드가 다르기 때문이다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLogDTO {
    private Long adminAuditLogId;

    /** 조작한 관리자. 계정이 삭제되면 {@code ON DELETE SET NULL}로 비워진다. */
    private Long adminUserId;

    /** 무엇을 했는지. 예: {@code TICKET_PRODUCT_STATUS_CHANGE} */
    private String actionType;

    /** 무엇에 했는지. 예: {@code TICKET_PRODUCT} */
    private String targetType;

    /** 대상 식별자. 숫자가 아닐 수도 있어 문자열이다. */
    private String targetId;

    private String beforeData;
    private String afterData;

    private String requestId;
    private String ipAddress;
    private String userAgent;

    private OffsetDateTime occurredAt;
}
