package org.example.all_my_trip_project.domain.support.dao;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryDTO;
import org.example.all_my_trip_project.domain.support.mapper.SupportMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("!ui")
@RequiredArgsConstructor
public class SupportDAO {
    private final SupportMapper mapper;

    public int insertInquiry(SupportInquiryDTO inquiry) { return mapper.insertInquiry(inquiry); }
    public Optional<SupportInquiryDTO> findInquiry(Long inquiryId) { return mapper.findInquiry(inquiryId); }
    public List<SupportInquiryDTO> findMyPage(Long userId, int offset, int limit) {
        return mapper.findMyPage(userId, offset, limit);
    }
    public long countMine(Long userId) { return mapper.countMine(userId); }
}
