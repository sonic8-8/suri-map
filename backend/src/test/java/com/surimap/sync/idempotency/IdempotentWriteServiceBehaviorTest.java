package com.surimap.sync.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.HttpStatus;

@DisplayName("IdempotentWriteService behavior")
class IdempotentWriteServiceBehaviorTest {

  @Test
  @DisplayName("same key and bodyHash replays cached response without invoking owner twice")
  void sameKeyAndSameBodyHashReplaysCachedResponseWithoutSecondOwnerInvocation() {
    FakeOwnerReplayRecoveryPort recoveryPort = new FakeOwnerReplayRecoveryPort();
    IdempotentWriteService service = new IdempotentWriteService(recoveryPort);
    AtomicInteger ownerInvocations = new AtomicInteger();

    IdempotentWriteRequest request =
        request("POST /markers", "op-marker-001", "marker-001", "idem-marker-001", "hash-ready");

    IdempotentWriteResponse first =
        service.reserveAndReplay(
            request,
            () -> {
              ownerInvocations.incrementAndGet();
              return response(201, "{\"id\":\"marker-001\",\"status\":\"ACTIVE\"}", 7L, 1201L);
            });
    IdempotentWriteResponse replay =
        service.reserveAndReplay(
            request,
            () -> {
              ownerInvocations.incrementAndGet();
              return failOwner("owner/domain operation must not run for same key/bodyHash replay");
            });

    assertThat(ownerInvocations).hasValue(1);
    assertThat(replay.statusCode()).isEqualTo(201);
    assertThat(replay.bodyJson()).isEqualTo(first.bodyJson());
    assertThat(replay.replayed()).isTrue();
    assertThat(replay.replayRecovered()).isFalse();
    assertThat(replay.error()).isNull();
  }

  @Test
  @DisplayName("same key and different bodyHash returns 409 idempotency_mismatch")
  void sameKeyAndDifferentBodyHashReturnsMismatchWithoutOwnerInvocation() {
    FakeOwnerReplayRecoveryPort recoveryPort = new FakeOwnerReplayRecoveryPort();
    IdempotentWriteService service = new IdempotentWriteService(recoveryPort);
    AtomicInteger ownerInvocations = new AtomicInteger();

    IdempotentWriteRequest firstRequest =
        request("POST /markers", "op-marker-002", "marker-002", "idem-marker-002", "hash-ready");
    IdempotentWriteRequest conflictingRequest =
        request(
            "POST /markers",
            "op-marker-002",
            "marker-002",
            "idem-marker-002",
            "hash-conflicting-body");

    service.reserveAndReplay(
        firstRequest,
        () -> {
          ownerInvocations.incrementAndGet();
          return response(201, "{\"id\":\"marker-002\",\"status\":\"ACTIVE\"}", 8L, 1202L);
        });
    IdempotentWriteResponse mismatch =
        service.reserveAndReplay(
            conflictingRequest,
            () -> {
              ownerInvocations.incrementAndGet();
              return failOwner("owner/domain operation must not run for bodyHash mismatch");
            });

    assertThat(ownerInvocations).hasValue(1);
    assertThat(mismatch.statusCode()).isEqualTo(409);
    assertThat(mismatch.error()).isEqualTo("idempotency_mismatch");
    assertThat(mismatch.replayed()).isFalse();
    assertThat(mismatch.replayRecovered()).isFalse();
  }

  @Test
  @DisplayName("same key on different endpoints is handled as a separate request")
  void sameIdempotencyKeyOnDifferentEndpointRunsOwner() {
    FakeOwnerReplayRecoveryPort recoveryPort = new FakeOwnerReplayRecoveryPort();
    IdempotentWriteService service = new IdempotentWriteService(recoveryPort);
    AtomicInteger ownerInvocations = new AtomicInteger();

    IdempotentWriteRequest firstRequest =
        request("POST /markers", "op-marker-004", "marker-004", "idem-global-001", "hash-marker");
    IdempotentWriteRequest secondEndpointRequest =
        request(
            "POST /search-paths/batch", "op-path-004", "path-004", "idem-global-001", "hash-path");

    service.reserveAndReplay(
        firstRequest,
        () -> {
          ownerInvocations.incrementAndGet();
          return response(201, "{\"id\":\"marker-004\",\"status\":\"ACTIVE\"}", 4L, 1404L);
        });
    IdempotentWriteResponse second =
        service.reserveAndReplay(
            secondEndpointRequest,
            () -> {
              ownerInvocations.incrementAndGet();
              return response(201, "{\"id\":\"path-004\",\"status\":\"ACTIVE\"}", 1L, 1405L);
            });

    assertThat(ownerInvocations).hasValue(2);
    assertThat(second.statusCode()).isEqualTo(201);
    assertThat(second.replayed()).isFalse();
  }

  @Test
  @DisplayName("owner failure rolls back RESERVED so retry can invoke owner again")
  void ownerFailureRollsBackReservedRecordForRetry() {
    IdempotentWriteService service = new IdempotentWriteService(new FakeOwnerReplayRecoveryPort());
    AtomicInteger ownerInvocations = new AtomicInteger();
    IdempotentWriteRequest request =
        request("POST /markers", "op-marker-005", "marker-005", "idem-marker-005", "hash-ready");

    assertThatThrownBy(
            () ->
                service.reserveAndReplay(
                    request,
                    () -> {
                      ownerInvocations.incrementAndGet();
                      throw new IllegalStateException("transient owner failure");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("transient owner failure");

    IdempotentWriteResponse retry =
        service.reserveAndReplay(
            request,
            () -> {
              ownerInvocations.incrementAndGet();
              return response(201, "{\"id\":\"marker-005\",\"status\":\"ACTIVE\"}", 5L, 1505L);
            });

    assertThat(ownerInvocations).hasValue(2);
    assertThat(retry.statusCode()).isEqualTo(201);
    assertThat(retry.error()).isNull();
  }

  @Test
  @DisplayName("COMMITTED cache-missing replay recovers through owner port and marks recovered")
  void committedCacheMissingReplayUsesOwnerRecoveryPortAndFillsCacheWhenVersionSequenceMatch() {
    FakeOwnerReplayRecoveryPort recoveryPort = new FakeOwnerReplayRecoveryPort();
    IdempotentWriteService service = new IdempotentWriteService(recoveryPort);
    IdempotentWriteRequest request =
        request(
            "POST /search-paths/batch",
            "op-path-batch-001",
            "path-segment-001",
            "idem-path-batch-001",
            "hash-path-points");

    service.recordCommittedWithoutResponse(request, 201, 11L, 901L);
    recoveryPort.recoverWith(
        response(
            201,
            "{\"id\":\"path-segment-001\",\"status\":\"APPENDED\"}",
            "path-segment-001",
            "APPENDED",
            11L,
            901L));

    IdempotentWriteResponse recovered =
        service.reserveAndReplay(
            request,
            () ->
                failOwner(
                    "owner/domain operation must not run for committed-cache-missing replay"));

    assertThat(recoveryPort.calls()).isEqualTo(1);
    assertThat(recoveryPort.lastEndpoint()).isEqualTo("POST /search-paths/batch");
    assertThat(recoveryPort.lastOperationId()).isEqualTo("op-path-batch-001");
    assertThat(recoveryPort.lastEntityId()).isEqualTo("path-segment-001");
    assertThat(recovered.statusCode()).isEqualTo(201);
    assertThat(recovered.bodyJson()).contains("path-segment-001");
    assertThat(recovered.contentType()).isEqualTo("application/json");
    assertThat(recovered.responseSchemaVersion()).isEqualTo(1);
    assertThat(recovered.entityId()).isEqualTo("path-segment-001");
    assertThat(recovered.entityStatus()).isEqualTo("APPENDED");
    assertThat(recovered.entityVersion()).isEqualTo(11L);
    assertThat(recovered.entitySequence()).isEqualTo(901L);
    assertThat(recovered.replayed()).isTrue();
    assertThat(recovered.replayRecovered()).isTrue();
    assertThat(recovered.error()).isNull();

    IdempotentWriteResponse cachedReplay =
        service.reserveAndReplay(
            request,
            () -> failOwner("recovered response must be cached after owner recovery fills it"));

    assertThat(recoveryPort.calls()).isEqualTo(1);
    assertThat(cachedReplay.bodyJson()).isEqualTo(recovered.bodyJson());
    assertThat(cachedReplay.replayed()).isTrue();
  }

  @Test
  @DisplayName(
      "COMMITTED cache-missing replay returns write_conflict when recovery version mismatches")
  void committedCacheMissingRecoveryVersionMismatchReturnsWriteConflict() {
    FakeOwnerReplayRecoveryPort recoveryPort = new FakeOwnerReplayRecoveryPort();
    IdempotentWriteService service = new IdempotentWriteService(recoveryPort);
    IdempotentWriteRequest request =
        request(
            "POST /search-paths/batch",
            "op-path-batch-002",
            "path-segment-002",
            "idem-path-batch-002",
            "hash-path-points");

    service.recordCommittedWithoutResponse(request, 201, 11L, 901L);
    recoveryPort.recoverWith(
        response(201, "{\"id\":\"path-segment-002\",\"status\":\"APPENDED\"}", 12L, 901L));

    IdempotentWriteResponse conflict =
        service.reserveAndReplay(
            request,
            () -> failOwner("owner/domain operation must not run during cache-missing recovery"));

    assertThat(recoveryPort.calls()).isEqualTo(1);
    assertThat(conflict.statusCode()).isEqualTo(409);
    assertThat(conflict.error()).isEqualTo("write_conflict");
    assertThat(conflict.replayed()).isFalse();
    assertThat(conflict.replayRecovered()).isFalse();
  }

  @Test
  @DisplayName("@IdempotentWrite is runtime method annotation for middleware interception")
  void idempotentWriteAnnotationTargetsMethodsAtRuntime() {
    Target target = IdempotentWrite.class.getAnnotation(Target.class);
    Retention retention = IdempotentWrite.class.getAnnotation(Retention.class);

    assertThat(target.value()).containsExactly(ElementType.METHOD);
    assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
  }

  @Test
  @DisplayName("@IdempotentWrite aspect wraps owner operation with reserve/replay middleware")
  void idempotentWriteAspectSkipsOwnerInvocationForReplay() {
    FakeOwnerReplayRecoveryPort recoveryPort = new FakeOwnerReplayRecoveryPort();
    IdempotentWriteService service = new IdempotentWriteService(recoveryPort);
    TestWriteEndpoint target = new TestWriteEndpoint();
    AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
    proxyFactory.addAspect(new IdempotentWriteAspect(service));
    TestWriteEndpoint proxy = proxyFactory.getProxy();
    IdempotentWriteRequest request =
        request("POST /markers", "op-marker-003", "marker-003", "idem-marker-003", "hash-ready");

    IdempotentWriteResponse first = proxy.write(request);
    IdempotentWriteResponse replay = proxy.write(request);

    assertThat(target.ownerInvocations()).isEqualTo(1);
    assertThat(first.replayed()).isFalse();
    assertThat(replay.replayed()).isTrue();
    assertThat(replay.bodyJson()).isEqualTo(first.bodyJson());
  }

  @Test
  @DisplayName("@IdempotentWrite aspect converts conflict response to 409 exception path")
  void idempotentWriteAspectConvertsMismatchToConflictException() {
    IdempotentWriteService service = new IdempotentWriteService(new FakeOwnerReplayRecoveryPort());
    TestWriteEndpoint target = new TestWriteEndpoint();
    AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
    proxyFactory.addAspect(new IdempotentWriteAspect(service));
    TestWriteEndpoint proxy = proxyFactory.getProxy();

    proxy.write(
        request("POST /markers", "op-marker-006", "marker-006", "idem-marker-006", "hash-one"));

    assertThatThrownBy(
            () ->
                proxy.write(
                    request(
                        "POST /markers",
                        "op-marker-006",
                        "marker-006",
                        "idem-marker-006",
                        "hash-two")))
        .isInstanceOf(IdempotencyMismatchException.class)
        .hasMessage("idempotency_mismatch");
    assertThat(target.ownerInvocations()).isEqualTo(1);
  }

  @Test
  @DisplayName("idempotency exception handler maps middleware conflicts to HTTP 409")
  void idempotencyExceptionHandlerMapsConflictsToHttp409() {
    IdempotencyExceptionHandler handler = new IdempotencyExceptionHandler();

    var mismatch = handler.handleMismatch(new IdempotencyMismatchException());
    var writeConflict = handler.handleWriteConflict(new WriteConflictException());

    assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(mismatch.getBody()).containsEntry("error", "idempotency_mismatch");
    assertThat(writeConflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(writeConflict.getBody()).containsEntry("error", "write_conflict");
  }

  @Test
  @DisplayName("different idempotency keys do not wait for the previous owner operation")
  void differentIdempotencyKeysRunConcurrently() throws Exception {
    IdempotentWriteService service = new IdempotentWriteService(new FakeOwnerReplayRecoveryPort());
    CountDownLatch firstOwnerStarted = new CountDownLatch(1);
    CountDownLatch releaseFirstOwner = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    Future<IdempotentWriteResponse> first =
        executor.submit(
            () ->
                service.reserveAndReplay(
                    request(
                        "POST /search-paths/batch",
                        "operation-a",
                        "entity-a",
                        "idem-concurrent-a",
                        "hash-a"),
                    () -> {
                      firstOwnerStarted.countDown();
                      await(releaseFirstOwner);
                      return response(
                          201,
                          "{\"id\":\"entity-a\",\"status\":\"APPENDED\"}",
                          "entity-a",
                          "APPENDED",
                          1L,
                          1L);
                    }));

    try {
      assertThat(firstOwnerStarted.await(5, TimeUnit.SECONDS)).isTrue();

      Future<IdempotentWriteResponse> second =
          executor.submit(
              () ->
                  service.reserveAndReplay(
                      request(
                          "POST /search-paths/batch",
                          "operation-b",
                          "entity-b",
                          "idem-concurrent-b",
                          "hash-b"),
                      () ->
                          response(
                              201,
                              "{\"id\":\"entity-b\",\"status\":\"APPENDED\"}",
                              "entity-b",
                              "APPENDED",
                              1L,
                              1L)));

      assertThat(second.get(2, TimeUnit.SECONDS).entityId()).isEqualTo("entity-b");
    } finally {
      releaseFirstOwner.countDown();
      first.get(5, TimeUnit.SECONDS);
      executor.shutdownNow();
    }
  }

  private static IdempotentWriteRequest request(
      String endpoint,
      String operationId,
      String entityId,
      String idempotencyKey,
      String bodyHash) {
    return new IdempotentWriteRequest(endpoint, operationId, entityId, idempotencyKey, bodyHash);
  }

  private static IdempotentWriteResponse response(
      int statusCode, String bodyJson, long entityVersion, long entitySequence) {
    return response(statusCode, bodyJson, null, null, entityVersion, entitySequence);
  }

  private static IdempotentWriteResponse response(
      int statusCode,
      String bodyJson,
      String entityId,
      String entityStatus,
      long entityVersion,
      long entitySequence) {
    return new IdempotentWriteResponse(
        statusCode,
        bodyJson,
        "application/json",
        1,
        entityId,
        entityStatus,
        entityVersion,
        entitySequence,
        false,
        false,
        null);
  }

  private static IdempotentWriteResponse failOwner(String message) {
    fail(message);
    return null;
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

  public static class TestWriteEndpoint {

    private int ownerInvocations;

    public TestWriteEndpoint() {}

    @IdempotentWrite
    public IdempotentWriteResponse write(IdempotentWriteRequest request) {
      ownerInvocations++;
      return response(
          201,
          "{\"id\":\"" + request.entityId() + "\",\"status\":\"ACTIVE\"}",
          ownerInvocations,
          1300L + ownerInvocations);
    }

    int ownerInvocations() {
      return ownerInvocations;
    }
  }

  private static final class FakeOwnerReplayRecoveryPort implements OwnerReplayRecoveryPort {

    private IdempotentWriteResponse recoveredResponse;
    private int calls;
    private String lastEndpoint;
    private String lastOperationId;
    private String lastEntityId;

    @Override
    public Optional<IdempotentWriteResponse> recover(
        String endpoint, String operationId, String entityId) {
      calls++;
      lastEndpoint = endpoint;
      lastOperationId = operationId;
      lastEntityId = entityId;
      return Optional.ofNullable(recoveredResponse);
    }

    void recoverWith(IdempotentWriteResponse response) {
      this.recoveredResponse = response;
    }

    int calls() {
      return calls;
    }

    String lastEndpoint() {
      return lastEndpoint;
    }

    String lastOperationId() {
      return lastOperationId;
    }

    String lastEntityId() {
      return lastEntityId;
    }
  }
}
