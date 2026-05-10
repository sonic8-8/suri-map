#!/bin/sh
# verify-s4-evidence.sh
# RED verification script for L2-D03 (S4 재전송·팬아웃 릴리즈 증거).
# Exits 1 when required S4 evidence artifacts are missing or malformed.
# Fixture constants from docs/spec/specs/S4.json SC-09:
#   replay event ID : 40000000-0000-4000-8000-000000000901
#   incident ID     : 10000000-0000-4000-8000-000000000001
#   replay sequence : 901

set -eu

S4_EVIDENCE_ARTIFACT_DIR="${S4_EVIDENCE_ARTIFACT_DIR:-ci-artifacts/s4-evidence}"

REPLAY_FILE="${S4_EVIDENCE_ARTIFACT_DIR}/s4-replay-evidence.txt"
FANOUT_FILE="${S4_EVIDENCE_ARTIFACT_DIR}/s4-fanout-evidence.txt"
NOTE_FILE="${S4_EVIDENCE_ARTIFACT_DIR}/s4-release-note.txt"

SC09_EVENT_ID="40000000-0000-4000-8000-000000000901"

fail() {
    echo "FAIL: $1" >&2
    exit 1
}

# --- s4-replay-evidence.txt ---
[ -s "${REPLAY_FILE}" ] || fail "${REPLAY_FILE} is missing or empty"

grep -qF "${SC09_EVENT_ID}" "${REPLAY_FILE}" \
    || fail "${REPLAY_FILE} does not contain SC-09 event ID ${SC09_EVENT_ID}"

# --- s4-fanout-evidence.txt ---
[ -s "${FANOUT_FILE}" ] || fail "${FANOUT_FILE} is missing or empty"

grep -qF "${SC09_EVENT_ID}" "${FANOUT_FILE}" \
    || fail "${FANOUT_FILE} does not contain SC-09 event ID ${SC09_EVENT_ID}"

# --- s4-release-note.txt ---
[ -s "${NOTE_FILE}" ] || fail "${NOTE_FILE} is missing or empty"

grep -qE "^REPLAY_STATUS: PASS$" "${NOTE_FILE}" \
    || fail "${NOTE_FILE}: REPLAY_STATUS is not PASS"
grep -qE "^FANOUT_STATUS: PASS$" "${NOTE_FILE}" \
    || fail "${NOTE_FILE}: FANOUT_STATUS is not PASS"
grep -qE "^OVERALL: PASS$" "${NOTE_FILE}" \
    || fail "${NOTE_FILE}: OVERALL is not PASS"

echo "OK: S4 evidence artifacts verified"
