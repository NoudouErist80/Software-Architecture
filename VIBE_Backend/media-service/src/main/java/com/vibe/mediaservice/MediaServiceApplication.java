package com.vibe.mediaservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * VIBE Media Service — File Upload and Storage
 *
 * Handles:
 * - Profile picture uploads
 * - Post image and video uploads
 * - Voice note uploads for messaging
 * - MinIO (S3-compatible) object storage
 * - Pre-signed URL generation for direct client upload
 * - File validation (type, size, content moderation hooks)
 *
 * @author TCHANGO NOUDOU JOSEPH
 */
@SpringBootApplication(scanBasePackages = {"com.vibe.mediaservice", "com.vibe.common"})
@EnableAsync
public class MediaServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MediaServiceApplication.class, args);
    }
}
