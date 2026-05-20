package com.mock112.controller;

import com.mock112.photo.MissingPersonPhotoUploadResult;
import com.mock112.photo.MissingPersonPhotoUploadService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MissingPersonPhotoController {

    private final MissingPersonPhotoUploadService uploadService;

    public MissingPersonPhotoController(MissingPersonPhotoUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(
            path = "/mock-112/missing-person-photos",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@RequestPart("file") MultipartFile file) {
        try {
            MissingPersonPhotoUploadResult result = uploadService.upload(file);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("objectKey", result.objectKey());
            body.put("photoUrl", result.photoUrl());
            body.put("storageUri", result.storageUri());
            body.put("contentType", result.contentType());
            body.put("sizeBytes", result.sizeBytes());
            body.put("message", "실종자 사진이 MinIO에 저장되었습니다.");
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", "invalid_photo",
                            "message", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "object_storage_unavailable",
                            "message", exception.getMessage()));
        }
    }
}
