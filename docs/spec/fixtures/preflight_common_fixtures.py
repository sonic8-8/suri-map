#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent
COMMON_FILE = ROOT / "common-fixtures.json"
PENDING_FILE = ROOT / "pending-confirmation.json"
README_FILE = ROOT / "README.md"

REQUIRED_CONFIRMED_SECTIONS = [
    "incidentSeed",
    "accountAliases",
    "gpsPath",
    "pathGuardFixtures",
    "geometryReference",
    "tileManifest",
    "offlinePackageUi",
    "networkScripts",
    "outboxSharedRules",
    "outboxReplay",
    "terminalStateRules",
    "objectStorageUpload",
    "markerAndNotification",
    "eventRegistry",
    "eventFanout",
    "ownerReferences",
    "boardAssembly",
    "commandFlow",
    "opTransition",
    "searchHistorySummary",
]

BOARD_REQUIRED_SLOTS = {
    "overall_search_area",
    "area",
    "path",
    "police_phone_freshness",
    "marker",
    "toast",
    "package_badge",
    "op_toggle",
    "op_history",
    "handover_memo",
    "handover_status",
    "search_history_summary",
    "incident_terminal",
}

GENERAL_STATUS_VALUES = {
    "ACTIVE",
    "ASSIGNED",
    "ONLINE",
    "OFFLINE",
    "RECOVERING",
    "VISIBLE",
    "STALE",
    "OPENED",
    "READY",
    "CLOSED",
    "MANIFEST_READY",
    "MANIFEST_FAILED_RETRYABLE",
    "PENDING_LOCAL",
    "PENDING_SEND",
    "SYNCED",
    "FAILED",
    "FAILED_RETRYABLE",
    "FAILED_FINAL",
    "FAILED_CORRUPT",
    "ATTACHED",
    "SNAPSHOT_CREATED",
    "UNAVAILABLE",
    "NEEDS_MEMO",
    "ENDED",
    "REQUESTED",
    "RECORDING",
    "SUCCEEDED",
    "SKIPPED",
    "RETRYING",
    "WAITING_FOR_SYNC",
    "DOWNLOADING",
}

S6_STATUS_VALUES = {
    "PENDING",
    "SENDING",
    "ACKED",
    "FAILED_RETRYABLE",
    "FAILED_FINAL",
    "PURGED",
}

HARNESS_STATUS_VALUES = {
    "PENDING_LOCAL",
    "PENDING_SEND",
    "SENDING",
    "SYNCED",
    "FAILED",
    "PURGED",
}

STATE_VALUES = {
    "STALE_REFETCH",
}

ERROR_VALUES = {
    "invalid_geometry",
    "gone_refetch_required",
    "clock_skew_exceeded",
    "stale_clock_sync",
    "idempotency_mismatch",
    "invalid_payload",
    "request_body_hash_mismatch",
    "write_conflict",
    "police_phone_required",
    "police_phone_not_registered",
    "police_phone_not_assigned",
    "incident_closed",
    "post_close_requeue_rejected",
    "network_unavailable",
    "timeout",
    "http_408",
    "http_429",
    "http_500",
}

FAILURE_KEYS = {
    "polygon-unclosed",
    "polygon-self-intersecting",
    "polygon-too-small-under-400m2",
    "point-null-nan",
    "manifest-expired",
    "manifest-overall-area-stale",
    "tile-404",
    "tile-timeout",
    "tile-checksum-mismatch",
    "tile-corrupt-blob",
    "upload-url-denied",
    "upload-timeout",
    "upload-500",
    "checksum-mismatch",
    "attach-missing-blob",
    "attach-duplicate",
}


def load_json(path: Path):
    with path.open("r", encoding="utf-8") as file:
        return json.load(file)


def ensure(condition: bool, message: str):
    if not condition:
        raise AssertionError(message)


def get_by_path(obj, path: str):
    current = obj
    for part in path.split("."):
        ensure(isinstance(current, dict) and part in current, f"path not found: {path}")
        current = current[part]
    return current


def parse_confirmed_catalog_ids(readme_path: Path):
    text = readme_path.read_text(encoding="utf-8")
    try:
        section = text.split("## Confirmed Catalog", 1)[1].split("## Pending Confirmation", 1)[0]
    except IndexError as error:
        raise AssertionError("README must contain Confirmed Catalog and Pending Confirmation sections") from error

    fixture_ids = []
    for raw_line in section.splitlines():
        line = raw_line.strip()
        if not line.startswith("|"):
            continue
        if line.startswith("|---") or "fixture ID" in line:
            continue
        columns = [col.strip() for col in line.strip("|").split("|")]
        if not columns:
            continue
        first_cell = columns[0]
        match = re.fullmatch(r"`(.+)`", first_cell)
        if match:
            fixture_ids.append(match.group(1))
    ensure(fixture_ids, "README Confirmed Catalog must contain fixture rows")
    return fixture_ids


def check_required_sections(common: dict, pending: dict):
    ensure(isinstance(common.get("meta"), dict), "common meta section missing")
    ensure(isinstance(common.get("catalogIndex"), list) and common["catalogIndex"], "catalogIndex must be a non-empty list")
    ensure(isinstance(common.get("usageRules"), dict), "common usageRules section missing")
    ensure(isinstance(common.get("confirmed"), dict), "common confirmed section missing")
    for section in REQUIRED_CONFIRMED_SECTIONS:
        ensure(section in common["confirmed"], f"confirmed.{section} section missing")
    ensure(isinstance(pending.get("meta"), dict), "pending meta section missing")
    ensure(isinstance(pending.get("pendingConfirmation"), list), "pendingConfirmation section missing")


def check_catalog(common: dict):
    readme_fixture_ids = parse_confirmed_catalog_ids(README_FILE)
    catalog = common["catalogIndex"]
    catalog_fixture_ids = []

    for item in catalog:
        ensure(isinstance(item, dict), "catalogIndex item must be an object")
        fixture_id = item.get("fixtureId")
        group = item.get("group")
        owner_path = item.get("ownerPath")
        uses_scenarios = item.get("usesScenarios")
        uses_lanes = item.get("usesLanes")

        ensure(isinstance(fixture_id, str) and fixture_id, "catalogIndex item must have fixtureId")
        ensure(isinstance(group, str) and group in common["confirmed"], f"catalogIndex group missing in confirmed: {group}")
        ensure(isinstance(owner_path, str) and owner_path, f"{fixture_id} must have ownerPath")
        ensure(isinstance(uses_scenarios, list) and uses_scenarios, f"{fixture_id} must have usesScenarios")
        ensure(isinstance(uses_lanes, list) and uses_lanes, f"{fixture_id} must have usesLanes")
        ensure(get_by_path(common["confirmed"], owner_path) == fixture_id, f"{fixture_id} ownerPath mismatch: {owner_path}")
        catalog_fixture_ids.append(fixture_id)

    duplicates = sorted({fixture_id for fixture_id in catalog_fixture_ids if catalog_fixture_ids.count(fixture_id) > 1})
    ensure(not duplicates, f"duplicate fixtureId found: {duplicates}")
    ensure(
        catalog_fixture_ids == readme_fixture_ids,
        f"README Confirmed Catalog and catalogIndex differ: README={readme_fixture_ids}, JSON={catalog_fixture_ids}",
    )


def check_pending_confirmation(pending: dict):
    seen_keys = set()
    for item in pending["pendingConfirmation"]:
        ensure(isinstance(item, dict), "pendingConfirmation item must be an object")
        key = item.get("key")
        status = item.get("status")
        owner = item.get("owner")
        blocking_scenarios = item.get("blockingScenarios")
        reason = item.get("reason")

        ensure(isinstance(key, str) and key, "pendingConfirmation item must have key")
        ensure(key not in seen_keys, f"duplicate pending key found: {key}")
        seen_keys.add(key)
        ensure(status == "CONFIRMATION_REQUIRED", f"unknown pending status for {key}: {status}")
        ensure(isinstance(owner, str) and owner, f"pending owner missing for {key}")
        ensure(isinstance(blocking_scenarios, list) and blocking_scenarios, f"pending blockingScenarios missing for {key}")
        ensure(isinstance(reason, str) and reason, f"pending reason missing for {key}")


def check_pending_guards(common: dict, pending: dict):
    common_text = json.dumps(common["confirmed"], ensure_ascii=False, sort_keys=True)
    for item in pending["pendingConfirmation"]:
        fragments = item.get("forbiddenConfirmedKeyFragments", [])
        ensure(isinstance(fragments, list), f"forbiddenConfirmedKeyFragments must be a list for {item['key']}")
        for fragment in fragments:
            ensure(fragment not in common_text, f"pending fragment leaked into confirmed data: {fragment}")


def validate_status_string(key: str, value: str, invalid_statuses: list, invalid_states: list):
    key_lower = key.lower()
    if key == "s6Statuses":
        if value not in S6_STATUS_VALUES:
            invalid_statuses.append((key, value))
        return
    if key == "harnessStatuses":
        if value not in HARNESS_STATUS_VALUES:
            invalid_statuses.append((key, value))
        return
    if key_lower.endswith("state"):
        if value not in STATE_VALUES and value not in GENERAL_STATUS_VALUES:
            invalid_states.append((key, value))
        return
    if key_lower.endswith("status"):
        if value not in GENERAL_STATUS_VALUES:
            invalid_statuses.append((key, value))


def traverse_status_and_error(obj, invalid_statuses, invalid_states, invalid_errors, invalid_failures):
    if isinstance(obj, dict):
        for key, value in obj.items():
            if key == "statusMapping":
                ensure(isinstance(value, dict), "statusMapping must be an object")
                for harness_status, s6_statuses in value.items():
                    if harness_status not in HARNESS_STATUS_VALUES:
                        invalid_statuses.append((key, harness_status))
                    for s6_status in s6_statuses.split("|"):
                        if s6_status not in S6_STATUS_VALUES:
                            invalid_statuses.append((key, s6_status))
                continue

            if key == "failureDisplayPolicy":
                ensure(isinstance(value, dict), "failureDisplayPolicy must be an object")
                for errors in value.values():
                    ensure(isinstance(errors, list), "failureDisplayPolicy values must be lists")
                    for error_code in errors:
                        if error_code not in ERROR_VALUES:
                            invalid_errors.append((key, error_code))
                continue

            if key == "failureKeys":
                ensure(isinstance(value, list), "failureKeys must be a list")
                for failure_key in value:
                    if failure_key not in FAILURE_KEYS:
                        invalid_failures.append((key, failure_key))
                continue

            if isinstance(value, list):
                if key in {"s6Statuses", "harnessStatuses"}:
                    for item in value:
                        validate_status_string(key, item, invalid_statuses, invalid_states)
                else:
                    traverse_status_and_error(value, invalid_statuses, invalid_states, invalid_errors, invalid_failures)
                continue

            if isinstance(value, str):
                if key in {"ready", "failedRetryable", "failedCorrupt", "online", "offline", "recovering"}:
                    if value not in GENERAL_STATUS_VALUES:
                        invalid_statuses.append((key, value))
                validate_status_string(key, value, invalid_statuses, invalid_states)
                if (key == "throws" or "error" in key.lower()) and value not in ERROR_VALUES:
                    invalid_errors.append((key, value))

            traverse_status_and_error(value, invalid_statuses, invalid_states, invalid_errors, invalid_failures)
    elif isinstance(obj, list):
        for value in obj:
            traverse_status_and_error(value, invalid_statuses, invalid_states, invalid_errors, invalid_failures)


def check_status_and_error_strings(common: dict):
    invalid_statuses = []
    invalid_states = []
    invalid_errors = []
    invalid_failures = []

    traverse_status_and_error(common["confirmed"], invalid_statuses, invalid_states, invalid_errors, invalid_failures)

    ensure(not invalid_statuses, f"unknown status values: {invalid_statuses}")
    ensure(not invalid_states, f"unknown state values: {invalid_states}")
    ensure(not invalid_errors, f"unknown error values: {invalid_errors}")
    ensure(not invalid_failures, f"unknown failure keys: {invalid_failures}")


def check_board_coverage(common: dict):
    board = common["confirmed"]["boardAssembly"]
    slot_rows = board.get("slotRows")
    ensure(isinstance(slot_rows, list) and slot_rows, "boardAssembly.slotRows must be a non-empty list")
    actual_slots = {row.get("slot") for row in slot_rows}
    ensure(actual_slots == BOARD_REQUIRED_SLOTS, f"boardAssembly slot coverage mismatch: {sorted(actual_slots)}")


def check_event_cross_references(common: dict):
    confirmed = common["confirmed"]
    event_ids = {
        event["eventId"]
        for event in confirmed["eventRegistry"].values()
        if isinstance(event, dict) and "eventId" in event
    }

    refs = [
        confirmed["outboxReplay"]["sc05PathReplay"]["expectedEventId"],
        confirmed["outboxReplay"]["sc06MarkerPhotoReplay"]["expectedEventId"],
        confirmed["outboxReplay"]["sc08SupportRequestReplay"]["expectedEventId"],
        confirmed["markerAndNotification"]["markerPhotoAttach"]["eventId"],
        confirmed["markerAndNotification"]["supportAssignmentNotification"]["eventId"],
        confirmed["markerAndNotification"]["supportRequestNotification"]["eventId"],
        confirmed["markerAndNotification"]["personFoundNotification"]["eventId"],
        confirmed["objectStorageUpload"]["expectedAttachOutcome"]["eventId"],
        confirmed["searchHistorySummary"]["expectedS4Events"]["handoverMemoCreated"]["eventId"],
        confirmed["searchHistorySummary"]["expectedS4Events"]["aiSummaryReady"]["eventId"],
        confirmed["eventFanout"]["sc08EmptyFcmRecipientSkip"]["eventId"],
        confirmed["eventFanout"]["sc09OutboxReplayConvergence"]["eventId"],
        confirmed["eventFanout"]["duplicateEventDedupe"]["eventId"],
        confirmed["eventFanout"]["closedAndPurgedStream"]["closedIncident"]["terminalEventId"],
        confirmed["eventFanout"]["fanoutFailureInjection"]["eventId"],
    ]

    refs.extend(row["latestEventId"] for row in confirmed["boardAssembly"]["slotRows"])
    for ref in refs:
        ensure(ref in event_ids, f"referenced eventId is missing from eventRegistry: {ref}")


def check_id_cross_references(common: dict):
    confirmed = common["confirmed"]
    owner_refs = confirmed["ownerReferences"]

    checks = [
        ("path replay incident", confirmed["outboxReplay"]["sc05PathReplay"]["incidentId"], confirmed["incidentSeed"]["incidentId"]),
        ("path replay policePhone", confirmed["outboxReplay"]["sc05PathReplay"]["policePhoneId"], confirmed["gpsPath"]["policePhoneId"]),
        ("marker replay marker", confirmed["outboxReplay"]["sc06MarkerPhotoReplay"]["markerId"], confirmed["markerAndNotification"]["markerPhotoAttach"]["markerId"]),
        ("marker replay photo", confirmed["outboxReplay"]["sc06MarkerPhotoReplay"]["photoId"], confirmed["objectStorageUpload"]["photoId"]),
        ("support replay marker", confirmed["outboxReplay"]["sc08SupportRequestReplay"]["markerId"], confirmed["markerAndNotification"]["supportRequestNotification"]["markerId"]),
        ("support replay notification", confirmed["outboxReplay"]["sc08SupportRequestReplay"]["notificationDeliveryId"], confirmed["markerAndNotification"]["supportRequestNotification"]["notificationDeliveryId"]),
        ("command incident", confirmed["commandFlow"]["incidentId"], confirmed["incidentSeed"]["incidentId"]),
        ("command current op", confirmed["commandFlow"]["opId"], confirmed["opTransition"]["currentOp"]["opId"]),
        ("command area", confirmed["commandFlow"]["areaId"], owner_refs["areaId"]),
        ("command next op", confirmed["commandFlow"]["nextOpId"], confirmed["opTransition"]["newOp"]["opId"]),
        ("command handover memo", confirmed["commandFlow"]["handoverMemoId"], confirmed["incidentSeed"]["seedIds"]["memoId"]),
        ("summary memo payload", confirmed["searchHistorySummary"]["memoId"], confirmed["searchHistorySummary"]["expectedS4Events"]["handoverMemoCreated"]["payloadId"]),
        ("summary id payload", confirmed["searchHistorySummary"]["summaryId"], confirmed["searchHistorySummary"]["expectedS4Events"]["aiSummaryReady"]["payloadId"]),
        ("summary handover op", confirmed["searchHistorySummary"]["expectedS4Events"]["handoverMemoCreated"]["opId"], confirmed["opTransition"]["newOp"]["opId"]),
        ("summary ai op", confirmed["searchHistorySummary"]["expectedS4Events"]["aiSummaryReady"]["opId"], confirmed["opTransition"]["newOp"]["opId"]),
        ("board overall_search_area", confirmed["boardAssembly"]["slotRows"][0]["sourceResponseId"], owner_refs["overallSearchAreaResponseId"]),
        ("board area", confirmed["boardAssembly"]["slotRows"][1]["sourceResponseId"], owner_refs["areaId"]),
        ("board path", confirmed["boardAssembly"]["slotRows"][2]["sourceResponseId"], confirmed["gpsPath"]["pathId"]),
        ("board freshness", confirmed["boardAssembly"]["slotRows"][3]["sourceResponseId"], confirmed["gpsPath"]["policePhoneId"]),
        ("board marker", confirmed["boardAssembly"]["slotRows"][4]["sourceResponseId"], confirmed["incidentSeed"]["seedIds"]["markerId"]),
        ("board toast", confirmed["boardAssembly"]["slotRows"][5]["sourceResponseId"], owner_refs["supportRequestResponseId"]),
        ("board package badge", confirmed["boardAssembly"]["slotRows"][6]["sourceResponseId"], owner_refs["packageBadgeResponseId"]),
        ("board op_toggle", confirmed["boardAssembly"]["slotRows"][7]["sourceResponseId"], confirmed["opTransition"]["currentOp"]["opId"]),
        ("board op_history", confirmed["boardAssembly"]["slotRows"][8]["sourceResponseId"], confirmed["opTransition"]["newOp"]["opId"]),
        ("board handover memo", confirmed["boardAssembly"]["slotRows"][9]["sourceResponseId"], confirmed["incidentSeed"]["seedIds"]["memoId"]),
        ("board handover status", confirmed["boardAssembly"]["slotRows"][10]["sourceResponseId"], owner_refs["handoverStatusResponseId"]),
        ("board search history summary", confirmed["boardAssembly"]["slotRows"][11]["sourceResponseId"], owner_refs["boardSearchHistorySummaryResponseId"]),
        ("board incident terminal", confirmed["boardAssembly"]["slotRows"][12]["sourceResponseId"], confirmed["incidentSeed"]["incidentId"]),
        ("board tombstone", confirmed["boardAssembly"]["slotRows"][12]["tombstoneResponseId"], owner_refs["terminalTombstoneResponseId"]),
    ]

    for label, actual, expected in checks:
        ensure(actual == expected, f"{label} mismatch: {actual} != {expected}")


def check_internal_consistency(common: dict):
    confirmed = common["confirmed"]

    path_replay = confirmed["outboxReplay"]["sc05PathReplay"]
    ensure(path_replay["sourceSpecEndpoint"] == "POST /search-paths/batch", "unexpected sourceSpecEndpoint for path replay")
    ensure(path_replay["canonicalApiEndpoint"] == "POST /api/search-paths/batch", "unexpected canonicalApiEndpoint for path replay")

    event_ids = {
        event["eventId"]
        for event in confirmed["eventRegistry"].values()
        if isinstance(event, dict) and "eventId" in event
    }
    ensure(confirmed["markerAndNotification"]["markerPhotoAttach"]["eventId"] in event_ids, "markerPhotoAttach eventId missing")
    ensure(confirmed["objectStorageUpload"]["expectedAttachOutcome"]["eventId"] == confirmed["markerAndNotification"]["markerPhotoAttach"]["eventId"], "SC-06 attach event mismatch")
    ensure(confirmed["offlinePackageUi"]["manifestDownloadPending"]["manifestId"] == confirmed["tileManifest"]["manifestId"], "offline package manifestId mismatch")
    ensure(confirmed["opTransition"]["currentOpConsumerContract"]["currentOp"]["opId"] == confirmed["opTransition"]["currentOp"]["opId"], "currentOp consumer contract mismatch")
    ensure(confirmed["opTransition"]["idempotentWriteReplay"]["sameBodyReplay"]["expectedResponseId"] == confirmed["opTransition"]["newOp"]["opId"], "S8 idempotent replay response mismatch")
    ensure(confirmed["terminalStateRules"]["sc12CloseRequeue"]["expectedPostClose"] == "FAILED_FINAL", "unexpected SC-12 post-close status")
    ensure(confirmed["searchHistorySummary"]["negativeOnlyInputs"]["forbiddenPhraseResponse"]["expectedDisplayStatus"] == "UNAVAILABLE", "SC-11 negative fixture display status mismatch")

    tile_manifest = confirmed["tileManifest"]
    ensure("overallAreaHash" not in tile_manifest, "tileManifest.overallAreaHash must stay pending until resolved")
    ensure("sc09PackageReplay" not in confirmed["outboxReplay"], "SC-09 package replay must stay pending until resolved")


def main():
    common = load_json(COMMON_FILE)
    pending = load_json(PENDING_FILE)

    check_required_sections(common, pending)
    check_catalog(common)
    check_pending_confirmation(pending)
    check_pending_guards(common, pending)
    check_status_and_error_strings(common)
    check_board_coverage(common)
    check_event_cross_references(common)
    check_id_cross_references(common)
    check_internal_consistency(common)

    print("common fixture preflight passed")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as error:
        print(f"preflight failed: {error}", file=sys.stderr)
        sys.exit(1)
