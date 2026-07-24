package org.example.all_my_trip_project.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.user.dao.UserDAO;
import org.example.all_my_trip_project.domain.user.dto.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserDAO userDAO;

    @Transactional
    public Long create(UserDTO user) {
        userDAO.insert(user);
        return user.getUserId();
    }

    public UserDTO get(Long userId) {
        return userDAO.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. userId=" + userId));
    }

    public List<UserDTO> getAll() {
        return userDAO.findAll();
    }

    @Transactional
    public void update(UserDTO user) {
        if (userDAO.update(user) == 0) {
            throw new IllegalArgumentException("수정할 사용자를 찾을 수 없습니다. userId=" + user.getUserId());
        }
    }

    @Transactional
    public void withdraw(Long userId) {
        if (userDAO.softDelete(userId) == 0) {
            throw new IllegalArgumentException("탈퇴할 사용자를 찾을 수 없습니다. userId=" + userId);
        }
    }
}
