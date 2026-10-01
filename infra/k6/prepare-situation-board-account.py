#!/usr/bin/env python3
"""Prepare one persistent browser-login account; never start load or create markers."""

import argparse
from contextlib import contextmanager, nullcontext
import importlib.util
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import tempfile
import urllib.parse
import uuid


# Root-run diagnostics must not leave root-owned cache files in Jenkins deployment sources.
sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location(
    "path_fixtures", Path(__file__).with_name("prepare-search-path-batch-fixtures.py")
)
path_fixtures = importlib.util.module_from_spec(spec)
spec.loader.exec_module(path_fixtures)


def query_database(sql):
    result = subprocess.run(
        ["docker", "exec", "-i", path_fixtures.POSTGRES_CONTAINER, "sh", "-lc",
         'psql -X -qAt -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1'],
        input=sql, text=True, capture_output=True, check=False,
    )
    if result.returncode:
        # SQL errors may contain row data. Do not copy them into shared load logs.
        raise RuntimeError("Database operation failed; inspect the database locally.")
    return result.stdout.strip()


def inspect_notification_targets():
    # Match IncidentAssignmentView + IncidentReadMapper, including COMMAND accounts
    # with MEMBER assignments. Do not filter on account status or phone.registered:
    # the production recipient query does not filter on either field.
    return json.loads(query_database(f"""
WITH recipients AS (
    SELECT DISTINCT a.id AS account_id, pp.id AS phone_id
    FROM incident_assignment ia
    LEFT JOIN account a ON a.id = ia.account_id
    LEFT JOIN police_phone pp ON pp.account_id = ia.account_id AND pp.status = 'ACTIVE'
    WHERE ia.incident_id = '{path_fixtures.INCIDENT_ID}'::uuid
      AND ia.revoked_at IS NULL
      AND (a.account_type = 'COMMAND' OR ia.incident_role = 'FIELD_COMMANDER')
)
SELECT json_build_object(
    'incidentOpen', EXISTS (SELECT 1 FROM incident
        WHERE id = '{path_fixtures.INCIDENT_ID}'::uuid AND status = 'OPEN'),
    'periodActive', EXISTS (SELECT 1 FROM operational_period
        WHERE id = '{path_fixtures.OPERATIONAL_PERIOD_ID}'::uuid
          AND incident_id = '{path_fixtures.INCIDENT_ID}'::uuid AND status = 'ACTIVE'),
    'recipientAccounts', (SELECT count(DISTINCT account_id) FROM recipients),
    'recipientPhones', (SELECT count(DISTINCT phone_id) FROM recipients),
    'activeFcmTokens', (SELECT count(*) FROM fcm_token
        WHERE status = 'ACTIVE' AND police_phone_id IN (SELECT phone_id FROM recipients))
);
"""))


def check_preflight(report):
    if report.get("incidentOpen") is not True or report.get("periodActive") is not True:
        raise RuntimeError("The shared fixture incident and operational period must be open/active.")
    if report.get("activeFcmTokens") != 0:
        raise RuntimeError("Recipient FCM tokens exist; do not create support-request markers.")


def check_mock_fcm_runtime(container, expected_backend=None):
    config = container["Config"]
    environment = dict(item.split("=", 1) for item in config.get("Env", []) if "=" in item)
    # Only accept the inspected deployment layout; do not guess property precedence.
    overrides = any(
        value and (key.startswith("SPRING_CONFIG_") or key == "SPRING_APPLICATION_JSON")
        for key, value in environment.items()
    )
    if (
        not container["State"]["Running"]
        or environment.get("FCM_PROVIDER") != "mock"
        or config.get("Entrypoint") != ["java", "-jar", "/app/app.jar"]
        or config.get("Cmd") or container.get("Mounts") or overrides
        or environment.get("JAVA_TOOL_OPTIONS", "") not in ("", "-Duser.timezone=Asia/Seoul")
        or environment.get("JDK_JAVA_OPTIONS") or environment.get("_JAVA_OPTIONS")
    ):
        raise RuntimeError("Cannot verify the existing mock FCM configuration; do not send markers.")
    identity = f"{container['Id']}/{container['State']['StartedAt']}"
    if expected_backend is not None and identity != expected_backend:
        raise RuntimeError("Backend restarted/replaced; stop the current load condition.")
    return identity


def check_marker_safety(expected_backend=None):
    result = subprocess.run(
        ["docker", "inspect", "suri-map-backend"],
        text=True, capture_output=True, check=True,
    )
    # Inspect output contains secrets; only return the non-secret identity below.
    identity = check_mock_fcm_runtime(json.loads(result.stdout)[0], expected_backend)
    report = inspect_notification_targets()
    check_preflight(report)
    return {**report, "backendIdentity": identity, "fcmProvider": "mock"}


def expected_backend_from_ssh_command(command):
    # Match the writer's exact request; never execute SSH_ORIGINAL_COMMAND in a shell.
    allowed = "python3 /srv/apps/suri-map/infra/k6/prepare-situation-board-account.py check-marker-safety"
    match = re.fullmatch(
        re.escape(allowed) + r"(?: --expected-backend ([a-f0-9]{64}/[0-9TZ:.+-]{1,40}))?",
        command,
    )
    if match is None:
        raise RuntimeError("SSH command not permitted")
    return match.group(1)


def account_identity(run_id):
    run_id = uuid.UUID(run_id)
    return {
        "runId": str(run_id),
        "username": f"board-load-{run_id.hex}",
        "accountId": str(uuid.uuid5(run_id, "commander-account")),
        "assignmentId": str(uuid.uuid5(run_id, "commander-assignment")),
    }


def load_manifest(path):
    if path.is_symlink() or not path.is_file() or path.stat().st_mode & 0o077:
        raise RuntimeError("Manifest must be a private regular file (mode 600).")
    manifest = json.loads(path.read_text())
    expected = account_identity(manifest["runId"])
    if any(manifest.get(key) != value for key, value in expected.items()):
        raise RuntimeError("Manifest identity mismatch; refusing cleanup.")
    if manifest.get("keycloakUserId"):
        uuid.UUID(manifest["keycloakUserId"])
    return manifest


def account_sql(manifest, cleanup=False):
    account_id = manifest["accountId"]
    assignment_id = manifest["assignmentId"]
    username = manifest["username"]
    if cleanup:
        return f"""
BEGIN;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM account
        WHERE id = '{account_id}'::uuid AND login_id != '{username}')
    OR EXISTS (SELECT 1 FROM incident_assignment
        WHERE id = '{assignment_id}'::uuid AND
            (account_id != '{account_id}'::uuid
             OR incident_id != '{path_fixtures.INCIDENT_ID}'::uuid))
    OR EXISTS (SELECT 1 FROM incident_assignment
        WHERE account_id = '{account_id}'::uuid AND id != '{assignment_id}'::uuid)
    OR EXISTS (SELECT 1 FROM police_phone WHERE account_id = '{account_id}'::uuid)
    OR EXISTS (SELECT 1 FROM marker WHERE created_by_account_id = '{account_id}'::uuid)
    THEN RAISE EXCEPTION 'Account ownership/use changed; refusing cleanup'; END IF;
END $$;
DELETE FROM incident_assignment WHERE id = '{assignment_id}'::uuid;
DELETE FROM account WHERE id = '{account_id}'::uuid AND login_id = '{username}';
COMMIT;
"""
    return f"""
BEGIN;
INSERT INTO account (id, login_id, display_name, account_type, organization_type,
                     password_hash, status, created_at, updated_at)
VALUES ('{account_id}'::uuid, '{username}', 'Board load commander', 'COMMAND',
        'MISSING_TEAM', 'oidc-only', 'ACTIVE', now(), now());
INSERT INTO incident_assignment (id, incident_id, account_id, incident_role,
                                assigned_at, created_at, updated_at)
VALUES ('{assignment_id}'::uuid, '{path_fixtures.INCIDENT_ID}'::uuid,
        '{account_id}'::uuid, 'INCIDENT_COMMANDER', now(), now(), now());
COMMIT;
"""


def find_keycloak_user(admin, username):
    query = urllib.parse.urlencode({"username": username, "exact": "true"})
    _, _, users = path_fixtures.admin_request(admin, "GET", f"users?{query}", {200})
    if len(users) > 1:
        raise RuntimeError("Expected at most one exact Keycloak username.")
    return users[0] if users else None


def check_keycloak_identity(user, manifest):
    attributes = user.get("attributes", {})
    if user.get("username") != manifest["username"] or any(
        attributes.get(key) != [value]
        for key, value in {
            "accountId": manifest["accountId"],
            "accountType": "COMMAND", "organizationType": "MISSING_TEAM",
        }.items()
    ):
        raise RuntimeError("Keycloak identity/claims mismatch; no existing user will be modified.")


@contextmanager
def temporary_admin_attribute_edit(admin, backup_path):
    _, _, original = path_fixtures.admin_request(admin, "GET", "users/profile", {200})
    with backup_path.open("x", encoding="utf-8") as backup:
        json.dump(original, backup, indent=2)
    editable = {**original, "unmanagedAttributePolicy": "ADMIN_EDIT"}
    try:
        path_fixtures.admin_request(admin, "PUT", "users/profile", {200}, editable)
        yield
    finally:
        _, _, current = path_fixtures.admin_request(admin, "GET", "users/profile", {200})
        if current != editable and current != original:
            raise RuntimeError("Profile changed concurrently; retain backup and inspect before restoring.")
        path_fixtures.restore_user_profile(admin, original)
        _, _, restored = path_fixtures.admin_request(admin, "GET", "users/profile", {200})
        if restored != original:
            raise RuntimeError("User profile restoration could not be verified; retain backup.")


def prepare_account(path, allow_temporary_profile_edit=False):
    report = inspect_notification_targets()
    check_preflight(report)
    admin = path_fixtures.AdminTokenProvider()
    # Use the existing browser client and login flow, not a new password-grant client.
    _, _, clients = path_fixtures.admin_request(
        admin, "GET", "clients?clientId=suri-map-web", {200}
    )
    if len(clients) != 1 or not clients[0].get("standardFlowEnabled"):
        raise RuntimeError("The existing suri-map-web browser login client is required.")
    manifest = account_identity(str(uuid.uuid4()))
    manifest.update(password=secrets.token_urlsafe(24), preflight=report)
    # Persist recovery identity before either external write. Never overwrite a run.
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8") as output:
        json.dump(manifest, output, indent=2)
        output.write("\n")
    attributes = {
        "accountId": [manifest["accountId"]], "accountType": ["COMMAND"],
        "organizationType": ["MISSING_TEAM"], "displayName": ["Board load commander"],
    }
    profile_edit = nullcontext()
    if allow_temporary_profile_edit:
        profile_edit = temporary_admin_attribute_edit(admin, path.with_suffix(".user-profile.json"))
    with profile_edit:
        _, headers, _ = path_fixtures.admin_request(admin, "POST", "users", {201}, {
            "username": manifest["username"], "enabled": True,
            "email": f"{manifest['username']}@suri-map.local", "emailVerified": True,
            "firstName": "Board", "lastName": "Load", "requiredActions": [],
            "attributes": attributes,
            "credentials": [{"type": "password", "value": manifest["password"], "temporary": False}],
        })
        manifest["keycloakUserId"] = str(uuid.UUID(headers["Location"].rstrip("/").rsplit("/", 1)[1]))
        # Retain proof of creation even if user-profile policy strips custom attributes.
        with tempfile.NamedTemporaryFile(mode="w", dir=path.parent, delete=False) as output:
            json.dump(manifest, output, indent=2)
            temporary_path = output.name
        os.replace(temporary_path, path)
        user = find_keycloak_user(admin, manifest["username"])
        check_keycloak_identity(user or {}, manifest)
    query_database(account_sql(manifest))
    # Users remain until cleanup; any approved temporary profile edit is already restored.
    print(f"Prepared browser account. Private login credentials: {path}")


def cleanup_account(path):
    manifest = load_manifest(path)
    admin = path_fixtures.AdminTokenProvider()
    user = find_keycloak_user(admin, manifest["username"])
    if user:
        if manifest.get("keycloakUserId"):
            if user["id"] != manifest["keycloakUserId"]:
                raise RuntimeError("Keycloak user was replaced; refusing cleanup.")
        else:
            check_keycloak_identity(user, manifest)
    # A DB ownership conflict stops before removing the login account.
    query_database(account_sql(manifest, cleanup=True))
    if user:
        path_fixtures.admin_request(admin, "DELETE", f"users/{user['id']}", {204})
    path.unlink()
    print("Removed only this run's commander account, assignment, and private manifest.")


def self_check():
    # One local check, no Docker, Keycloak, credentials, or extra test framework.
    import shutil
    from unittest.mock import patch

    with tempfile.TemporaryDirectory(prefix="suri-board-import-check-") as directory:
        for source in (Path(__file__), Path(path_fixtures.__file__)):
            shutil.copyfile(source, Path(directory) / source.name)
        subprocess.run(
            [sys.executable, "-I", str(Path(directory) / Path(__file__).name), "--help"],
            check=True, stdout=subprocess.DEVNULL,
        )
        assert not list(Path(directory).rglob("*.pyc")), "Tool import must not leave bytecode in deployment sources"

    command = "python3 /srv/apps/suri-map/infra/k6/prepare-situation-board-account.py check-marker-safety"
    backend = "a" * 64 + "/2026-10-01T00:00:00.000000000Z"
    assert expected_backend_from_ssh_command(command) is None
    assert expected_backend_from_ssh_command(command + " --expected-backend " + backend) == backend
    for denied in (
        "", "id", "internal-sftp", command.replace("check-marker-safety", "cleanup"),
        command + "; id", command + "\nid", command + " --manifest /tmp/account.json",
        command + " --expected-backend $(id)", command + " --expected-backend " + backend + "\n",
    ):
        try:
            expected_backend_from_ssh_command(denied)
        except RuntimeError:
            pass
        else:
            raise AssertionError("Unexpected SSH command was accepted")

    original = {"attributes": [{"name": "username"}]}
    state = dict(original)
    def profile_request(admin, method, endpoint, expected, json_body=None):
        nonlocal state
        if method == "PUT":
            state = dict(json_body)
        return 200, {}, dict(state)
    with tempfile.TemporaryDirectory(prefix="suri-board-profile-check-") as directory:
        with patch.object(path_fixtures, "admin_request", side_effect=profile_request):
            for fails in (False, True):
                try:
                    with temporary_admin_attribute_edit(None, Path(directory) / f"{fails}.json"):
                        assert state["unmanagedAttributePolicy"] == "ADMIN_EDIT"
                        if fails:
                            raise ValueError("simulated user creation failure")
                except ValueError:
                    assert fails
                assert state == original
    safe = {"incidentOpen": True, "periodActive": True, "activeFcmTokens": 0}
    check_preflight(safe)
    for invalid in (
        {**safe, "activeFcmTokens": 1}, {**safe, "incidentOpen": False},
        {**safe, "periodActive": False}, {},
    ):
        try:
            check_preflight(invalid)
        except RuntimeError:
            pass
        else:
            raise AssertionError("Unsafe preflight was accepted")
    runtime = {
        "Id": "test-backend", "State": {"Running": True, "StartedAt": "start-1"},
        "Config": {"Env": ["FCM_PROVIDER=mock"], "Entrypoint": ["java", "-jar", "/app/app.jar"]},
    }
    identity = check_mock_fcm_runtime(runtime)
    assert check_mock_fcm_runtime(runtime, identity) == identity
    for changed, expected in (
        ({**runtime, "Id": "replacement"}, identity),
        ({**runtime, "State": {"Running": True, "StartedAt": "start-2"}}, identity),
        ({**runtime, "Config": {**runtime["Config"], "Env": ["FCM_PROVIDER=firebase"]}}, None),
        ({**runtime, "Config": {**runtime["Config"], "Env": ["FCM_PROVIDER=mock", "SPRING_APPLICATION_JSON={}"]}}, None),
    ):
        try:
            check_mock_fcm_runtime(changed, expected)
        except RuntimeError:
            pass
        else:
            raise AssertionError("Changed/unverified FCM runtime was accepted")
    manifest = account_identity(str(uuid.uuid4()))
    with tempfile.TemporaryDirectory(prefix="suri-board-account-check-") as directory:
        path = Path(directory) / "account.json"
        path.write_text(json.dumps(manifest))
        os.chmod(path, 0o600)
        assert load_manifest(path) == manifest
        manifest["accountId"] = str(uuid.uuid4())
        path.write_text(json.dumps(manifest))
        try:
            load_manifest(path)
        except RuntimeError:
            pass
        else:
            raise AssertionError("Altered cleanup identity was accepted")
    print("PASS: no import bytecode, SSH command restrictions, preflight, mock FCM runtime/restart refusals and cleanup identity checks (no external calls).")


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("inspect", "check-marker-safety", "ssh-check-marker-safety", "prepare", "cleanup", "self-check"))
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--expected-backend", help="Identity from the first check-marker-safety result.")
    parser.add_argument("--allow-temporary-profile-edit", action="store_true",
                        help="Temporarily allow admin attribute editing; back up, restore and verify the profile.")
    args = parser.parse_args()
    if args.action in {"prepare", "cleanup"} and args.manifest is None:
        parser.error("prepare/cleanup require --manifest (private credentials/recovery file)")
    if args.action == "self-check":
        self_check()
    elif args.action == "check-marker-safety":
        print(json.dumps(check_marker_safety(args.expected_backend), sort_keys=True))
    elif args.action == "ssh-check-marker-safety":
        expected_backend = expected_backend_from_ssh_command(os.environ.get("SSH_ORIGINAL_COMMAND", ""))
        print(json.dumps(check_marker_safety(expected_backend), sort_keys=True))
    elif args.action == "inspect":
        report = inspect_notification_targets()
        print(json.dumps(report, sort_keys=True))
        check_preflight(report)
    elif args.action == "prepare":
        prepare_account(args.manifest, args.allow_temporary_profile_edit)
    else:
        cleanup_account(args.manifest)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Shared request helpers may include HTTP response bodies. Keep them out of logs.
        print(f"ERROR ({type(error).__name__}): operation incomplete; retain the private "
              "manifest for recovery. Check incident/FCM preflight and account ownership.",
              file=sys.stderr)
        sys.exit(1)
