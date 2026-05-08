package com.surimap.eventhub.validation;

/**
 * BaseEvent envelope 검증 실패 시 발생하는 예외.
 *
 * <p>boundaries.md §9.1 BaseEvent envelope 검증 위반을 나타낸다.
 */
public class InvalidEventEnvelopeException extends RuntimeException {

  public InvalidEventEnvelopeException(String message) {
    super(message);
  }
}
