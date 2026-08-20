package org.example.all_my_trip_project.domain.support.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.admin.service.AdminAuditService;
import org.example.all_my_trip_project.domain.support.dao.AdminSupportDAO;
import org.example.all_my_trip_project.domain.support.dto.AdminSupportInquiryDetail;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportInquiryPage;
import org.example.all_my_trip_project.domain.support.dto.SupportReplyDTO;
import org.example.all_my_trip_project.domain.support.dto.SupportReplyRequest;
import org.example.all_my_trip_project.domain.notification.service.NotificationService;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSupportService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "ANSWERED", "CLOSED");
    private final AdminSupportDAO dao;
    private final AdminAuditService adminAuditService;
    private final NotificationService notificationService;

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
        /* 답변 본문은 남기지 않는다. 회원 문의 내용이 감사 로그로 번지면 열람 범위가 넓어진다. */
        adminAuditService.record("SUPPORT_REPLY", "SUPPORT_INQUIRY", inquiryId,
                AdminAuditService.payload("status", inquiry.getStatus()),
                AdminAuditService.payload("status", "ANSWERED", "replyLength", reply.getContent().length()));
        /*
         * 문의를 올린 사람에게 알린다. 답변 본문은 담지 않는다 — 알림 목록은 훑어보는
         * 자리이고, 문의 내용이 그 자리에 펼쳐지면 어깨너머로 보이는 범위가 넓어진다.
         */
        notificationService.notify(inquiry.getUserId(), "SUPPORT_REPLIED",
                "문의에 답변이 달렸어요",
                "고객센터에서 답변을 남겼습니다. 확인해 주세요.",
                "/mypage?view=support");

        return getDetail(inquiryId);
    }

    @Transactional
    public AdminSupportInquiryDetail updateStatus(Long inquiryId, String status) {
        SupportInquiryDTO inquiry = requireInquiry(inquiryId);
        String normalized = normalizeStatus(status, false);
        if (dao.updateStatus(inquiryId, normalized) != 1) {
            throw new IllegalStateException("문의 상태를 변경하지 못했습니다.");
        }
        adminAuditService.record("SUPPORT_STATUS_CHANGE", "SUPPORT_INQUIRY", inquiryId,
                AdminAuditService.payload("status", inquiry.getStatus()),
                AdminAuditService.payload("status", normalized));
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
