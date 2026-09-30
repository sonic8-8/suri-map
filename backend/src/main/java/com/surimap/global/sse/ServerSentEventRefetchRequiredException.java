package com.surimap.global.sse;

public class ServerSentEventRefetchRequiredException extends RuntimeException {

  public ServerSentEventRefetchRequiredException() {
    super("gone_refetch_required");
  }
}
