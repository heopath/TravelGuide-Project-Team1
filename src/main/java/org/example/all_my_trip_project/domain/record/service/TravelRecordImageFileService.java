package org.example.all_my_trip_project.domain.record.service;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.record.dto.ReplaceRecordImagesRequest;
import org.example.all_my_trip_project.domain.record.dto.TravelRecordResponse;
import org.example.all_my_trip_project.domain.record.entity.TravelRecordEntity;
import org.example.all_my_trip_project.domain.record.entity.TravelRecordImageEntity;
import org.example.all_my_trip_project.domain.record.policy.RecordPolicy;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@Profile("!ui")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelRecordImageFileService {

    private final TravelRecordReader reader;
    private final TravelRecordValidator validator;
    private final TravelRecordImageReplacer imageReplacer;
    private final TravelRecordResponseMapper responseMapper;
    private final TravelRecordImageStorage storage;

    @Transactional
    public TravelRecordResponse upload(
            Long userId,
            Long travelRecordId,
            MultipartFile file,
            String altText,
            boolean cover
    ) {
        TravelRecordEntity record = reader.findOwned(userId, travelRecordId);
        List<TravelRecordImageEntity> existing = reader.findImages(travelRecordId);
        if (existing.size() >= RecordPolicy.MAX_IMAGE_COUNT
                || (altText != null && altText.length() > RecordPolicy.MAX_IMAGE_ALT_TEXT_LENGTH)) {
            throw new BusinessException(ErrorCode.INVALID_RECORD_REQUEST);
        }

        TravelRecordImageStorage.StoredImage stored = storage.store(travelRecordId, file);
        try {
            boolean makeCover = cover || existing.isEmpty();
            List<ReplaceRecordImagesRequest.ImageItem> items = new ArrayList<>(existing.size() + 1);
            for (TravelRecordImageEntity image : existing) {
                items.add(new ReplaceRecordImagesRequest.ImageItem(
                        image.getImageUrl(), image.getAltText(), image.getCover() && !makeCover));
            }
            items.add(new ReplaceRecordImagesRequest.ImageItem(
                    stored.imageUrl(), normalizeAltText(altText), makeCover));

            ReplaceRecordImagesRequest request = new ReplaceRecordImagesRequest(items);
            validator.validateImages(request);
            imageReplacer.replace(travelRecordId, request);
            return responseMapper.toResponse(record, reader.findImages(travelRecordId));
        } catch (RuntimeException exception) {
            storage.deleteManaged(travelRecordId, stored.imageUrl());
            throw exception;
        }
    }

    public TravelRecordImageContent load(Long viewerUserId, Long travelRecordId, String fileName) {
        reader.findAccessible(viewerUserId, travelRecordId);
        String expectedUrl = storage.publicUrl(travelRecordId, fileName);
        boolean attached = reader.findImages(travelRecordId).stream()
                .map(TravelRecordImageEntity::getImageUrl)
                .anyMatch(expectedUrl::equals);
        if (!attached) {
            throw new BusinessException(ErrorCode.RECORD_IMAGE_NOT_FOUND);
        }
        return storage.load(travelRecordId, fileName);
    }

    private String normalizeAltText(String altText) {
        if (altText == null || altText.isBlank()) {
            return null;
        }
        return altText.trim();
    }
}
