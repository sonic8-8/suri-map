package com.surimap.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.surimap.offlinepackage.fixture.OfflinePackageInstallationFixtures;
import com.surimap.offlinepackage.fixture.OfflinePackageLocalWarningInputFixtures;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures;
import com.surimap.offlinepackage.query.OfflinePackageInstallationQuery;
import com.surimap.offlinepackage.query.OfflinePackageInstallationStatus;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** L6-T06B RED: S7 OfflinePackageInstallationQuery into S3-2 package_badge. */
@DisplayName("L6-T06B package_badge board assembly")
class PackageBadgeBoardAssemblyRedTest {

  private static final String SLOT = "package_badge";
  private static final String SOURCE_SPEC = "S7";
  private static final String SOURCE_HASH = "hash-s7-package-current";

  @Test
  @DisplayName("assembles byIncident status rows into package_badge with exact cursors")
  void assembles_by_incident_status_rows_into_package_badge_with_exact_cursors() {
    FakeOfflinePackageInstallationQuery query = new FakeOfflinePackageInstallationQuery();

    BoardDTO board = boardFromPackageBadgeRows(query);

    assertThat(query.requestedIncidentIds())
        .containsExactly(OfflinePackageManifestFixtures.INCIDENT_ID);
    assertThat(packageBadgeRows(board)).hasSize(4);
    assertThat(packageBadgeRows(board))
        .extracting(row -> row.get("packageStatus"))
        .containsExactly("READY", "PARTIAL", "STALE", "FAILED");

    BoardSourceRowCursor readyCursor = packageBadgeCursor(board, readyStatus().id());
    assertThat(readyCursor.status()).isEqualTo("READY");
    assertThat(readyCursor.version()).isEqualTo(OfflinePackageInstallationFixtures.VERSION);
    assertThat(readyCursor.sequence()).isEqualTo(OfflinePackageInstallationFixtures.SEQUENCE);
    assertThat(readyCursor.sourceSpec()).isEqualTo(SOURCE_SPEC);
    assertThat(readyCursor.sourceHash()).isEqualTo(SOURCE_HASH);
    assertThat(readyCursor.latestEventId()).isEqualTo(OfflinePackageInstallationFixtures.EVENT_ID);
  }

  @Test
  @DisplayName("package_badge payload exposes S3-2 package fields from S7 query rows")
  void package_badge_payload_exposes_s3_2_package_fields_from_s7_query_rows() {
    BoardDTO board = boardFromPackageBadgeRows(new FakeOfflinePackageInstallationQuery());

    assertThat(packageBadgeRow(board, readyStatus().id()).responseFields())
        .containsEntry("id", OfflinePackageInstallationFixtures.INSTALLATION_ID)
        .containsEntry("status", "READY")
        .containsEntry("version", (long) OfflinePackageInstallationFixtures.VERSION)
        .containsEntry("sequence", (long) OfflinePackageInstallationFixtures.SEQUENCE)
        .containsEntry("incidentId", OfflinePackageManifestFixtures.INCIDENT_ID)
        .containsEntry("policePhoneId", OfflinePackageManifestFixtures.POLICE_PHONE_ID)
        .containsEntry("manifestVersion", OfflinePackageManifestFixtures.MANIFEST_VERSION)
        .containsEntry("packageStatus", "READY")
        .containsEntry("readyForOfflineUse", true);
  }

  @Test
  @DisplayName("stale and failed package_badge rows carry S6 PACKAGE_MISSING warning input")
  void stale_and_failed_package_badge_rows_carry_s6_package_missing_warning_input() {
    BoardDTO board = boardFromPackageBadgeRows(new FakeOfflinePackageInstallationQuery());

    assertLocalWarningInput(board, "pkg-status-precinct-stale-001", "STALE", true);
    assertLocalWarningInput(board, "pkg-status-precinct-failed-001", "FAILED", true);
    assertLocalWarningInput(board, readyStatus().id(), "READY", false);
  }

  private static BoardDTO boardFromPackageBadgeRows(OfflinePackageInstallationQuery query) {
    return new BoardAssembler()
        .assemble(
            new BoardAssemblyRequest(
                OfflinePackageManifestFixtures.INCIDENT_ID,
                "board-response-package-badge-l6-t06b",
                3,
                OfflinePackageInstallationFixtures.SERVER_TS,
                OfflinePackageManifestFixtures.OP_ID,
                List.of(OfflinePackageManifestFixtures.OP_ID),
                OfflinePackageManifestFixtures.OVERALL_AREA_HASH,
                packageBadgeSourceRows(query)));
  }

  @SuppressWarnings("unchecked")
  private static List<BoardSourceRow> packageBadgeSourceRows(OfflinePackageInstallationQuery query) {
    Class<?> assemblerType = classForName("com.surimap.board.PackageBadgeBoardAssembler");
    Object assembler = instantiate(assemblerType, query);
    Method method = method(assemblerType, "sourceRowsByIncident", String.class);
    Object rows = invoke(method, assembler, OfflinePackageManifestFixtures.INCIDENT_ID);
    assertThat(rows).isInstanceOf(List.class);
    return (List<BoardSourceRow>) rows;
  }

  private static BoardSourceRowCursor packageBadgeCursor(BoardDTO board, String id) {
    return board.slotSources().get(SLOT).stream()
        .filter(cursor -> cursor.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing package_badge cursor: " + id));
  }

  private static BoardSlotRow packageBadgeRow(BoardDTO board, String id) {
    return board.slotRow(SLOT, id);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> packageBadgeRows(BoardDTO board) {
    return (List<Map<String, Object>>) board.slots().get(SLOT);
  }

  @SuppressWarnings("unchecked")
  private static void assertLocalWarningInput(
      BoardDTO board, String id, String packageStatus, boolean raised) {
    Map<String, Object> localWarningInput =
        (Map<String, Object>) packageBadgeRow(board, id).payload().get("localWarningInput");
    assertThat(localWarningInput)
        .containsEntry("warningType", OfflinePackageLocalWarningInputFixtures.WARNING_TYPE)
        .containsEntry("packageStatus", packageStatus)
        .containsEntry("manifestVersion", OfflinePackageManifestFixtures.MANIFEST_VERSION)
        .containsEntry("activeManifestVersion", OfflinePackageManifestFixtures.MANIFEST_VERSION)
        .containsEntry("raised", raised);
    assertThat(localWarningInput.get("reason"))
        .isEqualTo(
            raised
                ? OfflinePackageLocalWarningInputFixtures.RAISE_WHEN
                : OfflinePackageLocalWarningInputFixtures.CLEAR_WHEN);
  }

  private static OfflinePackageInstallationStatus readyStatus() {
    return statuses().get(0);
  }

  private static List<OfflinePackageInstallationStatus> statuses() {
    return List.of(
        new OfflinePackageInstallationStatus(
            OfflinePackageInstallationFixtures.INSTALLATION_ID,
            OfflinePackageManifestFixtures.INCIDENT_ID,
            OfflinePackageManifestFixtures.POLICE_PHONE_ID,
            "READY",
            OfflinePackageInstallationFixtures.VERSION,
            OfflinePackageInstallationFixtures.SEQUENCE,
            OfflinePackageManifestFixtures.MANIFEST_VERSION,
            true),
        new OfflinePackageInstallationStatus(
            "pkg-status-precinct-partial-001",
            OfflinePackageManifestFixtures.INCIDENT_ID,
            "dev-precinct-phone-02",
            "PARTIAL",
            2,
            OfflinePackageInstallationFixtures.SEQUENCE,
            OfflinePackageManifestFixtures.MANIFEST_VERSION,
            false),
        new OfflinePackageInstallationStatus(
            "pkg-status-precinct-stale-001",
            OfflinePackageManifestFixtures.INCIDENT_ID,
            "dev-precinct-phone-03",
            "STALE",
            4,
            OfflinePackageInstallationFixtures.SEQUENCE,
            OfflinePackageManifestFixtures.MANIFEST_VERSION,
            false),
        new OfflinePackageInstallationStatus(
            "pkg-status-precinct-failed-001",
            OfflinePackageManifestFixtures.INCIDENT_ID,
            "dev-precinct-phone-04",
            "FAILED",
            5,
            OfflinePackageInstallationFixtures.SEQUENCE,
            OfflinePackageManifestFixtures.MANIFEST_VERSION,
            false));
  }

  private static Class<?> classForName(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException exception) {
      fail("Missing L6-T06B board package_badge assembler: %s".formatted(className));
      throw new IllegalStateException(exception);
    }
  }

  private static Object instantiate(Class<?> type, OfflinePackageInstallationQuery query) {
    try {
      Constructor<?> constructor = type.getConstructor(OfflinePackageInstallationQuery.class);
      return constructor.newInstance(query);
    } catch (NoSuchMethodException exception) {
      fail("%s must accept OfflinePackageInstallationQuery".formatted(type.getName()));
    } catch (InstantiationException | IllegalAccessException exception) {
      fail("Cannot instantiate %s: %s".formatted(type.getName(), exception.getMessage()));
    } catch (InvocationTargetException exception) {
      fail("%s constructor failed: %s".formatted(type.getName(), exception.getCause()));
    }
    throw new IllegalStateException("unreachable");
  }

  private static Method method(Class<?> type, String methodName, Class<?>... parameterTypes) {
    try {
      return type.getMethod(methodName, parameterTypes);
    } catch (NoSuchMethodException exception) {
      fail("%s must expose %s(String)".formatted(type.getName(), methodName));
      throw new IllegalStateException(exception);
    }
  }

  private static Object invoke(Method method, Object target, Object... args) {
    try {
      return method.invoke(target, args);
    } catch (IllegalAccessException exception) {
      fail("Cannot call %s: %s".formatted(method.getName(), exception.getMessage()));
    } catch (InvocationTargetException exception) {
      fail("%s failed: %s".formatted(method.getName(), exception.getCause()));
    }
    throw new IllegalStateException("unreachable");
  }

  private static final class FakeOfflinePackageInstallationQuery
      implements OfflinePackageInstallationQuery {

    private final List<String> requestedIncidentIds = new ArrayList<>();

    @Override
    public List<OfflinePackageInstallationStatus> byIncident(String incidentId) {
      requestedIncidentIds.add(incidentId);
      return statuses();
    }

    private List<String> requestedIncidentIds() {
      return List.copyOf(requestedIncidentIds);
    }
  }
}
