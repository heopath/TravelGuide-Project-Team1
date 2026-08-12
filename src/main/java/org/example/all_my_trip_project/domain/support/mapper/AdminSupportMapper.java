package org.example.all_my_trip_project.domain.support.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportReplyDTO;

import java.util.List;
import java.util.Optional;

@Mapper
public interface AdminSupportMapper {
    List<SupportInquiryDTO> findPage(@Param("status") String status,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);
    long count(@Param("status") String status);
    Optional<SupportInquiryDTO> findInquiry(@Param("inquiryId") Long inquiryId);
    List<SupportReplyDTO> findReplies(@Param("inquiryId") Long inquiryId);
    int insertReply(SupportReplyDTO reply);
    int updateStatus(@Param("inquiryId") Long inquiryId, @Param("status") String status);
}
