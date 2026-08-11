package org.example.all_my_trip_project.domain.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportInquiryDTO {
    private Long supportInquiryId;
    private Long userId;
    private String userNickname;
    private String userEmail;
    private String category;
    private String title;
    private String content;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
