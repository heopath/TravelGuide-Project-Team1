package org.example.all_my_trip_project.domain.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.all_my_trip_project.domain.user.dto.UserDTO;

import java.util.List;
import java.util.Optional;

@Mapper
public interface UserMapper {
    int insert(UserDTO user);
    Optional<UserDTO> findById(Long userId);
    Optional<UserDTO> findByEmail(String email);
    List<UserDTO> findAll();
    int update(UserDTO user);
    int softDelete(Long userId);
}
