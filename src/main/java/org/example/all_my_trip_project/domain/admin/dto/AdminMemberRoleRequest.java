package org.example.all_my_trip_project.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AdminMemberRoleRequest(
        @NotBlank
        @Pattern(regexp = "USER|ADMIN", message = "권한은 USER 또는 ADMIN이어야 합니다.")
        String role,

        /** 승격·해제 사유. 조작 이력에만 남는다. */
        String reason
) {}
