package com.surimap.incident.service;

import com.surimap.incident.repository.IncidentReadMapper;
import com.surimap.incident.repository.IncidentReadRows.AssignmentTargetRow;
import com.surimap.marker.notification.domain.NotificationRecipientPolicy;
import com.surimap.marker.notification.domain.NotificationRecipients;
import com.surimap.marker.notification.port.NotificationTargetPort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** S1-1 incident_assignment 기반 계정·알림 대상 read view. */
@Service
@Primary
public class IncidentAssignmentView implements NotificationTargetPort {

  private static final String SUPPORT_ASSIGNMENT = "SUPPORT_ASSIGNMENT";
  private static final String SUPPORT_REQUEST = "SUPPORT_REQUEST";
  private static final String PERSON_FOUND = "PERSON_FOUND";

  private final IncidentReadMapper incidentReadMapper;

  public IncidentAssignmentView(IncidentReadMapper incidentReadMapper) {
    this.incidentReadMapper = incidentReadMapper;
  }

  @Transactional(readOnly = true)
  public NotificationTargets notificationTargets(UUID incidentId, String targetPolicy) {
    Objects.requireNonNull(incidentId, "incidentId must not be null");
    String normalizedPolicy = normalizePolicy(targetPolicy);
    List<AssignmentTargetRow> rows =
        incidentReadMapper.findActiveAssignmentTargetsByIncidentId(incidentId);
    return targets(rows, predicateFor(normalizedPolicy));
  }

  @Override
  @Transactional(readOnly = true)
  public NotificationRecipients notificationTargets(
      UUID incidentId, NotificationRecipientPolicy recipientPolicy) {
    NotificationTargets targets = notificationTargets(incidentId, targetPolicyFor(recipientPolicy));
    return new NotificationRecipients(
        recipientPolicy, targets.accountIds(), targets.policePhoneIds());
  }

  private static String targetPolicyFor(NotificationRecipientPolicy recipientPolicy) {
    return switch (Objects.requireNonNull(recipientPolicy, "recipientPolicy must not be null")) {
      case COMMANDERS_AND_FIELD_COMMANDERS -> SUPPORT_REQUEST;
      case ALL_INCIDENT_ASSIGNED -> PERSON_FOUND;
    };
  }

  private static Predicate<AssignmentTargetRow> predicateFor(String targetPolicy) {
    return switch (targetPolicy) {
      case SUPPORT_ASSIGNMENT -> IncidentAssignmentView::isSupportTeamOrPatrolCar;
      case SUPPORT_REQUEST -> IncidentAssignmentView::isCommanderOrFieldCommander;
      case PERSON_FOUND -> row -> true;
      default -> throw new IllegalArgumentException("unsupported targetPolicy: " + targetPolicy);
    };
  }

  private static boolean isSupportTeamOrPatrolCar(AssignmentTargetRow row) {
    return "SUPPORT_UNIT".equals(row.getOrganizationType())
        && ("TEAM".equals(row.getAccountType()) || "PATROL_CAR".equals(row.getAccountType()));
  }

  private static boolean isCommanderOrFieldCommander(AssignmentTargetRow row) {
    return "COMMAND".equals(row.getAccountType())
        || "FIELD_COMMANDER".equals(row.getIncidentRole());
  }

  private static NotificationTargets targets(
      List<AssignmentTargetRow> rows, Predicate<AssignmentTargetRow> predicate) {
    Set<String> accountIds = new LinkedHashSet<>();
    Set<String> policePhoneIds = new LinkedHashSet<>();
    for (AssignmentTargetRow row : rows) {
      if (!predicate.test(row)) {
        continue;
      }
      accountIds.add(row.getAccountId());
      if (row.getPolicePhoneId() != null) {
        policePhoneIds.add(row.getPolicePhoneId());
      }
    }
    return new NotificationTargets(List.copyOf(accountIds), List.copyOf(policePhoneIds));
  }

  private static String normalizePolicy(String targetPolicy) {
    Objects.requireNonNull(targetPolicy, "targetPolicy must not be null");
    return targetPolicy.trim().toUpperCase(Locale.ROOT);
  }

  /** IncidentAssignmentView.notificationTargets 응답. */
  public record NotificationTargets(List<String> accountIds, List<String> policePhoneIds) {

    public NotificationTargets {
      accountIds = List.copyOf(Objects.requireNonNull(accountIds, "accountIds must not be null"));
      policePhoneIds =
          List.copyOf(Objects.requireNonNull(policePhoneIds, "policePhoneIds must not be null"));
    }
  }
}
