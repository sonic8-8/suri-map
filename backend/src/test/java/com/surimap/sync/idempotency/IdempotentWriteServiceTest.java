package com.surimap.sync.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("IdempotentWriteService")
class IdempotentWriteServiceTest extends PostGisIntegrationTestSupport {

  private static final String IDEMPOTENCY_KEY = "idem-s6-durable-001";
  private static final String ENDPOINT = "POST /api/markers";
  private static final String OPERATION_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa2721";
  private static final String ENTITY_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb2721";
  private static final String BODY_HASH = "hash-marker-body-272";

  @Autowired private IdempotencyRecordRepository repository;
  @Autowired private IdempotentWriteService service;

  @BeforeEach
  void cleanIdempotencyRecord() {
    jdbcTemplate.execute("TRUNCATE TABLE idempotency_record");
  }

  @Test
  @DisplayName(
      "committed response cache survives service recreation and replays without owner write")
  void committedResponseCacheSurvivesServiceRecreationAndReplaysWithoutOwnerWrite() {
    IdempotentWriteRequest request =
        new IdempotentWriteRequest(ENDPOINT, OPERATION_ID, ENTITY_ID, IDEMPOTENCY_KEY, BODY_HASH);
    IdempotentWriteService firstService =
        new IdempotentWriteService(new NoOwnerReplayRecoveryPort(), repository);

    IdempotentWriteResponse first =
        firstService.reserveAndReplay(
            request,
            () ->
                new IdempotentWriteResponse(
                    201,
                    "{\"id\":\"" + ENTITY_ID + "\",\"status\":\"ACTIVE\"}",
                    "application/json",
                    1,
                    ENTITY_ID,
                    "ACTIVE",
                    7L,
                    2721L,
                    false,
                    false,
                    null));

    assertThat(first.replayed()).isFalse();
    assertThat(rowCount()).isEqualTo(1);
    assertThat(storedStatus()).isEqualTo("COMPLETED");
    assertThat(storedResponseBody()).isEqualTo(first.bodyJson());
    IdempotencyRecord persisted = repository.find(ENDPOINT, IDEMPOTENCY_KEY).orElseThrow();
    assertThat(persisted.status()).isEqualTo(IdempotencyRecordStatus.COMMITTED);
    assertThat(persisted.bodyHash()).isEqualTo(BODY_HASH);
    assertThat(persisted.response()).isNotNull();
    assertThat(persisted.response().statusCode()).isEqualTo(201);

    IdempotentWriteService recreatedService =
        new IdempotentWriteService(new NoOwnerReplayRecoveryPort(), repository);
    IdempotentWriteResponse replay =
        recreatedService.reserveAndReplay(
            request, () -> failOwner("owner write must not run on durable replay"));

    assertThat(replay.statusCode()).isEqualTo(201);
    assertThat(replay.bodyJson()).isEqualTo(first.bodyJson());
    assertThat(replay.entityId()).isEqualTo(ENTITY_ID);
    assertThat(replay.entityStatus()).isEqualTo("ACTIVE");
    assertThat(replay.entityVersion()).isEqualTo(7L);
    assertThat(replay.entitySequence()).isEqualTo(2721L);
    assertThat(replay.replayed()).isTrue();
    assertThat(replay.replayRecovered()).isFalse();
    assertThat(rowCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("same key with different body hash returns mismatch from DB record")
  void sameKeyWithDifferentBodyHashReturnsMismatchFromDbRecord() {
    IdempotentWriteRequest request =
        new IdempotentWriteRequest(ENDPOINT, OPERATION_ID, ENTITY_ID, IDEMPOTENCY_KEY, BODY_HASH);
    IdempotentWriteService service =
        new IdempotentWriteService(new NoOwnerReplayRecoveryPort(), repository);
    service.reserveAndReplay(
        request,
        () ->
            new IdempotentWriteResponse(
                201,
                "{\"id\":\"" + ENTITY_ID + "\",\"status\":\"ACTIVE\"}",
                "application/json",
                1,
                ENTITY_ID,
                "ACTIVE",
                7L,
                2721L,
                false,
                false,
                null));

    IdempotentWriteResponse mismatch =
        service.reserveAndReplay(
            new IdempotentWriteRequest(
                ENDPOINT, OPERATION_ID, ENTITY_ID, IDEMPOTENCY_KEY, "hash-conflicting-body"),
            () -> failOwner("owner write must not run on body hash mismatch"));

    assertThat(mismatch.statusCode()).isEqualTo(409);
    assertThat(mismatch.error()).isEqualTo("idempotency_mismatch");
    assertThat(rowCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("different idempotency keys run without waiting for the previous owner operation")
  void differentIdempotencyKeysRunConcurrently() throws Exception {
    CountDownLatch firstOwnerStarted = new CountDownLatch(1);
    CountDownLatch releaseFirstOwner = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    Future<IdempotentWriteResponse> first =
        executor.submit(
            () ->
                service.reserveAndReplay(
                    request("idem-concurrent-a", "operation-a", "entity-a", "hash-a"),
                    () -> {
                      firstOwnerStarted.countDown();
                      await(releaseFirstOwner);
                      return response("entity-a");
                    }));

    try {
      assertThat(firstOwnerStarted.await(5, TimeUnit.SECONDS)).isTrue();

      Future<IdempotentWriteResponse> second =
          executor.submit(
              () ->
                  service.reserveAndReplay(
                      request("idem-concurrent-b", "operation-b", "entity-b", "hash-b"),
                      () -> response("entity-b")));

      assertThat(second.get(2, TimeUnit.SECONDS).entityId()).isEqualTo("entity-b");
    } finally {
      releaseFirstOwner.countDown();
      first.get(5, TimeUnit.SECONDS);
      executor.shutdownNow();
    }
  }

  @Test
  @DisplayName(
      "concurrent requests with the same key invoke the owner once and replay the response")
  void sameIdempotencyKeyRunsOwnerOnce() throws Exception {
    String idempotencyKey = "idem-concurrent-same";
    IdempotentWriteRequest request =
        request(idempotencyKey, "operation-same", "entity-same", "hash-same");
    CountDownLatch firstOwnerStarted = new CountDownLatch(1);
    CountDownLatch secondRequestStarted = new CountDownLatch(1);
    CountDownLatch releaseFirstOwner = new CountDownLatch(1);
    AtomicInteger ownerInvocations = new AtomicInteger();
    ExecutorService executor = Executors.newFixedThreadPool(2);

    Future<IdempotentWriteResponse> first =
        executor.submit(
            () ->
                service.reserveAndReplay(
                    request,
                    () -> {
                      ownerInvocations.incrementAndGet();
                      firstOwnerStarted.countDown();
                      await(releaseFirstOwner);
                      return response("entity-same");
                    }));

    try {
      assertThat(firstOwnerStarted.await(5, TimeUnit.SECONDS)).isTrue();

      Future<IdempotentWriteResponse> second =
          executor.submit(
              () -> {
                secondRequestStarted.countDown();
                return service.reserveAndReplay(
                    request,
                    () -> {
                      ownerInvocations.incrementAndGet();
                      return response("duplicate-entity");
                    });
              });

      assertThat(secondRequestStarted.await(5, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
          .isInstanceOf(TimeoutException.class);
      releaseFirstOwner.countDown();

      assertThat(first.get(5, TimeUnit.SECONDS).replayed()).isFalse();
      assertThat(second.get(5, TimeUnit.SECONDS).replayed()).isTrue();
      assertThat(ownerInvocations).hasValue(1);
      assertThat(rowCount(idempotencyKey)).isEqualTo(1);
    } finally {
      releaseFirstOwner.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  @DisplayName("same key on different endpoints is handled as a separate request")
  void sameIdempotencyKeyOnDifferentEndpointsRunsIndependently() {
    String idempotencyKey = "idem-same-key-different-endpoints";
    IdempotentWriteRequest markerRequest =
        new IdempotentWriteRequest(
            "POST /api/markers", "operation-marker", ENTITY_ID, idempotencyKey, "marker-hash");
    IdempotentWriteRequest pathRequest =
        request(idempotencyKey, "operation-path", "entity-path", "path-hash");
    AtomicInteger ownerInvocations = new AtomicInteger();

    service.reserveAndReplay(
        markerRequest,
        () -> {
          ownerInvocations.incrementAndGet();
          return response(ENTITY_ID);
        });
    IdempotentWriteResponse second =
        service.reserveAndReplay(
            pathRequest,
            () -> {
              ownerInvocations.incrementAndGet();
              return response("entity-path");
            });

    assertThat(ownerInvocations).hasValue(2);
    assertThat(second.replayed()).isFalse();
    assertThat(rowCount(idempotencyKey)).isEqualTo(2);
  }

  @Test
  @DisplayName("reservation and owner write roll back together")
  void reservationAndOwnerWriteRollBackTogether() {
    IdempotentWriteRequest request =
        request("idem-transaction", "operation-transaction", "entity-transaction", "hash");
    IdempotentWriteRequest ownerWrite =
        request("idem-owner-write", "operation-owner-write", "entity-owner-write", "owner-hash");

    assertThatThrownBy(
            () ->
                service.reserveAndReplay(
                    request,
                    () -> {
                      repository.reserve(IdempotencyRecord.reserved(ownerWrite));
                      throw new IllegalStateException("owner failed");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("owner failed");

    assertThat(rowCount(request.idempotencyKey())).isZero();
    assertThat(rowCount(ownerWrite.idempotencyKey())).isZero();
  }

  private int rowCount() {
    return rowCount(IDEMPOTENCY_KEY);
  }

  private int rowCount(String idempotencyKey) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM idempotency_record WHERE idempotency_key = ?",
        Integer.class,
        idempotencyKey);
  }

  private String storedStatus() {
    return jdbcTemplate.queryForObject(
        "SELECT idempotency_status FROM idempotency_record WHERE idempotency_key = ?",
        String.class,
        IDEMPOTENCY_KEY);
  }

  private String storedResponseBody() {
    return jdbcTemplate.queryForObject(
        "SELECT response_body_json FROM idempotency_record WHERE idempotency_key = ?",
        String.class,
        IDEMPOTENCY_KEY);
  }

  private static IdempotentWriteRequest request(
      String idempotencyKey, String operationId, String entityId, String bodyHash) {
    return new IdempotentWriteRequest(
        "POST /api/search-paths/batch", operationId, entityId, idempotencyKey, bodyHash);
  }

  private static IdempotentWriteResponse response(String entityId) {
    return new IdempotentWriteResponse(
        201,
        "{\"id\":\"" + entityId + "\",\"status\":\"APPENDED\"}",
        "application/json",
        1,
        entityId,
        "APPENDED",
        1L,
        1L,
        false,
        false,
        null);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("timed out while waiting for test release");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for test release", exception);
    }
  }

  private static IdempotentWriteResponse failOwner(String message) {
    fail(message);
    return null;
  }

  private static final class NoOwnerReplayRecoveryPort implements OwnerReplayRecoveryPort {

    @Override
    public Optional<IdempotentWriteResponse> recover(
        String endpoint, String operationId, String entityId) {
      return Optional.empty();
    }
  }
}
