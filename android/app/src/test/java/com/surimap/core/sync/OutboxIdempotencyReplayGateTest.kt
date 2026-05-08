package com.surimap.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class OutboxIdempotencyReplayGateTest {

    @Test
    fun sameIdempotencyKeyAndBodyHashReturnsReplayDecision() {
        val gate = InMemoryIdempotencyReplayGate()
        val key = "idem-path-001"
        val hash = "sha256:path-normal-001"

        assertEquals(IdempotencyReplayDecision.ACCEPTED, gate.reserve(key, hash))
        assertEquals(IdempotencyReplayDecision.REPLAYED, gate.reserve(key, hash))
    }

    @Test
    fun sameIdempotencyKeyWithDifferentBodyHashReturnsMismatch() {
        val gate = InMemoryIdempotencyReplayGate()
        val key = "idem-path-001"

        assertEquals(IdempotencyReplayDecision.ACCEPTED, gate.reserve(key, "sha256:path-normal-001"))
        assertEquals(IdempotencyReplayDecision.MISMATCH, gate.reserve(key, "sha256:path-abnormal-001"))
    }
}
