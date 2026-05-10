# infra/ci

## verify-evidence.sh

`verify-evidence.sh` is the RED fixture for **L2-D01 CI 품질 게이트 결과 수집**. It checks, in a CI workspace after the Backend Test CI stage completes, that three real evidence files exist under `ci-artifacts/backend-test/`: a non-empty `coverage/jacoco.xml` (JaCoCo XML report), a `coverage/coverage-summary.txt` containing a `COVERAGE: <N>%` line extracted from that report, and a `sonar/sonar-status.txt` containing a `STATUS: SUCCESS` or `STATUS: SKIPPED_NO_SERVER` line from the SonarQube scanner. The script prints a pass/fail line for each check and exits 1 if any check fails. It currently fails (RED) because the Jenkinsfile only writes placeholder `README.txt` files and `backend/build.gradle` has no JaCoCo plugin configured; the coder closes it GREEN by adding JaCoCo to `build.gradle`, wiring coverage extraction and SonarQube scanning into the Jenkinsfile `Backend Test CI` stage, and removing the placeholder files.
