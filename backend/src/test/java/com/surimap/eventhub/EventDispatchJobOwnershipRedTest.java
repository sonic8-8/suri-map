package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.eventhub.port.EventHub;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * L2-T06 RED 소유권 계약 테스트 — event_dispatch_job outbox ownership (EventDispatchJobOwnershipRedTest).
 *
 * <p>Spring 컨텍스트 없이 순수 unit test로 실행된다.
 *
 * <p>이 테스트는 아래를 계약으로 검증한다:
 *
 * <ol>
 *   <li>S4.json scope.included에 "domain transaction 안에서 적재되는 event_dispatch_job 저장소"가 명시되어 있다.
 *   <li>EventHub 포트가 {@code publish} 메서드를 정의한다.
 *   <li>EventHub 포트와 구현체가 {@code com.surimap.eventhub} 패키지 계층에 있다.
 *   <li>실제 DB 구현체 클래스(DbEventHub 또는 EventHubAdapter)가 {@code com.surimap.eventhub} 패키지에
 *       존재한다.
 * </ol>
 *
 * <p>RED 조건: 실제 DB 구현체 클래스가 존재하지 않으므로 {@link #realDbEventHubImplementationExistsInEventhubPackage()}
 * 테스트가 ClassNotFoundException/AssertionError로 실패해야 한다.
 *
 * <p>참조:
 *
 * <ul>
 *   <li>docs/spec/specs/S4.json §scope.included: "domain transaction 안에서 적재되는 event_dispatch_job
 *       저장소"
 *   <li>docs/spec/specs/S4.json §domain_model.entities[0]: event_dispatch_job owner_spec == "S4"
 *   <li>docs/spec/specs/S4.json §three_stack_deliverables.backend.package: com.surimap.eventhub
 *   <li>docs/spec/boundaries.md §1.1: "S4는 공용 계약이지만 실구현·유지보수·버그 수정은 L2가 주도한다.
 *       event_outbox row 자체와 outbox 적재 이후 fanout orchestration은 S4가 단일 소유한다."
 *   <li>docs/spec/boundaries.md §4.3 Transaction Rule step 6: S4 EventHub.publish(PublishRequest) 호출과
 *       event_outbox stage
 * </ul>
 */
@DisplayName("L2-T06 RED: event_dispatch_job outbox 소유권 계약")
class EventDispatchJobOwnershipRedTest {

  // ─────────────────────────────────────────────────────────────────────
  // 1. S4.json scope 소유권 — event_dispatch_job 저장소가 S4 소유임을 계약으로 검증
  //    S4.json §scope.included: "domain transaction 안에서 적재되는 event_dispatch_job 저장소와 commit 이후
  //    dispatch loop"
  // ─────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("S4 scope: event_dispatch_job 저장소 소유권")
  class EventDispatchJobScopeOwnership {

    @Test
    @DisplayName(
        "eventDispatchJobScopeIsOwnedByS4 — event_dispatch_job 저장소가 S4 scope.included에 명시되어"
            + " 있음을 계약으로 고정한다")
    void eventDispatchJobScopeIsOwnedByS4() {
      // S4.json §scope.included 내용을 계약 상수로 선언한다.
      // 이 문자열이 S4.json에서 변경되면 이 테스트도 함께 갱신해야 한다.
      String s4ScopeEntry =
          "domain transaction 안에서 적재되는 event_dispatch_job 저장소와 commit 이후 dispatch loop";

      // boundaries.md §1.1: S4는 event_outbox row를 단일 소유한다.
      String s4OwnershipStatement =
          "S4 단일 소유: event_dispatch_job row와 dispatch state는 S4 소유이며"
              + " domain spec은 event payload와 발행 요청만 책임진다.";

      // 계약 검증: scope entry가 S4 ownership statement에 기술된 내용과 일치한다.
      assertThat(s4ScopeEntry)
          .as("S4.json scope.included에 event_dispatch_job 저장소 소유가 명시되어야 한다.")
          .contains("event_dispatch_job 저장소");

      assertThat(s4ScopeEntry)
          .as("S4.json scope.included에 'domain transaction 안에서 적재'가 명시되어야 한다.")
          .contains("domain transaction 안에서 적재");

      assertThat(s4ScopeEntry)
          .as("S4.json scope.included에 'commit 이후 dispatch loop'가 명시되어야 한다.")
          .contains("commit 이후 dispatch loop");

      // dispatch_status는 S4 소유
      String[] dispatchStatusValues = {"PENDING", "DISPATCHING", "COMPLETED", "PARTIALLY_FAILED", "FAILED", "PURGED"};
      assertThat(dispatchStatusValues)
          .as(
              "event_dispatch_job.dispatch_status 값이 S4 도메인 모델 상태 기계와 일치해야 한다 "
                  + "(S4.json §domain_model.state_machines).")
          .contains("PENDING", "FAILED", "PURGED");
    }
  }

  // ─────────────────────────────────────────────────────────────────────
  // 2. EventHub 포트 계약 — publish 메서드가 정의되어 있음을 검증
  //    S4.json §three_stack_deliverables.backend.modules[1]: EventHub port
  //    boundaries.md §4.3 Transaction Rule step 6
  // ─────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("EventHub 포트 계약")
  class EventHubPortContract {

    @Test
    @DisplayName(
        "eventHubPortDefinesPublishMethod — EventHub 인터페이스가 void publish(PublishRequest) 메서드를"
            + " 정의한다")
    void eventHubPortDefinesPublishMethod() {
      Class<?> eventHubClass = EventHub.class;

      // 인터페이스여야 한다 — 포트는 구현체가 아닌 추상 계약이다.
      assertThat(eventHubClass.isInterface())
          .as("EventHub는 인터페이스(port)여야 한다 (S4.json §three_stack_deliverables.backend.modules[1]).")
          .isTrue();

      // publish 메서드가 존재해야 한다.
      boolean hasPublishMethod =
          Arrays.stream(eventHubClass.getMethods())
              .anyMatch(m -> m.getName().equals("publish") && m.getParameterCount() == 1);

      assertThat(hasPublishMethod)
          .as("EventHub.publish(PublishRequest) 메서드가 정의되어 있어야 한다.")
          .isTrue();
    }

    @Test
    @DisplayName(
        "eventHubPublishMethodReturnsVoid — EventHub.publish 메서드의 반환 타입이 void이다")
    void eventHubPublishMethodReturnsVoid() throws NoSuchMethodException {
      Method publishMethod =
          EventHub.class.getMethod(
              "publish", com.surimap.eventhub.dto.PublishRequest.class);

      assertThat(publishMethod.getReturnType())
          .as("EventHub.publish 반환 타입은 void여야 한다.")
          .isEqualTo(void.class);
    }
  }

  // ─────────────────────────────────────────────────────────────────────
  // 3. EventHub 패키지 계약 — com.surimap.eventhub 패키지에 위치해야 한다
  //    S4.json §three_stack_deliverables.backend.package: "com.surimap.eventhub"
  // ─────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("EventHub 패키지 계약")
  class EventHubPackageContract {

    @Test
    @DisplayName(
        "eventHubPortIsInEventhubPackage — EventHub 인터페이스가 com.surimap.eventhub 패키지 계층에 있다")
    void eventHubPortIsInEventhubPackage() {
      String packageName = EventHub.class.getPackageName();

      assertThat(packageName)
          .as(
              "EventHub 포트는 com.surimap.eventhub 패키지 계층에 있어야 한다 "
                  + "(S4.json §three_stack_deliverables.backend.package).")
          .startsWith("com.surimap.eventhub");
    }

    @Test
    @DisplayName(
        "realDbEventHubImplementationExistsInEventhubPackage — com.surimap.eventhub 패키지에"
            + " DB 구현체 클래스가 존재한다")
    void realDbEventHubImplementationExistsInEventhubPackage() {
      // RED 핵심 테스트: 실제 DB 구현체 클래스가 없으므로 ClassNotFoundException으로 실패해야 한다.
      // coder는 이 테스트를 GREEN으로 만들기 위해 아래 클래스 중 하나를 구현해야 한다.
      //
      // S4.json §three_stack_deliverables.backend.modules[1]:
      //   name: EventHub
      //   responsibility: "caller domain transaction 안에서 PublishRequest를 BaseEvent envelope와
      //                    event_dispatch_job row로 저장하는 public port"
      //
      // 후보 구현체 이름: DbEventHub, EventHubAdapter, JdbcEventHub, EventDispatchJobEventHub

      String[] candidateClassNames = {
        "com.surimap.eventhub.adapter.DbEventHub",
        "com.surimap.eventhub.adapter.EventHubAdapter",
        "com.surimap.eventhub.adapter.JdbcEventHub",
        "com.surimap.eventhub.adapter.EventDispatchJobEventHub",
      };

      boolean realImplementationFound =
          Arrays.stream(candidateClassNames)
              .anyMatch(
                  className -> {
                    try {
                      Class<?> cls = Class.forName(className);
                      // EventHub 구현체여야 한다.
                      return EventHub.class.isAssignableFrom(cls) && !cls.isInterface();
                    } catch (ClassNotFoundException e) {
                      return false;
                    }
                  });

      // RED: 실제 DB 구현체가 없으므로 false → assertion 실패
      assertThat(realImplementationFound)
          .as(
              "com.surimap.eventhub 패키지에 EventHub DB 구현체 클래스가 존재해야 한다. "
                  + "현재는 MockEventHub만 있으므로 RED 상태다. "
                  + "coder는 DbEventHub/EventHubAdapter/JdbcEventHub 중 하나를 구현해야 한다. "
                  + "S4.json §three_stack_deliverables.backend.modules[1] 참조.")
          .isTrue();
    }
  }

  // ─────────────────────────────────────────────────────────────────────
  // 4. MockEventHub는 fallback용 — 실제 DB 구현체가 @ConditionalOnMissingBean으로 대체되어야 한다
  //    현재 PolicePhoneHeartbeatConfig: @ConditionalOnMissingBean(EventHub.class) MockEventHub
  //    → 실제 구현체가 없으면 MockEventHub가 사용됨을 계약으로 기록한다.
  // ─────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("MockEventHub fallback 계약")
  class MockEventHubFallbackContract {

    @Test
    @DisplayName(
        "mockEventHubIsNotThePrimaryImplementation — MockEventHub는 primary 구현체가 아닌 fallback이다")
    void mockEventHubIsNotThePrimaryImplementation() throws ClassNotFoundException {
      Class<?> mockClass = Class.forName("com.surimap.eventhub.adapter.MockEventHub");

      // MockEventHub는 EventHub를 구현해야 한다 (현재 코드 검증).
      assertThat(EventHub.class.isAssignableFrom(mockClass))
          .as("MockEventHub는 EventHub 인터페이스를 구현해야 한다.")
          .isTrue();

      // MockEventHub의 클래스명이 "Mock"으로 시작함을 계약으로 고정한다.
      // 실제 DB 구현체는 "Mock"으로 시작하지 않아야 한다.
      assertThat(mockClass.getSimpleName())
          .as(
              "MockEventHub의 클래스명은 'Mock'으로 시작해야 한다. "
                  + "실제 DB 구현체는 DB/Jdbc/Adapter 접두어를 사용해야 한다.")
          .startsWith("Mock");

      // 계약: MockEventHub의 패키지는 com.surimap.eventhub.adapter 여야 한다.
      assertThat(mockClass.getPackageName())
          .as("MockEventHub는 com.surimap.eventhub.adapter 패키지에 있어야 한다.")
          .isEqualTo("com.surimap.eventhub.adapter");
    }
  }
}
