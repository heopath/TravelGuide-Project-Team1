package org.example.all_my_trip_project.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    EMAIL_DUPLICATED(
            HttpStatus.CONFLICT,
            "이미 사용 중인 이메일입니다."
    ),

    NICKNAME_DUPLICATED(
            HttpStatus.CONFLICT,
            "이미 사용 중인 닉네임입니다."
    ),

    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "이메일 또는 비밀번호가 올바르지 않습니다."
    ),

    TURNSTILE_VERIFICATION_FAILED(
            HttpStatus.BAD_REQUEST,
            "사람인지 확인하지 못했습니다. 다시 시도해 주세요."
    ),

    TURNSTILE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "사람 확인 서비스에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요."
    ),

    PASSWORD_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "현재 비밀번호가 일치하지 않습니다."
    ),

    NEW_PASSWORD_SAME_AS_CURRENT(
            HttpStatus.BAD_REQUEST,
            "새 비밀번호는 현재 비밀번호와 다르게 설정해주세요."
    ),

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "로그인이 필요합니다."
    ),

    AI_REQUEST_LIMIT_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS,
            "AI 추천 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."
    ),

    ACCOUNT_SUSPENDED(
            HttpStatus.FORBIDDEN,
            "정지된 계정입니다."
    ),

    ACCOUNT_WITHDRAWN(
            HttpStatus.FORBIDDEN,
            "탈퇴한 계정입니다."
    ),

    TRAVEL_STYLE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "사용할 수 없는 여행 스타일이 포함되어 있습니다."
    ),

    TRAVEL_STYLE_DUPLICATED(
            HttpStatus.BAD_REQUEST,
            "동일한 여행 스타일을 중복해서 저장할 수 없습니다."
    ),

    WEATHER_NOT_CONFIGURED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "날씨 서비스가 아직 설정되지 않았습니다."
    ),

    WEATHER_DATE_OUT_OF_RANGE(
            HttpStatus.BAD_REQUEST,
            "날씨는 오늘부터 3일 이내 일정만 확인할 수 있습니다."
    ),

    ROUTE_NOT_CONFIGURED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "경로 최적화 서비스가 아직 설정되지 않았습니다."
    ),

    ROUTE_API_FAILED(
            HttpStatus.BAD_GATEWAY,
            "카카오 길찾기 API 호출에 실패했습니다. REST API 키와 허용 IP를 확인해주세요."
    ),

    ROUTE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "해당 구간에 이용 가능한 경로가 없습니다. 다른 이동수단을 선택해주세요."
    ),

    INVALID_TRIP_REQUEST(
            HttpStatus.BAD_REQUEST,
            "여행 생성 요청값이 올바르지 않습니다."
    ),

    INVALID_TRIP_PERIOD(
            HttpStatus.BAD_REQUEST,
            "여행 기간은 시작일과 종료일을 포함해 1일 이상 30일 이하여야 합니다."
    ),

    TRIP_PERIOD_CONFLICT(
            HttpStatus.CONFLICT,
            "변경 범위 밖 일차에 일정이 있어 여행 기간을 변경할 수 없습니다."
    ),

    INVALID_COMPANION_COUNT(
            HttpStatus.BAD_REQUEST,
            "동행 인원은 1명 이상 20명 이하여야 하며 혼자 여행은 1명이어야 합니다."
    ),

    TRIP_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "여행을 찾을 수 없습니다."
    ),

    TRIP_CREATE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "여행을 생성하지 못했습니다."
    ),

    ITINERARY_ITEM_LIMIT_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            "하루 일정은 최대 5개까지 추가할 수 있습니다."
    ),

    ITINERARY_PLACE_ALREADY_ADDED(
            HttpStatus.CONFLICT,
            "같은 DAY에 이미 추가된 장소입니다."
    ),

    ITINERARY_TIME_CONFLICT(
            HttpStatus.CONFLICT,
            "기존 일정과 시간이 겹쳐 추가할 수 없습니다."
    ),

    INVALID_FLIGHT_LEG(
            HttpStatus.BAD_REQUEST,
            "항공 구간은 가는 편(0) 또는 오는 편(1)만 지정할 수 있습니다."
    ),

    FLIGHT_BOOKING_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "선택한 항공편을 찾을 수 없습니다."
    ),

    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "권한이 없습니다."
    ),

    INVALID_RECORD_REQUEST(
            HttpStatus.BAD_REQUEST,
            "여행 기록 요청값이 올바르지 않습니다."
    ),

    TRIP_NOT_COMPLETED(
            HttpStatus.BAD_REQUEST,
            "완료된 여행만 기록을 작성할 수 있습니다."
    ),

    RECORD_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "이미 이 여행에 대한 기록이 존재합니다."
    ),

    RECORD_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "여행 기록을 찾을 수 없습니다."
    ),

    PLACE_REVIEW_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "이미 이 장소에 작성한 후기가 있습니다."
    ),

    PLACE_REVIEW_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "장소 후기를 찾을 수 없습니다."
    ),

    PLACE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "추천 장소를 찾을 수 없습니다."
    ),

    INVALID_PLACE_REQUEST(
            HttpStatus.BAD_REQUEST,
            "추천 장소 입력값을 확인해 주세요."
    ),

    REPORT_ALREADY_PENDING(
            HttpStatus.CONFLICT,
            "이미 처리 대기 중인 신고가 있습니다."
    ),

    REPORT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "신고 내역을 찾을 수 없습니다."
    ),

    INVALID_REPORT_STATUS_TRANSITION(
            HttpStatus.BAD_REQUEST,
            "처리할 수 없는 신고 상태입니다."
    ),

    INVALID_ACCOMMODATION_PERIOD(
            HttpStatus.BAD_REQUEST,
            "체크아웃 날짜는 체크인 다음 날부터 최대 30박까지 지정할 수 있습니다."
    ),

    INVALID_ACCOMMODATION_DESTINATION(
            HttpStatus.BAD_REQUEST,
            "숙소를 검색할 지역을 입력해 주세요."
    ),

    ACCOMMODATION_PROVIDER_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "숙소 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."
    ),

    ACCOMMODATION_PERIOD_OUT_OF_TRIP(
            HttpStatus.BAD_REQUEST,
            "숙소 기간은 여행 기간 안에 있어야 합니다."
    ),

    ACCOMMODATION_PERIOD_OVERLAP(
            HttpStatus.CONFLICT,
            "같은 기간에 이미 담아둔 숙소가 있습니다."
    ),

    ACCOMMODATION_BOOKING_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "담아둔 숙소를 찾을 수 없습니다."
    ),

    TICKET_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "선택한 티켓 상품이나 시간대를 찾을 수 없습니다."
    ),

    TICKET_NOT_AVAILABLE(
            HttpStatus.CONFLICT,
            "선택한 티켓의 남은 수량이 부족합니다."
    ),

    TICKET_RESERVATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "티켓 예약을 찾을 수 없습니다."
    ),

    TICKET_CANCEL_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "현재 상태에서는 티켓 예약을 취소할 수 없습니다."
    ),

    INVALID_TICKET_REQUEST(
            HttpStatus.BAD_REQUEST,
            "티켓 사용일, 수량 또는 여행 정보가 올바르지 않습니다."
    ),

    /*
     * 위 문구는 셋 중 무엇이 잘못됐는지 말해 주지 않는다. 여행 기간 밖 회차를 골랐을 때
     * 손님은 날짜를 바꿔야 하는데, 수량을 줄여 보거나 여행을 다시 고르며 헤맸다.
     */
    TICKET_DATE_OUTSIDE_TRIP(
            HttpStatus.BAD_REQUEST,
            "이 회차의 이용일이 여행 기간 밖이에요. 여행 기간 안의 날짜를 골라 주세요."
    ),

    TICKET_QUANTITY_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            "한 사람이 살 수 있는 매수를 넘었어요. 매수를 줄여 주세요."
    ),

    BOOKING_QUEUE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "예약 대기열에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요."
    ),

    BOOKING_QUEUE_EXPIRED(
            HttpStatus.GONE,
            "예약 대기 순번이 만료되었습니다. 다시 시도해 주세요."
    ),

    BOOKING_QUEUE_NOT_READY(
            HttpStatus.CONFLICT,
            "아직 예약 차례가 아닙니다. 현재 순번을 다시 확인해 주세요."
    ),

    BOOKING_QUEUE_PROCESSING(
            HttpStatus.CONFLICT,
            "티켓 예약을 처리하고 있습니다. 잠시만 기다려 주세요."
    ),

    /*
     * 이미 예약된 수보다 재고를 적게 줄이려는 경우. ck_ticket_inventory_quantities가
     * 어차피 막지만, DB 제약 위반은 화면에 이유가 드러나지 않아 관리자가 원인을 알 수 없다.
     */
    TICKET_INVENTORY_BELOW_RESERVED(
            HttpStatus.CONFLICT,
            "이미 예약된 수량보다 적게 줄일 수 없습니다."
    ),

    /*
     * 취소·사용 완료된 티켓의 QR을 달라는 경우. 만들어주면 통하지 않는 QR을 손님이 들고
     * 현장에 서 있게 된다.
     */
    TICKET_QR_NOT_AVAILABLE(
            HttpStatus.CONFLICT,
            "이 티켓은 입장 코드를 다시 발급할 수 없습니다."
    ),

    TICKET_OPTION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "티켓 옵션을 찾을 수 없습니다."
    ),

    /*
     * 오픈 전과 판매 종료를 갈라서 알린다. 손님에게 해 줄 말이 다르다 — 하나는 "그 시각에
     * 다시 오세요"이고 다른 하나는 "이제 못 삽니다"이다. (#256)
     */
    TICKET_SALE_NOT_OPEN(
            HttpStatus.CONFLICT,
            "아직 판매가 시작되지 않았습니다. 오픈 시각에 다시 시도해 주세요."
    ),

    TICKET_SALE_ENDED(
            HttpStatus.CONFLICT,
            "판매가 끝난 티켓입니다."
    ),

    /*
     * uk_ticket_product_options_name·uk_ticket_product_options_order 위반. 제약으로 터지면
     * 이름이 겹친 것인지 순서가 겹친 것인지 화면에서 구분할 수 없어 먼저 거른다.
     */
    TICKET_OPTION_DUPLICATED(
            HttpStatus.CONFLICT,
            "같은 이름이나 순서의 옵션이 이미 있습니다."
    ),

    /* uk_ticket_time_slots (옵션·이용일·시작시각) 위반. */
    TICKET_SLOT_DUPLICATED(
            HttpStatus.CONFLICT,
            "같은 옵션에 같은 날짜·시작시각의 시간대가 이미 있습니다."
    ),

    /*
     * 예약이 걸린 시간대를 닫으려는 경우. 닫아도 예약 자체는 남지만, 관리자가 "정리했다"고
     * 오해하기 쉬워 막고 이유를 밝힌다.
     */
    TICKET_SLOT_HAS_RESERVATION(
            HttpStatus.CONFLICT,
            "이미 예약이 걸린 시간대입니다. 예약을 먼저 정리해 주세요."
    ),

    /*
     * 산 티켓을 여행에 붙일 때 이용일이 여행 기간 밖인 경우. 8월 여행에 9월 티켓을 붙이면
     * 일정 화면에서 그 티켓이 놓일 자리가 없다.
     */
    TICKET_TRIP_PERIOD_MISMATCH(
            HttpStatus.CONFLICT,
            "티켓 이용일이 선택한 여행 기간에 들어 있지 않습니다."
    ),

    SUPPORT_INQUIRY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "고객센터 문의를 찾을 수 없습니다."
    ),

    INVALID_ADMIN_REQUEST(
            HttpStatus.BAD_REQUEST,
            "조회 조건이 올바르지 않습니다."
    ),

    INVALID_SERVICE_VERSION(
            HttpStatus.BAD_REQUEST,
            "버전은 0.0.5 또는 v0.0.5 형식으로 입력해 주세요."
    ),

    UNKNOWN_API_KEY(
            HttpStatus.BAD_REQUEST,
            "관리 대상이 아닌 API 키입니다."
    ),

    INVALID_API_KEY(
            HttpStatus.BAD_REQUEST,
            "API 키를 입력해 주세요."
    ),

    /**
     * 마스터 키가 없으면 저장을 거절한다. 평문으로라도 저장하는 대안은 두지 않는다.
     * 한 번 평문으로 들어간 키는 아무도 다시 확인하지 않기 때문이다.
     */
    API_KEY_ENCRYPTION_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "서버에 API_KEY_ENCRYPTION_KEY가 설정되지 않아 키를 저장할 수 없습니다."
    ),

    API_KEY_NOT_STORED(
            HttpStatus.NOT_FOUND,
            "관리자가 저장한 키가 없습니다. 이미 환경변수 값을 쓰고 있습니다."
    ),

    MEMBER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "회원을 찾을 수 없습니다."
    ),

    INVALID_MEMBER_REQUEST(
            HttpStatus.BAD_REQUEST,
            "회원 변경 요청이 올바르지 않습니다."
    ),

    MEMBER_SELF_CHANGE_DENIED(
            HttpStatus.BAD_REQUEST,
            "자기 자신의 권한과 상태는 변경할 수 없습니다."
    ),

    LAST_ADMIN_PROTECTED(
            HttpStatus.BAD_REQUEST,
            "마지막 관리자입니다. 다른 관리자를 먼저 지정해 주세요."
    ),

    MEMBER_WITHDRAWN_IMMUTABLE(
            HttpStatus.BAD_REQUEST,
            "탈퇴한 회원은 변경할 수 없습니다."
    ),

    MEMBER_NOT_ACTIVE(
            HttpStatus.BAD_REQUEST,
            "정지된 회원은 관리자로 올릴 수 없습니다."
    ),

    RESERVATION_NOT_PAYABLE(
            HttpStatus.CONFLICT,
            "결제할 수 없는 예약입니다. 예약 상태를 확인해 주세요."
    ),

    RESERVATION_EXPIRED(
            HttpStatus.GONE,
            "예약 시간이 지나 자리가 반납되었습니다. 다시 예약해 주세요."
    ),

    INVALID_PAYMENT_REQUEST(
            HttpStatus.BAD_REQUEST,
            "결제 요청이 올바르지 않습니다."
    ),

    /*
     * 실제 결제사(토스·카카오페이)는 시크릿 키가 있어야 부를 수 있다. 없으면 화면이 모의
     * 결제로 돌아가야 하므로 그 사실을 코드로 구분해 알린다. (#281)
     */
    TOSS_NOT_CONFIGURED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "토스 결제가 설정되지 않았습니다. 관리자에게 문의해 주세요."
    ),

    TOSS_CONFIRM_FAILED(
            HttpStatus.BAD_GATEWAY,
            "결제 승인에 실패했습니다. 결제창에서 다시 시도해 주세요."
    ),

    KAKAO_PAY_NOT_CONFIGURED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "카카오페이 결제가 설정되지 않았습니다. 관리자에게 문의해 주세요."
    ),

    KAKAO_PAY_CALL_FAILED(
            HttpStatus.BAD_GATEWAY,
            "카카오페이 결제를 진행하지 못했습니다. 잠시 후 다시 시도해 주세요."
    ),

    /*
     * 결제를 시작한 기록이 사라진 경우다. 카카오페이 창을 열어 둔 채 오래 두면 만료된다.
     * 승인 실패와 갈라야 손님에게 "다시 결제해 주세요"라고 말할 수 있다.
     */
    KAKAO_PAY_SESSION_EXPIRED(
            HttpStatus.GONE,
            "결제 시간이 지났습니다. 결제를 다시 시작해 주세요."
    ),

    /*
     * 만료와 위조를 나눈다. 손님에게 해 줄 말이 다르다 — 하나는 "다시 띄우세요"이고
     * 다른 하나는 "이 QR로는 결제할 수 없습니다"이다. (#281)
     */
    PAYMENT_QR_INVALID(
            HttpStatus.BAD_REQUEST,
            "결제 QR이 올바르지 않습니다. 결제 화면에서 다시 띄워 주세요."
    ),

    PAYMENT_QR_EXPIRED(
            HttpStatus.GONE,
            "결제 QR의 유효 시간이 지났습니다. 다시 띄워 주세요."
    ),

    TICKET_ALREADY_USED(
            HttpStatus.CONFLICT,
            "이미 사용한 티켓이 있어 취소할 수 없습니다."
    ),

    TICKET_USAGE_DATE_PASSED(
            HttpStatus.CONFLICT,
            "이용일이 지나 취소할 수 없습니다."
    ),

    SUPPORT_CHAT_ROOM_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "상담을 찾을 수 없습니다."
    ),

    SUPPORT_CHAT_ROOM_CLOSED(
            HttpStatus.CONFLICT,
            "이미 종료된 상담입니다."
    ),

    SUPPORT_CHAT_ALREADY_ASSIGNED(
            HttpStatus.CONFLICT,
            "다른 관리자가 응대하고 있는 상담입니다."
    ),

    SUPPORT_CHAT_NOT_ASSIGNED(
            HttpStatus.CONFLICT,
            "먼저 내가 응대하기를 눌러야 답할 수 있습니다."
    ),

    INVALID_SUPPORT_CHAT_REQUEST(
            HttpStatus.BAD_REQUEST,
            "상담 요청이 올바르지 않습니다."
    ),

    SUPPORT_CHAT_SUBSCRIBE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "이 상담 채팅을 구독할 권한이 없습니다."
    ),

    SUPPORT_CHAT_BOT_RETURN_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "상담원이 응대를 시작해 봇으로 돌아갈 수 없습니다. 새 상담을 시작해 주세요."
    );

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
