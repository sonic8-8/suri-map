package com.surimap.retention.purge;

/** S1-3 internal operational/security log append contract. */
public interface OperationalLogSink {

  void append(OperationalLogEntry entry);

  static OperationalLogSink noop() {
    return entry -> {};
  }
}
