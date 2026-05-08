package com.surimap.eventhub.adapter;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * DB 기반 EventHub 실구현체.
 *
 * <p>S4.json §three_stack_deliverables.backend.modules[1]: EventHub port 실구현.
 * PublishRequest를 event_dispatch_job row로 caller domain transaction 안에서 원자적으로 저장한다.
 *
 * <p>AC-S4-01: domain row, event_dispatch_job row가 같은 eventId로 원자적으로 보존된다.
 * AC-S4-07: insert 실패 시 caller transaction이 rollback된다.
 *
 * <p>MockEventHub는 {@code PolicePhoneHeartbeatConfig}의 {@code @ConditionalOnMissingBean(EventHub.class)}
 * 조건으로 등록되므로, 이 빈이 존재하면 MockEventHub fallback은 비활성화된다.
 */
@Component
public class DbEventHub implements EventHub {

  private final EventDispatchJobMapper mapper;

  public DbEventHub(EventDispatchJobMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * PublishRequest를 event_dispatch_job 테이블에 INSERT한다.
   *
   * <p>caller의 트랜잭션에 참여하므로 caller rollback 시 row도 함께 취소된다 (AC-S4-07).
   * event_id UNIQUE constraint로 동일 eventId 중복 publish를 방지한다 (S4.json duplicate_event_dedupe).
   *
   * @param request 발행할 이벤트 요청
   */
  @Override
  public void publish(PublishRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(request.eventId(), "eventId must not be null");
    Objects.requireNonNull(request.incidentId(), "incidentId must not be null");
    Objects.requireNonNull(request.type(), "type must not be null");
    Objects.requireNonNull(request.payload(), "payload must not be null");

    mapper.insert(EventDispatchJobRow.from(request));
  }
}
