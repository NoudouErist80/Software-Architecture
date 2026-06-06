package com.vibe.mediaservice.service;

import com.vibe.common.exception.VibeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    @Value("${vibe.media.upload-dir:./media-uploads}")
    private String uploadDir;

    @Value("${vibe.media.base-url:http://localhost:8088/api/v1/media/files}")
    private String baseUrl;

    @Value("${vibe.media.max-size-bytes:104857600}") // 100MB default
    private long maxSizeBytes;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "image/heic", "image/heif");  // Apple/iPhone default photo format
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4", "video/quicktime", "video/webm", "video/3gpp");

    @Async("vibeTaskExecutor")
    public CompletableFuture<Map<String, Object>> uploadFile(
            MultipartFile file, String context, String userId) {
        try {
            validateFile(file);
            String fileId = UUID.randomUUID().toString();
            String extension = getExtension(Objects.requireNonNull(file.getOriginalFilename()));
            String filename = fileId + extension;

            Path dir = Paths.get(uploadDir, userId);
            Files.createDirectories(dir);
            Path filePath = dir.resolve(filename);
            file.transferTo(filePath.toFile());

            String fileUrl = baseUrl + "/" + userId + "/" + filename;

            log.info("Uploaded {} for user {} — context: {}", filename, userId, context);
            return CompletableFuture.completedFuture(Map.of(
                    "fileId",       fileId,
                    "url",          fileUrl,
                    "filename",     filename,
                    "contentType",  file.getContentType(),
                    "sizeBytes",    file.getSize(),
                    "uploadedAt",   Instant.now().toString()
            ));
        } catch (VibeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Upload failed: {}", e.getMessage());
            throw VibeException.internal("File upload failed: " + e.getMessage());
        }
    }

    public byte[] serveFile(String userId, String filename) throws IOException {
        Path filePath = Paths.get(uploadDir, userId, filename);
        if (!Files.exists(filePath)) throw VibeException.notFound("File");
        return Files.readAllBytes(filePath);
    }

    public String getContentType(String filename) {
        if (filename.matches(".*\\.(mp4|mov|webm|3gp)$")) return "video/mp4";
        if (filename.matches(".*\\.(jpg|jpeg)$")) return "image/jpeg";
        if (filename.matches(".*\\.png$")) return "image/png";
        if (filename.matches(".*\\.gif$")) return "image/gif";
        if (filename.matches(".*\\.webp$")) return "image/webp";
        if (filename.matches(".*\\.(heic|heif)$")) return "image/heic";
        return "application/octet-stream";
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) throw VibeException.badRequest("File is empty");
        if (file.getSize() > maxSizeBytes)
            throw VibeException.badRequest("File too large. Max size: 100MB");

        String contentType = file.getContentType();
        if (contentType == null ||
                (!ALLOWED_IMAGE_TYPES.contains(contentType) && !ALLOWED_VIDEO_TYPES.contains(contentType)))
            throw VibeException.badRequest("Unsupported file type: " + contentType);
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
