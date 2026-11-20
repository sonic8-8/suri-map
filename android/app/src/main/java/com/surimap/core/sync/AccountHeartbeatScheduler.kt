package com.surimap.core.sync

import com.surimap.core.network.PolicePhoneHeartbeatNetworkRequest
import com.surimap.core.network.SuriMapApiResponse
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

// 상황판의 정상 기준 60초가 이 주기의 두 배다.
const val ACCOUNT_HEARTBEAT_INTERVAL_MS = 30_000L

data class AccountHeartbeatTarget(
    val policePhoneId: String,
    val apiBaseUrl: String
)

// 같은 종류의 화면이 겹쳐 떠도 서로의 hold를 지우지 않도록, 인스턴스마다 다른 holder로 본다.
class AccountHeartbeatHolder {
    companion object {
        val SearchPathLocationService = AccountHeartbeatHolder()
    }
}

class AccountHeartbeatScheduler(
    private val scope: CoroutineScope,
    private val accessToken: suspend () -> String?,
    private val send: suspend (
        apiBaseUrl: String,
        request: PolicePhoneHeartbeatNetworkRequest,
        accessToken: String
    ) -> SuriMapApiResponse,
    private val clock: Clock = Clock.systemUTC(),
    private val intervalMs: Long = ACCOUNT_HEARTBEAT_INTERVAL_MS,
    private val sleep: suspend (Long) -> Unit = { delay(it) }
) {
    private val holders = LinkedHashMap<AccountHeartbeatHolder, AccountHeartbeatTarget>()
    private var signedIn = false
    private var runningTarget: AccountHeartbeatTarget? = null
    private var job: Job? = null

    @Synchronized
    fun hold(holder: AccountHeartbeatHolder, target: AccountHeartbeatTarget) {
        holders.remove(holder)
        holders[holder] = target
        refresh()
    }

    @Synchronized
    fun release(holder: AccountHeartbeatHolder) {
        holders.remove(holder)
        refresh()
    }

    // 로그아웃하거나 세션이 만료되면 위치 서비스가 동작하는 중이어도 전송이 멈춘다.
    // holder는 지우지 않으므로, 다시 로그인하면 남은 holder로 전송이 이어진다.
    @Synchronized
    fun setSignedIn(signedIn: Boolean) {
        this.signedIn = signedIn
        refresh()
    }

    private fun refresh() {
        val target = holders.values.lastOrNull()?.takeIf { signedIn }
        if (target == runningTarget) return
        job?.cancel()
        runningTarget = target
        job = target?.let { scope.launch { sendEveryInterval(it) } }
    }

    private suspend fun sendEveryInterval(target: AccountHeartbeatTarget) {
        while (true) {
            sleep(intervalMs)
            val request = PolicePhoneHeartbeatNetworkRequest(target.policePhoneId, clock.instant().toString())
            sendOnce(target.apiBaseUrl, request)
        }
    }

    // 실패해도 다음 주기에 다시 보내므로 결과를 버린다.
    private suspend fun sendOnce(apiBaseUrl: String, request: PolicePhoneHeartbeatNetworkRequest) {
        try {
            val token = accessToken() ?: return
            // 토큰을 받는 동안 로그아웃했으면 요청을 보내지 않는다.
            currentCoroutineContext().ensureActive()
            send(apiBaseUrl, request, token)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
        }
    }
}
