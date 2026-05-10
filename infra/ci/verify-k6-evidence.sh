#!/bin/sh
# verify-k6-evidence.sh — Checks that the k6 smoke stage produced its expected
# artifact files under K6_SMOKE_ARTIFACT_DIR (default: ci-artifacts/k6-smoke).
# Exits 0 when all checks pass, 1 on any failure.

ARTIFACTS_DIR="${K6_SMOKE_ARTIFACT_DIR:-ci-artifacts/k6-smoke}"

PASS_COUNT=0
FAIL_COUNT=0

check_pass() {
    echo "PASS: $1"
    PASS_COUNT=$((PASS_COUNT + 1))
}

check_fail() {
    echo "FAIL: $1"
    FAIL_COUNT=$((FAIL_COUNT + 1))
}

# Check 1: k6-results.json exists and is non-empty (k6 --out json output)
TARGET="${ARTIFACTS_DIR}/k6-results.json"
if [ -s "$TARGET" ]; then
    check_pass "${TARGET} exists and is non-empty"
else
    check_fail "${TARGET} missing or empty"
fi

# Check 2: k6-output.txt exists and is non-empty (k6 stdout capture)
TARGET="${ARTIFACTS_DIR}/k6-output.txt"
if [ -s "$TARGET" ]; then
    check_pass "${TARGET} exists and is non-empty"
else
    check_fail "${TARGET} missing or empty"
fi

# Check 3: k6-threshold-summary.txt exists and contains a STATUS: PASS or STATUS: FAIL line
TARGET="${ARTIFACTS_DIR}/k6-threshold-summary.txt"
if [ -f "$TARGET" ]; then
    if grep -qE '^STATUS: (PASS|FAIL)$' "$TARGET"; then
        check_pass "${TARGET} contains a valid STATUS: line"
    else
        check_fail "${TARGET} exists but contains no valid 'STATUS: PASS' or 'STATUS: FAIL' line"
    fi
else
    check_fail "${TARGET} missing"
fi

# Summary
echo ""
echo "Results: ${PASS_COUNT} passed, ${FAIL_COUNT} failed"

if [ "$FAIL_COUNT" -gt 0 ]; then
    exit 1
fi

exit 0
