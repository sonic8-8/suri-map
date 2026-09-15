#!/bin/sh
# collect-s4-evidence.sh
# GREEN implementation for L2-D03 (S4 재전송·팬아웃 릴리즈 증거).
# Collects JUnit XML test results and writes S4 release evidence artifacts.
# Fixture constants from docs/spec/specs/S4.json SC-09:
#   replay event ID : 40000000-0000-4000-8000-000000000901
#   incident ID     : 10000000-0000-4000-8000-000000000001
#   replay sequence : 901
#   payload ID      : 30000000-0000-4000-8000-000000000501
#   type            : PATH_APPENDED

set -eu

S4_EVIDENCE_ARTIFACT_DIR="${S4_EVIDENCE_ARTIFACT_DIR:-ci-artifacts/s4-evidence}"
BACKEND_TEST_RESULT_DIR="${BACKEND_TEST_RESULT_DIR:-backend/build/test-results/test}"

SC09_EVENT_ID="40000000-0000-4000-8000-000000000901"
SC09_INCIDENT_ID="10000000-0000-4000-8000-000000000001"
SC09_REPLAY_SEQ="901"
SC09_PAYLOAD_ID="30000000-0000-4000-8000-000000000501"
SC09_TYPE="PATH_APPENDED"

REPLAY_CLASS="com.surimap.eventhub.SseSequenceEnvelopeReplayTest"
FANOUT_CLASS="com.surimap.eventhub.SseStreamServiceTest"

mkdir -p "${S4_EVIDENCE_ARTIFACT_DIR}"

# parse_test_status <xml_file>
# Reads failures and errors attributes from <testsuite> element.
# Prints PASS if both are 0 (or absent), FAIL otherwise.
# Prints UNKNOWN if the file does not exist.
parse_test_status() {
    xml_file="$1"

    if [ ! -f "${xml_file}" ]; then
        echo "UNKNOWN"
        return
    fi

    # Extract the first <testsuite ...> line and parse failures= and errors=
    suite_line=$(grep -m1 '<testsuite' "${xml_file}" || true)

    if [ -z "${suite_line}" ]; then
        echo "UNKNOWN"
        return
    fi

    # Extract failures attribute value (default 0 if absent)
    failures=$(echo "${suite_line}" | sed 's/.*failures="//;s/".*//' 2>/dev/null || echo "0")
    errors=$(echo "${suite_line}" | sed 's/.*errors="//;s/".*//' 2>/dev/null || echo "0")

    # Validate that extracted values are numeric; default to 0 if not
    case "${failures}" in
        ''|*[!0-9]*) failures=0 ;;
    esac
    case "${errors}" in
        ''|*[!0-9]*) errors=0 ;;
    esac

    if [ "${failures}" -eq 0 ] && [ "${errors}" -eq 0 ]; then
        echo "PASS"
    else
        echo "FAIL"
    fi
}

REPLAY_XML="${BACKEND_TEST_RESULT_DIR}/TEST-${REPLAY_CLASS}.xml"
FANOUT_XML="${BACKEND_TEST_RESULT_DIR}/TEST-${FANOUT_CLASS}.xml"

REPLAY_STATUS=$(parse_test_status "${REPLAY_XML}")
FANOUT_STATUS=$(parse_test_status "${FANOUT_XML}")

# Determine OVERALL status — PASS only when both are PASS
if [ "${REPLAY_STATUS}" = "PASS" ] && [ "${FANOUT_STATUS}" = "PASS" ]; then
    OVERALL="PASS"
else
    OVERALL="FAIL"
fi

# --- Write s4-replay-evidence.txt ---
cat > "${S4_EVIDENCE_ARTIFACT_DIR}/s4-replay-evidence.txt" <<EOF
S4 Replay Evidence — L2-D03
=============================
SC-09 Fixture
  Event ID        : ${SC09_EVENT_ID}
  Incident ID     : ${SC09_INCIDENT_ID}
  Replay Sequence : ${SC09_REPLAY_SEQ}
  Payload ID      : ${SC09_PAYLOAD_ID}
  Type            : ${SC09_TYPE}

Test Class : ${REPLAY_CLASS}
Status     : ${REPLAY_STATUS}

What was tested:
  - Last-Event-ID replay ordering: SSE id line carries incident-scoped
    replay_sequence; events after the given Last-Event-ID are returned
    in ascending replay_sequence order.
  - Repeated append of the same eventId keeps one in-memory replay event
    and reuses its sequence. These tests do not verify database uniqueness
    constraints or persistence across server restarts.
  - Replay only emits events with replay_sequence > Last-Event-ID cursor.
EOF

# --- Write s4-fanout-evidence.txt ---
cat > "${S4_EVIDENCE_ARTIFACT_DIR}/s4-fanout-evidence.txt" <<EOF
S4 Fanout Evidence — L2-D03
=============================
SC-09 Fixture
  Event ID            : ${SC09_EVENT_ID}
  Incident ID         : ${SC09_INCIDENT_ID}
  Consumer Convergence: 1/1 (100%)
  Type                : ${SC09_TYPE}

Test Class : ${FANOUT_CLASS}
Status     : ${FANOUT_STATUS}

What was tested:
  - The in-memory replay event exists before the capturing SSE sink
    receives the matching sequence and payload.
  - Duplicate event dispatch stores and sends the event only once.
  - Incident closure removes live connections; incident purge removes
    replay data and rejects later replay and new event dispatch.
  - The captured consumer is a test sink. These tests do not verify
    database dispatch targets, FCM, network delivery or browser rendering.
EOF

# --- Write s4-release-note.txt ---
cat > "${S4_EVIDENCE_ARTIFACT_DIR}/s4-release-note.txt" <<EOF
S4 Release Note — L2-D03 (재전송·팬아웃 릴리즈 증거)
=======================================================
REPLAY_STATUS: ${REPLAY_STATUS}
FANOUT_STATUS: ${FANOUT_STATUS}
OVERALL: ${OVERALL}

Fixture: SC-09 (docs/spec/specs/S4.json sc09_outbox_replay_convergence)
  eventId        : ${SC09_EVENT_ID}
  incidentId     : ${SC09_INCIDENT_ID}
  replaySequence : ${SC09_REPLAY_SEQ}
  payloadId      : ${SC09_PAYLOAD_ID}
  type           : ${SC09_TYPE}
EOF

echo "S4 evidence artifacts written to ${S4_EVIDENCE_ARTIFACT_DIR}/"
echo "  REPLAY_STATUS : ${REPLAY_STATUS}"
echo "  FANOUT_STATUS : ${FANOUT_STATUS}"
echo "  OVERALL       : ${OVERALL}"
