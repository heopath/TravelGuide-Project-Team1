package org.example.all_my_trip_project.domain.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.admin.dto.AdminMemberDTO;

import java.util.List;
import java.util.Optional;

@Mapper
public interface AdminMemberMapper {

    List<AdminMemberDTO> findPage(@Param("keyword") String keyword,
                                  @Param("status") String status,
                                  @Param("role") String role,
                                  @Param("includeWithdrawn") boolean includeWithdrawn,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    long count(@Param("keyword") String keyword,
               @Param("status") String status,
               @Param("role") String role,
               @Param("includeWithdrawn") boolean includeWithdrawn);

    Optional<AdminMemberDTO> findById(@Param("userId") Long userId);

    long countActiveAdmins();

    /**
     * 활동 중인 관리자 행을 잠근다. "마지막 관리자 보호"를 실제로 성립시키기 위한 것이다.
     *
     * <p>세지 않고 바로 고치면, 관리자가 둘 남은 상태에서 두 요청이 동시에 들어올 때
     * 양쪽 모두 "나 말고 하나 더 있다"를 보고 통과해 <b>둘 다</b> 내려간다. 관리자가 0명이
     * 되면 화면으로는 복구할 수 없고 운영 DB에 SQL을 직접 실행해야 한다 — 이 화면이
     * 없애려던 바로 그 절차다.
     *
     * <p>관리자 수는 많아야 몇 명이라 전부 잠가도 비용이 사실상 없다.
     */
    long lockAndCountActiveAdmins();

    int updateStatus(@Param("userId") Long userId, @Param("status") String status);

    int updateRole(@Param("userId") Long userId, @Param("role") String role);
}
