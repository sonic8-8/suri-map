package com.surimap.harness.sc02;

import com.surimap.marker.notification.adapter.MockFcmDispatcher;
import com.surimap.marker.notification.adapter.MockFcmDispatcher.CapturedDispatch;
import com.surimap.marker.notification.fixture.NotificationFixtures;
import com.surimap.marker.notification.repository.MarkerNotificationRecord;
import com.surimap.marker.notification.repository.MarkerNotificationRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Test-local SC-02 support assignment FCM evidence harness runner. */
public class Sc02SupportAssignmentFcmHarnessRunner {

  private static final String SCENARIO_ID = "SC-02";
  private static final String EVENT_TYPE = "INCIDENT_ASSIGNMENT_CHANGED";
  private static final String EVENT_STATUS = "ACTIVE";
  private static final int EVENT_VERSION = 2;
  private static final String SOURCE_ENTITY_TYPE = "incident_assignment";
  private static final String MARKER_NOTIFICATION_TABLE = "marker_notification";
  private static final String SUPPORT_TEAM_ID = "team-support-bravo";

  public Sc02SupportAssignmentFcmHarnessRunner() {}

  public ScenarioEvidence runSupportAssignmentFcmEvidence() {
    HarnessContext harness = HarnessContext.create();
    int markerRowsBefore = harness.markerNotifications.count();

    AssignmentChangedEvent event = harness.assignmentImport.applySupportAssignment();
    AssignmentRecipients recipients = harness.recipientResolver.resolve(event);
    Map<String, Object> payload = harness.payloadFactory.assignmentPayload(event, recipients);
    harness.fanout.dispatch(event, recipients, payload);

    CapturedDispatch capture =
        harness
            .dispatcher
            .findByEventId(NotificationFixtures.ASSIGNMENT_EVENT_ID)
            .orElseThrow(Sc02SupportAssignmentFcmHarnessRunner::fcmMockMissing);

    return new ScenarioEvidence(
        SCENARIO_ID,
        NotificationFixtures.INCIDENT_ID,
        EventEvidence.from(event),
        FcmEvidence.from(harness.dispatcher, capture, recipients),
        PayloadEvidence.from(payload),
        MarkerNotificationEvidence.from(harness.markerNotifications, markerRowsBefore));
  }

  public void runMockFcmMissingFailureInjection() {
    HarnessContext harness = HarnessContext.create();
    harness.dispatcher.injectFailureFor(NotificationFixtures.ASSIGNMENT_EVENT_ID);

    AssignmentChangedEvent event = harness.assignmentImport.applySupportAssignment();
    AssignmentRecipients recipients = harness.recipientResolver.resolve(event);
    Map<String, Object> payload = harness.payloadFactory.assignmentPayload(event, recipients);
    harness.fanout.dispatch(event, recipients, payload);
  }

  private static IllegalStateException fcmMockMissing() {
    return new IllegalStateException(
        "fcmMockMissing: "
            + NotificationFixtures.ASSIGNMENT_EVENT_ID
            + " "
            + EVENT_TYPE
            + " has recipients but MockFcmDispatcher captured no matching payload");
  }

  private static final class HarnessContext {

    private final AssignmentStore assignmentStore = new AssignmentStore();
    private final ActiveFcmTokenStore tokenStore = new ActiveFcmTokenStore();
    private final MarkerNotificationTableSpy markerNotifications = new MarkerNotificationTableSpy();
    private final MockFcmDispatcher dispatcher = new MockFcmDispatcher();
    private final Mock112AssignmentImport assignmentImport =
        new Mock112AssignmentImport(assignmentStore);
    private final AssignmentRecipientResolver recipientResolver =
        new AssignmentRecipientResolver(assignmentStore, tokenStore);
    private final AssignmentPayloadFactory payloadFactory = new AssignmentPayloadFactory();
    private final AssignmentFanout fanout = new AssignmentFanout(dispatcher, markerNotifications);

    static HarnessContext create() {
      return new HarnessContext();
    }
  }

  private static final class Mock112AssignmentImport {

    private final AssignmentStore assignmentStore;

    private Mock112AssignmentImport(AssignmentStore assignmentStore) {
      this.assignmentStore = assignmentStore;
    }

    AssignmentChangedEvent applySupportAssignment() {
      List<IncidentAssignmentRow> added = assignmentStore.addSupportAssignments();
      return new AssignmentChangedEvent(
          NotificationFixtures.ASSIGNMENT_EVENT_ID,
          EVENT_TYPE,
          NotificationFixtures.INCIDENT_ID,
          EVENT_STATUS,
          EVENT_VERSION,
          SOURCE_ENTITY_TYPE,
          added.stream().map(IncidentAssignmentRow::accountId).toList());
    }
  }

  private static final class AssignmentStore {

    private final List<IncidentAssignmentRow> rows = new ArrayList<>();

    private AssignmentStore() {
      rows.add(
          new IncidentAssignmentRow(
              "ia-precinct-cmd-001",
              "11111111-1111-1111-1111-111111110001",
              "dev-precinct-cmd-phone-01",
              "team-precinct-jongno",
              "COMMAND",
              true,
              false));
      rows.add(
          new IncidentAssignmentRow(
              "ia-precinct-car-001",
              "11111111-1111-1111-1111-111111110002",
              "dev-precinct-car-01",
              "team-precinct-jongno",
              "PATROL_CAR",
              true,
              false));
      rows.add(
          new IncidentAssignmentRow(
              "ia-precinct-team-001",
              "11111111-1111-1111-1111-111111110003",
              "dev-precinct-phone-01",
              "team-precinct-jongno",
              "TEAM",
              true,
              false));
      rows.add(
          new IncidentAssignmentRow(
              "ia-precinct-alpha-cmd-001",
              "11111111-1111-1111-1111-111111110004",
              "dev-alpha-cmd-phone-01",
              "team-missing-alpha",
              "COMMAND",
              true,
              false));
      rows.add(
          new IncidentAssignmentRow(
              "ia-precinct-alpha-team-001",
              "11111111-1111-1111-1111-111111110005",
              "dev-alpha-phone-01",
              "team-missing-alpha",
              "TEAM",
              true,
              false));
    }

    List<IncidentAssignmentRow> addSupportAssignments() {
      List<IncidentAssignmentRow> supportRows =
          List.of(
              new IncidentAssignmentRow(
                  "ia-precinct-support-cmd-001",
                  "11111111-1111-1111-1111-111111110006",
                  "dev-support-cmd-phone-01",
                  SUPPORT_TEAM_ID,
                  "COMMAND",
                  true,
                  true),
              new IncidentAssignmentRow(
                  "ia-precinct-support-car-001",
                  "11111111-1111-1111-1111-111111110007",
                  "dev-support-car-01",
                  SUPPORT_TEAM_ID,
                  "PATROL_CAR",
                  true,
                  true),
              new IncidentAssignmentRow(
                  "ia-precinct-support-team-001",
                  "11111111-1111-1111-1111-111111110008",
                  "dev-support-phone-01",
                  SUPPORT_TEAM_ID,
                  "TEAM",
                  true,
                  true));
      rows.addAll(supportRows);
      return supportRows;
    }

    List<IncidentAssignmentRow> newlyAssignedSupportRows() {
      return rows.stream()
          .filter(row -> row.newlyAssigned() && row.active() && SUPPORT_TEAM_ID.equals(row.teamId()))
          .toList();
    }
  }

  private record IncidentAssignmentRow(
      String assignmentId,
      String accountId,
      String policePhoneId,
      String teamId,
      String accountType,
      boolean active,
      boolean newlyAssigned) {}

  private static final class ActiveFcmTokenStore {

    private final Map<String, String> tokensByPolicePhone =
        Map.of(
            "dev-support-cmd-phone-01",
            "fcm:dev-support-cmd-phone-01",
            "dev-support-car-01",
            "fcm:dev-support-car-01",
            "dev-support-phone-01",
            "fcm:dev-support-phone-01",
            "dev-alpha-phone-01",
            "fcm:dev-alpha-phone-01");

    boolean hasActiveToken(String policePhoneId) {
      return tokensByPolicePhone.containsKey(policePhoneId);
    }

    String tokenFor(String policePhoneId) {
      return tokensByPolicePhone.get(policePhoneId);
    }
  }

  private static final class AssignmentRecipientResolver {

    private final AssignmentStore assignments;
    private final ActiveFcmTokenStore tokens;

    private AssignmentRecipientResolver(AssignmentStore assignments, ActiveFcmTokenStore tokens) {
      this.assignments = assignments;
      this.tokens = tokens;
    }

    AssignmentRecipients resolve(AssignmentChangedEvent event) {
      Objects.requireNonNull(event, "event must not be null");
      List<IncidentAssignmentRow> supportRows = assignments.newlyAssignedSupportRows();
      List<IncidentAssignmentRow> androidRows =
          supportRows.stream()
              .filter(row -> !"COMMAND".equals(row.accountType()))
              .filter(row -> tokens.hasActiveToken(row.policePhoneId()))
              .toList();
      List<String> excludedPhoneIds =
          supportRows.stream()
              .filter(row -> "COMMAND".equals(row.accountType()))
              .filter(row -> tokens.hasActiveToken(row.policePhoneId()))
              .map(IncidentAssignmentRow::policePhoneId)
              .toList();
      return new AssignmentRecipients(
          NotificationFixtures.ASSIGNMENT_RECIPIENT_POLICY,
          androidRows.stream().map(row -> tokens.tokenFor(row.policePhoneId())).toList(),
          androidRows.stream().map(IncidentAssignmentRow::accountId).toList(),
          androidRows.stream().map(IncidentAssignmentRow::policePhoneId).toList(),
          supportRows.stream().map(IncidentAssignmentRow::accountId).toList(),
          supportRows.stream().map(IncidentAssignmentRow::policePhoneId).toList(),
          excludedPhoneIds);
    }
  }

  private record AssignmentRecipients(
      String recipientPolicy,
      List<String> fcmRecipients,
      List<String> recipientAccountIds,
      List<String> recipientPolicePhoneIds,
      List<String> changedAccountIds,
      List<String> activePolicePhoneIds,
      List<String> excludedPolicePhoneIds) {}

  private static final class AssignmentPayloadFactory {

    Map<String, Object> assignmentPayload(
        AssignmentChangedEvent event, AssignmentRecipients recipients) {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("type", event.type());
      payload.put("id", event.id());
      payload.put("incidentId", event.id());
      payload.put("status", event.status());
      payload.put("version", event.version());
      payload.put("changedAccountIds", recipients.changedAccountIds());
      payload.put("recipientPolicy", recipients.recipientPolicy());
      payload.put("recipientAccountIds", recipients.recipientAccountIds());
      payload.put("recipientPolicePhoneIds", recipients.recipientPolicePhoneIds());
      payload.put("pii", false);
      return payload;
    }
  }

  private static final class AssignmentFanout {

    private final MockFcmDispatcher dispatcher;
    private final MarkerNotificationTableSpy markerNotifications;

    private AssignmentFanout(
        MockFcmDispatcher dispatcher, MarkerNotificationTableSpy markerNotifications) {
      this.dispatcher = dispatcher;
      this.markerNotifications = markerNotifications;
    }

    void dispatch(
        AssignmentChangedEvent event, AssignmentRecipients recipients, Map<String, Object> payload) {
      int markerRowsBefore = markerNotifications.count();
      var result = dispatcher.send(recipients.fcmRecipients(), payload, event.eventId());
      if (!result.isFullySuccessful() || dispatcher.hasNoDispatchFor(event.eventId())) {
        throw fcmMockMissing();
      }
      if (markerNotifications.count() != markerRowsBefore || markerNotifications.insertAttempted()) {
        throw new IllegalStateException("assignment FCM must not create marker_notification rows");
      }
    }
  }

  private static final class MarkerNotificationTableSpy implements MarkerNotificationRepository {

    private int rows;
    private boolean insertAttempted;

    @Override
    public int insertIfAbsent(MarkerNotificationRecord record) {
      insertAttempted = true;
      rows++;
      return 1;
    }

    int count() {
      return rows;
    }

    boolean insertAttempted() {
      return insertAttempted;
    }
  }

  private record AssignmentChangedEvent(
      String eventId,
      String type,
      String id,
      String status,
      int version,
      String sourceEntityType,
      List<String> changedAccountIds) {}

  public record ScenarioEvidence(
      String scenarioId,
      String incidentId,
      EventEvidence event,
      FcmEvidence fcm,
      PayloadEvidence payload,
      MarkerNotificationEvidence markerNotification) {}

  public record EventEvidence(
      String eventId,
      String type,
      String id,
      String status,
      int version,
      String sourceEntityType,
      List<String> changedAccountIds) {

    static EventEvidence from(AssignmentChangedEvent event) {
      return new EventEvidence(
          event.eventId(),
          event.type(),
          event.id(),
          event.status(),
          event.version(),
          event.sourceEntityType(),
          event.changedAccountIds());
    }
  }

  public record FcmEvidence(
      String dispatcher,
      int dispatchCount,
      String capturedEventId,
      List<String> recipients,
      List<String> recipientAccountIds,
      List<String> recipientPolicePhoneIds,
      List<String> excludedPolicePhoneIds,
      List<String> activePolicePhoneIds,
      boolean commandPolicePhoneExcluded) {

    static FcmEvidence from(
        MockFcmDispatcher dispatcher, CapturedDispatch capture, AssignmentRecipients recipients) {
      return new FcmEvidence(
          "MockFcmDispatcher",
          dispatcher.getDispatchCount(),
          capture.eventId(),
          capture.recipients(),
          capture.recipientAccountIds(),
          capture.recipientPolicePhoneIds(),
          recipients.excludedPolicePhoneIds(),
          recipients.activePolicePhoneIds(),
          recipients.excludedPolicePhoneIds().stream()
              .noneMatch(phone -> capture.recipients().contains("fcm:" + phone)));
    }
  }

  public record PayloadEvidence(
      String type,
      String incidentId,
      String status,
      int version,
      String recipientPolicy,
      boolean pii,
      Map<String, Object> data) {

    static PayloadEvidence from(Map<String, Object> payload) {
      return new PayloadEvidence(
          String.valueOf(payload.get("type")),
          String.valueOf(payload.get("incidentId")),
          String.valueOf(payload.get("status")),
          ((Number) payload.get("version")).intValue(),
          String.valueOf(payload.get("recipientPolicy")),
          Boolean.TRUE.equals(payload.get("pii")),
          payload);
    }
  }

  public record MarkerNotificationEvidence(
      String table, int rowsBefore, int rowsAfter, boolean created) {

    static MarkerNotificationEvidence from(MarkerNotificationTableSpy spy, int rowsBefore) {
      return new MarkerNotificationEvidence(
          MARKER_NOTIFICATION_TABLE, rowsBefore, spy.count(), spy.insertAttempted());
    }
  }
}
