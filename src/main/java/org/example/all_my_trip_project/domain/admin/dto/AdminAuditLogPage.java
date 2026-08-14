package org.example.all_my_trip_project.domain.admin.dto;

import java.util.List;

public record AdminAuditLogPage(
        List<AdminAuditLogDTO> items,
        int page,
        int size,
        long total,
        int totalPages,
        /** 실제로 쌓인 동작 종류. 화면 필터를 코드에 박지 않으려고 함께 내린다. */
        List<String> actionTypes
) {}
