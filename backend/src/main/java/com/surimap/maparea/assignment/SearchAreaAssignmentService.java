package com.surimap.maparea.assignment;

/**
 * OP 담당 구역 배정 use case 포트 (S8, Phase 2).
 *
 * <p>기준 문서: docs/spec/specs/S8.json §api_contracts POST
 * /search-areas/{searchAreaId}/assignments.
 * 구현체는 S8 production adapter가 담당한다.
 */
public interface SearchAreaAssignmentService {

  SearchAreaAssignmentResponse assign(SearchAreaAssignmentServiceRequest request);
}
