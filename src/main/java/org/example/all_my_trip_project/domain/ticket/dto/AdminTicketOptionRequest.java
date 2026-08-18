package org.example.all_my_trip_project.domain.ticket.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 티켓 옵션 등록·수정 요청.
 *
 * <p>가격은 옵션이 들고 있다. 상품이 아니다. 같은 상품에 "일반"과 "프리미엄"이 다른 값으로
 * 붙기 때문이다.
 *
 * <p>{@code isActive}는 여기서 받는다. 옵션을 끄면 그 아래 시간대가 예약 화면에서 통째로
 * 사라지므로 상품 판매 상태와는 다른 축이고, 상태 전용 엔드포인트를 따로 둘 만큼 무겁지 않다.
 */
public record AdminTicketOptionRequest(
        @NotBlank(message = "옵션명을 입력해 주세요.")
        @Size(max = 150, message = "옵션명은 150자를 넘을 수 없습니다.")
        String name,

        @Size(max = 1000, message = "설명은 1000자를 넘을 수 없습니다.")
        String description,

        @NotNull(message = "가격을 입력해 주세요.")
        @DecimalMin(value = "0", message = "가격은 0원 이상이어야 합니다.")
        @Digits(integer = 13, fraction = 2, message = "가격 형식이 올바르지 않습니다.")
        BigDecimal unitPrice,

        @NotNull(message = "1인 최대 구매 수량을 입력해 주세요.")
        @Min(value = 1, message = "1인 최대 구매 수량은 1장 이상이어야 합니다.")
        @Max(value = 10, message = "1인 최대 구매 수량은 10장을 넘을 수 없습니다.")
        Integer maxQuantityPerUser,

        @NotNull(message = "표시 순서를 입력해 주세요.")
        @Min(value = 1, message = "표시 순서는 1 이상이어야 합니다.")
        @Max(value = 999, message = "표시 순서는 999를 넘을 수 없습니다.")
        Integer sortOrder,

        @NotNull(message = "노출 여부를 선택해 주세요.")
        Boolean isActive
) {}
