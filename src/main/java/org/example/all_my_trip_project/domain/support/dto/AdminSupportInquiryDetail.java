package org.example.all_my_trip_project.domain.support.dto;

import java.util.List;

public record AdminSupportInquiryDetail(
        SupportInquiryDTO inquiry,
        List<SupportReplyDTO> replies
) {
}
