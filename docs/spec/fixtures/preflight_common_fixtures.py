#!/usr/bin/env python3
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent
COMMON_FILE = ROOT / "common-fixtures.json"
PENDING_FILE = ROOT / "pending-confirmation.json"

ALLOWED_STATUS_VALUES = {
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
    "DOWNLOADING",
    "MANIFEST_READY",
    "MANIFEST_FAILED_RETRYABLE",
    "PENDING_LOCAL",
    "PENDING_SEND",
    "SYNCED",
    "UPDATED",
    "ATTACHED",
    "FAILED",
    "UNAVAILABLE",
    "ENDED",
    "NEEDS_MEMO"
}

ALLOWED_ERROR_VALUES = {
    "invalid_geometry",
    "gone_refetch_required",
    "manifest_expired",
    "manifest_overall_area_stale",
    "timeout"
}


def load_json(path: Path):
    with path.open("r", encoding="utf-8") as file:
        return json.load(file)


def ensure(condition: bool, message: str):
    if not condition:
        raise AssertionError(message)


def check_fixture_ids(common: dict):
    catalog = common.get("catalogIndex")
    ensure(isinstance(catalog, list) and catalog, "catalogIndex must be a non-empty list")

    fixture_ids = []
    for item in catalog:
        ensure("fixtureId" in item and item["fixtureId"], "catalogIndex item must have fixtureId")
        fixture_ids.append(item["fixtureId"])

    duplicates = sorted({fixture_id for fixture_id in fixture_ids if fixture_ids.count(fixture_id) > 1})
    ensure(not duplicates, f"duplicate fixtureId found: {duplicates}")


def traverse(obj, callback):
    if isinstance(obj, dict):
        for key, value in obj.items():
            callback(key, value)
            traverse(value, callback)
    elif isinstance(obj, list):
        for value in obj:
            traverse(value, callback)


def check_status_and_error_strings(common: dict):
    invalid_statuses = []
    invalid_errors = []

    def callback(key, value):
        if isinstance(value, str) and key.lower().endswith("status"):
            if value not in ALLOWED_STATUS_VALUES:
                invalid_statuses.append((key, value))
        if isinstance(value, str) and "error" in key.lower():
            if value not in ALLOWED_ERROR_VALUES:
                invalid_errors.append((key, value))

    traverse(common["confirmed"], callback)
    ensure(not invalid_statuses, f"unknown status values: {invalid_statuses}")
    ensure(not invalid_errors, f"unknown error values: {invalid_errors}")


def check_required_sections(common: dict, pending: dict):
    ensure("confirmed" in common and isinstance(common["confirmed"], dict), "common confirmed section missing")
    ensure("usageRules" in common and isinstance(common["usageRules"], dict), "common usageRules section missing")
    ensure("pendingConfirmation" in pending and isinstance(pending["pendingConfirmation"], list), "pendingConfirmation section missing")


def main():
    common = load_json(COMMON_FILE)
    pending = load_json(PENDING_FILE)

    check_required_sections(common, pending)
    check_fixture_ids(common)
    check_status_and_error_strings(common)

    print("common fixture preflight passed")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as error:
        print(f"preflight failed: {error}", file=sys.stderr)
        sys.exit(1)
