package com.surimap.eventhub.stream;

public class GoneRefetchRequiredException extends RuntimeException {

  public GoneRefetchRequiredException() {
    super("gone_refetch_required");
  }
}
