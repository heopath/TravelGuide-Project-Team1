package org.example.all_my_trip_project.domain.user.repository;

/**
 * 세션이 아직 유효한지 확인할 때만 쓰는 최소 정보.
 *
 * <p>{@link org.example.all_my_trip_project.domain.user.entity.UserEntity}를 통째로 읽지 않는 것은
 * 이 조회가 <b>인증된 요청마다</b> 일어나기 때문이다. 비밀번호 해시까지 딸려 오는 것도 피한다.
 */
public interface UserAccountView {

    String getStatus();

    String getRole();
}
