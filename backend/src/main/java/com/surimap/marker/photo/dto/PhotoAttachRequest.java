package com.surimap.marker.photo.dto;

public record PhotoAttachRequest(
    long sizeBytes, String contentType, Integer width, Integer height, String checksumSha256) {}
