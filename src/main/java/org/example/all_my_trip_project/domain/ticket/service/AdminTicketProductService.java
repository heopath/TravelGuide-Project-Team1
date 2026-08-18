package org.example.all_my_trip_project.domain.ticket.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.service.AdminAuditService;
import org.example.all_my_trip_project.domain.ticket.dao.AdminTicketDAO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketOptionDTO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketOptionRequest;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductDTO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductPage;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketProductRequest;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketSlotCreateResponse;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketSlotDTO;
import org.example.all_my_trip_project.domain.ticket.dto.AdminTicketSlotRequest;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

    /**
     * 한 번에 만들 수 있는 날짜 수. 1년이면 충분하고, 오타로 10년을 넣었을 때
     * 수천 행이 들어가는 것을 막는다.
     */
    private static final int MAX_SLOT_BATCH = 366;

    /** {@code ck_ticket_products_status}와 같은 값이어야 한다. */
    private static final Set<String> STATUSES =
            Set.of("DRAFT", "ON_SALE", "SOLD_OUT", "ENDED", "CANCELLED");

    /** {@code ck_ticket_time_slots_status}와 같은 값이어야 한다. */
    private static final Set<String> SLOT_STATUSES = Set.of("OPEN", "CLOSED", "CANCELLED");

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

    public List<AdminTicketOptionDTO> listOptions(Long ticketProductId) {
        requireProduct(ticketProductId);
        return adminTicketDAO.findOptions(ticketProductId);
    }

    @Transactional
    public AdminTicketOptionDTO createOption(Long ticketProductId, AdminTicketOptionRequest request) {
        requireProduct(ticketProductId);
        requireNoOptionConflict(ticketProductId, request, null);
        Long createdId = adminTicketDAO.insertOption(ticketProductId, request);
        if (createdId == null) {
            throw new IllegalStateException("티켓 옵션을 등록하지 못했습니다.");
        }
        adminAuditService.record("TICKET_OPTION_CREATE", "TICKET_PRODUCT_OPTION", createdId,
                null, AdminAuditService.payload("ticketProductId", ticketProductId,
                        "name", request.name(), "unitPrice", request.unitPrice()));
        return requireOption(createdId);
    }

    @Transactional
    public AdminTicketOptionDTO updateOption(Long optionId, AdminTicketOptionRequest request) {
        AdminTicketOptionDTO current = requireOption(optionId);
        requireNoOptionConflict(current.getTicketProductId(), request, optionId);
        if (adminTicketDAO.updateOption(optionId, request) != 1) {
            throw new BusinessException(ErrorCode.TICKET_OPTION_NOT_FOUND);
        }
        adminAuditService.record("TICKET_OPTION_UPDATE", "TICKET_PRODUCT_OPTION", optionId,
                AdminAuditService.payload("name", current.getName(),
                        "unitPrice", current.getUnitPrice(), "isActive", current.getIsActive()),
                AdminAuditService.payload("name", request.name(),
                        "unitPrice", request.unitPrice(), "isActive", request.isActive()));
        return requireOption(optionId);
    }

    /**
     * 시간대를 만든다. 하루짜리와 기간 반복을 같은 경로로 처리한다.
     *
     * <p><b>시간대와 재고를 같은 트랜잭션에서 만든다.</b> {@code ticket_inventory}가 없으면
     * 조회 질의들의 INNER JOIN에서 빠져 시간대가 예약 화면에도, 관리자 시간대 목록에도
     * 나오지 않는다. 오류 없이 "없음"으로 보여 원인을 찾기 어려운 종류다.
     *
     * <p>이미 있는 (옵션·이용일·시작시각)은 건너뛴다. 유니크 제약으로 터뜨리면 앞서 만든
     * 날까지 함께 롤백되어, 하루가 겹쳤다는 이유로 나머지 29일을 다시 등록해야 한다.
     */
    @Transactional
    public AdminTicketSlotCreateResponse createSlots(Long ticketProductId, AdminTicketSlotRequest request) {
        requireProduct(ticketProductId);
        Long optionId = request.ticketProductOptionId();
        Long ownerProductId = adminTicketDAO.findProductIdByOption(optionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_OPTION_NOT_FOUND));
        /* 다른 상품의 옵션 ID를 보내 남의 상품에 시간대를 붙이는 것을 막는다. */
        if (!ownerProductId.equals(ticketProductId)) {
            throw new BusinessException(ErrorCode.TICKET_OPTION_NOT_FOUND);
        }

        LocalDate start = request.usageStartDate();
        LocalDate end = request.effectiveEndDate();
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.INVALID_TICKET_REQUEST);
        }
        if (request.startTime() != null && request.endTime() != null
                && !request.endTime().isAfter(request.startTime())) {
            throw new BusinessException(ErrorCode.INVALID_TICKET_REQUEST);
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_SLOT_BATCH) {
            throw new BusinessException(ErrorCode.INVALID_TICKET_REQUEST);
        }

        Set<DayOfWeek> weekdays = request.weekdays() == null || request.weekdays().isEmpty()
                ? null : request.weekdays();

        int created = 0;
        int skipped = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (weekdays != null && !weekdays.contains(date.getDayOfWeek())) {
                continue;
            }
            if (adminTicketDAO.existsSlot(optionId, date, request.startTime())) {
                skipped++;
                continue;
            }
            Long slotId = adminTicketDAO.insertSlot(
                    optionId, date, request.startTime(), request.endTime());
            if (slotId == null) {
                throw new IllegalStateException("시간대를 등록하지 못했습니다.");
            }
            if (adminTicketDAO.insertInventory(slotId, request.totalQuantity()) != 1) {
                throw new IllegalStateException("시간대 재고를 등록하지 못했습니다.");
            }
            created++;
        }

        if (created == 0 && skipped == 0) {
            /* 요일 조건이 기간과 하나도 안 맞은 경우다. 조용히 0건을 돌려주면 오해한다. */
            throw new BusinessException(ErrorCode.INVALID_TICKET_REQUEST);
        }
        if (created > 0) {
            adminAuditService.record("TICKET_SLOT_CREATE", "TICKET_PRODUCT_OPTION", optionId,
                    null, AdminAuditService.payload("ticketProductId", ticketProductId,
                            "created", created, "skipped", skipped,
                            "usageStartDate", start, "usageEndDate", end,
                            "totalQuantity", request.totalQuantity()));
        }
        return new AdminTicketSlotCreateResponse(created, skipped, adminTicketDAO.findSlots(ticketProductId));
    }

    /**
     * 시간대를 열거나 닫는다. 지우지는 않는다.
     *
     * <p>{@code reservation_items}가 시간대를 참조하므로 지우면 이미 팔린 예약이 무엇이었는지
     * 되짚을 수 없다. 예약이 걸린 시간대는 닫는 것도 막는다 — 닫아도 예약은 그대로 남는데,
     * 관리자는 "정리했다"고 믿기 쉽다.
     */
    @Transactional
    public AdminTicketSlotDTO changeSlotStatus(Long slotId, String status) {
        if (slotId == null || slotId < 1) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!SLOT_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_TICKET_REQUEST);
        }
        AdminTicketSlotDTO slot = adminTicketDAO.findSlotForUpdate(slotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));
        if (normalized.equals(slot.getStatus())) {
            return slot;
        }
        if (!"OPEN".equals(normalized) && adminTicketDAO.countSlotReserved(slotId) > 0) {
            throw new BusinessException(ErrorCode.TICKET_SLOT_HAS_RESERVATION);
        }
        if (adminTicketDAO.updateSlotStatus(slotId, normalized) != 1) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
        adminAuditService.record("TICKET_SLOT_STATUS_CHANGE", "TICKET_TIME_SLOT", slotId,
                AdminAuditService.payload("status", slot.getStatus()),
                AdminAuditService.payload("status", normalized));
        return adminTicketDAO.findSlotForUpdate(slotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));
    }

    private void requireNoOptionConflict(Long ticketProductId, AdminTicketOptionRequest request, Long optionId) {
        if (adminTicketDAO.existsConflictingOption(
                ticketProductId, request.name().trim(), request.sortOrder(), optionId)) {
            throw new BusinessException(ErrorCode.TICKET_OPTION_DUPLICATED);
        }
    }

    private AdminTicketOptionDTO requireOption(Long optionId) {
        if (optionId == null || optionId < 1) {
            throw new BusinessException(ErrorCode.TICKET_OPTION_NOT_FOUND);
        }
        return adminTicketDAO.findOptionById(optionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_OPTION_NOT_FOUND));
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
