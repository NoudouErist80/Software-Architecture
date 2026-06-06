package com.vibe.mediaservice.controller;

import com.vibe.common.response.ApiResponse;
import com.vibe.mediaservice.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
@Tag(name = "Media", description = "VIBE media upload and serving — images, videos, status media")
public class MediaController {

    private final MediaService mediaService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload image or video (max 100MB)")
    public CompletableFuture<ResponseEntity<ApiResponse<Object>>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "general") String context,
            @RequestHeader("X-User-Id") String userId) {
        return mediaService.uploadFile(file, context, userId)
                .thenApply(result -> ResponseEntity.ok(ApiResponse.success("Upload successful", result)));
    }

    @GetMapping("/files/{userId}/{filename}")
    @Operation(summary = "Serve a media file")
    public ResponseEntity<byte[]> serveFile(
            @PathVariable String userId,
            @PathVariable String filename) throws IOException {
        byte[] data = mediaService.serveFile(userId, filename);
        String contentType = mediaService.getContentType(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                .body(data);
    }
}
