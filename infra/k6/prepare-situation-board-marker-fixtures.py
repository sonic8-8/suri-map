#!/usr/bin/env python3
"""Reserve marker request IDs before load; remove only their completed test records."""

import argparse
import importlib.util
import json
import os
from pathlib import Path
import sys
import uuid

spec = importlib.util.spec_from_file_location(
    "board_account", Path(__file__).with_name("prepare-situation-board-account.py")
)
board_account = importlib.util.module_from_spec(spec)
spec.loader.exec_module(board_account)
path_fixtures = board_account.path_fixtures


def marker_fixture(run_id, count):
    run_id = uuid.UUID(run_id)
    # A five-minute condition sends one marker every ten seconds; smoke may use fewer.
    if type(count) is not int or not 1 <= count <= 30:
        raise ValueError("Marker count must be between 1 and 30.")
    return {
        "runId": str(run_id),
        "incidentId": path_fixtures.INCIDENT_ID,
        "opId": path_fixtures.OPERATIONAL_PERIOD_ID,
        "accountId": path_fixtures.fixture_id("a1", 1),
        "policePhoneId": path_fixtures.fixture_id("b1", 1),
        "markers": [
            {
                "id": str(uuid.uuid5(run_id, f"marker-{index}")),
                "idempotencyKey": f"board-marker-{run_id}-{index}",
            }
            for index in range(count)
        ],
    }


def load_fixture(path):
    fixture = json.loads(path.read_text())
    expected = marker_fixture(fixture["runId"], len(fixture["markers"]))
    if fixture != expected:
        raise ValueError("Fixture was altered; refusing to use it for cleanup.")
    return fixture


def cleanup_sql(fixture):
    # Accept only generated UUIDs/keys, even when this function is imported by a runner.
    if fixture != marker_fixture(fixture["runId"], len(fixture["markers"])):
        raise ValueError("Fixture identity mismatch.")
    values = ",\n".join(
        f"('{marker['id']}'::uuid, '{marker['idempotencyKey']}')"
        for marker in fixture["markers"]
    )
    return f"""
BEGIN;
CREATE TEMP TABLE planned_marker (id uuid PRIMARY KEY, request_key text) ON COMMIT DROP;
INSERT INTO planned_marker VALUES {values};

-- The caller must stop all writers/readers first. Lock existing target rows as well.
SELECT m.id FROM marker m JOIN planned_marker p ON p.id = m.id FOR UPDATE OF m;
SELECT n.id FROM marker_notification n JOIN planned_marker p ON p.id = n.marker_id
FOR UPDATE OF n;
CREATE TEMP TABLE marker_jobs ON COMMIT DROP AS
SELECT j.id FROM event_dispatch_job j
WHERE (j.source_entity_type = 'marker' AND j.source_entity_id IN (SELECT id FROM planned_marker))
   OR (j.source_entity_type = 'marker_notification' AND j.source_entity_id IN
       (SELECT id FROM marker_notification WHERE marker_id IN (SELECT id FROM planned_marker)));
SELECT j.id FROM event_dispatch_job j JOIN marker_jobs t ON t.id = j.id FOR UPDATE OF j;
SELECT r.id FROM idempotency_record r JOIN planned_marker p ON p.request_key = r.idempotency_key
FOR UPDATE OF r;

DO $$ BEGIN
    IF EXISTS (
        SELECT 1 FROM marker m JOIN planned_marker p ON p.id = m.id
        WHERE m.incident_id IS DISTINCT FROM '{fixture['incidentId']}'::uuid
           OR m.operational_period_id IS DISTINCT FROM '{fixture['opId']}'::uuid
           OR m.created_by_account_id IS DISTINCT FROM '{fixture['accountId']}'::uuid
           OR m.police_phone_id IS DISTINCT FROM '{fixture['policePhoneId']}'::uuid
           OR m.marker_source != 'APP' OR m.marker_type != 'SUPPORT_REQUEST'
           OR m.version != 1 OR m.status != 'ACTIVE'
    ) THEN RAISE EXCEPTION 'Marker ownership or contents changed'; END IF;
    IF EXISTS (SELECT 1 FROM photo WHERE marker_id IN (SELECT id FROM planned_marker))
    THEN RAISE EXCEPTION 'Unexpected photos; preserve records for inspection'; END IF;
    IF EXISTS (
        SELECT 1 FROM marker_notification WHERE marker_id IN (SELECT id FROM planned_marker)
        AND (notification_type != 'SUPPORT_REQUEST_CREATED' OR version != 1
             OR status != 'SNAPSHOT_CREATED')
    ) THEN RAISE EXCEPTION 'Notification changed'; END IF;
    IF EXISTS (
        SELECT 1 FROM event_dispatch_job j JOIN marker_jobs t ON t.id = j.id
        WHERE j.incident_id != '{fixture['incidentId']}'::uuid
           OR j.dispatch_status != 'COMPLETED'
           OR (j.source_entity_type = 'marker' AND j.event_type != 'MARKER_CREATED')
           OR (j.source_entity_type = 'marker_notification'
               AND j.event_type != 'SUPPORT_REQUEST_CREATED')
    ) THEN RAISE EXCEPTION 'Unfinished or unexpected event; preserve diagnostic evidence'; END IF;
    IF EXISTS (
        SELECT 1 FROM idempotency_record r JOIN planned_marker p ON p.request_key = r.idempotency_key
        WHERE r.request_method != 'POST' OR r.request_path != '/api/markers'
           OR r.idempotency_status != 'COMPLETED'
           OR r.result_entity_id IS DISTINCT FROM p.id
    ) THEN RAISE EXCEPTION 'Unfinished or unexpected request record'; END IF;
END $$;

DELETE FROM event_dispatch_job WHERE id IN (SELECT id FROM marker_jobs);
DELETE FROM marker_notification WHERE marker_id IN (SELECT id FROM planned_marker);
DELETE FROM idempotency_record WHERE idempotency_key IN (SELECT request_key FROM planned_marker);
DELETE FROM marker WHERE id IN (SELECT id FROM planned_marker);
COMMIT;
"""


def prepare_fixture(path, count):
    # This only writes request identities locally; it does not create markers in the DB.
    fixture = marker_fixture(str(uuid.uuid4()), count)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8") as output:
        json.dump(fixture, output, indent=2)
        output.write("\n")
    print(f"Reserved {count} marker requests at {path}; no requests sent.")


def self_check():
    fixture = marker_fixture(str(uuid.uuid4()), 30)
    assert len({marker["id"] for marker in fixture["markers"]}) == 30
    assert fixture == marker_fixture(fixture["runId"], 30)
    assert "LIKE" not in cleanup_sql(fixture)
    fixture["markers"][0]["id"] = str(uuid.uuid4())
    try:
        cleanup_sql(fixture)
    except ValueError:
        pass
    else:
        raise AssertionError("Altered marker identity was accepted")
    print("PASS: unique request IDs and altered-fixture rejection; no external calls.")


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("prepare", "cleanup", "self-check"))
    parser.add_argument("--fixture", type=Path)
    parser.add_argument("--count", type=int, default=30)
    parser.add_argument("--writers-stopped", action="store_true",
                        help="Confirm load writers and observation browsers have stopped.")
    args = parser.parse_args()
    if args.action != "self-check" and args.fixture is None:
        parser.error("--fixture is required")
    if args.action == "self-check":
        self_check()
    elif args.action == "prepare":
        prepare_fixture(args.fixture, args.count)
    else:
        if not args.writers_stopped:
            parser.error("Stop load writers/readers first and pass --writers-stopped")
        board_account.query_database(cleanup_sql(load_fixture(args.fixture)))
        print("Removed this fixture's markers, notifications, completed events and request records. "
              "Fixture file retained; never reuse it for a new run.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"ERROR ({type(error).__name__}): preparation/cleanup incomplete; retain the fixture "
              "and inspect ownership, photos, pending jobs and request records.", file=sys.stderr)
        sys.exit(1)
