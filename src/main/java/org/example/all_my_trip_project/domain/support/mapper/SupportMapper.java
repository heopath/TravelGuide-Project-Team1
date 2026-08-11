package org.example.all_my_trip_project.domain.support.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryDTO;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SupportMapper {
    int insertInquiry(SupportInquiryDTO inquiry);
    Optional<SupportInquiryDTO> findInquiry(@Param("inquiryId") Long inquiryId);
    List<SupportInquiryDTO> findMyPage(@Param("userId") Long userId,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);
    long countMine(@Param("userId") Long userId);
}
