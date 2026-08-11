package org.example.all_my_trip_project.domain.support.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.support.dao.AdminSupportDAO;
import org.example.all_my_trip_project.domain.support.dto.AdminSupportInquiryDetail;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryPage;
import org.example.all_my_trip_project.domain.support.dto.SupportReplyDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportReplyRequest;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSupportService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "ANSWERED", "CLOSED");
    private final AdminSupportDAO dao;

    public SupportInquiryPage getPage(String status, int page, int size) {
        validatePage(page, size);
        String filter = normalizeStatus(status, true);
        long total = dao.count(filter);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new SupportInquiryPage(
                dao.findPage(filter, Math.multiplyExact(page, size), size), page, size, total, totalPages);
    }

    public AdminSupportInquiryDetail getDetail(Long inquiryId) {
        SupportInquiryDTO inquiry = requireInquiry(inquiryId);
        return new AdminSupportInquiryDetail(inquiry, dao.findReplies(inquiryId));
    }

    @Transactional
    public AdminSupportInquiryDetail reply(Long adminUserId, Long inquiryId, SupportReplyRequest request) {
        if (adminUserId == null || adminUserId < 1) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        SupportInquiryDTO inquiry = requireInquiry(inquiryId);
        if ("CLOSED".equals(inquiry.getStatus())) {
            throw new IllegalStateException("종료된 문의에는 답변할 수 없습니다.");
        }
        SupportReplyDTO reply = SupportReplyDTO.builder()
                .supportInquiryId(inquiryId)
                .adminUserId(adminUserId)
                .content(request.content().trim())
                .build();
        if (dao.insertReply(reply) != 1) throw new IllegalStateException("문의 답변을 저장하지 못했습니다.");
        if (dao.updateStatus(inquiryId, "ANSWERED") != 1) throw new IllegalStateException("문의 상태를 변경하지 못했습니다.");
        return getDetail(inquiryId);
    }

    @Transactional
    public AdminSupportInquiryDetail updateStatus(Long inquiryId, String status) {
        requireInquiry(inquiryId);
        String normalized = normalizeStatus(status, false);
        if (dao.updateStatus(inquiryId, normalized) != 1) {
            throw new IllegalStateException("문의 상태를 변경하지 못했습니다.");
        }
        return getDetail(inquiryId);
    }

    private SupportInquiryDTO requireInquiry(Long inquiryId) {
        if (inquiryId == null || inquiryId < 1) throw new BusinessException(ErrorCode.SUPPORT_INQUIRY_NOT_FOUND);
        return dao.findInquiry(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_INQUIRY_NOT_FOUND));
    }

    private String normalizeStatus(String status, boolean optional) {
        if ((status == null || status.isBlank()) && optional) return null;
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!STATUSES.contains(normalized)) throw new IllegalArgumentException("올바른 문의 상태가 아닙니다.");
        return normalized;
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page는 0 이상, size는 1 이상 50 이하여야 합니다.");
        }
        Math.multiplyExact(page, size);
    }
}
