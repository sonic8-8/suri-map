package com.surimap.maparea.query;

import com.surimap.maparea.SearchAreaAssignmentMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SearchAreaAssignmentQueryService implements SearchAreaAssignmentQuery {

  private final SearchAreaAssignmentMapper mapper;

  public SearchAreaAssignmentQueryService(SearchAreaAssignmentMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<SearchAreaAssignmentRow> byOp(UUID opId) {
    return mapper.findActiveByOp(opId);
  }

  @Override
  public List<SearchAreaAssignmentRow> byArea(UUID searchAreaId) {
    return mapper.findByArea(searchAreaId);
  }
}
