package org.example.all_my_trip_project.domain.trip.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!ui")
public class PostgreSqlTripDraftSnapshotRepository implements TripDraftSnapshotRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgreSqlTripDraftSnapshotRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public StoredTripDraft create(Long userId, Map<String, Object> draft) {
        String draftId = UUID.randomUUID().toString();
        OffsetDateTime savedAt = jdbcTemplate.queryForObject(
                """
                INSERT INTO trip_drafts (draft_id, user_id, status, draft_payload)
                VALUES (CAST(? AS UUID), ?, 'SAVED', CAST(? AS JSONB))
                RETURNING updated_at
                """,
                (resultSet, rowNumber) ->
                        resultSet.getObject("updated_at", OffsetDateTime.class),
                draftId,
                userId,
                writeDraft(draft)
        );
        return new StoredTripDraft(draftId, userId, copyMap(draft), savedAt);
    }

    @Override
    public Optional<StoredTripDraft> findById(String draftId, Long userId) {
        if (!isUuid(draftId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                """
                SELECT draft_id::TEXT AS draft_id,
                       user_id,
                       draft_payload::TEXT AS draft_payload,
                       updated_at
                FROM trip_drafts
                WHERE draft_id = CAST(? AS UUID)
                  AND user_id = ?
                  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                """,
                (resultSet, rowNumber) -> new StoredTripDraft(
                        resultSet.getString("draft_id"),
                        resultSet.getLong("user_id"),
                        readDraft(resultSet.getString("draft_payload")),
                        resultSet.getObject("updated_at", OffsetDateTime.class)
                ),
                draftId,
                userId
        ).stream().findFirst();
    }

    @Override
    public Optional<StoredTripDraft> update(String draftId, Long userId, Map<String, Object> draft) {
        if (!isUuid(draftId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                """
                UPDATE trip_drafts
                SET draft_payload = CAST(? AS JSONB),
                    status = 'SAVED'
                WHERE draft_id = CAST(? AS UUID)
                  AND user_id = ?
                  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                RETURNING updated_at
                """,
                (resultSet, rowNumber) -> new StoredTripDraft(
                        draftId,
                        userId,
                        copyMap(draft),
                        resultSet.getObject("updated_at", OffsetDateTime.class)
                ),
                writeDraft(draft),
                draftId,
                userId
        ).stream().findFirst();
    }

    private String writeDraft(Map<String, Object> draft) {
        try {
            return objectMapper.writeValueAsString(draft);
        } catch (Exception error) {
            throw new IllegalStateException("여행 초안을 JSON으로 변환하지 못했습니다.", error);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readDraft(String draftJson) {
        try {
            Object value = objectMapper.readValue(draftJson, Map.class);
            if (value instanceof Map<?, ?> map) {
                return copyMap((Map<String, Object>) map);
            }
            throw new IllegalStateException("저장된 여행 초안이 JSON 객체가 아닙니다.");
        } catch (Exception error) {
            throw new IllegalStateException("저장된 여행 초안을 읽지 못했습니다.", error);
        }
    }

    private boolean isUuid(String draftId) {
        try {
            UUID.fromString(draftId);
            return true;
        } catch (IllegalArgumentException | NullPointerException error) {
            return false;
        }
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        return new LinkedHashMap<>(source);
    }
}
