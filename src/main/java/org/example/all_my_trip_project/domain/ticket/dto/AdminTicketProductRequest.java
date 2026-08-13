package org.example.all_my_trip_project.domain.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 티켓 상품 등록·수정 요청.
 *
 * <p>판매 상태는 여기에 없다. 상태 변경은 {@code PATCH .../status}가 담당한다. 한 요청에서
 * 정보와 상태를 함께 바꾸면 "무엇을 의도한 변경인지"가 감사 로그에서 흐려진다.
 *
 * <p>기간 검증은 DB의 {@code ck_ticket_products_sale_period}·{@code ck_ticket_products_usage_period}가
 * 이미 막지만, 제약 위반은 화면에 이유가 드러나지 않아 서비스에서 먼저 거른다.
 */
public record AdminTicketProductRequest(
        @NotNull(message = "장소를 선택해 주세요.")
        @Positive(message = "장소를 선택해 주세요.")
        Long placeId,

        @NotBlank(message = "상품명을 입력해 주세요.")
        @Size(max = 150, message = "상품명은 150자를 넘을 수 없습니다.")
        String name,

        String description,

        @NotNull(message = "판매 시작 일시를 입력해 주세요.")
        OffsetDateTime saleStartAt,

        @NotNull(message = "판매 종료 일시를 입력해 주세요.")
        OffsetDateTime saleEndAt,

        @NotNull(message = "이용 시작일을 입력해 주세요.")
        LocalDate usageStartDate,

        @NotNull(message = "이용 종료일을 입력해 주세요.")
        LocalDate usageEndDate
) {}
