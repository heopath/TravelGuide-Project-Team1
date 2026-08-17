package org.example.all_my_trip_project.domain.ticket.service;

import org.example.all_my_trip_project.domain.ticket.dao.TicketValidationDAO;
import org.example.all_my_trip_project.domain.ticket.dto.TicketValidationRequest;
import org.example.all_my_trip_project.domain.ticket.dto.TicketValidationResponse;
import org.example.all_my_trip_project.domain.ticket.dto.ValidatableTicketDTO;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketValidationServiceTest {

    private static final String TOKEN = "entry-code-1";
    private static final long TICKET_ID = 5L;

    private TicketValidationDAO dao;
    private TicketValidationService service;

    @BeforeEach
    void setUp() {
        dao = mock(TicketValidationDAO.class);
        service = new TicketValidationService(dao);
    }

    private TicketValidationRequest request() {
        return new TicketValidationRequest(TOKEN, null, null);
    }

    private ValidatableTicketDTO ticket(String status, String reservationStatus,
                                        OffsetDateTime from, OffsetDateTime until) {
        return ValidatableTicketDTO.builder()
                .issuedTicketId(TICKET_ID)
                .ticketNumber("AMT-TKN-AAA")
                .status(status)
                .reservationStatus(reservationStatus)
                .validFrom(from)
                .validUntil(until)
                .productName("아쿠아리움")
                .optionName("성인")
                .usageDate(LocalDate.now())
                .build();
    }

    private ValidatableTicketDTO usable() {
        return ticket("ISSUED", "CONFIRMED",
                OffsetDateTime.now().minusHours(1), OffsetDateTime.now().plusHours(5));
    }

    private String loggedResult() {
        ArgumentCaptor<String> result = ArgumentCaptor.forClass(String.class);
        verify(dao).insertLog(any(), any(), anyString(), result.capture(), anyString(), any(), any());
        return result.getValue();
    }

    @Test
    @DisplayName("유효한 티켓은 입장시키고 사용 처리한다")
    void admitsValidTicket() {
        when(dao.lockByTokenHash(anyString())).thenReturn(Optional.of(usable()));
        when(dao.markUsed(TICKET_ID)).thenReturn(1);

        TicketValidationResponse response = service.validate(request());

        assertThat(response.result()).isEqualTo("SUCCESS");
        assertThat(response.admitted()).isTrue();
        assertThat(response.ticketNumber()).isEqualTo("AMT-TKN-AAA");
        verify(dao).markUsed(TICKET_ID);
        assertThat(loggedResult()).isEqualTo("SUCCESS");
    }

    /*
     * 원문 코드가 아니라 해시가 남아야 한다. 로그에 원문이 들어가면 기록을 읽을 수 있는 사람이
     * 그대로 입장할 수 있다.
     */
    @Test
    @DisplayName("검표 기록에는 코드 원문이 아니라 해시를 남긴다")
    void logsFingerprintNotRawToken() {
        when(dao.lockByTokenHash(anyString())).thenReturn(Optional.of(usable()));
        when(dao.markUsed(TICKET_ID)).thenReturn(1);

        service.validate(request());

        ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
        verify(dao).insertLog(any(), any(), fingerprint.capture(), anyString(), anyString(), any(), any());
        assertThat(fingerprint.getValue()).hasSize(64).isNotEqualTo(TOKEN);
    }

    /* ── 거절 ── */

    @Test
    @DisplayName("없는 코드도 기록을 남긴다")
    void logsUnknownCode() {
        when(dao.lockByTokenHash(anyString())).thenReturn(Optional.empty());

        TicketValidationResponse response = service.validate(request());

        assertThat(response.result()).isEqualTo("NOT_FOUND");
        assertThat(response.admitted()).isFalse();
        assertThat(response.ticketNumber()).isNull();
        /* 티켓을 못 찾았으므로 issued_ticket_id는 비고, 지문은 남는다. */
        verify(dao).insertLog(eq(null), any(), anyString(), eq("NOT_FOUND"), anyString(), any(), any());
    }

    @Test
    @DisplayName("이미 사용된 티켓은 언제 썼는지 알려준다")
    void rejectsUsedTicket() {
        ValidatableTicketDTO used = usable();
        used.setStatus("USED");
        used.setUsedAt(OffsetDateTime.now().minusHours(2));
        when(dao.lockByTokenHash(anyString())).thenReturn(Optional.of(used));

        TicketValidationResponse response = service.validate(request());

        assertThat(response.result()).isEqualTo("ALREADY_USED");
        assertThat(response.message()).contains("이미").contains("사용");
        assertThat(response.usedAt()).isNotNull();
        verify(dao, never()).markUsed(any());
    }

    @Test
    @DisplayName("취소된 예약의 티켓은 입장시키지 않는다")
    void rejectsCancelledReservation() {
        when(dao.lockByTokenHash(anyString()))
                .thenReturn(Optional.of(ticket("ISSUED", "CANCELLED",
                        OffsetDateTime.now().minusHours(1), OffsetDateTime.now().plusHours(5))));

        TicketValidationResponse response = service.validate(request());

        assertThat(response.result()).isEqualTo("CANCELLED");
        verify(dao, never()).markUsed(any());
    }

    /*
     * 아직 이른 것과 이미 지난 것은 손님이 해야 할 일이 다르다. 기다리면 되는지 아닌지를
     * 문장이 구분해야 한다.
     */
    @Test
    @DisplayName("입장 시간 전에는 언제부터 되는지 알려준다")
    void rejectsBeforeValidFrom() {
        when(dao.lockByTokenHash(anyString()))
                .thenReturn(Optional.of(ticket("ISSUED", "CONFIRMED",
                        OffsetDateTime.now().plusHours(2), OffsetDateTime.now().plusHours(8))));

        TicketValidationResponse response = service.validate(request());

        assertThat(response.result()).isEqualTo("EXPIRED");
        assertThat(response.message()).contains("아직").contains("부터");
    }

    /*
     * 로컬 DB로 확인하다 찾은 것이다. 드라이버가 오프셋을 UTC로 돌려주면 그대로 포맷했을 때
     * 한국 기준 9시간이 밀려, 오전 10시부터인 티켓을 "01:00부터"라고 읽어 주게 된다.
     * 검표원이 손님에게 그대로 말하는 문장이라 여기가 어긋나면 바로 사고가 된다.
     */
    @Test
    @DisplayName("안내 문구의 시각은 현장 시간대로 찍는다")
    void formatsTimeInLocalZone() {
        /* 같은 순간을 UTC 오프셋으로 들고 있는 티켓. 한국이면 09:00이다. */
        OffsetDateTime midnightUtc = OffsetDateTime.parse("2099-01-01T00:00:00Z");
        when(dao.lockByTokenHash(anyString()))
                .thenReturn(Optional.of(ticket("ISSUED", "CONFIRMED",
                        midnightUtc, midnightUtc.plusHours(8))));

        String message = service.validate(request()).message();

        String expected = midnightUtc.atZoneSameInstant(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("M월 d일 HH:mm",
                        java.util.Locale.KOREAN));
        assertThat(message).contains(expected);
    }

    @Test
    @DisplayName("입장 시간이 지나면 지났다고 알려준다")
    void rejectsAfterValidUntil() {
        when(dao.lockByTokenHash(anyString()))
                .thenReturn(Optional.of(ticket("ISSUED", "CONFIRMED",
                        OffsetDateTime.now().minusDays(2), OffsetDateTime.now().minusDays(1))));

        TicketValidationResponse response = service.validate(request());

        assertThat(response.result()).isEqualTo("EXPIRED");
        assertThat(response.message()).contains("지난");
    }

    /*
     * 어제 쓴 티켓을 오늘 가져오면 기간도 지나 있다. 그때 "기간 지남"이라고 하면 중복 입장
     * 시도가 가려진다. 사용 여부를 기간보다 먼저 본다.
     */
    @Test
    @DisplayName("이미 쓴 티켓은 기간이 지났더라도 사용됨으로 답한다")
    void usedBeatsExpired() {
        ValidatableTicketDTO used = ticket("USED", "CONFIRMED",
                OffsetDateTime.now().minusDays(2), OffsetDateTime.now().minusDays(1));
        used.setUsedAt(OffsetDateTime.now().minusDays(1).minusHours(3));
        when(dao.lockByTokenHash(anyString())).thenReturn(Optional.of(used));

        assertThat(service.validate(request()).result()).isEqualTo("ALREADY_USED");
    }

    @Test
    @DisplayName("취소는 기간보다 먼저 본다")
    void cancelledBeatsExpired() {
        when(dao.lockByTokenHash(anyString()))
                .thenReturn(Optional.of(ticket("ISSUED", "CANCELLED",
                        OffsetDateTime.now().minusDays(2), OffsetDateTime.now().minusDays(1))));

        assertThat(service.validate(request()).result()).isEqualTo("CANCELLED");
    }

    /*
     * 잠갔는데도 갱신이 0건이면 다른 창구가 먼저 처리한 것이다. 성공으로 답하면 한 장으로
     * 두 명이 들어간다.
     */
    @Test
    @DisplayName("동시에 들어온 검표 중 하나만 입장시킨다")
    void rejectsWhenAnotherScannerWonTheRace() {
        when(dao.lockByTokenHash(anyString())).thenReturn(Optional.of(usable()));
        when(dao.markUsed(TICKET_ID)).thenReturn(0);

        TicketValidationResponse response = service.validate(request());

        assertThat(response.result()).isEqualTo("ALREADY_USED");
        assertThat(response.admitted()).isFalse();
        assertThat(loggedResult()).isEqualTo("ALREADY_USED");
    }

    /*
     * 현장에 줄이 서 있는데 로그를 못 썼다고 입장을 거부하면 그게 더 큰 문제다.
     */
    @Test
    @DisplayName("기록을 남기지 못해도 검표 결과는 돌려준다")
    void returnsResultEvenIfLoggingFails() {
        when(dao.lockByTokenHash(anyString())).thenReturn(Optional.of(usable()));
        when(dao.markUsed(TICKET_ID)).thenReturn(1);
        when(dao.insertLog(any(), any(), anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new RuntimeException("데이터베이스 연결 실패"));

        TicketValidationResponse response = service.validate(request());

        assertThat(response.result()).isEqualTo("SUCCESS");
        assertThat(response.admitted()).isTrue();
    }

    /* ── 기록 조회 ── */

    @Test
    @DisplayName("알 수 없는 결과로는 기록을 거를 수 없다")
    void rejectsUnknownResultFilter() {
        assertThatThrownBy(() -> service.recentLogs("WHATEVER", 30))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ADMIN_REQUEST);
    }

    @Test
    @DisplayName("한 번에 가져올 수 있는 기록 수를 제한한다")
    void rejectsTooLargeLimit() {
        assertThatThrownBy(() -> service.recentLogs(null, 1000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ADMIN_REQUEST);
    }

    @Test
    @DisplayName("결과 조건은 대소문자를 가리지 않는다")
    void normalizesResultFilter() {
        service.recentLogs("success", 30);
        verify(dao).findRecentLogs(eq("SUCCESS"), anyInt());
    }
}
