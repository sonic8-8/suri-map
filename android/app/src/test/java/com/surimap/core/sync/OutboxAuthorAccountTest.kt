package com.surimap.core.sync

import androidx.room.Room
import com.surimap.core.database.SuriMapDatabase
import com.surimap.core.location.GpsLocationFix
import com.surimap.core.path.PathPoint
import com.surimap.feature.handover.data.DutyShiftLocalRecorder
import com.surimap.feature.handover.data.DutyShiftWriteContext
import com.surimap.feature.handover.data.DutyShiftWriteResult
import com.surimap.feature.handover.data.HandoverMemoInput
import com.surimap.feature.handover.data.HandoverMemoLocalRecorder
import com.surimap.feature.handover.data.HandoverWriteContext
import com.surimap.feature.handover.data.HandoverWriteResult
import com.surimap.feature.marker.data.MarkerLocalRecorder
import com.surimap.feature.marker.data.MarkerLocation
import com.surimap.feature.marker.data.MarkerPhotoAttachInput
import com.surimap.feature.marker.data.MarkerPhotoUploadUrlInput
import com.surimap.feature.marker.data.MarkerUpsertInput
import com.surimap.feature.marker.data.MarkerWriteContext
import com.surimap.feature.marker.data.MarkerWriteResult
import com.surimap.feature.search.data.SearchAreaBoundaryAlertLocalRecorder
import com.surimap.feature.search.data.SearchPathLocalRecorder
import com.surimap.feature.search.data.SearchPathWriteContext
import com.surimap.feature.search.data.SearchPathWriteResult
import com.surimap.feature.search.domain.AssignedSearchAreaBoundary
import com.surimap.testing.dutyShiftIdFixture
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.markerIdFixture
import com.surimap.testing.opIdFixture
import com.surimap.testing.photoIdFixture
import com.surimap.testing.policePhoneIdFixture
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// 기록을 만들 때 로그인해 있던 계정이 outbox의 작성 계정으로 남는지 recorder에서 outbox까지 확인한다.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OutboxAuthorAccountTest {
    private lateinit var database: SuriMapDatabase
    private lateinit var syncClient: RoomSyncClient

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SuriMapDatabase::class.java).build()
        syncClient = RoomSyncClient(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun markerWritesStoreLoggedInAccountAsAuthor() = runBlocking {
        val recorder = MarkerLocalRecorder(syncClient = syncClient, localMarkerDao = database.localMarkerDao())
        val context = MarkerWriteContext(INCIDENT_ID, OP_ID, POLICE_PHONE_ID, accountId = ACCOUNT_ID)

        val outboxIds =
            listOf(
                recorder.createMarker(context, MarkerUpsertInput(type = "CLUE", location = MarkerLocation(126.9, 35.1))),
                recorder.updateMarker(context, MARKER_ID, version = 1, input = MarkerUpsertInput(memo = "메모 수정")),
                recorder.deleteMarker(context, MARKER_ID, version = 2),
                recorder.requestPhotoUploadUrl(
                    context,
                    MarkerPhotoUploadUrlInput(markerId = MARKER_ID, contentType = "image/jpeg", sizeBytes = 1_024L)
                ),
                recorder.attachPhoto(
                    context,
                    MarkerPhotoAttachInput(
                        markerId = MARKER_ID,
                        photoId = PHOTO_ID,
                        contentType = "image/jpeg",
                        sizeBytes = 1_024L
                    )
                )
            ).map { (it as MarkerWriteResult.Enqueued).outboxId }

        assertEquals(List(5) { ACCOUNT_ID }, outboxIds.map { authorOf(it) })
    }

    @Test
    fun searchPathAndGpsBatchStoreLoggedInAccountAsAuthor() = runBlocking {
        val recorder = SearchPathLocalRecorder(syncClient = syncClient)
        val context = SearchPathWriteContext(INCIDENT_ID, OP_ID, ACCOUNT_ID, POLICE_PHONE_ID)
        val start = recorder.start(context) as SearchPathWriteResult.Enqueued
        val now = Instant.now()
        val batch =
            recorder.appendBatch(
                context,
                start.entityId,
                listOf(
                    PathPoint("point-1", 126.9, 37.5, clientTs = now),
                    PathPoint("point-2", 126.91, 37.51, clientTs = now.plusSeconds(5))
                )
            ) as SearchPathWriteResult.Enqueued
        val end = recorder.end(context, start.entityId) as SearchPathWriteResult.Enqueued

        assertEquals(
            listOf(ACCOUNT_ID, ACCOUNT_ID, ACCOUNT_ID),
            listOf(start, batch, end).map { authorOf(it.outboxId) }
        )
    }

    @Test
    fun searchAreaBoundaryAlertStoresLoggedInAccountAsAuthor() = runBlocking {
        val recorder = SearchAreaBoundaryAlertLocalRecorder(syncClient = syncClient)
        val context = SearchPathWriteContext(INCIDENT_ID, OP_ID, ACCOUNT_ID, POLICE_PHONE_ID)

        val alert =
            recorder.outsideAssignedArea(
                context = context,
                boundary = AssignedSearchAreaBoundary(searchAreaId = SEARCH_AREA_ID, label = "A 구역", geoJson = "{}"),
                searchPathId = null,
                fix =
                    GpsLocationFix(
                        lon = 126.9,
                        lat = 37.5,
                        bearingDegrees = null,
                        speedMps = null,
                        horizontalAccuracyM = 5,
                        capturedAt = Instant.now()
                    )
            ) as SearchPathWriteResult.Enqueued

        assertEquals(ACCOUNT_ID, authorOf(alert.outboxId))
    }

    @Test
    fun dutyShiftStartAndEndStoreLoggedInAccountAsAuthor() = runBlocking {
        val recorder = DutyShiftLocalRecorder(syncClient = syncClient)
        val context = DutyShiftWriteContext(INCIDENT_ID, OP_ID, DUTY_SHIFT_ID, POLICE_PHONE_ID, accountId = ACCOUNT_ID)

        val start = recorder.start(context) as DutyShiftWriteResult.Enqueued
        val end = recorder.end(context, memo = "교대") as DutyShiftWriteResult.Enqueued

        assertEquals(listOf(ACCOUNT_ID, ACCOUNT_ID), listOf(start, end).map { authorOf(it.outboxId) })
    }

    @Test
    fun handoverMemoStoresLoggedInAccountAsAuthor() = runBlocking {
        val recorder = HandoverMemoLocalRecorder(syncClient = syncClient)
        val context = HandoverWriteContext(INCIDENT_ID, OP_ID, DUTY_SHIFT_ID, POLICE_PHONE_ID, accountId = ACCOUNT_ID)

        val memo =
            recorder.createMemo(
                context,
                HandoverMemoInput(memoTargetType = "OPERATIONAL_PERIOD", memoTargetId = null, content = "북쪽 능선 확인")
            ) as HandoverWriteResult.Enqueued

        assertEquals(ACCOUNT_ID, authorOf(memo.outboxId))
    }

    private suspend fun authorOf(outboxId: String): String? = database.outboxDao().findById(outboxId)!!.authorAccountId

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val OP_ID = opIdFixture("precinct-first-001")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-car-01")
        val DUTY_SHIFT_ID = dutyShiftIdFixture("precinct-first-001")
        val MARKER_ID = markerIdFixture("precinct-clue-001")
        val PHOTO_ID = photoIdFixture("precinct-clue-001")
        val SEARCH_AREA_ID: String = UUID.nameUUIDFromBytes("search-area-a".toByteArray()).toString()
        val ACCOUNT_ID = UUID.nameUUIDFromBytes("account-a".toByteArray()).toString()
    }
}
