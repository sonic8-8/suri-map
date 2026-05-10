package com.surimap.sync.outbox;

import org.springframework.http.HttpStatus;

class OutboxRequeueApiException extends RuntimeException {

  private final HttpStatus status;
  private final String error;
  private final OutboxRequeueErrorResponse response;

  OutboxRequeueApiException(HttpStatus status, String error) {
    this(status, new OutboxRequeueErrorResponse(error, null, false, null, null));
  }

  OutboxRequeueApiException(HttpStatus status, OutboxRequeueErrorResponse response) {
    this(status, response.error(), response);
  }

  private OutboxRequeueApiException(
      HttpStatus status, String error, OutboxRequeueErrorResponse response) {
    super(error);
    this.status = status;
    this.error = error;
    this.response = response;
  }

  HttpStatus getStatus() {
    return status;
  }

  String getError() {
    return error;
  }

  OutboxRequeueErrorResponse getResponse() {
    return response;
  }
}
