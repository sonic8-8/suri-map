package com.surimap.global.event;

/** 내부 이벤트 발행 입력의 공통 필드나 payload 형식이 올바르지 않을 때 발생한다. */
public class InvalidEventPublishRequestException extends RuntimeException {

  public InvalidEventPublishRequestException(String message) {
    super(message);
  }
}
