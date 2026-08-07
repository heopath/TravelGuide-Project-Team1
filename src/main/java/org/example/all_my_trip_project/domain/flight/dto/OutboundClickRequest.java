package org.example.all_my_trip_project.domain.flight.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.example.all_my_trip_project.domain.flight.type.PriceSource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 딥링크 클릭 시점에 보내는 요청.
 *
 * <p>운임을 클라이언트가 보내는 게 이상해 보일 수 있지만, 저장해야 하는 값은
 * "지금 사용자가 화면에서 보고 나간 금액"이다. 서버가 다시 조회하면 그 사이 바뀐 값이 박제된다.
 *
 * <p>이 시점에 선택을 먼저 저장하기 때문에, 복귀 감지를 놓쳐도 데이터가 사라지지 않는다.
 */
public record OutboundClickRequest(
        @NotBlank String offerId,
        @NotBlank String provider,
        @NotBlank String carrierCode,
        @NotBlank String carrierName,
        @NotBlank String flightNumber,
        @NotNull OffsetDateTime departureAt,
        @NotNull OffsetDateTime arrivalAt,
        @NotNull @PositiveOrZero BigDecimal totalPrice,
        String currency,
        /** PUBLISHED / MARKET / MOCK. 어떤 가격을 보고 나갔는지 함께 박제한다. */
        @NotNull PriceSource priceSource,
        String deeplinkUrl
) {}
