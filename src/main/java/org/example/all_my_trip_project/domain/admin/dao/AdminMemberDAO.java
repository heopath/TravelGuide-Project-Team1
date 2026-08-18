package org.example.all_my_trip_project.domain.admin.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.dto.AdminMemberDTO;
import org.example.all_my_trip_project.domain.admin.mapper.AdminMemberMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class AdminMemberDAO {

    private final AdminMemberMapper adminMemberMapper;

    public List<AdminMemberDTO> findPage(String keyword, String status, String role,
                                         boolean includeWithdrawn, int offset, int size) {
        return adminMemberMapper.findPage(keyword, status, role, includeWithdrawn, offset, size);
    }

    public long count(String keyword, String status, String role, boolean includeWithdrawn) {
        return adminMemberMapper.count(keyword, status, role, includeWithdrawn);
    }

    public Optional<AdminMemberDTO> findById(Long userId) {
        return adminMemberMapper.findById(userId);
    }

    public long countActiveAdmins() {
        return adminMemberMapper.countActiveAdmins();
    }

    public long lockAndCountActiveAdmins() {
        return adminMemberMapper.lockAndCountActiveAdmins();
    }

    public int updateStatus(Long userId, String status) {
        return adminMemberMapper.updateStatus(userId, status);
    }

    public int updateRole(Long userId, String role) {
        return adminMemberMapper.updateRole(userId, role);
    }
}
