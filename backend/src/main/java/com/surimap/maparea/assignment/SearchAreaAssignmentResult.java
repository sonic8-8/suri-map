package com.surimap.maparea.assignment;

import com.surimap.maparea.query.SearchAreaAssignmentRow;

/**
 * SearchAreaAssignmentCommand 포트 응답 record (S8, Phase 2).
 *
 * <p>newAssignment row와 발행된 이벤트를 함께 반환한다.
 */
public record SearchAreaAssignmentResult(
    SearchAreaAssignmentRow newAssignment,
    SearchAreaAssignmentChangedEvent publishedEvent) {}
