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
public class SupportReplyDTO {
    private Long supportReplyId;
    private Long supportInquiryId;
    private Long adminUserId;
    private String adminNickname;
    private String content;
    private OffsetDateTime createdAt;
}
