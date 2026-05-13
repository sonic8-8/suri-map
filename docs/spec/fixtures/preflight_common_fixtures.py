#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent
COMMON_FILE = ROOT / "common-fixtures.json"
PENDING_FILE = ROOT / "pending-confirmation.json"
README_FILE = ROOT / "README.md"
BOUNDARIES_FILE = ROOT.parent / "boundaries.md"
S7_SPEC_FILE = ROOT.parent / "specs" / "S7.json"
MOCK112_SEED_FILE = ROOT.parent.parent.parent / "mock-112" / "src" / "main" / "resources" / "seed" / "precinct-first-scenario.json"

REQUIRED_CONFIRMED_SECTIONS = [
    "incidentSeed",
    "mock112SourceContract",
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

VALID_SCENARIOS = {f"SC-{index:02d}" for index in range(1, 13)}
VALID_LANES = {f"L{index}" for index in range(1, 7)}
VALID_INCIDENT_ROLES = {"INCIDENT_COMMANDER", "FIELD_COMMANDER", "MEMBER"}
ACCOUNT_ALIAS_META_KEYS = {"validIncidentRoles", "validAuthorityRoles"}
VALID_ALIAS_TYPES = {"COMMANDER", "TEAM", "PATROL"}
VALID_ACCOUNT_TYPES = {"COMMAND", "TEAM", "PATROL_CAR"}
README_SOURCE_DOC_ALIASES = {
    "boundaries.md": "docs/spec/boundaries.md",
    "harness-scenarios.md": "docs/spec/harness-scenarios.md",
}
README_SOURCE_DOC_ALIASES.update({f"S{index}.json": f"docs/spec/specs/S{index}.json" for index in range(1, 9)})
README_SOURCE_DOC_ALIASES.update(
    {
        f"S{major}-{minor}.json": f"docs/spec/specs/S{major}-{minor}.json"
        for major in range(1, 4)
        for minor in range(1, 4)
    }
)

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
    "low_connectivity_timeout",
    "timeout",
    "http_408",
    "http_429",
    "http_500",
    "http_502",
    "http_503",
    "http_504",
    "clock_skew_exceeded_after_resync",
}

SOURCE_TEXT = "\n".join(
    path.read_text(encoding="utf-8")
    for path in [
        ROOT.parent.parent / "api" / "api-spec.md",
        ROOT.parent.parent / "db-design" / "db-design-erd.md",
        ROOT.parent.parent / "db-design" / "db-design-readable.md",
        BOUNDARIES_FILE,
        ROOT.parent / "harness-scenarios.md",
        *sorted((ROOT.parent / "specs").glob("*.json")),
        ROOT.parent.parent / "tasks" / "mock-112-demo-guidance.md",
    ]
)

UUID_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

DB_ALIAS_TO_UUID = {
    "inc-precinct-first-001": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001",
    "inc-precinct-closed-001": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0012",
    "op-precinct-001-op1": "88888888-8888-8888-8888-888888880001",
    "op-precinct-001-op2": "88888888-8888-8888-8888-888888880002",
    "osa-precinct-001": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001",
    "osa-precinct-001-v1": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001",
    "area-precinct-a1": "cccccccc-cccc-cccc-cccc-cccccccc0001",
    "area-precinct-a1-v1": "cccccccc-cccc-cccc-cccc-cccccccc0001",
    "tile-manifest-inc-precinct-001": "77777777-0000-4000-8000-000000000701",
    "pkg-status-precinct-001": "77777777-0000-4000-8000-000000000901",
    "acct-precinct-cmd": "11111111-1111-1111-1111-111111110001",
    "acct-precinct-car": "11111111-1111-1111-1111-111111110002",
    "acct-precinct-team": "11111111-1111-1111-1111-111111110003",
    "acct-cmd-alpha": "11111111-1111-1111-1111-111111110004",
    "acct-team-alpha": "11111111-1111-1111-1111-111111110005",
    "acct-support-cmd": "11111111-1111-1111-1111-111111110006",
    "acct-support-car": "11111111-1111-1111-1111-111111110007",
    "acct-support-team": "11111111-1111-1111-1111-111111110008",
    "dev-precinct-cmd-phone-01": "00000000-0000-0000-0000-000000000201",
    "dev-precinct-car-01": "50000000-0000-0000-0000-000000000001",
    "dev-precinct-phone-01": "00000000-0000-0000-0000-000000000101",
    "dev-alpha-cmd-phone-01": "00000000-0000-0000-0000-000000000204",
    "dev-alpha-phone-01": "00000000-0000-0000-0000-000000000205",
    "dev-support-cmd-phone-01": "00000000-0000-0000-0000-000000000206",
    "dev-support-car-01": "00000000-0000-0000-0000-000000000207",
    "dev-support-phone-01": "00000000-0000-0000-0000-000000000208",
    "op-outbox-path-001": "66666666-0000-4000-8000-000000000501",
    "op-outbox-marker-001": "66666666-0000-4000-8000-000000000601",
    "op-outbox-photo-001": "66666666-0000-4000-8000-000000000602",
    "op-outbox-support-001": "66666666-0000-4000-8000-000000000801",
    "op-outbox-package-001": "66666666-0000-4000-8000-000000000901",
    "op-outbox-preclose-path-001": "66666666-0000-4000-8000-000000001201",
    "op-outbox-postclose-marker-001": "66666666-0000-4000-8000-000000001202",
    "op-fail-clock-001": "66666666-0000-4000-8000-000000001701",
    "op-fail-idem-001": "66666666-0000-4000-8000-000000001702",
    "op-fail-PolicePhone-001": "66666666-0000-4000-8000-000000001703",
    "op-fail-closed-001": "66666666-0000-4000-8000-000000001704",
    "op-fail-network-001": "66666666-0000-4000-8000-000000001705",
    "path-precinct-car-001": "ffffffff-ffff-ffff-ffff-ffffffff0001",
    "path-precinct-foot-001": "ffffffff-ffff-ffff-ffff-ffffffff0002",
    "path-precinct-mixed-001": "ffffffff-ffff-ffff-ffff-ffffffffffff",
    "seg-precinct-vehicle-001": "33333333-3333-3333-3333-333333330001",
    "seg-precinct-foot-001": "33333333-3333-3333-3333-333333330002",
    "mk-precinct-clue-001": "55555555-5555-5555-5555-555555550001",
    "mk-precinct-support-001": "55555555-5555-5555-5555-555555550801",
    "mk-precinct-person-found-001": "55555555-5555-5555-5555-555555550802",
    "mk-precinct-op-mismatch-001": "55555555-5555-5555-5555-555555550099",
    "mk-precinct-closed-001": "55555555-5555-5555-5555-555555551148",
    "photo-precinct-clue-001": "55555555-5555-5555-5555-555555550101",
    "photo-precinct-closed-001": "55555555-5555-5555-5555-555555551149",
    "notif-precinct-support-001": "66666666-6666-6666-6666-666666660801",
    "notif-precinct-person-found-001": "66666666-6666-6666-6666-666666660802",
    "memo-precinct-handover-001": "eeeeeeee-eeee-eeee-eeee-eeeeeeee0001",
    "memo-precinct-op2-001": "eeeeeeee-eeee-eeee-eeee-eeeeeeee0010",
    "summary-precinct-op2-001": "44444444-4444-4444-4444-444444440001",
}

DB_ALIAS_ALLOWED_SUFFIXES = ("Alias", "Aliases", "Code", "Codes")
DB_ALIAS_ALLOWED_KEYS = {
    "fixtureId",
    "ownerPath",
    "sourceDocs",
    "implementationReferences",
    "dataFile",
    "branch",
    "sourceSpecEndpoint",
    "canonicalApiEndpoint",
    "endpoint",
    "requestPathAlias",
    "request_pathAlias",
}

ID_PREFIXES = (
    "acct-",
    "ai-summary-",
    "area-",
    "bs-",
    "board-",
    "dec-",
    "dev-",
    "evt-",
    "fcm:",
    "gps-",
    "handover-",
    "ia-",
    "idem-",
    "inc-",
    "memo-",
    "mk-",
    "mock-112-",
    "net-script-",
    "notif-",
    "op-",
    "outbox-",
    "osa-",
    "path-",
    "photo-",
    "pkg-",
    "rr-",
    "seg-",
    "summary-",
    "support-",
    "team-",
    "tile-manifest-",
    "tombstone-",
)

DERIVED_PATH_FRAGMENTS = (
    "sourceHash",
    "bodyHash",
    "originalBodyHash",
    "changedBodyHash",
    "fanoutAttemptUniqueKeys",
    "blobUriTemplate",
    "uploadUrlTemplate",
    "storageBaseUri",
    "externalAssignmentKey",
    "photoObjectKey",
)

DERIVED_PREFIXES = ("sha256:", "hash-", "local://", "mock://", "http://")

ID_KIND_VALUES = {
    "board_or_projection_id",
    "event_fanout_fixture_id",
    "event_id",
    "fixture_id",
    "outbox_or_sync_fixture_id",
    "owner_reference",
    "seed_id",
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
    "low_connectivity_timeout",
}

S6_REQUIRED_FAILURE_ROWS = {
    "op-fail-clock-001": {
        "lastError": "clock_skew_exceeded",
        "userSafeFailureCategory": "CLOCK_RESYNC_REQUIRED",
        "retryable": True,
    },
    "op-fail-idem-001": {
        "lastError": "idempotency_mismatch",
        "userSafeFailureCategory": "NON_RETRYABLE_CONFLICT",
        "retryable": False,
    },
    "op-fail-PolicePhone-001": {
        "lastError": "police_phone_not_assigned",
        "userSafeFailureCategory": "POLICE_PHONE_ACCESS_REQUIRED",
        "retryable": False,
    },
    "op-fail-closed-001": {
        "lastError": "post_close_requeue_rejected",
        "userSafeFailureCategory": "CLOSED_NO_RETRY",
        "retryable": False,
    },
    "op-fail-network-001": {
        "lastError": "low_connectivity_timeout",
        "userSafeFailureCategory": "RETRYABLE_NETWORK",
        "retryable": True,
    },
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


def get_by_index_path(obj, path: str):
    current = obj
    for part in path.split("."):
        match = re.fullmatch(r"([^\[\]]+)(?:\[(\d+)\])?", part)
        ensure(match is not None, f"invalid indexed path part: {part}")
        key = match.group(1)
        index = match.group(2)
        ensure(isinstance(current, dict) and key in current, f"path not found: {path}")
        current = current[key]
        if index is not None:
            ensure(isinstance(current, list), f"path part is not a list: {part}")
            current = current[int(index)]
    return current


def is_confirmed_id_value(value: str, path: str):
    if not isinstance(value, str) or not value:
        return False
    if value.startswith(DERIVED_PREFIXES):
        return False
    if any(fragment in path for fragment in DERIVED_PATH_FRAGMENTS):
        return False
    if value.startswith(ID_PREFIXES):
        return True
    if value.startswith(("incident:", "connection:")):
        return True
    return False


def classify_confirmed_id(paths):
    if any(path.startswith("ownerReferences.") for path in paths):
        return "owner_reference"
    if any(path.startswith("boardAssembly.") for path in paths):
        return "board_or_projection_id"
    if any(path.startswith("eventFanout.") for path in paths):
        return "event_fanout_fixture_id"
    if any(path.startswith("outboxReplay.") or path.startswith("terminalStateRules.") for path in paths):
        return "outbox_or_sync_fixture_id"
    if any(path.startswith("eventRegistry.") for path in paths):
        return "event_id"
    if any(path.startswith("incidentSeed.") or path.startswith("mock112SourceContract.") for path in paths):
        return "seed_id"
    return "fixture_id"


def build_confirmed_id_index(confirmed: dict):
    entries = {}

    def walk(obj, path_parts):
        if isinstance(obj, dict):
            for key, value in obj.items():
                walk(value, [*path_parts, key])
            return
        if isinstance(obj, list):
            for index, value in enumerate(obj):
                if path_parts:
                    walk(value, [*path_parts[:-1], f"{path_parts[-1]}[{index}]"])
                else:
                    walk(value, [f"[{index}]"])
            return
        if isinstance(obj, str):
            path = ".".join(path_parts)
            if not is_confirmed_id_value(obj, path):
                return
            group = path_parts[0] if path_parts else "confirmed"
            entry = entries.setdefault(obj, {"id": obj, "paths": [], "groups": set()})
            entry["paths"].append(path)
            entry["groups"].add(group)

    walk(confirmed, [])
    for entry in entries.values():
        entry["paths"] = sorted(entry["paths"])
        entry["groups"] = sorted(entry["groups"])
        entry["kind"] = classify_confirmed_id(entry["paths"])
    return {key: entries[key] for key in sorted(entries)}


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


def parse_readme_load_order(readme_path: Path):
    text = readme_path.read_text(encoding="utf-8")
    try:
        section = text.split("## Load Order", 1)[1].split("## Loader 사용 규칙", 1)[0]
    except IndexError as error:
        raise AssertionError("README must contain Load Order and Loader usage sections") from error

    load_order = []
    for raw_line in section.splitlines():
        line = raw_line.strip()
        if not re.match(r"^\d+\.", line):
            continue
        load_order.extend(re.findall(r"`([^`]+)`", line))
    ensure(load_order, "README Load Order must contain numbered fixture sections")
    return load_order


def parse_readme_pending_keys(readme_path: Path):
    text = readme_path.read_text(encoding="utf-8")
    try:
        section = text.split("## Pending Confirmation", 1)[1].split("## Source Coverage", 1)[0]
    except IndexError as error:
        raise AssertionError("README must contain Pending Confirmation and Source Coverage sections") from error

    pending_keys = []
    for raw_line in section.splitlines():
        line = raw_line.strip()
        if not line.startswith("|"):
            continue
        if line.startswith("|---") or "key" in line:
            continue
        columns = [col.strip() for col in line.strip("|").split("|")]
        if not columns:
            continue
        match = re.fullmatch(r"`(.+)`", columns[0])
        if match:
            pending_keys.append(match.group(1))
    return pending_keys


def parse_readme_pending_rows(readme_path: Path):
    text = readme_path.read_text(encoding="utf-8")
    try:
        section = text.split("## Pending Confirmation", 1)[1].split("## Source Coverage", 1)[0]
    except IndexError as error:
        raise AssertionError("README must contain Pending Confirmation and Source Coverage sections") from error

    rows = []
    for raw_line in section.splitlines():
        line = raw_line.strip()
        if not line.startswith("|"):
            continue
        if line.startswith("|---") or "key" in line:
            continue
        columns = [col.strip() for col in line.strip("|").split("|")]
        if len(columns) < 5:
            continue
        match = re.fullmatch(r"`(.+)`", columns[0])
        if not match:
            continue
        source_docs = []
        for label in re.findall(r"`([^`]+)`", columns[4]):
            source_docs.append(README_SOURCE_DOC_ALIASES.get(label, label))
        rows.append({"key": match.group(1), "sourceDocs": source_docs})
    return rows


def parse_boundaries_scenario_slots(scenario_label: str):
    for raw_line in BOUNDARIES_FILE.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line.startswith("|"):
            continue
        columns = [col.strip() for col in line.strip("|").split("|")]
        if len(columns) < 4:
            continue
        if not columns[0].startswith(scenario_label):
            continue
        return set(re.findall(r"`([^`]+)`", columns[3]))
    raise AssertionError(f"scenario row not found in boundaries.md: {scenario_label}")


def parse_harness_scenario_board_merge_slots(scenario_label: str):
    text = (ROOT.parent / "harness-scenarios.md").read_text(encoding="utf-8")
    marker = f"### {scenario_label}"
    start = text.find(marker)
    ensure(start >= 0, f"scenario section not found in harness-scenarios.md: {scenario_label}")
    section = text[start:]
    next_section = section.find("\n### ", 1)
    if next_section >= 0:
        section = section[:next_section]
    for raw_line in section.splitlines():
        line = raw_line.strip()
        if line.startswith("- **board_merge**:"):
            return set(re.findall(r"`([^`]+)`", line))
    raise AssertionError(f"board_merge row not found in harness-scenarios.md: {scenario_label}")


def check_required_sections(common: dict, pending: dict):
    ensure(isinstance(common.get("meta"), dict), "common meta section missing")
    ensure(isinstance(common.get("catalogIndex"), list) and common["catalogIndex"], "catalogIndex must be a non-empty list")
    ensure(isinstance(common.get("usageRules"), dict), "common usageRules section missing")
    ensure(isinstance(common.get("confirmed"), dict), "common confirmed section missing")
    for section in REQUIRED_CONFIRMED_SECTIONS:
        ensure(section in common["confirmed"], f"confirmed.{section} section missing")
    ensure(isinstance(pending.get("meta"), dict), "pending meta section missing")
    ensure(isinstance(pending.get("pendingConfirmation"), list), "pendingConfirmation section missing")


def check_source_docs_exist(common: dict, pending: dict):
    for file_label, data in [("common", common), ("pending", pending)]:
        source_docs = data["meta"].get("sourceDocs")
        ensure(isinstance(source_docs, list), f"{file_label} meta.sourceDocs must be a list")
        has_pending_items = file_label != "pending" or bool(data.get("pendingConfirmation"))
        ensure(source_docs or not has_pending_items, f"{file_label} meta.sourceDocs must be a non-empty list")
        for source_doc in source_docs:
            ensure(isinstance(source_doc, str) and source_doc, f"{file_label} sourceDocs item must be a non-empty string")
            ensure((ROOT.parent.parent.parent / source_doc).exists(), f"{file_label} sourceDoc does not exist: {source_doc}")
        implementation_references = data["meta"].get("implementationReferences", [])
        ensure(isinstance(implementation_references, list), f"{file_label} meta.implementationReferences must be a list")
        for implementation_reference in implementation_references:
            ensure(
                isinstance(implementation_reference, str) and implementation_reference,
                f"{file_label} implementationReferences item must be a non-empty string",
            )
            ensure(
                (ROOT.parent.parent.parent / implementation_reference).exists(),
                f"{file_label} implementationReference does not exist: {implementation_reference}",
            )


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
        invalid_scenarios = [scenario for scenario in uses_scenarios if not isinstance(scenario, str) or scenario not in VALID_SCENARIOS]
        invalid_lanes = [lane for lane in uses_lanes if not isinstance(lane, str) or lane not in VALID_LANES]
        ensure(not invalid_scenarios, f"{fixture_id} has invalid usesScenarios: {invalid_scenarios}")
        ensure(not invalid_lanes, f"{fixture_id} has invalid usesLanes: {invalid_lanes}")
        ensure(get_by_path(common["confirmed"], owner_path) == fixture_id, f"{fixture_id} ownerPath mismatch: {owner_path}")
        catalog_fixture_ids.append(fixture_id)

    duplicates = sorted({fixture_id for fixture_id in catalog_fixture_ids if catalog_fixture_ids.count(fixture_id) > 1})
    ensure(not duplicates, f"duplicate fixtureId found: {duplicates}")
    ensure(
        catalog_fixture_ids == readme_fixture_ids,
        f"README Confirmed Catalog and catalogIndex differ: README={readme_fixture_ids}, JSON={catalog_fixture_ids}",
    )


def check_confirmed_id_index(common: dict):
    index = common.get("confirmedIdIndex")
    ensure(isinstance(index, list) and index, "confirmedIdIndex must be a non-empty list")

    generated = build_confirmed_id_index(common["confirmed"])
    indexed = {}
    for item in index:
        ensure(isinstance(item, dict), "confirmedIdIndex item must be an object")
        fixture_id = item.get("id")
        paths = item.get("paths")
        groups = item.get("groups")
        kind = item.get("kind")

        ensure(isinstance(fixture_id, str) and fixture_id, "confirmedIdIndex item must have id")
        ensure(fixture_id not in indexed, f"duplicate confirmedIdIndex id found: {fixture_id}")
        ensure(isinstance(paths, list) and paths, f"confirmedIdIndex item must have paths: {fixture_id}")
        ensure(isinstance(groups, list) and groups, f"confirmedIdIndex item must have groups: {fixture_id}")
        ensure(isinstance(kind, str) and kind, f"confirmedIdIndex item must have kind: {fixture_id}")
        ensure(kind in ID_KIND_VALUES, f"unknown confirmedIdIndex kind for {fixture_id}: {kind}")

        for path in paths:
            ensure(isinstance(path, str) and path, f"confirmedIdIndex path must be a non-empty string: {fixture_id}")
            actual = get_by_index_path(common["confirmed"], path)
            ensure(actual == fixture_id, f"confirmedIdIndex path mismatch: {fixture_id} at {path} = {actual}")
        indexed[fixture_id] = {"paths": sorted(paths), "groups": sorted(groups)}

    generated_ids = set(generated)
    indexed_ids = set(indexed)
    ensure(
        indexed_ids == generated_ids,
        f"confirmedIdIndex coverage mismatch: missing={sorted(generated_ids - indexed_ids)}, extra={sorted(indexed_ids - generated_ids)}",
    )

    for fixture_id, generated_entry in generated.items():
        ensure(indexed[fixture_id]["paths"] == generated_entry["paths"], f"confirmedIdIndex paths mismatch: {fixture_id}")
        ensure(indexed[fixture_id]["groups"] == generated_entry["groups"], f"confirmedIdIndex groups mismatch: {fixture_id}")
        actual_kind = next(item["kind"] for item in index if item["id"] == fixture_id)
        ensure(actual_kind == generated_entry["kind"], f"confirmedIdIndex kind mismatch: {fixture_id}")
        ensure(fixture_id in SOURCE_TEXT, f"confirmedIdIndex id missing from source docs: {fixture_id}")

    catalog_ids = {item["fixtureId"] for item in common["catalogIndex"]}
    ensure(catalog_ids.issubset(indexed_ids), f"catalogIndex id missing from confirmedIdIndex: {sorted(catalog_ids - indexed_ids)}")


def check_readme_load_order(common: dict):
    readme_load_order = parse_readme_load_order(README_FILE)
    json_load_order = common["usageRules"]["loadOrder"]
    ensure(
        readme_load_order == json_load_order,
        f"README Load Order and usageRules.loadOrder differ: README={readme_load_order}, JSON={json_load_order}",
    )


def check_pending_confirmation(pending: dict):
    readme_pending_keys = parse_readme_pending_keys(README_FILE)
    readme_pending_docs_by_key = {
        row["key"]: row["sourceDocs"]
        for row in parse_readme_pending_rows(README_FILE)
    }
    pending_source_docs = set(pending["meta"]["sourceDocs"])
    seen_keys = set()
    json_pending_keys = []
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
        json_pending_keys.append(key)
        ensure(status == "CONFIRMATION_REQUIRED", f"unknown pending status for {key}: {status}")
        ensure(isinstance(owner, str) and owner, f"pending owner missing for {key}")
        ensure(isinstance(blocking_scenarios, list) and blocking_scenarios, f"pending blockingScenarios missing for {key}")
        invalid_blocking_scenarios = [
            scenario for scenario in blocking_scenarios if not isinstance(scenario, str) or scenario not in VALID_SCENARIOS
        ]
        ensure(not invalid_blocking_scenarios, f"{key} has invalid blockingScenarios: {invalid_blocking_scenarios}")
        ensure(isinstance(reason, str) and reason, f"pending reason missing for {key}")
        for issue_id in re.findall(r"OI-[A-Z0-9]+(?:-[A-Z0-9]+)*", reason):
            ensure(issue_id in SOURCE_TEXT, f"pending reason references unknown open issue: {issue_id}")
        for source_doc in readme_pending_docs_by_key.get(key, []):
            ensure(
                source_doc in pending_source_docs,
                f"README pending source doc missing from pending meta.sourceDocs: {key} -> {source_doc}",
            )

    ensure(
        json_pending_keys == readme_pending_keys,
        f"README Pending Confirmation and pending-confirmation.json differ: README={readme_pending_keys}, JSON={json_pending_keys}",
    )


def check_pending_guards(common: dict, pending: dict):
    common_text = json.dumps(common["confirmed"], ensure_ascii=False, sort_keys=True)
    for item in pending["pendingConfirmation"]:
        fragments = item.get("forbiddenConfirmedKeyFragments", [])
        ensure(isinstance(fragments, list), f"forbiddenConfirmedKeyFragments must be a list for {item['key']}")
        for fragment in fragments:
            ensure(fragment not in common_text, f"pending fragment leaked into confirmed data: {fragment}")
        values = item.get("forbiddenConfirmedValues", [])
        ensure(isinstance(values, list), f"forbiddenConfirmedValues must be a list for {item['key']}")
        for value in values:
            ensure(isinstance(value, str) and value, f"forbiddenConfirmedValues item must be a non-empty string for {item['key']}")
            ensure(value not in common_text, f"pending value leaked into confirmed data: {value}")


def check_conditional_pending_guards(common: dict, pending: dict):
    pending_keys = {item["key"] for item in pending["pendingConfirmation"]}

    s7 = load_json(S7_SPEC_FILE)
    common_hash = common["confirmed"]["tileManifest"].get("overallAreaHash")
    s7_manifest = s7["harness_fixtures"]["tile_manifest_fixture"]
    s7_stale_failure = s7_manifest["failureKeys"]["manifest-overall-area-stale"]
    ensure(s7_manifest["overallAreaHash"] == common_hash, "S7 overallAreaHash must match common tileManifest.overallAreaHash")
    ensure(
        s7_stale_failure["expectedOverallAreaHash"] == common_hash,
        "S7 stale failure expectedOverallAreaHash must match common tileManifest.overallAreaHash",
    )
    ensure(
        s7_stale_failure["overallAreaHash"] == "overall-area-hash-precinct-stale",
        "S7 stale failure overallAreaHash must use stale semantic fixture name",
    )

    sc09_slots = parse_boundaries_scenario_slots("SC-09")
    if "package_badge" not in sc09_slots:
        ensure(
            "boundaries.sc09PackageBadgeSlotCoverage" in pending_keys,
            "boundaries SC-09 slot summary lacks package_badge but boundaries.sc09PackageBadgeSlotCoverage pending key is missing",
        )
    sc09_board_merge_slots = parse_harness_scenario_board_merge_slots("SC-09")
    if "package_badge" not in sc09_board_merge_slots:
        ensure(
            "boundaries.sc09PackageBadgeSlotCoverage" in pending_keys,
            "harness SC-09 board_merge lacks package_badge but boundaries.sc09PackageBadgeSlotCoverage pending key is missing",
        )


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
                if (key == "throws" or "error" in key.lower()) and value not in ERROR_VALUES and value not in FAILURE_KEYS:
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
    missing_source_errors = sorted(error for error in ERROR_VALUES if error not in SOURCE_TEXT)
    ensure(not missing_source_errors, f"preflight error allow-list contains values missing from source docs: {missing_source_errors}")


def check_board_coverage(common: dict):
    board = common["confirmed"]["boardAssembly"]
    slot_rows = board.get("slotRows")
    ensure(isinstance(slot_rows, list) and slot_rows, "boardAssembly.slotRows must be a non-empty list")
    actual_slots = []
    for row in slot_rows:
        ensure(isinstance(row, dict), "boardAssembly.slotRows item must be an object")
        slot = row.get("slot")
        ensure(isinstance(slot, str) and slot, f"boardAssembly.slotRows item must have slot: {row}")
        actual_slots.append(slot)
    duplicate_slots = sorted({slot for slot in actual_slots if actual_slots.count(slot) > 1})
    ensure(not duplicate_slots, f"duplicate boardAssembly slots found: {duplicate_slots}")
    ensure(set(actual_slots) == BOARD_REQUIRED_SLOTS, f"boardAssembly slot coverage mismatch: {sorted(actual_slots)}")


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
    board_rows = {row["slot"]: row for row in confirmed["boardAssembly"]["slotRows"]}

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
        ("board overall_search_area", board_rows["overall_search_area"]["sourceResponseId"], owner_refs["overallSearchAreaResponseId"]),
        ("board area", board_rows["area"]["sourceResponseId"], owner_refs["areaId"]),
        ("board path", board_rows["path"]["sourceResponseId"], confirmed["gpsPath"]["pathId"]),
        ("board freshness", board_rows["police_phone_freshness"]["sourceResponseId"], confirmed["gpsPath"]["policePhoneId"]),
        ("board marker", board_rows["marker"]["sourceResponseId"], confirmed["incidentSeed"]["seedIds"]["markerId"]),
        ("board toast", board_rows["toast"]["sourceResponseId"], owner_refs["supportRequestResponseId"]),
        ("board package badge", board_rows["package_badge"]["sourceResponseId"], owner_refs["packageBadgeResponseId"]),
        ("board op_toggle", board_rows["op_toggle"]["sourceResponseId"], confirmed["opTransition"]["currentOp"]["opId"]),
        ("board op_history", board_rows["op_history"]["sourceResponseId"], confirmed["opTransition"]["newOp"]["opId"]),
        ("board handover memo", board_rows["handover_memo"]["sourceResponseId"], confirmed["incidentSeed"]["seedIds"]["memoId"]),
        ("board handover status", board_rows["handover_status"]["sourceResponseId"], owner_refs["handoverStatusResponseId"]),
        ("board search history summary", board_rows["search_history_summary"]["sourceResponseId"], owner_refs["boardSearchHistorySummaryResponseId"]),
        ("board incident terminal", board_rows["incident_terminal"]["sourceResponseId"], confirmed["incidentSeed"]["incidentId"]),
        ("board tombstone", board_rows["incident_terminal"]["tombstoneResponseId"], owner_refs["terminalTombstoneResponseId"]),
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
    failure_rows = confirmed["terminalStateRules"]["failureCategoryRows"]
    failure_policy = confirmed["outboxSharedRules"]["failureDisplayPolicy"]
    failure_operation_aliases = [row.get("operationAlias") for row in failure_rows if isinstance(row, dict)]
    duplicate_failure_ops = sorted(
        {op for op in failure_operation_aliases if failure_operation_aliases.count(op) > 1}
    )
    ensure(not duplicate_failure_ops, f"duplicate S6 failure category operationAlias found: {duplicate_failure_ops}")
    ensure(
        set(failure_operation_aliases) == set(S6_REQUIRED_FAILURE_ROWS),
        f"S6 failure category coverage mismatch: {sorted(failure_operation_aliases)}",
    )
    rows_by_operation = {row["operationAlias"]: row for row in failure_rows}
    for operation_alias, expected in S6_REQUIRED_FAILURE_ROWS.items():
        row = rows_by_operation[operation_alias]
        for key, value in expected.items():
            ensure(row.get(key) == value, f"S6 failure category row mismatch for {operation_alias}.{key}: {row.get(key)} != {value}")
        category = row["userSafeFailureCategory"]
        ensure(category in failure_policy, f"S6 failure category missing from display policy: {category}")
        ensure(
            row["lastError"] in failure_policy[category],
            f"S6 failure row lastError not mapped by display policy: {operation_alias} -> {row['lastError']} / {category}",
        )
    ensure(confirmed["searchHistorySummary"]["negativeOnlyInputs"]["forbiddenPhraseResponse"]["expectedDisplayStatus"] == "UNAVAILABLE", "SC-11 negative fixture display status mismatch")

    tile_manifest = confirmed["tileManifest"]
    ensure(
        tile_manifest.get("overallAreaHash") == "overall-area-hash-precinct-current",
        "tileManifest.overallAreaHash must use harness-scenarios.md form (overall-area-hash-precinct-current)",
    )

    package_replay = confirmed["outboxReplay"]["sc09PackageReplay"]
    package_payload = package_replay["requestPayload"]
    package_write_operation = package_replay["writeOperation"]
    package_owner_response = package_replay["expectedOwnerResponse"]
    package_event = package_replay["expectedS4Event"]
    package_event_payload = package_event["payload"]
    package_query = package_replay["expectedPackageStatusQuery"]
    package_board_probe = package_replay["expectedBoardProbe"]
    package_board_relation = package_board_probe["relationToBoardAssembly"]
    board_rows = {row["slot"]: row for row in confirmed["boardAssembly"]["slotRows"]}
    package_board_row = board_rows["package_badge"]
    ensure(package_replay["idempotencyKey"].startswith("idem-"), "SC-09 package replay idempotencyKey must be fixture-like")
    ensure(package_replay["dependencyGroup"] == "PACKAGE_INSTALLATION", "SC-09 package replay dependencyGroup mismatch")
    ensure(package_replay["sourceSpecEndpoint"] == "POST /incidents/{incidentId}/offline-package/installations", "SC-09 package sourceSpecEndpoint mismatch")
    ensure(package_replay["canonicalApiEndpoint"] == "POST /api/incidents/{incidentId}/offline-package/installations", "SC-09 package canonicalApiEndpoint mismatch")
    ensure(package_replay["requestMethod"] == "POST", "SC-09 package requestMethod mismatch")
    ensure(
        package_replay["requestPath"] == f"/api/incidents/{package_replay['incidentId']}/offline-package/installations",
        "SC-09 package requestPath mismatch",
    )
    ensure(package_replay["requestBodyHash"] == package_replay["bodyHash"], "SC-09 package requestBodyHash/bodyHash mismatch")
    ensure(package_replay["manifestId"] == confirmed["tileManifest"]["manifestId"], "SC-09 package manifestId mismatch")
    ensure(package_payload["policePhoneId"] == package_replay["policePhoneId"], "SC-09 package payload policePhoneId mismatch")
    ensure(package_payload["manifestId"] == package_replay["manifestId"], "SC-09 package payload manifestId mismatch")
    ensure(package_payload["manifestVersion"] == package_owner_response["manifestVersion"], "SC-09 package payload manifestVersion mismatch")
    ensure(package_payload["status"] == package_owner_response["status"], "SC-09 package payload status mismatch")
    ensure(package_payload["version"] == package_owner_response["version"], "SC-09 package payload version mismatch")
    ensure(package_payload["readyForOfflineUse"] == package_owner_response["readyForOfflineUse"], "SC-09 package payload readyForOfflineUse mismatch")
    ensure(package_payload["sequence"] == package_replay["sequence"], "SC-09 package payload sequence mismatch")
    ensure(package_payload["clientTs"] == package_replay["clientTs"], "SC-09 package payload clientTs mismatch")
    ensure(package_payload["clockOffsetMs"] == package_replay["clockOffsetMs"], "SC-09 package payload clockOffsetMs mismatch")
    ensure(package_payload["completedItems"] + package_payload["failedItems"] <= package_payload["totalItems"], "SC-09 package payload item counts mismatch")
    ensure(package_payload["status"] != "READY" or package_payload["completedItems"] == package_payload["totalItems"], "SC-09 READY package must complete all items")
    ensure(package_payload["status"] != "READY" or package_payload["failedItems"] == 0, "SC-09 READY package must not have failed items")
    required_write_operation = {
        "operationId": package_replay["operationId"],
        "incidentId": package_replay["incidentId"],
        "policePhoneId": package_replay["policePhoneId"],
        "dependencyGroup": package_replay["dependencyGroup"],
        "sequence": package_replay["sequence"],
        "request_method": package_replay["requestMethod"],
        "request_path": package_replay["requestPath"],
        "payload": package_payload,
        "requestBodyHash": package_replay["requestBodyHash"],
        "idempotencyKey": package_replay["idempotencyKey"],
        "clientTs": package_replay["clientTs"],
        "clockOffsetMs": package_replay["clockOffsetMs"],
        "clockSyncedAt": package_replay["clockSyncedAt"],
    }
    for key, expected in required_write_operation.items():
        ensure(package_write_operation.get(key) == expected, f"SC-09 package writeOperation {key} mismatch")
    ensure(package_write_operation["entityId"] == package_owner_response["id"], "SC-09 package writeOperation entityId mismatch")
    ensure(package_write_operation["entityType"] == "offline_package_installation", "SC-09 package writeOperation entityType mismatch")
    ensure(isinstance(package_owner_response.get("serverTs"), str) and package_owner_response["serverTs"], "SC-09 package owner response serverTs missing")
    ensure(package_event["eventId"] == confirmed["eventRegistry"]["packageStatusBoardProjection"]["eventId"], "SC-09 package eventId mismatch")
    ensure(package_event["schemaVersion"] == 1, "SC-09 package event schemaVersion mismatch")
    ensure(package_event["serverTs"] == package_owner_response["serverTs"], "SC-09 package event serverTs mismatch")
    ensure(package_event["incidentId"] == package_replay["incidentId"], "SC-09 package event incidentId mismatch")
    ensure(package_event["payloadId"] == package_owner_response["id"], "SC-09 package event payloadId mismatch")
    ensure(package_event["policePhoneId"] == package_replay["policePhoneId"], "SC-09 package event policePhoneId mismatch")
    ensure(package_event_payload["id"] == package_owner_response["id"], "SC-09 package event payload id mismatch")
    ensure(package_event_payload["status"] == package_owner_response["status"], "SC-09 package event payload status mismatch")
    ensure(package_event_payload["version"] == package_owner_response["version"], "SC-09 package event payload version mismatch")
    ensure(package_event_payload["incidentId"] == package_replay["incidentId"], "SC-09 package event payload incidentId mismatch")
    ensure(package_event_payload["policePhoneId"] == package_replay["policePhoneId"], "SC-09 package event payload policePhoneId mismatch")
    ensure(package_event_payload["manifestVersion"] == package_owner_response["manifestVersion"], "SC-09 package event payload manifestVersion mismatch")
    ensure(package_query["method"] == "OfflinePackageInstallationQuery.byIncident", "SC-09 package status query method mismatch")
    ensure(package_query["incidentId"] == package_replay["incidentId"], "SC-09 package status query incidentId mismatch")
    ensure(package_query["policePhoneId"] == package_replay["policePhoneId"], "SC-09 package status query policePhoneId mismatch")
    ensure(package_query["id"] == package_owner_response["id"], "SC-09 package status query id mismatch")
    ensure(package_board_probe["incidentId"] == package_replay["incidentId"], "SC-09 package board incidentId mismatch")
    ensure(package_board_probe["policePhoneId"] == package_replay["policePhoneId"], "SC-09 package board policePhoneId mismatch")
    ensure(package_board_probe["id"] == package_owner_response["id"], "SC-09 package board probe id mismatch")
    ensure(package_board_probe["slot"] == "package_badge", "SC-09 package board probe slot mismatch")
    ensure(package_board_relation["baseSlot"] == package_board_row["slot"], "SC-09 package board relation slot mismatch")
    ensure(package_board_relation["baseSourceResponseId"] == package_board_row["sourceResponseId"], "SC-09 package board relation sourceResponseId mismatch")
    ensure(package_board_relation["baseBoardRowId"] == package_board_row["boardRowId"], "SC-09 package board relation boardRowId mismatch")
    ensure(package_board_relation["baseStatusBeforeReplay"] == package_board_row["status"], "SC-09 package board relation status mismatch")
    ensure(package_board_row["latestEventId"] != package_event["eventId"], "SC-09 package stale base row must not reuse READY replay eventId")
    ensure(package_board_row["sequence"] < package_replay["sequence"], "SC-09 package stale base row sequence must be lower than replay sequence")
    ensure(package_event["payloadStatus"] == package_owner_response["status"], "SC-09 package status mismatch")
    ensure(package_query["status"] == package_owner_response["status"], "SC-09 package query status mismatch")
    ensure(package_board_probe["status"] == package_owner_response["status"], "SC-09 package board status mismatch")
    ensure(package_event["payloadVersion"] == package_owner_response["version"], "SC-09 package version mismatch")
    ensure(package_query["version"] == package_owner_response["version"], "SC-09 package query version mismatch")
    ensure(package_board_probe["minimumVersion"] == package_owner_response["version"], "SC-09 package board version mismatch")
    ensure(package_event["sequence"] == package_replay["sequence"], "SC-09 package event sequence mismatch")
    ensure(package_query["sequence"] == package_replay["sequence"], "SC-09 package query sequence mismatch")
    ensure(package_board_probe["sequence"] == package_replay["sequence"], "SC-09 package board sequence mismatch")
    ensure(
        package_owner_response["manifestVersion"]
        == package_event["manifestVersion"]
        == package_event_payload["manifestVersion"]
        == package_query["manifestVersion"]
        == package_board_probe["manifestVersion"],
        "SC-09 package manifestVersion mismatch",
    )


def check_mock112_seed_alignment(common: dict):
    seed = load_json(MOCK112_SEED_FILE)
    contract = common["confirmed"]["mock112SourceContract"]
    account_aliases = common["confirmed"]["accountAliases"]
    alias_entries = {
        account_id: alias
        for account_id, alias in account_aliases.items()
        if account_id not in ACCOUNT_ALIAS_META_KEYS
    }
    valid_roles = set(common["confirmed"]["accountAliases"]["validIncidentRoles"])
    ensure(valid_roles == VALID_INCIDENT_ROLES, f"validIncidentRoles mismatch: {sorted(valid_roles)}")
    ensure(
        set(alias_entries) == set(common["confirmed"]["incidentSeed"]["accountCodes"]),
        "accountAliases account codes must exactly match incidentSeed.accountCodes: "
        f"missing={sorted(set(common['confirmed']['incidentSeed']['accountCodes']) - set(alias_entries))}, "
        f"extra={sorted(set(alias_entries) - set(common['confirmed']['incidentSeed']['accountCodes']))}",
    )
    for account_id, alias in alias_entries.items():
        ensure(isinstance(alias, dict), f"accountAliases entry must be an object: {account_id}")
        ensure(alias.get("aliasType") in VALID_ALIAS_TYPES, f"accountAliases.{account_id} has invalid aliasType: {alias}")
        ensure(alias.get("accountType") in VALID_ACCOUNT_TYPES, f"accountAliases.{account_id} has invalid accountType: {alias}")

    scalar_fields = ["sourceIncidentId", "title", "openedAt", "missingPerson", "seedMarkers"]
    for field in scalar_fields:
        ensure(seed.get(field) == contract.get(field), f"mock-112 seed mismatch for {field}")
    for marker in contract["seedMarkers"]:
        ensure(marker.get("source") == "MOCK_SEED", f"mock112SourceContract seed marker source must match S5 marker_source enum: {marker}")

    initial_account_codes = [assignment.get("accountCode") for assignment in contract["initialAssignments"]]
    ensure(
        initial_account_codes == common["confirmed"]["incidentSeed"]["accountCodes"][:3],
        "mock112SourceContract.initialAssignments must match the precinct before-handover account set",
    )
    ensure(
        len(initial_account_codes) == len(common["confirmed"]["incidentSeed"]["expectedIncidentAssignments"]["beforeHandover"]),
        "mock112SourceContract.initialAssignments must cover every beforeHandover incident assignment row",
    )

    assignment_groups = [
        ("assignments", "initialAssignments"),
        ("handoverAssignments", "handoverAssignments"),
        ("supportAssignments", "supportAssignments"),
    ]
    for seed_field, contract_field in assignment_groups:
        ensure(
            seed.get(seed_field) == contract.get(contract_field),
            f"mock-112 seed mismatch for {seed_field} != mock112SourceContract.{contract_field}",
        )
        for assignment in contract.get(contract_field, []):
            ensure(
                assignment.get("accountCode") in alias_entries,
                f"mock112SourceContract.{contract_field} accountCode missing from accountAliases: {assignment}",
            )
            ensure(
                assignment.get("incidentRole") in valid_roles,
                f"mock112SourceContract.{contract_field} has invalid incidentRole: {assignment}",
            )


def should_allow_db_alias_key(key: str):
    return key in DB_ALIAS_ALLOWED_KEYS or key.endswith(DB_ALIAS_ALLOWED_SUFFIXES)


def check_no_db_alias_in_uuid_fields(common: dict):
    violations = []

    def walk(obj, path_parts):
        if isinstance(obj, dict):
            for key, value in obj.items():
                walk(value, [*path_parts, key])
            return
        if isinstance(obj, list):
            for index, value in enumerate(obj):
                if path_parts:
                    walk(value, [*path_parts[:-1], f"{path_parts[-1]}[{index}]"])
                else:
                    walk(value, [f"[{index}]"])
            return
        if not isinstance(obj, str) or not path_parts:
            return

        key = path_parts[-1]
        if should_allow_db_alias_key(key):
            return
        if obj not in DB_ALIAS_TO_UUID:
            return
        if not (key == "id" or key.endswith("Id") or key.endswith("Ids") or "Id[" in key):
            return
        violations.append((".".join(path_parts), obj, DB_ALIAS_TO_UUID[obj]))

    walk(common["confirmed"], ["confirmed"])
    for spec_path in sorted((ROOT.parent / "specs").glob("*.json")):
        spec = load_json(spec_path)
        harness_fixtures = spec.get("harness_fixtures")
        if harness_fixtures is not None:
            walk(harness_fixtures, [spec_path.name, "harness_fixtures"])

    ensure(
        not violations,
        "DB UUID field contains fixture alias/code; use UUID field plus *Alias/*Code sibling: "
        + ", ".join(f"{path}={alias} -> {uuid}" for path, alias, uuid in violations[:20]),
    )


def main():
    common = load_json(COMMON_FILE)
    pending = load_json(PENDING_FILE)

    check_required_sections(common, pending)
    check_source_docs_exist(common, pending)
    check_catalog(common)
    check_confirmed_id_index(common)
    check_readme_load_order(common)
    check_pending_confirmation(pending)
    check_pending_guards(common, pending)
    check_conditional_pending_guards(common, pending)
    check_status_and_error_strings(common)
    check_board_coverage(common)
    check_event_cross_references(common)
    check_id_cross_references(common)
    check_internal_consistency(common)
    check_mock112_seed_alignment(common)
    check_no_db_alias_in_uuid_fields(common)

    print("common fixture preflight passed")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as error:
        print(f"preflight failed: {error}", file=sys.stderr)
        sys.exit(1)
