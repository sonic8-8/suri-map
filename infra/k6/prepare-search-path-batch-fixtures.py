#!/usr/bin/env python3

import argparse
import base64
import concurrent.futures
import json
import os
import secrets
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

KEYCLOAK_CONTAINER = "suri-map-keycloak"
POSTGRES_CONTAINER = "suri-map-postgis"
KEYCLOAK_BASE_URL = "http://127.0.0.1:18094/keycloak"
KEYCLOAK_PUBLIC_HOST = "suri-map.sonic8-8.com"
REALM = "suri-map"
CLIENT_ID = "suri-map-load-test"
USERNAME_PREFIX = "load-phone-"
DEFAULT_POLICE_PHONE_COUNT = 468
EXPECTED_ISSUER = f"https://{KEYCLOAK_PUBLIC_HOST}/keycloak/realms/{REALM}"
POINTS_PER_BATCH = 6
GPS_SAMPLE_INTERVAL_SECONDS = 2.5
PATH_BATCH_CREATION_INTERVAL_SECONDS = (
    POINTS_PER_BATCH * GPS_SAMPLE_INTERVAL_SECONDS
)
PREPOPULATED_PATH_BATCH_COUNTS = {
    "30m": 120,
    "8h": 1920,
    "24h": 5760,
}

INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"
OPERATIONAL_PERIOD_ID = "88888888-8888-8888-8888-888888880001"


def parse_args():
    parser = argparse.ArgumentParser(
        description="Prepare isolated Keycloak and PostgreSQL fixtures for the search-path load test."
    )
    parser.add_argument(
        "--police-phone-count", type=int, default=DEFAULT_POLICE_PHONE_COUNT
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(
            "/srv/apps/suri-map/load-test/path-append-requester-fixtures.json"
        ),
    )
    parser.add_argument(
        "--prepopulated-path-duration",
        choices=PREPOPULATED_PATH_BATCH_COUNTS,
        help="Fill each recording path with 30m, 8h, or 24h of GPS points and segments.",
    )
    parser.add_argument("--cleanup", action="store_true")
    return parser.parse_args()


def container_env(container, names):
    for name in names:
        result = subprocess.run(
            ["docker", "exec", container, "printenv", name],
            check=False,
            capture_output=True,
            text=True,
        )
        value = result.stdout.strip()
        if result.returncode == 0 and value:
            return value
    raise RuntimeError(f"Missing required admin environment variable in {container}")


def request(method, url, expected, *, token=None, json_body=None, form=None, public_host=False):
    headers = {}
    body = None
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if json_body is not None:
        body = json.dumps(json_body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    elif form is not None:
        body = urllib.parse.urlencode(form).encode("utf-8")
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    if public_host:
        headers["Host"] = KEYCLOAK_PUBLIC_HOST
        headers["X-Forwarded-Proto"] = "https"

    for attempt in range(4):
        try:
            req = urllib.request.Request(url, data=body, headers=headers, method=method)
            with urllib.request.urlopen(req, timeout=30) as response:
                response_body = response.read()
                if response.status not in expected:
                    raise RuntimeError(f"{method} {url} returned HTTP {response.status}")
                parsed = json.loads(response_body) if response_body else None
                return response.status, response.headers, parsed
        except urllib.error.HTTPError as error:
            response_body = error.read().decode("utf-8", errors="replace")
            if error.code not in {429, 500, 502, 503, 504} or attempt == 3:
                raise RuntimeError(
                    f"{method} {url} returned HTTP {error.code}: {response_body}"
                ) from error
        except urllib.error.URLError as error:
            if attempt == 3:
                raise RuntimeError(f"{method} {url} failed: {error}") from error
        time.sleep(0.5 * (attempt + 1))


class AdminTokenProvider:
    def __init__(self):
        self.username = container_env(
            KEYCLOAK_CONTAINER,
            ["KC_BOOTSTRAP_ADMIN_USERNAME", "KEYCLOAK_ADMIN"],
        )
        self.password = container_env(
            KEYCLOAK_CONTAINER,
            ["KC_BOOTSTRAP_ADMIN_PASSWORD", "KEYCLOAK_ADMIN_PASSWORD"],
        )
        self.access_token = None
        self.refresh_token = None
        self.refresh_at = 0
        self.lock = threading.Lock()

    def token(self):
        with self.lock:
            if self.access_token and time.monotonic() < self.refresh_at:
                return self.access_token
            self._renew()
            return self.access_token

    def _renew(self):
        if self.refresh_token:
            try:
                self._request_token(
                    {
                        "grant_type": "refresh_token",
                        "client_id": "admin-cli",
                        "refresh_token": self.refresh_token,
                    }
                )
                return
            except RuntimeError:
                self.refresh_token = None

        self._request_token(
            {
                "grant_type": "password",
                "client_id": "admin-cli",
                "username": self.username,
                "password": self.password,
            }
        )

    def _request_token(self, form):
        _, _, response = request(
            "POST",
            f"{KEYCLOAK_BASE_URL}/realms/master/protocol/openid-connect/token",
            {200},
            form=form,
        )
        expires_in = int(response["expires_in"])
        self.access_token = response["access_token"]
        self.refresh_token = response.get("refresh_token", self.refresh_token)
        self.refresh_at = time.monotonic() + max(1, expires_in - 15)


def admin_request(admin, method, path, expected, json_body=None):
    return request(
        method,
        f"{KEYCLOAK_BASE_URL}/admin/realms/{REALM}/{path}",
        expected,
        token=admin.token(),
        json_body=json_body,
    )


def existing_load_resources(admin):
    query = urllib.parse.urlencode(
        {
            "search": USERNAME_PREFIX,
            "max": 1000,
            "briefRepresentation": "true",
        }
    )
    _, _, users = admin_request(admin, "GET", f"users?{query}", {200})
    load_users = [user for user in users if user.get("username", "").startswith(USERNAME_PREFIX)]

    client_query = urllib.parse.urlencode({"clientId": CLIENT_ID})
    _, _, clients = admin_request(admin, "GET", f"clients?{client_query}", {200})
    return load_users, clients


def delete_keycloak_resources(admin):
    def delete_user(user):
        admin_request(admin, "DELETE", f"users/{user['id']}", {204})

    while True:
        users, clients = existing_load_resources(admin)
        if not users and not clients:
            return

        with concurrent.futures.ThreadPoolExecutor(max_workers=16) as executor:
            list(executor.map(delete_user, users))

        for client in clients:
            admin_request(admin, "DELETE", f"clients/{client['id']}", {204})


def allow_admin_to_edit_unmanaged_attributes(admin):
    _, _, original_profile = admin_request(admin, "GET", "users/profile", {200})
    updated_profile = dict(original_profile)
    updated_profile["unmanagedAttributePolicy"] = "ADMIN_EDIT"
    admin_request(admin, "PUT", "users/profile", {200}, updated_profile)
    return original_profile


def restore_user_profile(admin, original_profile):
    admin_request(admin, "PUT", "users/profile", {200}, original_profile)


def claim_mapper(claim_name):
    return {
        "name": claim_name,
        "protocol": "openid-connect",
        "protocolMapper": "oidc-usermodel-attribute-mapper",
        "consentRequired": False,
        "config": {
            "user.attribute": claim_name,
            "claim.name": claim_name,
            "jsonType.label": "String",
            "id.token.claim": "true",
            "access.token.claim": "true",
            "userinfo.token.claim": "true",
            "multivalued": "false",
        },
    }


def create_client(admin, client_secret):
    client = {
        "clientId": CLIENT_ID,
        "name": "Suri-Map load test",
        "enabled": True,
        "protocol": "openid-connect",
        "publicClient": False,
        "clientAuthenticatorType": "client-secret",
        "secret": client_secret,
        "standardFlowEnabled": False,
        "implicitFlowEnabled": False,
        "directAccessGrantsEnabled": True,
        "serviceAccountsEnabled": False,
        "fullScopeAllowed": True,
        "attributes": {
            "access.token.lifespan": "3600",
        },
        "protocolMappers": [
            claim_mapper("accountId"),
            claim_mapper("accountType"),
            claim_mapper("organizationType"),
        ],
    }
    admin_request(admin, "POST", "clients", {201}, client)
    query = urllib.parse.urlencode({"clientId": CLIENT_ID})
    _, _, clients = admin_request(admin, "GET", f"clients?{query}", {200})
    if len(clients) != 1:
        raise RuntimeError(f"Expected one {CLIENT_ID} client, found {len(clients)}")
    client_resource_id = clients[0]["id"]
    _, _, mappers = admin_request(
        admin,
        "GET",
        f"clients/{client_resource_id}/protocol-mappers/models",
        {200},
    )
    mapper_names = {mapper.get("name") for mapper in mappers}
    required_mappers = {"accountId", "accountType", "organizationType"}
    if not required_mappers.issubset(mapper_names):
        raise RuntimeError(f"Missing Keycloak protocol mappers: {required_mappers - mapper_names}")


def fixture_id(prefix, index):
    return f"{prefix}000000-0000-4000-8000-{index:012d}"


def load_user(index, password):
    return {
        "username": f"{USERNAME_PREFIX}{index:04d}",
        "enabled": True,
        "email": f"{USERNAME_PREFIX}{index:04d}@suri-map.local",
        "emailVerified": True,
        "firstName": "Load",
        "lastName": f"Phone {index:04d}",
        "requiredActions": [],
        "attributes": {
            "accountId": [fixture_id("a1", index)],
            "accountType": ["TEAM"],
            "organizationType": ["SUPPORT_UNIT"],
        },
        "credentials": [
            {
                "type": "password",
                "value": password,
                "temporary": False,
            }
        ],
    }


def create_users(admin, police_phone_count, password):
    users = [
        load_user(index, password) for index in range(1, police_phone_count + 1)
    ]

    def create_user(user):
        _, headers, _ = admin_request(admin, "POST", "users", {201}, user)
        location = headers.get("Location")
        if not location:
            raise RuntimeError(f"Keycloak did not return a Location header for {user['username']}")
        return {
            "id": location.rstrip("/").rsplit("/", 1)[-1],
            "username": user["username"],
            "accountId": user["attributes"]["accountId"][0],
        }

    with concurrent.futures.ThreadPoolExecutor(max_workers=16) as executor:
        created_users = list(executor.map(create_user, users))

    _, _, stored_user = admin_request(
        admin,
        "GET",
        f"users/{created_users[0]['id']}",
        {200},
    )
    stored_attributes = stored_user.get("attributes", {})
    if stored_attributes.get("accountId") != [created_users[0]["accountId"]]:
        raise RuntimeError(
            f"Keycloak did not store accountId: {stored_attributes.get('accountId')!r}"
        )
    return created_users


def access_token(username, password, client_secret):
    _, _, response = request(
        "POST",
        f"{KEYCLOAK_BASE_URL}/realms/{REALM}/protocol/openid-connect/token",
        {200},
        form={
            "grant_type": "password",
            "client_id": CLIENT_ID,
            "client_secret": client_secret,
            "username": username,
            "password": password,
            "scope": "openid",
        },
        public_host=True,
    )
    return response["access_token"]


def decode_jwt_payload(token):
    encoded = token.split(".")[1]
    encoded += "=" * (-len(encoded) % 4)
    return json.loads(base64.urlsafe_b64decode(encoded))


def scalar_claim(payload, claim_name):
    value = payload.get(claim_name)
    if isinstance(value, list) and len(value) == 1:
        return value[0]
    return value


def create_path_append_requester_fixtures(users, password, client_secret):
    def create_requester_fixture(user):
        token = access_token(user["username"], password, client_secret)
        payload = decode_jwt_payload(token)
        if payload.get("iss") != EXPECTED_ISSUER:
            raise RuntimeError(
                f"issuer claim mismatch for {user['username']}: {payload.get('iss')!r}"
            )
        if scalar_claim(payload, "accountId") != user["accountId"]:
            raise RuntimeError(
                f"accountId claim mismatch for {user['username']}: "
                f"{payload.get('accountId')!r}"
            )
        if scalar_claim(payload, "accountType") != "TEAM":
            raise RuntimeError(
                f"accountType claim mismatch for {user['username']}: "
                f"{payload.get('accountType')!r}"
            )
        if scalar_claim(payload, "organizationType") != "SUPPORT_UNIT":
            raise RuntimeError(
                f"organizationType claim mismatch for {user['username']}: "
                f"{payload.get('organizationType')!r}"
            )

        index = int(user["username"].removeprefix(USERNAME_PREFIX))
        return {
            "accountId": user["accountId"],
            "policePhoneId": fixture_id("b1", index),
            "pathId": fixture_id("e1", index),
            "accessToken": token,
            "expiresAt": payload["exp"],
        }

    with concurrent.futures.ThreadPoolExecutor(max_workers=24) as executor:
        requester_fixtures = list(executor.map(create_requester_fixture, users))

    minimum_lifetime = min(
        fixture["expiresAt"] for fixture in requester_fixtures
    ) - int(time.time())
    if minimum_lifetime < 3000:
        raise RuntimeError(
            f"Load-test access tokens are too short-lived: minimum {minimum_lifetime} seconds"
        )
    return requester_fixtures


def cleanup_sql():
    return """
BEGIN;

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM marker
        WHERE created_by_account_id::text LIKE 'a1000000-0000-4000-8000-%'
           OR police_phone_id::text LIKE 'b1000000-0000-4000-8000-%')
    THEN RAISE EXCEPTION 'Remove owned board-test markers before resetting GPS fixtures';
    END IF;
END $$;

DELETE FROM idempotency_record
WHERE idempotency_key LIKE 'load-test-%';

DELETE FROM event_dispatch_job
WHERE source_entity_id::text LIKE 'e1000000-0000-4000-8000-%';

DELETE FROM search_path_segment
WHERE search_path_id::text LIKE 'e1000000-0000-4000-8000-%';

DELETE FROM search_path
WHERE id::text LIKE 'e1000000-0000-4000-8000-%';

DELETE FROM duty_shift
WHERE id::text LIKE 'd1000000-0000-4000-8000-%';

DELETE FROM incident_assignment
WHERE id::text LIKE 'c1000000-0000-4000-8000-%';

DELETE FROM police_phone
WHERE id::text LIKE 'b1000000-0000-4000-8000-%';

DELETE FROM account
WHERE id::text LIKE 'a1000000-0000-4000-8000-%';

COMMIT;
"""


def seed_sql(police_phone_count):
    return (
        cleanup_sql()
        + f"""
BEGIN;

WITH fixture AS (
    SELECT
        value,
        ('a1000000-0000-4000-8000-' || lpad(value::text, 12, '0'))::uuid AS account_id,
        ('b1000000-0000-4000-8000-' || lpad(value::text, 12, '0'))::uuid AS phone_id,
        ('c1000000-0000-4000-8000-' || lpad(value::text, 12, '0'))::uuid AS assignment_id,
        ('d1000000-0000-4000-8000-' || lpad(value::text, 12, '0'))::uuid AS duty_shift_id,
        ('e1000000-0000-4000-8000-' || lpad(value::text, 12, '0'))::uuid AS path_id
    FROM generate_series(1, {police_phone_count}) AS value
)
INSERT INTO account (
    id, login_id, display_name, account_type, organization_type,
    password_hash, status, created_at, updated_at
)
SELECT
    account_id,
    'load-phone-' || lpad(value::text, 4, '0'),
    'Load phone ' || lpad(value::text, 4, '0'),
    'TEAM',
    'SUPPORT_UNIT',
    'oidc-only',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM fixture;

WITH fixture AS (
    SELECT
        value,
        ('a1000000-0000-4000-8000-' || lpad(value::text, 12, '0'))::uuid AS account_id,
        ('b1000000-0000-4000-8000-' || lpad(value::text, 12, '0'))::uuid AS phone_id
    FROM generate_series(1, {police_phone_count}) AS value
)
INSERT INTO police_phone (
    id, phone_code, display_name, account_id, status, registered,
    version, created_at, updated_at
)
SELECT
    phone_id,
    'load-phone-' || lpad(value::text, 4, '0'),
    'Load phone ' || lpad(value::text, 4, '0'),
    account_id,
    'ACTIVE',
    TRUE,
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM fixture;

WITH fixture AS (
    SELECT
        value,
        ('a1000000-0000-4000-8000-' || lpad(value::text, 12, '0'))::uuid AS account_id,
        ('c1000000-0000-4000-8000-' || lpad(value::text, 12, '0'))::uuid AS assignment_id
    FROM generate_series(1, {police_phone_count}) AS value
)
INSERT INTO incident_assignment (
    id, incident_id, account_id, incident_role, assigned_at,
    revoked_at, created_at, updated_at
)
SELECT
    assignment_id,
    '{INCIDENT_ID}'::uuid,
    account_id,
    'MEMBER',
    CURRENT_TIMESTAMP,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM fixture;

WITH fixture AS (
    SELECT
        value,
        ('a1000000-0000-4000-8000-' || lpad(value::text, 12, '0'))::uuid AS account_id,
        ('b1000000-0000-4000-8000-' || lpad(value::text, 12, '0'))::uuid AS phone_id,
        ('c1000000-0000-4000-8000-' || lpad(value::text, 12, '0'))::uuid AS assignment_id,
        ('d1000000-0000-4000-8000-' || lpad(value::text, 12, '0'))::uuid AS duty_shift_id
    FROM generate_series(1, {police_phone_count}) AS value
)
INSERT INTO duty_shift (
    id, operational_period_id, incident_assignment_id, police_phone_id,
    status, started_by_account_id, ended_by_account_id, started_at,
    ended_at, version, created_at, updated_at
)
SELECT
    duty_shift_id,
    '{OPERATIONAL_PERIOD_ID}'::uuid,
    assignment_id,
    phone_id,
    'ACTIVE',
    account_id,
    NULL,
    CURRENT_TIMESTAMP,
    NULL,
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM fixture;

WITH fixture AS (
    SELECT
        value,
        ('a1000000-0000-4000-8000-' || lpad(value::text, 12, '0'))::uuid AS account_id,
        ('d1000000-0000-4000-8000-' || lpad(value::text, 12, '0'))::uuid AS duty_shift_id,
        ('e1000000-0000-4000-8000-' || lpad(value::text, 12, '0'))::uuid AS path_id
    FROM generate_series(1, {police_phone_count}) AS value
)
INSERT INTO search_path (
    id, duty_shift_id, account_id, status, started_at, ended_at,
    geometry, version, created_at, updated_at
)
SELECT
    path_id,
    duty_shift_id,
    account_id,
    'RECORDING',
    CURRENT_TIMESTAMP,
    NULL,
    NULL,
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM fixture;

COMMIT;
"""
    )


def prepopulated_path_sql(police_phone_count, batch_count):
    point_count = batch_count * POINTS_PER_BATCH
    path_started_at = (
        "CURRENT_TIMESTAMP "
        f"- ({batch_count} * INTERVAL '{PATH_BATCH_CREATION_INTERVAL_SECONDS} seconds') "
        f"- INTERVAL '{PATH_BATCH_CREATION_INTERVAL_SECONDS - GPS_SAMPLE_INTERVAL_SECONDS} seconds'"
    )
    return f"""
BEGIN;

WITH fixture AS (
    SELECT
        value - 1 AS requester_index,
        ('e1000000-0000-4000-8000-' || lpad(value::text, 12, '0'))::uuid AS path_id
    FROM generate_series(1, {police_phone_count}) AS value
)
INSERT INTO search_path_gps_point (
    search_path_id,
    point_order,
    point_id,
    client_ts,
    lon,
    lat,
    speed_mps,
    horizontal_accuracy_m,
    location_provider,
    elapsed_realtime_nanos,
    created_at
)
SELECT
    path_id,
    point_order,
    'load-fixture-' || requester_index || '-' || point_order,
    {path_started_at} + point_order * INTERVAL '{GPS_SAMPLE_INTERVAL_SECONDS} seconds',
    round((
        126.9
        + (requester_index % 100) * 0.00005
        + (point_order / {POINTS_PER_BATCH}) * 0.00015
        + (point_order % {POINTS_PER_BATCH}) * 0.000025
    )::numeric, 6),
    round((
        35.16
        + (requester_index / 100) * 0.0001
        + (point_order % {POINTS_PER_BATCH}) * 0.000005
    )::numeric, 6),
    1.2,
    8,
    'gps',
    (point_order + 1)::bigint * {int(GPS_SAMPLE_INTERVAL_SECONDS * 1_000_000_000)},
    {path_started_at} + point_order * INTERVAL '{GPS_SAMPLE_INTERVAL_SECONDS} seconds'
FROM fixture
CROSS JOIN generate_series(0, {point_count - 1}) AS points(point_order);

WITH path_history AS (
    SELECT
        search_path_id,
        min(client_ts) AS started_at,
        ST_MakeLine(
            ST_SetSRID(ST_MakePoint(lon::double precision, lat::double precision), 4326)
            ORDER BY point_order
        ) AS geometry
    FROM search_path_gps_point
    WHERE search_path_id::text LIKE 'e1000000-0000-4000-8000-%'
    GROUP BY search_path_id
)
UPDATE search_path AS path
SET started_at = history.started_at,
    geometry = history.geometry,
    version = {batch_count + 1},
    created_at = history.started_at,
    updated_at = CURRENT_TIMESTAMP
FROM path_history AS history
WHERE path.id = history.search_path_id;

WITH segment_history AS (
    SELECT
        search_path_id,
        point_order / {POINTS_PER_BATCH} AS batch_index,
        min(client_ts) AS started_at,
        max(client_ts) AS ended_at,
        ST_MakeLine(
            ST_SetSRID(ST_MakePoint(lon::double precision, lat::double precision), 4326)
            ORDER BY point_order
        ) AS geometry
    FROM search_path_gps_point
    WHERE search_path_id::text LIKE 'e1000000-0000-4000-8000-%'
    GROUP BY search_path_id, point_order / {POINTS_PER_BATCH}
)
INSERT INTO search_path_segment (
    id,
    search_path_id,
    movement_type,
    movement_type_source,
    geometry,
    started_at,
    ended_at,
    corrected_by_account_id,
    corrected_at,
    version,
    created_at,
    updated_at
)
SELECT
    md5('load-prepopulated-segment:' || search_path_id || ':' || batch_index)::uuid,
    search_path_id,
    'FOOT',
    'AUTO',
    geometry,
    started_at,
    ended_at,
    NULL,
    NULL,
    1,
    started_at,
    ended_at
FROM segment_history;

COMMIT;

ANALYZE search_path;
ANALYZE search_path_gps_point;
ANALYZE search_path_segment;
"""


def run_psql(sql):
    subprocess.run(
        [
            "docker",
            "exec",
            "-i",
            POSTGRES_CONTAINER,
            "sh",
            "-lc",
            'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1',
        ],
        input=sql,
        text=True,
        check=True,
    )


def write_path_append_requester_fixtures(path, requester_fixtures):
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(path.parent, 0o700)
    temporary_path = path.with_suffix(path.suffix + ".tmp")
    with open(temporary_path, "w", encoding="utf-8") as output:
        json.dump(requester_fixtures, output, ensure_ascii=True, separators=(",", ":"))
        output.write("\n")
    os.chmod(temporary_path, 0o600)
    os.replace(temporary_path, path)


def main():
    os.umask(0o077)
    args = parse_args()
    if not 1 <= args.police_phone_count <= 9999:
        raise RuntimeError("--police-phone-count must be between 1 and 9999")

    admin = AdminTokenProvider()
    delete_keycloak_resources(admin)

    if args.cleanup:
        run_psql(cleanup_sql())
        args.output.unlink(missing_ok=True)
        print("Removed search-path load-test fixtures.")
        return

    password = secrets.token_urlsafe(24)
    client_secret = secrets.token_urlsafe(32)
    original_profile = allow_admin_to_edit_unmanaged_attributes(admin)
    try:
        create_client(admin, client_secret)
        users = create_users(admin, args.police_phone_count, password)
        requester_fixtures = create_path_append_requester_fixtures(
            users, password, client_secret
        )
        sql = seed_sql(args.police_phone_count)
        if args.prepopulated_path_duration:
            batch_count = PREPOPULATED_PATH_BATCH_COUNTS[
                args.prepopulated_path_duration
            ]
            for fixture in requester_fixtures:
                fixture["prepopulatedPathDuration"] = (
                    args.prepopulated_path_duration
                )
                fixture["prepopulatedBatchCount"] = batch_count
            sql += prepopulated_path_sql(args.police_phone_count, batch_count)
        run_psql(sql)
        write_path_append_requester_fixtures(args.output, requester_fixtures)
    finally:
        try:
            delete_keycloak_resources(admin)
        finally:
            restore_user_profile(admin, original_profile)

    print(f"Prepared {len(requester_fixtures)} requester fixtures at {args.output}.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"ERROR: {error}", file=sys.stderr)
        sys.exit(1)
