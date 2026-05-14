package com.surimap.marker.service;

import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.dto.MarkerListResponse;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.query.MarkerQuery;
import com.surimap.marker.query.MarkerQueryFilters;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarkerReadService {

  private final MarkerQuery markerQuery;

  public MarkerReadService(MarkerQuery markerQuery) {
    this.markerQuery = markerQuery;
  }

  @Transactional(readOnly = true)
  public MarkerListResponse list(UUID incidentId, UUID opId, String type, String status) {
    return MarkerListResponse.from(
        markerQuery.byIncident(
            incidentId, new MarkerQueryFilters(opId, parseType(type), parseStatus(status))));
  }

  private MarkerType parseType(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return MarkerType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw invalidFilter();
    }
  }

  private MarkerStatus parseStatus(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return MarkerStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw invalidFilter();
    }
  }

  private MarkerApiException invalidFilter() {
    return new MarkerApiException("invalid_marker_filter", HttpStatus.BAD_REQUEST);
  }
}
