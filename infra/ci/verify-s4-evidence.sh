#!/bin/sh
# Fail closed when DB replay or live dispatch tests failed, were skipped, or are absent.

set -eu

S4_EVIDENCE_ARTIFACT_DIR="${S4_EVIDENCE_ARTIFACT_DIR:-ci-artifacts/s4-evidence}"

REPLAY_FILE="${S4_EVIDENCE_ARTIFACT_DIR}/s4-replay-evidence.txt"
FANOUT_FILE="${S4_EVIDENCE_ARTIFACT_DIR}/s4-fanout-evidence.txt"
NOTE_FILE="${S4_EVIDENCE_ARTIFACT_DIR}/s4-release-note.txt"

fail() {
    echo "FAIL: $1" >&2
    exit 1
}

# --- s4-replay-evidence.txt ---
[ -s "${REPLAY_FILE}" ] || fail "${REPLAY_FILE} is missing or empty"

grep -qE '^Test Class : com\.surimap\.api\.service\.sse\.ServerSentEventHistoryServiceTest$' "${REPLAY_FILE}" \
    || fail "${REPLAY_FILE} does not identify the current replay test"

# --- s4-fanout-evidence.txt ---
[ -s "${FANOUT_FILE}" ] || fail "${FANOUT_FILE} is missing or empty"

grep -qE '^Test Class : com\.surimap\.global\.sse\.ServerSentEventJobWorkerTest$' "${FANOUT_FILE}" \
    || fail "${FANOUT_FILE} does not identify the current dispatch test"

# --- s4-release-note.txt ---
[ -s "${NOTE_FILE}" ] || fail "${NOTE_FILE} is missing or empty"

grep -qE "^REPLAY_STATUS: PASS$" "${NOTE_FILE}" \
    || fail "${NOTE_FILE}: REPLAY_STATUS is not PASS"
grep -qE "^FANOUT_STATUS: PASS$" "${NOTE_FILE}" \
    || fail "${NOTE_FILE}: FANOUT_STATUS is not PASS"
grep -qE "^OVERALL: PASS$" "${NOTE_FILE}" \
    || fail "${NOTE_FILE}: OVERALL is not PASS"

echo "OK: S4 evidence artifacts verified"
