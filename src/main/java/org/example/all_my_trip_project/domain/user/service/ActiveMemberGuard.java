package org.example.all_my_trip_project.domain.user.service;

import org.example.all_my_trip_project.global.exception.BusinessException;

/**
 * 다른 도메인이 회원 활성 상태를 확인할 때 쓰는 공개 계약이다. 회원 도메인의 Repository나 Entity를
 * 직접 참조하지 않고 이 계약만 의존하게 해서, "무엇이 활성 회원인가"에 대한 판단을 user 도메인 하나로 모은다.
 */
public interface ActiveMemberGuard {

    /**
     * 존재하지 않거나 정지·탈퇴한 회원이면 {@link BusinessException}을 던진다.
     * 통과하면 아무 값도 반환하지 않는다 — 호출자는 회원 엔티티를 받지 않는다.
     */
    void requireActiveMember(Long userId);
}
