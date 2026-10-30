// Mirror of the Hetzner Jenkins job's inline pipeline; see README.md before applying.
def notifyDiscord(String status) {
  try {
    withEnv(["DISCORD_STATUS=${status}"]) {
      withCredentials([string(credentialsId: env.DISCORD_CREDENTIAL_ID, variable: 'DISCORD_WEBHOOK_URL')]) {
        sh '''
set +x
branch="${GIT_BRANCH:-${BRANCH:-}}"
[ -n "$branch" ] || branch="-"
commit="${GIT_COMMIT:-unknown}"
short_commit="$(printf '%s' "$commit" | cut -c1-12)"
failed_stage="${FAILED_STAGE:-}"
[ -n "$failed_stage" ] || failed_stage="-"

case "$DISCORD_STATUS" in
  SUCCESS)
    status_label="성공"
    color=3066993
    description="${PROJECT_NAME} 배포가 끝났습니다."
    ;;
  UNSTABLE)
    status_label="불안정"
    color=16776960
    description="${PROJECT_NAME} 배포는 끝났지만 ${failed_stage} 단계가 실패했습니다."
    ;;
  ABORTED)
    status_label="중단"
    color=9807270
    description="${PROJECT_NAME} 배포가 중단됐습니다."
    ;;
  *)
    status_label="실패"
    color=15158332
    description="${PROJECT_NAME} 배포가 ${failed_stage} 단계에서 멈췄습니다."
    ;;
esac

build_url="${BUILD_URL:-}"
[ -n "$build_url" ] || build_url="https://jenkins.sonic8-8.com/job/${JOB_NAME}/${BUILD_NUMBER}/"

jq -n \
  --arg title "${PROJECT_NAME} 배포 ${status_label}" \
  --arg description "$description" \
  --arg build "#${BUILD_NUMBER}" \
  --arg branch "$branch" \
  --arg commit "$short_commit" \
  --arg stage "$failed_stage" \
  --arg url "$build_url" \
  --argjson color "$color" \
  '{embeds: [{title: $title, description: $description, color: $color, url: $url, fields: [{name: "빌드", value: $build, inline: true}, {name: "브랜치", value: $branch, inline: true}, {name: "커밋", value: $commit, inline: true}, {name: "단계", value: $stage, inline: true}, {name: "Jenkins", value: ("[빌드 로그](" + $url + ")"), inline: false}]}]}' \
  > .discord-payload.json
curl -fsS -H "Content-Type: application/json" --data @.discord-payload.json "$DISCORD_WEBHOOK_URL" >/dev/null || true
        '''
      }
    }
  } catch (err) {
    echo 'Discord notification skipped.'
  }
}

pipeline {
  agent any

  triggers { githubPush() }

  options {
    timestamps()
    disableConcurrentBuilds()
    // Stage restart must not bypass tests and image preservation.
    disableRestartFromStage()
  }

  environment {
    PROJECT_NAME = '수리맵'
    REPO_URL = 'https://github.com/sonic8-8/suri-map.git'
    BRANCH = '*/develop'
    APP_HOST = '167.233.204.148'
    APP_DIR = '/srv/apps/suri-map'
    DISCORD_CREDENTIAL_ID = 'discord-suri-map-webhook-url'
    BACKEND_TEST_IMAGE = 'suri-map-backend-test:jenkins'
    BACKEND_TEST_ARTIFACT_DIR = "ci-artifacts/backend-test/${BUILD_NUMBER}"
    BACKEND_TEST_RESULT_DIR = "${BACKEND_TEST_ARTIFACT_DIR}/test-results"
    S4_EVIDENCE_ARTIFACT_DIR = "${BACKEND_TEST_ARTIFACT_DIR}/sse"
    FRONTEND_TEST_IMAGE = 'suri-map-frontend-test:jenkins'
    FRONTEND_TEST_ARTIFACT_DIR = "ci-artifacts/frontend-test/${BUILD_NUMBER}"
    // The checks load a .ts module, so Node must strip types by default.
    PATH_DISPLAY_TIMING_CHECK_IMAGE = 'node:22.22.0-alpine'
  }

  stages {
    stage('Checkout') {
      steps {
        script { env.FAILED_STAGE = 'Checkout' }
        checkout([$class: 'GitSCM', branches: [[name: env.BRANCH]], userRemoteConfigs: [[url: env.REPO_URL]]])
        sh 'git log -1 --oneline'
      }
    }

    stage('Backend Test CI') {
      steps {
        script { env.FAILED_STAGE = 'Backend Test CI' }
        sh '''
set -eu
# Build-specific paths prevent stale reports from passing a later build.
test ! -e "${BACKEND_TEST_ARTIFACT_DIR}"
mkdir -p "${BACKEND_TEST_ARTIFACT_DIR}"
git rev-parse HEAD > "${BACKEND_TEST_ARTIFACT_DIR}/commit.txt"
git archive HEAD | docker build --target tester -t "${BACKEND_TEST_IMAGE}" -f backend/Dockerfile -

test_container=$(docker create \
  --network host \
  --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock \
  --add-host host.docker.internal:host-gateway \
  --env TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  "${BACKEND_TEST_IMAGE}")
trap 'docker rm -fv "${test_container}" >/dev/null 2>&1 || true' EXIT

test_exit=0
docker start --attach "${test_container}" || test_exit=$?

# Recover reports before removing the container, including on test failure.
artifact_exit=0
docker cp "${test_container}:/workspace/build/test-results/test" \
  "${BACKEND_TEST_RESULT_DIR}" || artifact_exit=1
docker cp "${test_container}:/workspace/build/reports/tests/test" \
  "${BACKEND_TEST_ARTIFACT_DIR}/test-report" || artifact_exit=1
docker cp "${test_container}:/workspace/build/reports/jacoco/test" \
  "${BACKEND_TEST_ARTIFACT_DIR}/coverage" || artifact_exit=1
test -s "${BACKEND_TEST_ARTIFACT_DIR}/test-report/index.html" || artifact_exit=1
test -s "${BACKEND_TEST_ARTIFACT_DIR}/coverage/jacocoTestReport.xml" || artifact_exit=1
test -s "${BACKEND_TEST_ARTIFACT_DIR}/coverage/html/index.html" || artifact_exit=1

evidence_exit=0
sh infra/ci/collect-s4-evidence.sh || evidence_exit=$?
sh infra/ci/verify-s4-evidence.sh || evidence_exit=$?

[ "${test_exit}" -eq 0 ] || exit "${test_exit}"
[ "${artifact_exit}" -eq 0 ] || exit "${artifact_exit}"
exit "${evidence_exit}"
        '''
      }
      post {
        always {
          script {
            try {
              def tests = junit(
                allowEmptyResults: false,
                skipPublishingChecks: true,
                testResults: "${env.BACKEND_TEST_RESULT_DIR}/TEST-*.xml"
              )
              if (tests.failCount > 0) {
                error('Backend test failures block deployment.')
              }
            } finally {
              archiveArtifacts(
                allowEmptyArchive: false,
                artifacts: "${env.BACKEND_TEST_ARTIFACT_DIR}/**"
              )
            }
          }
        }
      }
    }

    stage('Frontend Test CI') {
      steps {
        script { env.FAILED_STAGE = 'Frontend Test CI' }
        sh '''
set -eu
test ! -e "${FRONTEND_TEST_ARTIFACT_DIR}"
mkdir -p "${FRONTEND_TEST_ARTIFACT_DIR}"
git rev-parse HEAD > "${FRONTEND_TEST_ARTIFACT_DIR}/commit.txt"
git archive HEAD:frontend | docker build --target tester -t "${FRONTEND_TEST_IMAGE}" -f Dockerfile -

# Unit tests need no network, secrets or databases.
test_container=$(docker create --network none "${FRONTEND_TEST_IMAGE}")
trap 'docker rm -fv "${test_container}" >/dev/null 2>&1 || true' EXIT

test_exit=0
docker start --attach "${test_container}" || test_exit=$?

artifact_exit=0
docker cp "${test_container}:/app/reports/junit.xml" \
  "${FRONTEND_TEST_ARTIFACT_DIR}/junit.xml" || artifact_exit=1
test -s "${FRONTEND_TEST_ARTIFACT_DIR}/junit.xml" || artifact_exit=1

[ "${test_exit}" -eq 0 ] || exit "${test_exit}"
exit "${artifact_exit}"
        '''
      }
      post {
        always {
          script {
            try {
              def tests = junit(
                allowEmptyResults: false,
                skipPublishingChecks: true,
                testResults: "${env.FRONTEND_TEST_ARTIFACT_DIR}/junit.xml"
              )
              // Test Result merges Backend and Frontend, so log the Frontend counts separately.
              echo "Frontend tests: total=${tests.totalCount}, failed=${tests.failCount}, skipped=${tests.skipCount}"
              if (tests.failCount > 0) {
                error('Frontend test failures block deployment.')
              }
            } finally {
              archiveArtifacts(
                allowEmptyArchive: false,
                artifacts: "${env.FRONTEND_TEST_ARTIFACT_DIR}/**"
              )
            }
          }
        }
      }
    }

    stage('Path Display Timing Checks CI') {
      steps {
        script { env.FAILED_STAGE = 'Path Display Timing Checks CI' }
        // Broken measurement checks must not block deployment: mark the build UNSTABLE and continue.
        // A user abort or timeout must still stop the build instead of counting as a failed check.
        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE', catchInterruptions: false) {
          sh '''
set -eu
# Only the measurement scripts and the Frontend module they load; no network, secrets or databases.
git archive HEAD infra/k6/path-display-timing frontend/package.json frontend/src/features/path/model/searchPathPages.ts |
  docker run --rm -i --network none "${PATH_DISPLAY_TIMING_CHECK_IMAGE}" sh -c '
set -eu
cd "$(mktemp -d)"
tar -xf -
sh infra/k6/path-display-timing/run-checks.sh
'
          '''
        }
      }
    }

    stage('Sync') {
      steps {
        script { env.FAILED_STAGE = 'Sync' }
        withCredentials([sshUserPrivateKey(credentialsId: 'app-deploy-key', keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
          sh '''
set -eu
RSYNC_RSH="ssh -i ${SSH_KEY} -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new"
ssh -i "${SSH_KEY}" -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new "${SSH_USER}@${APP_HOST}" "mkdir -p ${APP_DIR}/source"
rsync -az --delete \
  -e "${RSYNC_RSH}" \
  --exclude '.git' \
  --exclude '.superpowers' \
  --exclude '_workspace' \
  --exclude 'node_modules' \
  --exclude '.gradle' \
  --exclude 'build' \
  --exclude 'dist' \
  --exclude 'ci-artifacts' \
  ./ "${SSH_USER}@${APP_HOST}:${APP_DIR}/source/"
          '''
        }
      }
    }

    stage('Build Images') {
      steps {
        script { env.FAILED_STAGE = 'Build Images' }
        withCredentials([sshUserPrivateKey(credentialsId: 'app-deploy-key', keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
          sh '''
set -eu
ssh -i "${SSH_KEY}" -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new "${SSH_USER}@${APP_HOST}" <<'REMOTE'
set -eu
cd /srv/apps/suri-map/source
# Preserve the running images before BuildKit replaces their develop tags.
preserve_tag="before-build-$(date -u +%Y%m%dT%H%M%SZ)"
for service in backend frontend mock-112; do
  image_id=$(docker inspect -f '{{.Image}}' "suri-map-${service}")
  docker image inspect "${image_id}" >/dev/null
  docker tag "${image_id}" "suri-map-${service}:${preserve_tag}"
done
docker build -t suri-map-frontend:develop -f frontend/Dockerfile frontend
docker build -t suri-map-backend:develop -f backend/Dockerfile .
docker build -t suri-map-mock-112:develop -f mock-112/Dockerfile mock-112
REMOTE
          '''
        }
      }
    }

    stage('Deploy Backend') {
      steps {
        script { env.FAILED_STAGE = 'Deploy Backend' }
        withCredentials([sshUserPrivateKey(credentialsId: 'app-deploy-key', keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
          sh '''
set -eu
ssh -i "${SSH_KEY}" -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new "${SSH_USER}@${APP_HOST}" <<'REMOTE'
set -eu
cd /srv/apps/suri-map
# Start only the new writer. Do not publish the new web or recreate the databases.
docker compose --env-file .env -f source/infra/docker/docker-compose.runtime.yml up -d --no-deps backend
REMOTE
          '''
        }
      }
    }

    stage('Verify Backend') {
      options { timeout(time: 3, unit: 'MINUTES') }
      steps {
        script { env.FAILED_STAGE = 'Verify Backend' }
        withCredentials([sshUserPrivateKey(credentialsId: 'app-deploy-key', keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
          sh '''
set -eu
# Keep login/logout hooks from changing the health check exit status.
ssh -i "${SSH_KEY}" -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 -o ServerAliveInterval=10 -o ServerAliveCountMax=2 "${SSH_USER}@${APP_HOST}" sh -s <<'REMOTE'
set -eu
command -v curl >/dev/null
command -v python3 >/dev/null
expected_image=$(docker image inspect -f '{{.Id}}' suri-map-backend:develop)
attempt=0
while [ "$attempt" -lt 60 ]; do
  attempt=$((attempt + 1))
  identity=$(docker inspect -f '{{.Id}}|{{.Image}}|{{.State.Status}}|{{.RestartCount}}' suri-map-backend)
  case "$identity" in
    *"|${expected_image}|running|"*)
      binding=$(docker port suri-map-backend 8080/tcp)
      case "$binding" in
        127.0.0.1:*) port=${binding#127.0.0.1:} ;;
        *) echo 'Backend health port must be loopback-only' >&2; exit 1 ;;
      esac
      case "$port" in ''|*[!0-9]*) echo 'Invalid Backend health port' >&2; exit 1 ;; esac
      if health=$(curl --fail --silent --show-error --connect-timeout 1 --max-time 2 "http://${binding}/actuator/health" 2>/dev/null) &&
          printf '%s' "$health" | python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin).get("status") == "UP" else 1)' 2>/dev/null; then
        current=$(docker inspect -f '{{.Id}}|{{.Image}}|{{.State.Status}}|{{.RestartCount}}' suri-map-backend)
        if [ "$identity" = "$current" ]; then
          echo "Backend health UP; image=${expected_image}; attempt=${attempt}"
          exit 0
        fi
      fi
      ;;
  esac
  sleep 1
done
echo 'Backend did not become healthy on the expected image; web deployment blocked' >&2
exit 1
REMOTE
          '''
        }
      }
    }

    stage('Deploy Web') {
      steps {
        script { env.FAILED_STAGE = 'Deploy Web' }
        withCredentials([sshUserPrivateKey(credentialsId: 'app-deploy-key', keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
          sh '''
set -eu
ssh -i "${SSH_KEY}" -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new "${SSH_USER}@${APP_HOST}" <<'REMOTE'
set -eu
cd /srv/apps/suri-map
# Keep the verified Backend and existing infrastructure unchanged.
mkdir -p tileserver/styles tileserver/fonts tileserver/data
if [ ! -f tileserver/config.json ]; then
  cp source/infra/docker/tileserver/config.json tileserver/config.json
fi
if [ -d source/infra/docker/tileserver/styles ]; then
  cp -R source/infra/docker/tileserver/styles/. tileserver/styles/
fi
docker compose --env-file .env -f source/infra/docker/docker-compose.runtime.yml up -d --no-deps frontend mock-112
REMOTE
          '''
        }
      }
    }

  }

  post {
    success { script { notifyDiscord('SUCCESS') } }
    // Only Path Display Timing Checks CI ends a build UNSTABLE; later stages overwrote FAILED_STAGE.
    unstable { script { env.FAILED_STAGE = 'Path Display Timing Checks CI'; notifyDiscord('UNSTABLE') } }
    failure { script { notifyDiscord('FAILURE') } }
    aborted { script { notifyDiscord('ABORTED') } }
  }
}
