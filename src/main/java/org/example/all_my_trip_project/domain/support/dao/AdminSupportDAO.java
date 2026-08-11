package org.example.all_my_trip_project.domain.support.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportReplyDTO;
import org.example.all_my_trip_project.domain.support.mapper.AdminSupportMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class AdminSupportDAO {
    private final AdminSupportMapper mapper;

    public List<SupportInquiryDTO> findPage(String status, int offset, int limit) {
        return mapper.findPage(status, offset, limit);
    }
    public long count(String status) { return mapper.count(status); }
    public Optional<SupportInquiryDTO> findInquiry(Long inquiryId) { return mapper.findInquiry(inquiryId); }
    public List<SupportReplyDTO> findReplies(Long inquiryId) { return mapper.findReplies(inquiryId); }
    public int insertReply(SupportReplyDTO reply) { return mapper.insertReply(reply); }
    public int updateStatus(Long inquiryId, String status) { return mapper.updateStatus(inquiryId, status); }
}
