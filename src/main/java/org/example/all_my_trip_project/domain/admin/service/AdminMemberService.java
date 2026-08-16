package org.example.all_my_trip_project.domain.admin.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.dao.AdminMemberDAO;
import org.example.all_my_trip_project.domain.admin.dto.AdminMemberDTO;
import org.example.all_my_trip_project.domain.admin.dto.AdminMemberPage;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.example.all_my_trip_project.global.security.AuthenticatedUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * 관리자 회원 관리.
 *
 * <p>이 서비스의 두 변경 동작(정지·승격)은 이 서비스에서 가장 되돌리기 어렵다. 그래서
 * 두 가지를 막는다.
 *
 * <ol>
 *   <li><b>자기 자신</b> — 스스로를 정지하거나 권한을 내리면 그 즉시 이 화면에 못 들어간다.
 *       되돌리려면 운영 DB에 SQL을 직접 실행해야 하는데, 그 절차를 없애려고 만든 화면에서
 *       그 절차가 다시 필요해지는 것은 앞뒤가 맞지 않는다.
 *   <li><b>마지막 관리자</b> — 활동 중인 관리자가 하나뿐일 때는 정지도 강등도 거부한다.
 *       둘 중 하나만 막으면 남은 쪽으로 돌아가면 그만이라 사실상 아무것도 막지 못한다.
 * </ol>
 *
 * <p>두 검사 모두 서버에서 한다. 화면도 같은 조건으로 버튼을 잠그지만, 그건 눌러 보고 나서
 * 거부당하는 일을 줄이려는 것이지 방어가 아니다.
 */
@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMemberService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_SUSPENDED = "SUSPENDED";
    private static final String STATUS_WITHDRAWN = "WITHDRAWN";

    private final AdminMemberDAO adminMemberDAO;
    private final AdminAuditService adminAuditService;

    public AdminMemberPage list(int page, int size, String keyword, String status, String role) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_ADMIN_REQUEST);
        }
        int offset;
        try {
            offset = Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_ADMIN_REQUEST);
        }

        String normalizedKeyword = text(keyword);
        String normalizedStatus = upper(status);
        String normalizedRole = upper(role);
        validateStatusFilter(normalizedStatus);
        validateRoleFilter(normalizedRole);

        /* 탈퇴 회원은 상태 필터로 직접 고를 때만 목록에 넣는다. */
        boolean includeWithdrawn = STATUS_WITHDRAWN.equals(normalizedStatus);

        long total = adminMemberDAO.count(normalizedKeyword, normalizedStatus, normalizedRole, includeWithdrawn);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new AdminMemberPage(
                adminMemberDAO.findPage(normalizedKeyword, normalizedStatus, normalizedRole,
                        includeWithdrawn, offset, size),
                page, size, total, totalPages,
                adminMemberDAO.countActiveAdmins(),
                currentAdminUserId());
    }

    @Transactional
    public AdminMemberDTO changeStatus(Long userId, String status, String reason) {
        String target = upper(status);
        if (!STATUS_ACTIVE.equals(target) && !STATUS_SUSPENDED.equals(target)) {
            throw new BusinessException(ErrorCode.INVALID_MEMBER_REQUEST);
        }

        AdminMemberDTO member = requireChangeableMember(userId);
        if (target.equals(member.getStatus())) return member;

        /* 정지될 관리자가 마지막 한 명이면 아무도 /admin에 못 들어간다. */
        if (STATUS_SUSPENDED.equals(target) && ROLE_ADMIN.equals(member.getRole())) {
            requireAnotherActiveAdminRemains();
        }

        if (adminMemberDAO.updateStatus(userId, target) != 1) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
        adminAuditService.record("MEMBER_STATUS_CHANGE", "USER", userId,
                AdminAuditService.payload("status", member.getStatus()),
                AdminAuditService.payload(
                        "status", target,
                        "nickname", member.getNickname(),
                        "reason", text(reason)));
        return requireMember(userId);
    }

    @Transactional
    public AdminMemberDTO changeRole(Long userId, String role, String reason) {
        String target = upper(role);
        if (!ROLE_ADMIN.equals(target) && !"USER".equals(target)) {
            throw new BusinessException(ErrorCode.INVALID_MEMBER_REQUEST);
        }

        AdminMemberDTO member = requireChangeableMember(userId);
        if (target.equals(member.getRole())) return member;

        if (ROLE_ADMIN.equals(target)) {
            /*
             * 정지된 회원을 관리자로 올리지 않는다. 로그인을 못 하므로 권한만 붙고 아무것도
             * 할 수 없는데, 목록에서는 관리자로 보여 "관리자가 더 있다"고 오해하게 된다.
             */
            if (!STATUS_ACTIVE.equals(member.getStatus())) {
                throw new BusinessException(ErrorCode.MEMBER_NOT_ACTIVE);
            }
        } else {
            requireAnotherActiveAdminRemains();
        }

        if (adminMemberDAO.updateRole(userId, target) != 1) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
        adminAuditService.record("MEMBER_ROLE_CHANGE", "USER", userId,
                AdminAuditService.payload("role", member.getRole()),
                AdminAuditService.payload(
                        "role", target,
                        "nickname", member.getNickname(),
                        "reason", text(reason)));
        return requireMember(userId);
    }

    /**
     * 지금 로그인한 관리자 말고 활동 중인 관리자가 더 있는지 확인한다.
     *
     * <p>세는 것으로 끝내지 않고 관리자 행을 잠근다. 관리자가 둘 남은 상태에서 두 요청이
     * 동시에 들어오면, 양쪽 모두 "나 말고 하나 더 있다"를 읽고 통과해 결국 둘 다 내려간다.
     * 잠그면 뒤에 온 요청이 앞의 결과를 보고 거부된다.
     */
    private void requireAnotherActiveAdminRemains() {
        if (adminMemberDAO.lockAndCountActiveAdmins() <= 1) {
            throw new BusinessException(ErrorCode.LAST_ADMIN_PROTECTED);
        }
    }

    private AdminMemberDTO requireChangeableMember(Long userId) {
        AdminMemberDTO member = requireMember(userId);
        Long currentAdminId = currentAdminUserId();
        if (currentAdminId != null && currentAdminId.equals(member.getUserId())) {
            throw new BusinessException(ErrorCode.MEMBER_SELF_CHANGE_DENIED);
        }
        if (STATUS_WITHDRAWN.equals(member.getStatus()) || member.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.MEMBER_WITHDRAWN_IMMUTABLE);
        }
        return member;
    }

    private AdminMemberDTO requireMember(Long userId) {
        if (userId == null || userId < 1) throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        return adminMemberDAO.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private void validateStatusFilter(String status) {
        if (status == null) return;
        if (!STATUS_ACTIVE.equals(status) && !STATUS_SUSPENDED.equals(status)
                && !STATUS_WITHDRAWN.equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_ADMIN_REQUEST);
        }
    }

    private void validateRoleFilter(String role) {
        if (role == null) return;
        if (!ROLE_ADMIN.equals(role) && !"USER".equals(role)) {
            throw new BusinessException(ErrorCode.INVALID_ADMIN_REQUEST);
        }
    }

    private Long currentAdminUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        Object principal = authentication.getPrincipal();
        return principal instanceof AuthenticatedUser user ? user.userId() : null;
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String upper(String value) {
        String normalized = text(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
