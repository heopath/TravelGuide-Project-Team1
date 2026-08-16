package org.example.all_my_trip_project.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 회원 관리 목록의 한 행.
 *
 * <p>{@code password_hash}는 담지 않는다. 화면에 쓸 일이 없고, DTO에 한 번 올라오면
 * 로그나 응답 직렬화 어디로든 새어 나갈 수 있다. 조회 SQL에서도 아예 뽑지 않는다.
 *
 * <p>record가 아니라 Lombok 클래스인 것은 MyBatis가 채우는 자리이기 때문이다. 이 프로젝트의
 * {@code resultType} DTO는 모두 기본 생성자 + setter 방식이고, record로 두면 컬럼이 조용히
 * 비어 온다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminMemberDTO {
    private Long userId;
    private String email;
    private String nickname;
    private String role;
    private String status;
    private OffsetDateTime lastLoginAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime deletedAt;
}
