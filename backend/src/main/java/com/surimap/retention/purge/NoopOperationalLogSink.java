package com.surimap.retention.purge;

import org.springframework.stereotype.Component;

@Component
public class NoopOperationalLogSink implements OperationalLogSink {

  @Override
  public void append(OperationalLogEntry entry) {
    // Internal contract boundary; deployments may replace this with a durable security log sink.
  }
}
