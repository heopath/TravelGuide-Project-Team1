package org.example.all_my_trip_project.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * {@code WITHDRAWN}은 받지 않는다. 탈퇴는 회원 본인이 하는 것이고, 관리자가 화면에서
 * 대신 탈퇴시키는 동작은 이 스프린트 범위 밖이다. 정규식으로 막아 컨트롤러 앞에서 거른다.
 */
public record AdminMemberStatusRequest(
        @NotBlank
        @Pattern(regexp = "ACTIVE|SUSPENDED", message = "상태는 ACTIVE 또는 SUSPENDED여야 합니다.")
        String status,

        /** 정지 사유. 조작 이력에만 남는다. 회원에게 보내지 않는다. */
        String reason
) {}
