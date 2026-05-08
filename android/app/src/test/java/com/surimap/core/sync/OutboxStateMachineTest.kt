package com.surimap.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class OutboxStateMachineTest {

    @Test
    fun pendingTransitionsToSendingOnReplayStart() {
        assertEquals(
            OutboxStatus.SENDING,
            OutboxStateMachine.transition(OutboxStatus.PENDING, OutboxEvent.REPLAY_STARTED)
        )
    }

    @Test
    fun sendingTransitionsToAckedOnAck() {
        assertEquals(
            OutboxStatus.ACKED,
            OutboxStateMachine.transition(OutboxStatus.SENDING, OutboxEvent.ACK_RECEIVED)
        )
    }

    @Test
    fun sendingTransitionsToFailedRetryableOnRetryableFailure() {
        assertEquals(
            OutboxStatus.FAILED_RETRYABLE,
            OutboxStateMachine.transition(OutboxStatus.SENDING, OutboxEvent.RETRYABLE_FAILURE)
        )
    }

    @Test
    fun sendingTransitionsToFailedFinalOnFinalFailure() {
        assertEquals(
            OutboxStatus.FAILED_FINAL,
            OutboxStateMachine.transition(OutboxStatus.SENDING, OutboxEvent.FINAL_FAILURE)
        )
    }

    @Test
    fun failedRetryableTransitionsBackToPendingOnRequeueAccepted() {
        assertEquals(
            OutboxStatus.PENDING,
            OutboxStateMachine.transition(OutboxStatus.FAILED_RETRYABLE, OutboxEvent.REQUEUE_ACCEPTED)
        )
    }

    @Test
    fun ackedAndFailedFinalCanBePurged() {
        assertEquals(
            OutboxStatus.PURGED,
            OutboxStateMachine.transition(OutboxStatus.ACKED, OutboxEvent.PURGE_CONFIRMED)
        )
        assertEquals(
            OutboxStatus.PURGED,
            OutboxStateMachine.transition(OutboxStatus.FAILED_FINAL, OutboxEvent.PURGE_CONFIRMED)
        )
    }

    @Test
    fun harnessStatusMappingMatchesS6Contract() {
        assertEquals(OutboxStatus.PENDING.name, HarnessStatusMapper.toOutboxStatus(HarnessSyncStatus.PENDING_LOCAL))
        assertEquals(OutboxStatus.PENDING.name, HarnessStatusMapper.toOutboxStatus(HarnessSyncStatus.PENDING_SEND))
        assertEquals(OutboxStatus.SENDING.name, HarnessStatusMapper.toOutboxStatus(HarnessSyncStatus.SENDING))
        assertEquals(OutboxStatus.ACKED.name, HarnessStatusMapper.toOutboxStatus(HarnessSyncStatus.SYNCED))
        assertEquals(
            "${OutboxStatus.FAILED_RETRYABLE.name}|${OutboxStatus.FAILED_FINAL.name}",
            HarnessStatusMapper.toOutboxStatus(HarnessSyncStatus.FAILED)
        )
        assertEquals(OutboxStatus.PURGED.name, HarnessStatusMapper.toOutboxStatus(HarnessSyncStatus.PURGED))
    }
}
