package org.example.all_my_trip_project.domain.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"local-ai", "prod-ai-rag"})
@ConditionalOnProperty(prefix = "ai.rag", name = "reindex-on-startup", havingValue = "true")
@RequiredArgsConstructor
public class PlaceRagIndexingRunner implements ApplicationRunner {

    private final PlaceRagService placeRagService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("RAG place indexing completed. indexed={}", placeRagService.reindexAllPlaces());
        } catch (Exception exception) {
            log.warn("RAG place indexing failed. The server will continue without updated RAG data.", exception);
        }
    }
}
