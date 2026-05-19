package com.surimap.eventhub.adapter;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * event_dispatch_job 테이블 MyBatis Mapper.
 *
 * <p>S4.json §domain_model.entities[0]: event_dispatch_job outbox row INSERT를 담당한다.
 * SQL 본문은 {@code mapper/event/EventDispatchJobMapper.xml}에 정의된다.
 */
@Mapper
public interface EventDispatchJobMapper {

  /**
   * event_dispatch_job row를 INSERT한다.
   *
   * <p>event_id는 UNIQUE constraint로 중복 publish를 방지한다.
   * S4.json duplicate_event_dedupe fixture: ux_event_dispatch_job_event_id UNIQUE.
   *
   * @param row INSERT할 row DTO
   */
  void insert(EventDispatchJobRow row);

  EventDispatchJobDispatchRecord claimById(
      @Param("id") UUID id, @Param("claimStatus") String claimStatus);

  List<EventDispatchJobDispatchRecord> claimPending(
      @Param("limit") int limit, @Param("claimStatus") String claimStatus);

  int markCompleted(@Param("id") UUID id, @Param("completedStatus") String completedStatus);

  int markFailed(@Param("id") UUID id, @Param("failedStatus") String failedStatus);
}
