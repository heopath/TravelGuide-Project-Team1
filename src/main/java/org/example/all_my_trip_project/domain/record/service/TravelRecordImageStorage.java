package org.example.all_my_trip_project.domain.record.service;

import jakarta.annotation.PostConstruct;
import org.example.all_my_trip_project.domain.record.policy.RecordPolicy;
import org.example.all_my_trip_project.global.exception.BusinessException;
import org.example.all_my_trip_project.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Profile("!ui")
class TravelRecordImageStorage {

    private static final Pattern FILE_NAME = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)");

    private final Path root;

    TravelRecordImageStorage(@Value("${travel.record.image-directory:./data/travel-record-images}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("여행 기록 사진 저장 폴더를 준비하지 못했습니다: " + root, exception);
        }
    }

    StoredImage store(Long travelRecordId, MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > RecordPolicy.MAX_IMAGE_FILE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_RECORD_IMAGE_FILE);
        }

        try {
            byte[] bytes = file.getBytes();
            ImageType type = detect(bytes);
            String fileName = UUID.randomUUID() + "." + type.extension;
            Path recordDirectory = recordDirectory(travelRecordId);
            Files.createDirectories(recordDirectory);

            Path temporary = Files.createTempFile(recordDirectory, ".upload-", ".tmp");
            try {
                Files.write(temporary, bytes);
                Files.move(temporary, recordDirectory.resolve(fileName), StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return new StoredImage(publicUrl(travelRecordId, fileName), fileName);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.RECORD_IMAGE_STORAGE_FAILED);
        }
    }

    TravelRecordImageContent load(Long travelRecordId, String fileName) {
        if (!isValidFileName(fileName)) {
            throw new BusinessException(ErrorCode.RECORD_IMAGE_NOT_FOUND);
        }
        Path file = resolveFile(travelRecordId, fileName);
        if (!Files.isRegularFile(file)) {
            throw new BusinessException(ErrorCode.RECORD_IMAGE_NOT_FOUND);
        }
        try {
            return new TravelRecordImageContent(Files.readAllBytes(file), contentType(fileName));
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.RECORD_IMAGE_STORAGE_FAILED);
        }
    }

    void deleteManaged(Long travelRecordId, String imageUrl) {
        String prefix = publicUrl(travelRecordId, "");
        if (imageUrl == null || !imageUrl.startsWith(prefix)) {
            return;
        }
        String fileName = imageUrl.substring(prefix.length());
        if (!isValidFileName(fileName)) {
            return;
        }
        try {
            Files.deleteIfExists(resolveFile(travelRecordId, fileName));
        } catch (IOException ignored) {
            // DB 변경은 성공할 수 있어야 한다. 남은 파일은 운영 정리 대상으로만 취급한다.
        }
    }

    String publicUrl(Long travelRecordId, String fileName) {
        return "/api/v1/travel-records/" + travelRecordId + "/images/files/" + fileName;
    }

    private Path recordDirectory(Long travelRecordId) {
        if (travelRecordId == null || travelRecordId < 1) {
            throw new BusinessException(ErrorCode.INVALID_RECORD_REQUEST);
        }
        Path directory = root.resolve(String.valueOf(travelRecordId)).normalize();
        if (!directory.startsWith(root)) {
            throw new BusinessException(ErrorCode.INVALID_RECORD_REQUEST);
        }
        return directory;
    }

    private Path resolveFile(Long travelRecordId, String fileName) {
        Path file = recordDirectory(travelRecordId).resolve(fileName).normalize();
        if (!file.startsWith(recordDirectory(travelRecordId))) {
            throw new BusinessException(ErrorCode.RECORD_IMAGE_NOT_FOUND);
        }
        return file;
    }

    private boolean isValidFileName(String fileName) {
        return fileName != null && FILE_NAME.matcher(fileName.toLowerCase(Locale.ROOT)).matches();
    }

    private String contentType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> throw new BusinessException(ErrorCode.RECORD_IMAGE_NOT_FOUND);
        };
    }

    private ImageType detect(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return new ImageType("jpg");
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return new ImageType("png");
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return new ImageType("webp");
        }
        throw new BusinessException(ErrorCode.INVALID_RECORD_IMAGE_FILE);
    }

    record StoredImage(String imageUrl, String fileName) {
    }

    private record ImageType(String extension) {
    }
}
