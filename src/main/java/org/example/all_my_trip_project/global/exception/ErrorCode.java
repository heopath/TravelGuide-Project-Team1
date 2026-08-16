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

    SUPPORT_INQUIRY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "고객센터 문의를 찾을 수 없습니다."
    ),

    INVALID_ADMIN_REQUEST(
            HttpStatus.BAD_REQUEST,
            "조회 조건이 올바르지 않습니다."
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
