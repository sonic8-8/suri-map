package com.surimap.offlinepackage.dto;

import org.springframework.http.MediaType;

public class TileBlobResponse {

  private final MediaType contentType;
  private final byte[] bytes;
  private final String contentEncoding;

  public TileBlobResponse(MediaType contentType, byte[] bytes) {
    this(contentType, bytes, null);
  }

  public TileBlobResponse(MediaType contentType, byte[] bytes, String contentEncoding) {
    this.contentType = contentType;
    this.bytes = bytes.clone();
    this.contentEncoding = contentEncoding;
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

  public String getContentEncoding() {
    return contentEncoding;
  }

  public String contentEncoding() {
    return contentEncoding;
  }
}
