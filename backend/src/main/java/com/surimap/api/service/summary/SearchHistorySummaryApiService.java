package com.surimap.api.service.summary;

import com.surimap.api.controller.summary.response.SearchHistorySummaryItemResponse;
import com.surimap.api.controller.summary.response.SearchHistorySummaryListResponse;
import com.surimap.summary.SearchHistorySummaryMapper;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SearchHistorySummaryApiService {

  private final SearchHistorySummaryMapper mapper;

  public SearchHistorySummaryApiService(SearchHistorySummaryMapper mapper) {
    this.mapper = mapper;
  }

  public SearchHistorySummaryListResponse list(
      UUID opId, UUID incidentId, String scopeType, UUID scopeId, UUID dutyShiftId, String status) {
    return new SearchHistorySummaryListResponse(
        mapper.findByOp(opId, incidentId, scopeType, scopeId, dutyShiftId, status).stream()
            .map(SearchHistorySummaryItemResponse::from)
            .toList());
  }
}
