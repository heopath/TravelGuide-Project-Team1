package org.example.all_my_trip_project.domain.support.dto;

import java.util.List;

public record SupportInquiryPage(
        List<SupportInquiryDTO> inquiries,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
