#!/bin/sh
# Collect the current DB replay and SSE dispatch test results.
# Artifact filenames are retained for the existing Jenkins stage.
set -eu

S4_EVIDENCE_ARTIFACT_DIR="${S4_EVIDENCE_ARTIFACT_DIR:-ci-artifacts/s4-evidence}"
BACKEND_TEST_RESULT_DIR="${BACKEND_TEST_RESULT_DIR:-backend/build/test-results/test}"
REPLAY_CLASS="com.surimap.eventhub.SseReplayServiceTest"
FANOUT_CLASS="com.surimap.eventhub.SseStreamServiceTest"

mkdir -p "${S4_EVIDENCE_ARTIFACT_DIR}"

parse_test_status() {
    if [ ! -f "$1" ]; then
        echo "UNKNOWN"
        return
    fi
    awk '
        function attribute(name, pattern, value) {
            pattern = " " name "=\"[0-9]+\""
            if (!match($0, pattern)) return -1
            value = substr($0, RSTART, RLENGTH)
            gsub(/[^0-9]/, "", value)
            return value + 0
        }
        /<testsuite / {
            tests = attribute("tests")
            failures = attribute("failures")
            errors = attribute("errors")
            skipped = attribute("skipped")
            if (tests <= 0 || failures < 0 || errors < 0 || skipped < 0) status = "UNKNOWN"
            else if (failures > 0 || errors > 0) status = "FAIL"
            else if (skipped > 0) status = "SKIPPED"
            else status = "PASS"
            print status
            found = 1
            exit
        }
        END { if (!found) print "UNKNOWN" }
    ' "$1"
}

REPLAY_STATUS=$(parse_test_status "${BACKEND_TEST_RESULT_DIR}/TEST-${REPLAY_CLASS}.xml")
FANOUT_STATUS=$(parse_test_status "${BACKEND_TEST_RESULT_DIR}/TEST-${FANOUT_CLASS}.xml")
OVERALL="FAIL"
if [ "${REPLAY_STATUS}" = "PASS" ] && [ "${FANOUT_STATUS}" = "PASS" ]; then
    OVERALL="PASS"
fi

cat > "${S4_EVIDENCE_ARTIFACT_DIR}/s4-replay-evidence.txt" <<EOF
SSE DB Replay Test Results
Test Class : ${REPLAY_CLASS}
Status     : ${REPLAY_STATUS}

Coverage:
  - PostgreSQL history is read in sequence order, at most 100 events per page.
  - Reconnect rejects missing history and invalid or future cursors.
  - Page reads retain the initial upper sequence and recheck incident state.
  - Legacy completed jobs without a sequence remain unchanged.
Limits:
  - Not a real JVM restart, network delivery or browser recovery test.
  - A skipped test is not a pass.
EOF

cat > "${S4_EVIDENCE_ARTIFACT_DIR}/s4-fanout-evidence.txt" <<EOF
SSE Live Dispatch Test Results
Test Class : ${FANOUT_CLASS}
Status     : ${FANOUT_STATUS}

Coverage:
  - A capturing sink receives the committed DB sequence and payload outside a transaction.
  - Retries send the same sequence again while retaining one DB history entry.
  - Terminal DB state blocks old payload; closure removes live connections.
Limits:
  - DB history deletion is not implemented by live dispatch.
  - The sink is a test double, not an actual browser or FCM recipient.
  - A skipped test is not a pass.
EOF

cat > "${S4_EVIDENCE_ARTIFACT_DIR}/s4-release-note.txt" <<EOF
SSE Replay and Dispatch Verification
REPLAY_STATUS: ${REPLAY_STATUS}
FANOUT_STATUS: ${FANOUT_STATUS}
OVERALL: ${OVERALL}
EOF

echo "SSE test artifacts written to ${S4_EVIDENCE_ARTIFACT_DIR}/"
echo "  REPLAY_STATUS : ${REPLAY_STATUS}"
echo "  FANOUT_STATUS : ${FANOUT_STATUS}"
echo "  OVERALL       : ${OVERALL}"
