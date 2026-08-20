package org.example.all_my_trip_project.domain.payment.dto;

import java.time.OffsetDateTime;

/**
 * QR로 결제받기 위해 발급한 토큰. (#281)
 *
 * <p>주소는 서버가 만들지 않는다. 화면이 자기 주소({@code location.origin})에 붙여 QR로
 * 그린다. 서버가 만들면 배포 주소를 설정으로 들고 있어야 하고, 로컬·운영에서 어긋나면
 * 스캔한 폰이 엉뚱한 곳으로 간다.
 *
 * <p>{@code serverTime}을 함께 내리는 이유는 남은 시간을 서버 시각으로 세기 위해서다.
 * 손님 기기의 시계는 몇 분씩 어긋나 있을 수 있어, 아직 살아 있는 QR을 만료로 표시하거나
 * 그 반대가 된다.
 */
public record PaymentQrIssueResponse(
        Long reservationId,
        String token,
        OffsetDateTime expiresAt,
        OffsetDateTime serverTime
) {}
