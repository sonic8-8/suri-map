package com.surimap.marker.photo.dto;

public record PhotoUploadUrlRequest(String contentType, long sizeBytes, String checksumSha256) {}
