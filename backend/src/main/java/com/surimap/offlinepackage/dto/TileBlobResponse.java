package com.surimap.offlinepackage.dto;

import org.springframework.http.MediaType;

public class TileBlobResponse {

  private final MediaType contentType;
  private final byte[] bytes;

  public TileBlobResponse(MediaType contentType, byte[] bytes) {
    this.contentType = contentType;
    this.bytes = bytes.clone();
  }

  public MediaType getContentType() {
    return contentType;
  }

  public MediaType contentType() {
    return contentType;
  }

  public byte[] getBytes() {
    return bytes.clone();
  }

  public byte[] bytes() {
    return bytes.clone();
  }
}
