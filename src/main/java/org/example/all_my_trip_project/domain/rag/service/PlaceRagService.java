package org.example.all_my_trip_project.domain.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.rag.dto.RagSearchResult;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@Profile({"local-ai", "prod-ai-rag"})
@RequiredArgsConstructor
public class PlaceRagService {

    private static final int TOP_K = 3;
    /** 요청 수와 비용을 제어하기 위해 색인 문서를 96건 단위로 전송한다. */
    private static final int EMBEDDING_BATCH_SIZE = 96;
    /** 무료/체험 키의 429 연속 호출을 막고, 카카오 검색 기반 추천을 우선 유지한다. */
    private static final long RATE_LIMIT_COOLDOWN_MILLIS = 60_000L;
    private final PlaceDAO placeDAO;
    private final VectorStore vectorStore;
    private final AtomicLong rateLimitedUntilMillis = new AtomicLong();

    /**
     * {@code LOCAL_SEED} 장소도 추천 후보로 쓸지. local-ai 프로필의 seed 데이터는 "RAG 검색
     * 테스트용"이라는 문서화된 목적이 있는데, 아래 {@link #search}가 이를 무조건 걸러내
     * 실제로는 절대 추천되지 않았다 — 로컬에서 진짜 카드를 볼 방법이 없었던 원인이다.
     * prod-ai-rag에서는 기본값 false로 실서비스에 테스트 장소가 노출되지 않게 막고,
     * application-local-ai.properties에서만 true로 켠다.
     */
    @Value("${ai.rag.include-seed-places:false}")
    private boolean includeSeedPlaces;

    /** 관리자·개발자만 명시적으로 호출하는 전체 장소 재색인 작업이다. */
    public int reindexAllPlaces() {
        if (isRateLimited()) {
            log.warn("RAG reindex skipped while the embedding API is rate limited.");
            return 0;
        }
        List<Document> documents = placeDAO.findAll().stream()
                .map(this::toDocument)
                .toList();
        if (documents.isEmpty()) {
            return 0;
        }
        for (int start = 0; start < documents.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + EMBEDDING_BATCH_SIZE, documents.size());
            addDocuments(documents.subList(start, end));
        }
        return documents.size();
    }

    /** 외부 검색으로 새로 확인한 장소만 즉시 RAG에 추가한다. */
    public void indexPlaces(List<PlaceDTO> places) {
        if (places == null || places.isEmpty() || isRateLimited()) {
            return;
        }
        for (int start = 0; start < places.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + EMBEDDING_BATCH_SIZE, places.size());
            addDocuments(places.subList(start, end).stream().map(this::toDocument).toList());
        }
    }

    /**
     * 검색 장애는 AI 추천 자체를 막지 않는다. 호출자는 빈 결과를 기본 추천 흐름으로 사용한다.
     */
    public List<RagSearchResult> search(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        if (isRateLimited()) {
            log.debug("RAG place search skipped while the embedding API is rate limited.");
            return List.of();
        }
        try {
            return vectorStore.similaritySearch(SearchRequest.builder().query(question).topK(TOP_K).build()).stream()
                    .filter(document -> includeSeedPlaces
                            || !"LOCAL_SEED".equals(document.getMetadata().get("externalProvider")))
                    .map(document -> new RagSearchResult(
                            String.valueOf(document.getMetadata().getOrDefault("source", "place")),
                            document.getText(),
                            toLong(document.getMetadata().get("placeId")),
                            toNullableString(document.getMetadata().get("name")),
                            toNullableString(document.getMetadata().get("category")),
                            toNullableString(document.getMetadata().get("address")),
                            toNullableString(document.getMetadata().get("placeUrl"))))
                    .toList();
        } catch (Exception exception) {
            markRateLimited(exception);
            log.warn("RAG place search failed. Falling back to question-based recommendation.", exception);
            return List.of();
        }
    }

    public RagSearchResult toSearchResult(PlaceDTO place) {
        return new RagSearchResult(
                "place:" + place.getPlaceId(),
                toDocument(place).getText(),
                place.getPlaceId(),
                nullToEmpty(place.getName()),
                nullToEmpty(place.getCategory()),
                nullToEmpty(place.getAddress()),
                nullToEmpty(place.getWebsiteUrl())
        );
    }

    /**
     * 이미 화면에 표시한 실제 장소를 다시 추천할 때는 벡터 유사도 결과에 맡기지 않고
     * placeId로 동일한 장소 메타데이터를 복원한다.
     */
    public Optional<RagSearchResult> findByPlaceId(Long placeId) {
        if (placeId == null) {
            return Optional.empty();
        }
        return placeDAO.findById(placeId).map(this::toSearchResult);
    }

    private Document toDocument(PlaceDTO place) {
        Map<String, Object> metadata = Map.of(
                "source", "place:" + place.getPlaceId(),
                "externalProvider", nullToEmpty(place.getExternalProvider()),
                "placeId", place.getPlaceId(),
                "name", nullToEmpty(place.getName()),
                "region", nullToEmpty(place.getRegion()),
                "category", nullToEmpty(place.getCategory()),
                "address", nullToEmpty(place.getAddress()),
                "placeUrl", nullToEmpty(place.getWebsiteUrl())
        );
        return Document.builder()
                .id(UUID.nameUUIDFromBytes(("place:" + place.getPlaceId()).getBytes(StandardCharsets.UTF_8)).toString())
                .text("장소명: " + nullToEmpty(place.getName())
                        + "\n지역: " + nullToEmpty(place.getRegion())
                        + "\n카테고리: " + nullToEmpty(place.getCategory())
                        + "\n주소: " + nullToEmpty(place.getAddress())
                        + "\n설명: " + nullToEmpty(place.getDescription()))
                .metadata(metadata)
                .build();
    }

    private void addDocuments(List<Document> documents) {
        try {
            vectorStore.add(documents);
        } catch (RuntimeException exception) {
            markRateLimited(exception);
            throw exception;
        }
    }

    private boolean isRateLimited() {
        return System.currentTimeMillis() < rateLimitedUntilMillis.get();
    }

    private void markRateLimited(Exception exception) {
        if (exception.getMessage() != null && exception.getMessage().contains("status=429")) {
            rateLimitedUntilMillis.set(System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MILLIS);
            log.warn("Embedding API rate limit detected. RAG embedding calls will pause for {} seconds.",
                    RATE_LIMIT_COOLDOWN_MILLIS / 1_000);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String toNullableString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
