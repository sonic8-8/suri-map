package com.surimap.core.sync

import com.surimap.core.network.PolicePhoneHeartbeatNetworkRequest
import com.surimap.core.network.SuriMapApiResponse
import com.surimap.core.network.SuriMapNetworkException
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountHeartbeatSchedulerTest {

    @Test
    fun sendsHeartbeatEvery30SecondsWithFixedClock() = runBlocking {
        val harness = HeartbeatHarness(this)

        harness.scheduler.setSignedIn(true)
        harness.scheduler.hold(harness.screen, TARGET)
        repeat(3) { harness.tick() }
        harness.scheduler.setSignedIn(false)

        assertEquals(listOf(30_000L, 30_000L, 30_000L, 30_000L), harness.sleeps)
        assertEquals(
            listOf(
                PolicePhoneHeartbeatNetworkRequest(POLICE_PHONE_ID, "2026-11-20T00:00:30Z"),
                PolicePhoneHeartbeatNetworkRequest(POLICE_PHONE_ID, "2026-11-20T00:01:00Z"),
                PolicePhoneHeartbeatNetworkRequest(POLICE_PHONE_ID, "2026-11-20T00:01:30Z")
            ),
            harness.requests
        )
        assertEquals(listOf(API_BASE_URL, API_BASE_URL, API_BASE_URL), harness.apiBaseUrls)
        assertEquals(listOf(ACCESS_TOKEN, ACCESS_TOKEN, ACCESS_TOKEN), harness.sentAccessTokens)
    }

    @Test
    fun sendsAgainOnNextCycleAfterFailureWithoutBlockingCaller() = runBlocking {
        val harness = HeartbeatHarness(this)
        harness.responses += { throw SuriMapNetworkException(IOException("network down")) }
        harness.responses += { SuriMapApiResponse(503, null, "service_unavailable") }

        // hold는 전송을 기다리지 않고 바로 돌아온다. 화면에서는 hold만 부른다.
        harness.scheduler.setSignedIn(true)
        harness.scheduler.hold(harness.screen, TARGET)
        repeat(3) { assertTrue(harness.tick()) }
        harness.scheduler.setSignedIn(false)

        assertEquals(
            listOf("2026-11-20T00:00:30Z", "2026-11-20T00:01:00Z", "2026-11-20T00:01:30Z"),
            harness.requests.map(PolicePhoneHeartbeatNetworkRequest::clientTs)
        )
    }

    @Test
    fun keepsSendingWhileSearchPathLocationServiceHoldsAfterScreenReleases() = runBlocking {
        val harness = HeartbeatHarness(this)

        harness.scheduler.setSignedIn(true)
        harness.scheduler.hold(harness.screen, TARGET)
        harness.scheduler.hold(AccountHeartbeatHolder.SearchPathLocationService, TARGET)
        assertTrue(harness.tick())
        harness.scheduler.release(harness.screen)
        assertTrue(harness.tick())
        assertTrue(harness.tick())
        harness.scheduler.release(AccountHeartbeatHolder.SearchPathLocationService)
        val sentAfterServiceStopped = harness.tick()

        assertEquals(3, harness.requests.size)
        assertFalse(sentAfterServiceStopped)
    }

    @Test
    fun stopsSendingAfterLogoutEvenWhileSearchPathLocationServiceHolds() = runBlocking {
        val harness = HeartbeatHarness(this)

        harness.scheduler.setSignedIn(true)
        harness.scheduler.hold(harness.screen, TARGET)
        harness.scheduler.hold(AccountHeartbeatHolder.SearchPathLocationService, TARGET)
        assertTrue(harness.tick())
        harness.scheduler.setSignedIn(false)
        val sentAfterLogout = harness.tick()

        assertEquals(1, harness.requests.size)
        assertFalse(sentAfterLogout)
    }

    // R1: 수색 경로를 기록하는 중에 세션이 만료되고, 같은 계정으로 다시 로그인한 뒤 Activity가 사라지는 순서다.
    @Test
    fun resumesSearchPathLocationServiceHeartbeatAfterSessionExpiresAndUserSignsInAgain() = runBlocking {
        val harness = HeartbeatHarness(this)

        harness.scheduler.setSignedIn(true)
        harness.scheduler.hold(harness.screen, TARGET)
        harness.scheduler.hold(AccountHeartbeatHolder.SearchPathLocationService, TARGET)
        assertTrue(harness.tick())
        harness.scheduler.setSignedIn(false)
        val sentWhileSignedOut = harness.tick()
        harness.scheduler.setSignedIn(true)
        harness.scheduler.release(harness.screen)
        val sentAfterSignInWithoutScreen = harness.tick()
        harness.scheduler.setSignedIn(false)

        assertFalse(sentWhileSignedOut)
        assertTrue(sentAfterSignInWithoutScreen)
        assertEquals(2, harness.requests.size)
    }

    // R2: 알림으로 새 Activity가 만들어지면 새 화면의 hold가 먼저, 옛 화면의 release가 나중에 불린다.
    @Test
    fun keepsSendingWhenOldScreenReleasesAfterNewScreenHolds() = runBlocking {
        val harness = HeartbeatHarness(this)
        val oldScreen = AccountHeartbeatHolder()
        val newScreen = AccountHeartbeatHolder()

        harness.scheduler.setSignedIn(true)
        harness.scheduler.hold(oldScreen, TARGET)
        harness.scheduler.hold(newScreen, TARGET)
        harness.scheduler.release(oldScreen)
        val sentWithNewScreen = harness.tick()
        harness.scheduler.release(newScreen)
        val sentWithoutScreen = harness.tick()

        assertTrue(sentWithNewScreen)
        assertFalse(sentWithoutScreen)
        assertEquals(1, harness.requests.size)
    }

    // R3: 30초 대기가 끝나 토큰을 받는 동안 사용자가 로그아웃하는 순서다.
    @Test
    fun doesNotSendWhenUserSignsOutWhileAccessTokenIsLoading() = runBlocking {
        val harness = HeartbeatHarness(this)
        harness.onAccessToken = { harness.scheduler.setSignedIn(false) }

        harness.scheduler.setSignedIn(true)
        harness.scheduler.hold(harness.screen, TARGET)
        harness.tick()
        val sentAfterLogout = harness.tick()

        assertEquals(emptyList<PolicePhoneHeartbeatNetworkRequest>(), harness.requests)
        assertFalse(sentAfterLogout)
    }

    @Test
    fun skipsSendingWithoutAccessTokenAndTriesAgainOnNextCycle() = runBlocking {
        val harness = HeartbeatHarness(this)
        harness.accessTokens += null

        harness.scheduler.setSignedIn(true)
        harness.scheduler.hold(harness.screen, TARGET)
        assertTrue(harness.tick())
        assertTrue(harness.tick())
        harness.scheduler.setSignedIn(false)

        assertEquals(listOf("2026-11-20T00:01:00Z"), harness.requests.map(PolicePhoneHeartbeatNetworkRequest::clientTs))
    }

    private class HeartbeatHarness(scope: CoroutineScope) {
        val screen = AccountHeartbeatHolder()
        val sleeps = mutableListOf<Long>()
        val requests = mutableListOf<PolicePhoneHeartbeatNetworkRequest>()
        val apiBaseUrls = mutableListOf<String>()
        val sentAccessTokens = mutableListOf<String>()
        val responses = ArrayDeque<() -> SuriMapApiResponse>()
        val accessTokens = ArrayDeque<String?>()
        var onAccessToken: () -> Unit = {}
        private val clock = MutableClock(Instant.parse("2026-11-20T00:00:00Z"))
        private val ticks = Channel<Unit>(Channel.RENDEZVOUS)

        val scheduler =
            AccountHeartbeatScheduler(
                scope = scope,
                accessToken = {
                    onAccessToken()
                    if (accessTokens.isEmpty()) ACCESS_TOKEN else accessTokens.removeFirst()
                },
                send = { apiBaseUrl, request, accessToken ->
                    apiBaseUrls += apiBaseUrl
                    requests += request
                    sentAccessTokens += accessToken
                    responses.removeFirstOrNull()?.invoke() ?: SuriMapApiResponse(200, null, null)
                },
                clock = clock,
                sleep = { delayMs ->
                    sleeps += delayMs
                    ticks.receive()
                    clock.now = clock.now.plusMillis(delayMs)
                }
            )

        // 주기 대기 한 번을 끝낸다. 대기 중인 반복이 없어 끝내지 못하면 false를 돌려준다.
        suspend fun tick(): Boolean {
            repeat(5) { yield() }
            val delivered = ticks.trySend(Unit).isSuccess
            repeat(5) { yield() }
            return delivered
        }
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now
    }

    private companion object {
        const val POLICE_PHONE_ID = "11111111-1111-4111-8111-111111111111"
        const val API_BASE_URL = "https://suri-map.example.com/api"
        const val ACCESS_TOKEN = "access-token-001"
        val TARGET = AccountHeartbeatTarget(policePhoneId = POLICE_PHONE_ID, apiBaseUrl = API_BASE_URL)
    }
}
