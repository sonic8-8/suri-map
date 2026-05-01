package com.surimap.marker.notification.adapter;

import com.surimap.marker.notification.adapter.MockFcmDispatcher.CapturedDispatch;
import com.surimap.marker.notification.fixture.NotificationFixtures;
import com.surimap.marker.notification.port.FcmDispatcherPort.DispatchResult;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MockFcmDispatcher 테스트.
 *
 * <p>완료 기준: external FCM 호출 없이 mock notification dispatch가
 * recipient와 payload를 기록한다.</p>
 */
class MockFcmDispatcherTest {

    private MockFcmDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new MockFcmDispatcher();
    }

    // ════════════════════════════════════════════════════════
    //  기본 발송 기능
    // ════════════════════════════════════════════════════════

    @Nested
    @DisplayName("기본 발송")
    class BasicDispatch {

        @Test
        @DisplayName("발송 성공 시 recipient와 payload가 기록된다")
        void dispatchRecordsRecipientsAndPayload() {
            List<String> recipients = NotificationFixtures.SUPPORT_FCM_RECIPIENTS;
            Map<String, Object> payload = NotificationFixtures.supportRequestPayload();
            String eventId = NotificationFixtures.SUPPORT_EVENT_ID;

            DispatchResult result = dispatcher.send(recipients, payload, eventId);

            assertTrue(result.isFullySuccessful());
            assertEquals(3, result.successCount());
            assertEquals(0, result.failureCount());
            assertEquals(eventId, result.eventId());
        }

        @Test
        @DisplayName("발송 기록을 eventId로 조회할 수 있다")
        void canFindDispatchByEventId() {
            String eventId = NotificationFixtures.SUPPORT_EVENT_ID;
            dispatcher.send(
                    NotificationFixtures.SUPPORT_FCM_RECIPIENTS,
                    NotificationFixtures.supportRequestPayload(),
                    eventId
            );

            Optional<CapturedDispatch> found = dispatcher.findByEventId(eventId);

            assertTrue(found.isPresent());
            assertEquals(eventId, found.get().eventId());
            assertEquals(3, found.get().recipients().size());
        }

        @Test
        @DisplayName("존재하지 않는 eventId 조회 시 빈 결과")
        void findByEventIdReturnsEmptyForUnknown() {
            Optional<CapturedDispatch> found = dispatcher.findByEventId("unknown-event");
            assertTrue(found.isEmpty());
        }

        @Test
        @DisplayName("recipients가 null이면 예외")
        void nullRecipientsThrows() {
            assertThrows(NullPointerException.class, () ->
                    dispatcher.send(null, Map.of(), "evt-1"));
        }

        @Test
        @DisplayName("recipients가 비어있으면 예외")
        void emptyRecipientsThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    dispatcher.send(List.of(), Map.of(), "evt-1"));
        }
    }

    // ════════════════════════════════════════════════════════
    //  recipient 기반 조회
    // ════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Recipient 기반 조회")
    class RecipientQuery {

        @Test
        @DisplayName("특정 recipient가 수신한 발송 기록을 조회한다")
        void findByRecipient() {
            dispatcher.send(
                    NotificationFixtures.SUPPORT_FCM_RECIPIENTS,
                    NotificationFixtures.supportRequestPayload(),
                    NotificationFixtures.SUPPORT_EVENT_ID
            );

            List<CapturedDispatch> results = dispatcher.findByRecipient("fcm:dev-alpha-phone-01");

            assertEquals(1, results.size());
            assertTrue(results.get(0).wasDeliveredTo("fcm:dev-alpha-phone-01"));
        }

        @Test
        @DisplayName("제외된 디바이스는 수신 기록에 없다")
        void excludedDeviceNotInRecipients() {
            // 지원 배정: dev-support-cmd-phone-01은 제외 대상
            dispatcher.send(
                    NotificationFixtures.ASSIGNMENT_FCM_RECIPIENTS,
                    NotificationFixtures.assignmentPayload(),
                    NotificationFixtures.ASSIGNMENT_EVENT_ID
            );

            List<CapturedDispatch> results = dispatcher.findByRecipient("fcm:dev-support-cmd-phone-01");
            assertTrue(results.isEmpty(), "지휘 계정 디바이스는 FCM 수신 대상이 아니어야 한다");
        }
    }

    // ════════════════════════════════════════════════════════
    //  이벤트 타입 기반 조회
    // ════════════════════════════════════════════════════════

    @Nested
    @DisplayName("이벤트 타입 기반 조회")
    class EventTypeQuery {

        @Test
        @DisplayName("SUPPORT_REQUEST_CREATED 이벤트 타입으로 조회")
        void findBySupportRequestType() {
            dispatcher.send(
                    NotificationFixtures.SUPPORT_FCM_RECIPIENTS,
                    NotificationFixtures.supportRequestPayload(),
                    NotificationFixtures.SUPPORT_EVENT_ID
            );

            List<CapturedDispatch> results = dispatcher.findByEventType("SUPPORT_REQUEST_CREATED");
            assertEquals(1, results.size());
        }

        @Test
        @DisplayName("PERSON_FOUND 이벤트 타입으로 조회")
        void findByPersonFoundType() {
            dispatcher.send(
                    NotificationFixtures.PERSON_FOUND_FCM_RECIPIENTS,
                    NotificationFixtures.personFoundPayload(),
                    NotificationFixtures.PERSON_FOUND_EVENT_ID
            );

            List<CapturedDispatch> results = dispatcher.findByEventType("PERSON_FOUND");
            assertEquals(1, results.size());
        }

        @Test
        @DisplayName("INCIDENT_MEMBERSHIP_CHANGED 배정 알림 조회")
        void findByAssignmentType() {
            dispatcher.send(
                    NotificationFixtures.ASSIGNMENT_FCM_RECIPIENTS,
                    NotificationFixtures.assignmentPayload(),
                    NotificationFixtures.ASSIGNMENT_EVENT_ID
            );

            List<CapturedDispatch> results = dispatcher.findByEventType("INCIDENT_MEMBERSHIP_CHANGED");
            assertEquals(1, results.size());

            // PII 없음 검증
            assertEquals(false, results.get(0).getPayloadField("pii"));
        }
    }

    // ════════════════════════════════════════════════════════
    //  SC-08: 지원 요청 시나리오 전체 검증
    // ════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SC-08: 지원 요청 알림")
    class SupportRequestScenario {

        @Test
        @DisplayName("지원 요청 시 지휘라인 3명에게 올바른 payload로 발송된다")
        void supportRequestDispatchesToCommandLine() {
            DispatchResult result = dispatcher.send(
                    NotificationFixtures.SUPPORT_FCM_RECIPIENTS,
                    NotificationFixtures.supportRequestPayload(),
                    NotificationFixtures.SUPPORT_EVENT_ID
            );

            assertTrue(result.isFullySuccessful());
            assertEquals(3, result.successCount());

            CapturedDispatch capture = dispatcher.findByEventId(NotificationFixtures.SUPPORT_EVENT_ID).orElseThrow();

            // recipient 검증
            assertTrue(capture.wasDeliveredTo("fcm:dev-alpha-phone-01"));
            assertTrue(capture.wasDeliveredTo("fcm:dev-support-car-01"));
            assertTrue(capture.wasDeliveredTo("fcm:dev-support-phone-01"));

            // payload 검증
            assertEquals("SUPPORT_REQUEST_CREATED", capture.getPayloadField("type"));
            assertEquals(NotificationFixtures.SUPPORT_NOTIFICATION_ID, capture.getPayloadField("id"));
            assertEquals(NotificationFixtures.INCIDENT_ID, capture.getPayloadField("incidentId"));
            assertEquals("READY_FOR_FANOUT", capture.getPayloadField("status"));
            assertEquals(1, capture.getPayloadField("version"));
        }
    }

    // ════════════════════════════════════════════════════════
    //  SC-08: 실종자 발견 시나리오
    // ════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SC-08: 실종자 발견 알림")
    class PersonFoundScenario {

        @Test
        @DisplayName("실종자 발견 시 FCM 수신자 3명에게 high priority payload로 발송된다")
        void personFoundDispatchesToAllAssigned() {
            DispatchResult result = dispatcher.send(
                    NotificationFixtures.PERSON_FOUND_FCM_RECIPIENTS,
                    NotificationFixtures.personFoundPayload(),
                    NotificationFixtures.PERSON_FOUND_EVENT_ID
            );

            assertTrue(result.isFullySuccessful());

            CapturedDispatch capture = dispatcher.findByEventId(NotificationFixtures.PERSON_FOUND_EVENT_ID).orElseThrow();

            assertEquals("PERSON_FOUND", capture.getPayloadField("type"));
            assertEquals(NotificationFixtures.PERSON_FOUND_MARKER_ID, capture.getPayloadField("markerId"));
            assertEquals("READY_FOR_FANOUT", capture.getPayloadField("status"));
        }
    }

    // ════════════════════════════════════════════════════════
    //  Failure Injection (하네스 실패 주입)
    // ════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Failure Injection")
    class FailureInjection {

        @Test
        @DisplayName("failure injection 시 발송 실패를 반환하고 기록하지 않는다")
        void injectedFailureReturnsFailedResult() {
            String eventId = NotificationFixtures.SUPPORT_EVENT_ID;
            dispatcher.injectFailureFor(eventId);

            DispatchResult result = dispatcher.send(
                    NotificationFixtures.SUPPORT_FCM_RECIPIENTS,
                    NotificationFixtures.supportRequestPayload(),
                    eventId
            );

            assertFalse(result.isFullySuccessful());
            assertEquals(3, result.failureCount());
            assertEquals(0, result.successCount());
            assertTrue(dispatcher.hasNoDispatchFor(eventId),
                    "실패 주입 시 발송 기록이 남지 않아야 한다");
        }

        @Test
        @DisplayName("failure injection 해제 후 정상 발송된다")
        void clearInjectionAllowsNormalDispatch() {
            String eventId = NotificationFixtures.SUPPORT_EVENT_ID;
            dispatcher.injectFailureFor(eventId);
            dispatcher.clearFailureInjection(eventId);

            DispatchResult result = dispatcher.send(
                    NotificationFixtures.SUPPORT_FCM_RECIPIENTS,
                    NotificationFixtures.supportRequestPayload(),
                    eventId
            );

            assertTrue(result.isFullySuccessful());
            assertFalse(dispatcher.hasNoDispatchFor(eventId));
        }
    }

    // ════════════════════════════════════════════════════════
    //  리셋
    // ════════════════════════════════════════════════════════

    @Nested
    @DisplayName("리셋")
    class Reset {

        @Test
        @DisplayName("reset 후 모든 기록과 injection이 초기화된다")
        void resetClearsAll() {
            dispatcher.send(
                    NotificationFixtures.SUPPORT_FCM_RECIPIENTS,
                    NotificationFixtures.supportRequestPayload(),
                    NotificationFixtures.SUPPORT_EVENT_ID
            );
            dispatcher.injectFailureFor("some-event");

            dispatcher.reset();

            assertEquals(0, dispatcher.getDispatchCount());
            assertTrue(dispatcher.hasNoDispatchFor(NotificationFixtures.SUPPORT_EVENT_ID));
        }

        @Test
        @DisplayName("여러 시나리오 발송 후 카운트 정확")
        void multipleDispatchesCountCorrectly() {
            dispatcher.send(
                    NotificationFixtures.SUPPORT_FCM_RECIPIENTS,
                    NotificationFixtures.supportRequestPayload(),
                    NotificationFixtures.SUPPORT_EVENT_ID
            );
            dispatcher.send(
                    NotificationFixtures.PERSON_FOUND_FCM_RECIPIENTS,
                    NotificationFixtures.personFoundPayload(),
                    NotificationFixtures.PERSON_FOUND_EVENT_ID
            );
            dispatcher.send(
                    NotificationFixtures.ASSIGNMENT_FCM_RECIPIENTS,
                    NotificationFixtures.assignmentPayload(),
                    NotificationFixtures.ASSIGNMENT_EVENT_ID
            );

            assertEquals(3, dispatcher.getDispatchCount());
        }
    }
}
