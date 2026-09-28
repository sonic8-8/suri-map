package com.surimap.offlinepackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.offlinepackage.dto.OfflinePackageInstallationReportRequest;
import com.surimap.offlinepackage.query.OfflinePackageInstallationStatus;
import com.surimap.offlinepackage.repository.OfflinePackageInstallationRecord;
import com.surimap.offlinepackage.repository.OfflinePackageMapper;
import com.surimap.offlinepackage.service.OfflinePackageService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(
    properties = {
      "spring.datasource.hikari.maximum-pool-size=2",
      "spring.datasource.hikari.minimum-idle=2",
      "spring.datasource.hikari.connection-timeout=1000"
    })
class OfflinePackageServiceTest extends PostGisIntegrationTestSupport {

  @Autowired private OfflinePackageService service;
  @Autowired private OfflinePackageMapper mapper;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  @DisplayName("조회 트랜잭션이 풀의 커넥션을 모두 사용 중이어도 패키지 상태 조회를 완료한다")
  void by_incident_when_outer_transactions_fill_pool_returns_installation_statuses()
      throws Exception {
    // given: 준비 완료된 패키지와 커넥션 두 개를 사용하는 읽기 트랜잭션을 준비한다.
    String incidentId = UUID.randomUUID().toString();
    String installationId = insertReadyInstallation(incidentId);
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.setReadOnly(true);
    CountDownLatch connectionsAcquired = new CountDownLatch(2);
    var executor = Executors.newFixedThreadPool(2);

    try {
      // when: 두 트랜잭션이 각각 커넥션을 확보한 뒤 같은 서비스 조회를 실행한다.
      Callable<List<OfflinePackageInstallationStatus>> query =
          () ->
              transaction.execute(
                  status -> {
                    jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                    connectionsAcquired.countDown();
                    awaitConnections(connectionsAcquired);
                    return service.byIncident(incidentId);
                  });
      var results = List.of(executor.submit(query), executor.submit(query));

      // then: 추가 커넥션을 기다리다 실패하지 않고 두 요청 모두 저장된 상태를 반환한다.
      for (var result : results) {
        assertThat(result.get(10, TimeUnit.SECONDS))
            .extracting(
                OfflinePackageInstallationStatus::id,
                OfflinePackageInstallationStatus::incidentId,
                OfflinePackageInstallationStatus::status,
                OfflinePackageInstallationStatus::readyForOfflineUse)
            .containsExactly(tuple(installationId, incidentId, "READY", true));
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  private String insertReadyInstallation(String incidentId) {
    String manifestId = UUID.randomUUID().toString();
    String installationId = UUID.randomUUID().toString();
    String policePhoneId = UUID.randomUUID().toString();
    OffsetDateTime now = OffsetDateTime.parse("2026-09-28T00:00:00Z");
    mapper.insertManifest(
        manifestId,
        incidentId,
        1,
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        1L,
        "a".repeat(64),
        now.plusHours(1),
        now);
    OfflinePackageInstallationReportRequest request =
        new OfflinePackageInstallationReportRequest(
            policePhoneId,
            manifestId,
            1,
            "READY",
            1,
            1,
            0,
            1L,
            now,
            true,
            List.of(),
            null,
            1L,
            null);
    mapper.insertInstallation(
        OfflinePackageInstallationRecord.from(
            installationId, incidentId, manifestId, policePhoneId, request, now));
    return installationId;
  }

  private void awaitConnections(CountDownLatch connectionsAcquired) {
    try {
      assertThat(connectionsAcquired.await(10, TimeUnit.SECONDS))
          .as("두 조회 트랜잭션이 모두 커넥션을 확보한다")
          .isTrue();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("동시 조회 준비 대기가 중단됐습니다", exception);
    }
  }
}
