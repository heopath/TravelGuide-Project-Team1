package org.example.all_my_trip_project.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 관리자 예약 모니터링 목록의 한 행.
 *
 * <p>읽기 전용이다. 이 화면에서 예약 상태를 바꾸지 않는다. 상태 변경은 재고
 * ({@code ticket_inventory.reserved_quantity})와 함께 움직여야 하는데, 목록에서 개별 행을
 * 건드리면 재고를 되돌리는 경로가 빠진다. 취소는 사용자 흐름과 티켓 서비스가 담당한다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminReservationDTO {
    private Long reservationId;
    private String reservationNumber;
    private String status;
    private BigDecimal totalAmount;
    private String currency;
    private Long tripId;

    private String nickname;

    /** 예약에 담긴 상품 중 첫 번째 이름. 나머지는 {@link #itemCount}로 몇 건인지만 알린다. */
    private String productName;
    private Integer itemCount;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime cancelledAt;
    private OffsetDateTime expiresAt;

    /**
     * 만료 시각이 지났는데 아직 {@code PENDING}인 예약.
     *
     * <p>{@code expires_at}을 지나면 {@code EXPIRED}로 바꾸는 처리가 아직 없다. 그래서 이런 행은
     * 상태만 보면 정상 대기처럼 보이지만 실제로는 아무도 손대지 않은 채 재고를 잡고 있다.
     * 상태를 대신 바꾸지 않고 표시만 한다.
     */
    private boolean expiredPending;
}
