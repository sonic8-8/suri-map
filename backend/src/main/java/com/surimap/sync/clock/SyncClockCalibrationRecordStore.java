package com.surimap.sync.clock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class SyncClockCalibrationRecordStore {

  private final Map<String, SyncClockResponse> records = new ConcurrentHashMap<>();

  public void record(String incidentId, SyncClockResponse response) {
    records.put(incidentId, response);
  }
}

