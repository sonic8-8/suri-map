package com.surimap.eventhub.adapter;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 전송 작업의 저장·조회 SQL. 트랜잭션 조율과 SSE 전송은 호출부가 담당한다. */
@Mapper
public interface EventDispatchJobMapper {

  void insert(EventDispatchJob job);

  EventDispatchJob findById(@Param("id") UUID id);

  EventDispatchJob findByIdForUpdate(@Param("id") UUID id);

  /** afterSequence는 제외하고 throughSequence까지 순번순으로 조회한다. 전송 완료 여부와 무관하다. */
  List<EventDispatchJob> findBySseSequenceRange(
      @Param("incidentId") UUID incidentId,
      @Param("afterSequence") long afterSequence,
      @Param("throughSequence") long throughSequence,
      @Param("limit") int limit);

  long countBySseSequenceRange(
      @Param("incidentId") UUID incidentId,
      @Param("afterSequence") long afterSequence,
      @Param("throughSequence") long throughSequence);

  EventDispatchJob findLatestSequencedIncidentClosedEvent(@Param("incidentId") UUID incidentId);

  /**
   * 순번이 없고 완료되지 않은 작업만 갱신한다. 갱신 건수가 0이면 배정되지 않은 것이다. 사건 카운터 증가와의 원자성·재호출 처리는 서비스 트랜잭션에서 조율해야 한다.
   */
  int assignSseSequenceIfAbsent(@Param("id") UUID id, @Param("sseSequence") long sseSequence);

  EventDispatchJob claimById(@Param("id") UUID id, @Param("claimStatus") String claimStatus);

  List<EventDispatchJob> claimPending(
      @Param("limit") int limit, @Param("claimStatus") String claimStatus);

  int requeueInterruptedJobs();

  int requeueFailedJobs();

  int markCompleted(@Param("id") UUID id, @Param("completedStatus") String completedStatus);

  int markFailed(@Param("id") UUID id, @Param("failedStatus") String failedStatus);
}
