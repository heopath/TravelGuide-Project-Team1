package org.example.all_my_trip_project.domain.user.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.user.dto.UserDTO;
import org.example.all_my_trip_project.domain.user.mapper.UserMapper;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class UserDAO {
    private final UserMapper userMapper;

    public int insert(UserDTO user) { return userMapper.insert(user); }
    public Optional<UserDTO> findById(Long userId) { return userMapper.findById(userId); }
    public Optional<UserDTO> findByEmail(String email) { return userMapper.findByEmail(email); }
    public List<UserDTO> findAll() { return userMapper.findAll(); }
    public int update(UserDTO user) { return userMapper.update(user); }
    public int softDelete(Long userId) { return userMapper.softDelete(userId); }
}
