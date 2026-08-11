package org.example.all_my_trip_project.domain.support.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.support.dao.SupportDAO;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryPage;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryRequest;
import org.example.all_my_trip_project.domain.user.service.ActiveMemberGuard;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportService {
    private static final int MAX_PAGE_SIZE = 50;
    private final SupportDAO supportDAO;
    private final ActiveMemberGuard activeMemberGuard;

    @Transactional
    public SupportInquiryDTO create(Long userId, SupportInquiryRequest request) {
        validateUser(userId);
        activeMemberGuard.requireActiveMember(userId);
        SupportInquiryDTO inquiry = SupportInquiryDTO.builder()
                .userId(userId)
                .category(request.category())
                .title(request.title().trim())
                .content(request.content().trim())
                .status("OPEN")
                .build();
        if (supportDAO.insertInquiry(inquiry) != 1 || inquiry.getSupportInquiryId() == null) {
            throw new IllegalStateException("고객센터 문의를 저장하지 못했습니다.");
        }
        return requireInquiry(inquiry.getSupportInquiryId());
    }

    public SupportInquiryPage getMine(Long userId, int page, int size) {
        validateUser(userId);
        validatePage(page, size);
        activeMemberGuard.requireActiveMember(userId);
        long total = supportDAO.countMine(userId);
        return page(supportDAO.findMyPage(userId, Math.multiplyExact(page, size), size), page, size, total);
    }

    public SupportInquiryDTO getMineDetail(Long userId, Long inquiryId) {
        validateUser(userId);
        SupportInquiryDTO inquiry = requireInquiry(inquiryId);
        if (!userId.equals(inquiry.getUserId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        return inquiry;
    }

    private SupportInquiryPage page(java.util.List<SupportInquiryDTO> items, int page, int size, long total) {
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new SupportInquiryPage(items, page, size, total, totalPages);
    }

    private SupportInquiryDTO requireInquiry(Long inquiryId) {
        if (inquiryId == null || inquiryId < 1) throw new BusinessException(ErrorCode.SUPPORT_INQUIRY_NOT_FOUND);
        return supportDAO.findInquiry(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_INQUIRY_NOT_FOUND));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page는 0 이상, size는 1 이상 50 이하여야 합니다.");
        }
        Math.multiplyExact(page, size);
    }

    private void validateUser(Long userId) {
        if (userId == null || userId < 1) throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
