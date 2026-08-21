package org.example.all_my_trip_project.domain.payment.dto;

/**
 * 카카오페이 결제 시작 결과. (#281)
 *
 * <p>손님을 보낼 주소만 돌려준다. 카카오가 함께 준 거래번호(tid)는 승인할 때 필요하지만
 * <b>화면에 주지 않는다</b> — 화면이 들고 있다가 되돌려주는 값이 되면, 다른 결제의
 * 거래번호를 끼워 넣어 승인시키는 길이 열린다. 서버가 보관한다.
 *
 * @param redirectUrl 카카오페이 결제 화면 주소
 */
public record KakaoPayReadyResponse(String redirectUrl) {}
