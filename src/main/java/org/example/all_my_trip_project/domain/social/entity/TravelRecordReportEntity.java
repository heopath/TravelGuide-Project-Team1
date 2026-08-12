package org.example.all_my_trip_project.domain.social.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.all_my_trip_project.domain.social.type.ReportReason;
import org.example.all_my_trip_project.domain.social.type.ReportStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Entity
@Table(name = "travel_record_reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelRecordReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "travel_record_report_id")
    private Long travelRecordReportId;

    @Column(name = "travel_record_id", nullable = false)
    private Long travelRecordId;

    @Column(name = "reporter_user_id", nullable = false)
    private Long reporterUserId;

    @Column(nullable = false, length = 30)
    private String reason;

    @Column(length = 1000)
    private String detail;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "processed_by")
    private Long processedBy;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    private TravelRecordReportEntity(
            Long travelRecordId,
            Long reporterUserId,
            ReportReason reason,
            String detail
    ) {
        this.travelRecordId = travelRecordId;
        this.reporterUserId = reporterUserId;
        this.reason = reason.name();
        this.detail = detail;
        this.status = ReportStatus.PENDING.name();
    }

    public static TravelRecordReportEntity create(
            Long travelRecordId,
            Long reporterUserId,
            ReportReason reason,
            String detail
    ) {
        return new TravelRecordReportEntity(travelRecordId, reporterUserId, reason, detail);
    }

    public boolean isOpen() {
        return ReportStatus.PENDING.name().equals(status) || ReportStatus.REVIEWING.name().equals(status);
    }

    public void resolve(Long adminUserId, ReportStatus targetStatus, String resolutionNote) {
        this.status = targetStatus.name();
        this.processedBy = adminUserId;
        this.processedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.resolutionNote = resolutionNote;
    }

    @PrePersist
    private void onCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
