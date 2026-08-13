package org.example.all_my_trip_project.domain.ticket.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.ticket.dao.AdminTicketDAO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductDTO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductPage;
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
public class AdminTicketProductService {

    private static final int MAX_PAGE_SIZE = 100;

    /** {@code ck_ticket_products_status}와 같은 값이어야 한다. */
    private static final Set<String> STATUSES =
            Set.of("DRAFT", "ON_SALE", "SOLD_OUT", "ENDED", "CANCELLED");

    private final AdminTicketDAO adminTicketDAO;

    public AdminTicketProductPage list(int page, int size, String keyword, String status) {
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
        int offset;
        try {
            offset = Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_TICKET_REQUEST);
        }
        String normalizedKeyword = text(keyword);
        long total = adminTicketDAO.countAdmin(normalizedKeyword, normalizedStatus);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new AdminTicketProductPage(
                adminTicketDAO.findAdminPage(normalizedKeyword, normalizedStatus, offset, size),
                page, size, total, totalPages);
    }

    /**
     * 판매 상태만 바꾼다. 재고(ticket_inventory)는 건드리지 않는다.
     *
     * <p>{@code SOLD_OUT}으로 내려도 남은 수량은 그대로 둔다. 예약이 걸린 시간대의 수량을
     * 함께 줄이면 {@code ck_ticket_inventory_quantities}(reserved ≤ total)를 깨뜨릴 수 있고,
     * 다시 판매로 되돌릴 때 원래 수량을 복원할 방법도 없다.
     */
    @Transactional
    public AdminTicketProductDTO changeStatus(Long ticketProductId, String status) {
        AdminTicketProductDTO product = requireProduct(ticketProductId);
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_TICKET_REQUEST);
        }
        if (normalized.equals(product.getStatus())) {
            return product;
        }
        if (adminTicketDAO.updateStatus(ticketProductId, normalized) != 1) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        return requireProduct(ticketProductId);
    }

    private AdminTicketProductDTO requireProduct(Long ticketProductId) {
        if (ticketProductId == null || ticketProductId < 1) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        return adminTicketDAO.findAdminById(ticketProductId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
