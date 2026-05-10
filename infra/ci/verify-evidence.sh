#!/bin/sh
# verify-evidence.sh
# RED fixture for L2-D01: verifies that the Backend Test CI stage has produced
# real coverage and SonarQube gate evidence under ci-artifacts/backend-test/.
#
# Exit codes:
#   0 — all checks passed
#   1 — one or more checks failed

ARTIFACTS_DIR="${ARTIFACTS_DIR:-ci-artifacts/backend-test}"

COVERAGE_DIR="${ARTIFACTS_DIR}/coverage"
SONAR_DIR="${ARTIFACTS_DIR}/sonar"

JACOCO_XML="${COVERAGE_DIR}/jacoco.xml"
COVERAGE_SUMMARY="${COVERAGE_DIR}/coverage-summary.txt"
SONAR_STATUS="${SONAR_DIR}/sonar-status.txt"

pass=0
fail=0

check_pass() {
    echo "  PASS: $1"
    pass=$((pass + 1))
}

check_fail() {
    echo "  FAIL: $1"
    fail=$((fail + 1))
}

echo "=== L2-D01 CI quality gate evidence check ==="
echo "Artifact root: ${ARTIFACTS_DIR}"
echo ""

# ------------------------------------------------------------------
# Check 1: jacoco.xml exists and is non-empty
# ------------------------------------------------------------------
echo "[1] ${JACOCO_XML} — exists and non-empty"
if [ -f "${JACOCO_XML}" ]; then
    size=$(wc -c < "${JACOCO_XML}")
    if [ "${size}" -gt 0 ]; then
        check_pass "jacoco.xml exists and is non-empty (${size} bytes)"
    else
        check_fail "jacoco.xml exists but is empty"
    fi
else
    check_fail "jacoco.xml not found (directory is empty or build did not produce coverage data)"
fi

# ------------------------------------------------------------------
# Check 2: coverage-summary.txt exists and has a COVERAGE: <pct> line
# ------------------------------------------------------------------
echo "[2] ${COVERAGE_SUMMARY} — exists and contains COVERAGE: <percentage>"
if [ -f "${COVERAGE_SUMMARY}" ]; then
    if grep -qE '^COVERAGE:[[:space:]]*[0-9]+(\.[0-9]+)?%' "${COVERAGE_SUMMARY}"; then
        coverage_line=$(grep -E '^COVERAGE:' "${COVERAGE_SUMMARY}" | head -1)
        check_pass "coverage-summary.txt contains: ${coverage_line}"
    else
        check_fail "coverage-summary.txt exists but has no 'COVERAGE: <N>%' line"
    fi
else
    check_fail "coverage-summary.txt not found (directory is empty or build did not produce coverage data)"
fi

# ------------------------------------------------------------------
# Check 3: sonar-status.txt exists and contains STATUS:
# ------------------------------------------------------------------
echo "[3] ${SONAR_STATUS} — exists and contains STATUS:"
if [ -f "${SONAR_STATUS}" ]; then
    if grep -qE '^STATUS:[[:space:]]*(SUCCESS|SKIPPED_NO_SERVER)' "${SONAR_STATUS}"; then
        status_line=$(grep -E '^STATUS:' "${SONAR_STATUS}" | head -1)
        check_pass "sonar-status.txt contains: ${status_line}"
    else
        check_fail "sonar-status.txt exists but has no 'STATUS: SUCCESS|SKIPPED_NO_SERVER' line"
    fi
else
    check_fail "sonar-status.txt not found (directory is empty or SonarQube step did not run)"
fi

# ------------------------------------------------------------------
# Summary
# ------------------------------------------------------------------
echo ""
echo "=== Results: ${pass} passed, ${fail} failed ==="

if [ "${fail}" -gt 0 ]; then
    echo "VERDICT: RED — evidence files are missing or are placeholder-only."
    echo "  The coder must wire JaCoCo, coverage extraction, and SonarQube scanning"
    echo "  into the Jenkinsfile Backend Test CI stage and backend/build.gradle."
    exit 1
fi

echo "VERDICT: GREEN — all CI quality gate evidence files are present and valid."
exit 0
