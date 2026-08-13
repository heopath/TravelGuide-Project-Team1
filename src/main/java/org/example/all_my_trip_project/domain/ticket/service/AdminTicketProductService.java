package org.example.all_my_trip_project.domain.ticket.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.service.AdminAuditService;
import org.example.all_my_trip_project.domain.ticket.dao.AdminTicketDAO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductDTO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductPage;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductRequest;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketSlotDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final AdminAuditService adminAuditService;

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
        adminAuditService.record("TICKET_PRODUCT_STATUS_CHANGE", "TICKET_PRODUCT", ticketProductId,
                AdminAuditService.payload("status", product.getStatus()),
                AdminAuditService.payload("status", normalized, "name", product.getName()));
        return requireProduct(ticketProductId);
    }

    @Transactional
    public AdminTicketProductDTO create(AdminTicketProductRequest request) {
        validatePeriods(request);
        requirePlace(request.placeId());
        Long createdId = adminTicketDAO.insertProduct(request);
        if (createdId == null) {
            throw new IllegalStateException("예약 상품을 등록하지 못했습니다.");
        }
        adminAuditService.record("TICKET_PRODUCT_CREATE", "TICKET_PRODUCT", createdId,
                null, AdminAuditService.payload("name", request.name(), "placeId", request.placeId()));
        return requireProduct(createdId);
    }

    @Transactional
    public AdminTicketProductDTO update(Long ticketProductId, AdminTicketProductRequest request) {
        AdminTicketProductDTO current = requireProduct(ticketProductId);
        validatePeriods(request);
        requirePlace(request.placeId());
        if (adminTicketDAO.updateProduct(ticketProductId, request) != 1) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        adminAuditService.record("TICKET_PRODUCT_UPDATE", "TICKET_PRODUCT", ticketProductId,
                AdminAuditService.payload("name", current.getName(), "placeId", current.getPlaceId()),
                AdminAuditService.payload("name", request.name(), "placeId", request.placeId()));
        return requireProduct(ticketProductId);
    }

    public List<AdminTicketSlotDTO> listSlots(Long ticketProductId) {
        requireProduct(ticketProductId);
        return adminTicketDAO.findSlots(ticketProductId);
    }

    /**
     * 시간대 하나의 전체 수량을 바꾼다.
     *
     * <p>{@code reserved_quantity}는 건드리지 않는다. 예약 흐름이 관리하는 값이라 관리자가
     * 직접 고치면 실제 예약 건수와 어긋나 남은 수량이 틀어진다.
     *
     * <p>이미 예약된 수보다 적게 줄이려 하면 거부한다. {@code ck_ticket_inventory_quantities}가
     * 어차피 막지만, 제약 위반으로 터지면 화면에 이유가 드러나지 않는다.
     */
    @Transactional
    public AdminTicketSlotDTO changeInventory(Long slotId, int totalQuantity) {
        if (slotId == null || slotId < 1 || totalQuantity < 0) {
            throw new BusinessException(ErrorCode.INVALID_TICKET_REQUEST);
        }
        AdminTicketSlotDTO slot = adminTicketDAO.findSlotForUpdate(slotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));
        int reserved = slot.getReservedQuantity() == null ? 0 : slot.getReservedQuantity();
        if (totalQuantity < reserved) {
            throw new BusinessException(ErrorCode.TICKET_INVENTORY_BELOW_RESERVED);
        }
        if (adminTicketDAO.updateInventory(slotId, totalQuantity) != 1) {
            /* 잠금과 갱신 사이에 예약이 들어와 조건이 깨진 경우다. 조용히 넘기지 않는다. */
            throw new BusinessException(ErrorCode.TICKET_INVENTORY_BELOW_RESERVED);
        }
        adminAuditService.record("TICKET_INVENTORY_CHANGE", "TICKET_TIME_SLOT", slotId,
                AdminAuditService.payload("totalQuantity", slot.getTotalQuantity(),
                        "reservedQuantity", reserved),
                AdminAuditService.payload("totalQuantity", totalQuantity));
        return adminTicketDAO.findSlotForUpdate(slotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));
    }

    private void validatePeriods(AdminTicketProductRequest request) {
        if (!request.saleEndAt().isAfter(request.saleStartAt())
                || request.usageEndDate().isBefore(request.usageStartDate())) {
            throw new BusinessException(ErrorCode.INVALID_TICKET_REQUEST);
        }
    }

    private void requirePlace(Long placeId) {
        if (!adminTicketDAO.existsPlace(placeId)) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }
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
