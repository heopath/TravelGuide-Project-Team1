package org.example.all_my_trip_project.domain.rag.service;

import org.example.all_my_trip_project.domain.place.dao.PlaceDAO;
import org.example.all_my_trip_project.domain.place.dto.PlaceDTO;
import org.example.all_my_trip_project.domain.ai.service.AiModelException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceRagServiceTest {

    private final PlaceDAO placeDAO = mock(PlaceDAO.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final PlaceRagService service = new PlaceRagService(placeDAO, vectorStore);

    @Test
    void indexesPlacesWithUuidDocumentIds() {
        PlaceDTO place = PlaceDTO.builder()
                .placeId(12L).name("광안리 해수욕장").region("부산").category("ATTRACTION")
                .address("부산 수영구").description("해변 산책 장소").build();
        when(placeDAO.findAll()).thenReturn(List.of(place));

        int indexed = service.reindexAllPlaces();

        assertThat(indexed).isEqualTo(1);
        org.mockito.ArgumentCaptor<List<Document>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        Document document = captor.getValue().getFirst();
        assertThat(document.getId()).matches("[0-9a-f-]{36}");
        assertThat(document.getText()).contains("광안리 해수욕장", "부산");
    }

    @Test
    void indexesMoreThanNinetySixPlacesInSeparateEmbeddingBatches() {
        List<PlaceDTO> places = IntStream.rangeClosed(1, 97)
                .mapToObj(index -> PlaceDTO.builder()
                        .placeId((long) index)
                        .name("Place " + index)
                        .region("Busan")
                        .category("CAFE")
                        .build())
                .toList();
        when(placeDAO.findAll()).thenReturn(places);

        assertThat(service.reindexAllPlaces()).isEqualTo(97);

        org.mockito.ArgumentCaptor<List<Document>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(2)).add(captor.capture());
        assertThat(captor.getAllValues()).extracting(List::size).containsExactly(96, 1);
    }

    @Test
    void returnsEmptyResultsWhenVectorSearchFails() {
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenThrow(new IllegalStateException("vector store unavailable"));

        assertThat(service.search("부산 해변 추천")).isEmpty();
    }

    @Test
    void pausesFurtherRagCallsAfterEmbeddingRateLimit() {
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenThrow(new AiModelException("OpenAI embedding request failed. status=429"));

        assertThat(service.search("서귀포 식당 추천")).isEmpty();
        assertThat(service.search("다른 식당 추천")).isEmpty();

        verify(vectorStore, times(1)).similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class));
    }

    @Test
    void convertsSearchDocumentsToPromptSafeResults() {
        Document document = Document.builder().id("11111111-1111-1111-1111-111111111111")
                .text("장소명: 광안리 해수욕장").metadata("source", "place:12").build();
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenReturn(List.of(document));

        var results = service.search("부산 해변 추천");

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.source()).isEqualTo("place:12");
            assertThat(result.content()).contains("광안리");
        });
    }

    @Test
    void excludesLocalSeedDocumentsFromUserFacingSearchResults() {
        Document localSeed = Document.builder().id("11111111-1111-1111-1111-111111111111")
                .text("synthetic place")
                .metadata(Map.of("source", "place:1", "externalProvider", "LOCAL_SEED"))
                .build();
        Document verified = Document.builder().id("22222222-2222-2222-2222-222222222222")
                .text("verified place")
                .metadata(Map.of("source", "place:2", "externalProvider", "KAKAO"))
                .build();
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenReturn(List.of(localSeed, verified));

        assertThat(service.search("Seongsu cafe")).containsExactly(
                new org.example.all_my_trip_project.domain.rag.dto.RagSearchResult("place:2", "verified place")
        );
    }

    @Test
    void restoresVerifiedPlaceMetadataByPlaceIdForTimeAdjustment() {
        PlaceDTO place = PlaceDTO.builder()
                .placeId(65L).name("서울명예도로 끼리끼리3길").category("ATTRACTION")
                .address("서울 마포구 연남동 255-30")
                .websiteUrl("https://place.map.kakao.com/894873893")
                .build();
        when(placeDAO.findById(65L)).thenReturn(Optional.of(place));

        var result = service.findByPlaceId(65L);

        assertThat(result).hasValueSatisfying(found -> {
            assertThat(found.placeId()).isEqualTo(65L);
            assertThat(found.placeName()).isEqualTo("서울명예도로 끼리끼리3길");
            assertThat(found.placeUrl()).isEqualTo("https://place.map.kakao.com/894873893");
        });
    }
}
