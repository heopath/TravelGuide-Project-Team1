package org.example.all_my_trip_project.domain.admin.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.dao.AdminReservationDAO;
import org.example.all_my_trip_project.domain.admin.dto.AdminReservationPage;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReservationService {

    private static final int MAX_PAGE_SIZE = 100;

    /** {@code ck_reservations_status}와 같은 값이어야 한다. */
    private static final Set<String> STATUSES =
            Set.of("PENDING", "CONFIRMED", "CANCELLED", "EXPIRED", "USED");

    private final AdminReservationDAO adminReservationDAO;

    public AdminReservationPage list(int page, int size, String status, String keyword,
                                     boolean expiredPendingOnly) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_TICKET_REQUEST);
        }
        String normalizedStatus = text(status);
        if (normalizedStatus != null) {
            normalizedStatus = normalizedStatus.toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(normalizedStatus)) {
                throw new BusinessException(ErrorCode.INVALID_TICKET_REQUEST);
            }
        }
        /*
         * 만료 방치 필터는 PENDING 안에서만 의미가 있다. 다른 상태와 함께 걸면 항상 0건이 나와
         * 필터가 고장난 것처럼 보이므로, 상태 선택을 PENDING으로 맞춘다.
         */
        if (expiredPendingOnly) {
            normalizedStatus = null;
        }
        int offset;
        try {
            offset = Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_TICKET_REQUEST);
        }
        String normalizedKeyword = text(keyword);

        long total = adminReservationDAO.countAdmin(normalizedStatus, normalizedKeyword, expiredPendingOnly);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new AdminReservationPage(
                adminReservationDAO.findAdminPage(
                        normalizedStatus, normalizedKeyword, expiredPendingOnly, offset, size),
                page, size, total, totalPages,
                adminReservationDAO.countExpiredPending());
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
