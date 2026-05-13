package com.surimap.sync.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("S6 DB-backed idempotency record repository")
class DbIdempotencyRecordRepositoryIntegrationTest extends PostGisIntegrationTestSupport {

  private static final String IDEMPOTENCY_KEY = "idem-s6-durable-001";
  private static final String ENDPOINT = "POST /api/markers";
  private static final String OPERATION_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa2721";
  private static final String ENTITY_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb2721";
  private static final String BODY_HASH = "hash-marker-body-272";

  @Autowired private IdempotencyRecordRepository repository;

  @BeforeEach
  void cleanIdempotencyRecord() {
    jdbcTemplate.execute("TRUNCATE TABLE idempotency_record");
  }

  @Test
  @DisplayName("committed response cache survives service recreation and replays without owner write")
  void committedResponseCacheSurvivesServiceRecreationAndReplaysWithoutOwnerWrite() {
    IdempotentWriteRequest request =
        new IdempotentWriteRequest(
            ENDPOINT, OPERATION_ID, ENTITY_ID, IDEMPOTENCY_KEY, BODY_HASH);
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
    IdempotencyRecord persisted = repository.find(IDEMPOTENCY_KEY).orElseThrow();
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
        new IdempotentWriteRequest(
            ENDPOINT, OPERATION_ID, ENTITY_ID, IDEMPOTENCY_KEY, BODY_HASH);
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

  private int rowCount() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM idempotency_record WHERE idempotency_key = ?",
        Integer.class,
        IDEMPOTENCY_KEY);
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
