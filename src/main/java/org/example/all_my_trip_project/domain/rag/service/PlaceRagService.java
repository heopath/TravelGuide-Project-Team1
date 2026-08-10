package org.example.all_my_trip_project.domain.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.rag.dto.RagSearchResult;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Profile({"local-ai", "prod-ai-rag"})
@RequiredArgsConstructor
public class PlaceRagService {

    private static final int TOP_K = 3;
    /** Cohere Embed v2 accepts at most 96 texts in one request. */
    private static final int EMBEDDING_BATCH_SIZE = 96;
    private final PlaceDAO placeDAO;
    private final VectorStore vectorStore;

    /** 관리자·개발자만 명시적으로 호출하는 전체 장소 재색인 작업이다. */
    public int reindexAllPlaces() {
        List<Document> documents = placeDAO.findAll().stream()
                .map(this::toDocument)
                .toList();
        if (documents.isEmpty()) {
            return 0;
        }
        for (int start = 0; start < documents.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + EMBEDDING_BATCH_SIZE, documents.size());
            vectorStore.add(documents.subList(start, end));
        }
        return documents.size();
    }

    /**
     * 검색 장애는 AI 추천 자체를 막지 않는다. 호출자는 빈 결과를 기본 추천 흐름으로 사용한다.
     */
    public List<RagSearchResult> search(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        try {
            return vectorStore.similaritySearch(SearchRequest.builder().query(question).topK(TOP_K).build()).stream()
                    .map(document -> new RagSearchResult(
                            String.valueOf(document.getMetadata().getOrDefault("source", "place")),
                            document.getText()))
                    .toList();
        } catch (Exception exception) {
            log.warn("RAG place search failed. Falling back to question-based recommendation.", exception);
            return List.of();
        }
    }

    private Document toDocument(PlaceDTO place) {
        Map<String, Object> metadata = Map.of(
                "source", "place:" + place.getPlaceId(),
                "placeId", place.getPlaceId(),
                "name", nullToEmpty(place.getName()),
                "region", nullToEmpty(place.getRegion()),
                "category", nullToEmpty(place.getCategory())
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

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
