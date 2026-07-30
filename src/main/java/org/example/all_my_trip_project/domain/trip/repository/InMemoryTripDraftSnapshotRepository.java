package org.example.all_my_trip_project.domain.trip.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("ui")
public class InMemoryTripDraftSnapshotRepository implements TripDraftSnapshotRepository {

    private final Map<String, StoredTripDraft> drafts = new ConcurrentHashMap<>();

    @Override
    public StoredTripDraft create(Map<String, Object> draft) {
        String draftId = UUID.randomUUID().toString();
        StoredTripDraft stored = new StoredTripDraft(
                draftId,
                copyMap(draft),
                OffsetDateTime.now()
        );
        drafts.put(draftId, stored);
        return copy(stored);
    }

    @Override
    public Optional<StoredTripDraft> findById(String draftId) {
        return Optional.ofNullable(drafts.get(draftId)).map(this::copy);
    }

    @Override
    public Optional<StoredTripDraft> update(String draftId, Map<String, Object> draft) {
        if (!drafts.containsKey(draftId)) {
            return Optional.empty();
        }
        StoredTripDraft stored = new StoredTripDraft(
                draftId,
                copyMap(draft),
                OffsetDateTime.now()
        );
        drafts.put(draftId, stored);
        return Optional.of(copy(stored));
    }

    private StoredTripDraft copy(StoredTripDraft stored) {
        return new StoredTripDraft(
                stored.draftId(),
                copyMap(stored.draft()),
                stored.savedAt()
        );
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, copyValue(value)));
        return copy;
    }

    private Object copyValue(Object value) {
        if (value instanceof Map<?, ?> sourceMap) {
            Map<String, Object> copy = new LinkedHashMap<>();
            sourceMap.forEach((key, nestedValue) ->
                    copy.put(String.valueOf(key), copyValue(nestedValue)));
            return copy;
        }
        if (value instanceof List<?> sourceList) {
            List<Object> copy = new ArrayList<>(sourceList.size());
            sourceList.forEach(item -> copy.add(copyValue(item)));
            return copy;
        }
        return value;
    }
}
