package org.example.all_my_trip_project.domain.admin.dto;

import java.util.List;

public record AdminMemberPage(
        List<AdminMemberDTO> items,
        int page,
        int size,
        long total,
        int totalPages,
        /**
         * 현재 활동 중인 관리자 수. 화면이 "마지막 관리자"를 미리 알아보고 버튼을 잠그는 데 쓴다.
         * 서버도 같은 검사를 하지만, 눌러 보고 나서야 거부당하면 이유를 알기 어렵다.
         */
        long activeAdminCount,
        /** 지금 로그인한 관리자. 화면이 자기 줄의 버튼을 잠그는 데 쓴다. */
        Long currentAdminUserId
) {}
