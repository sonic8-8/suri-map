package com.surimap.core.auth

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OidcSessionStateStoreTest {
    private val preferences: SharedPreferences =
        RuntimeEnvironment.getApplication().getSharedPreferences("oidc-session-store-test", Context.MODE_PRIVATE)

    // 저장소 값을 비교한 직후에 다른 스레드에서 로그아웃(clear)하는 순서다.
    @Test
    fun signOutRightAfterComparisonIsNotOverwrittenByRefreshedSession() {
        lateinit var store: OidcSessionStateStore
        var signOut: Thread? = null
        val signedOut = CountDownLatch(1)
        var signOutWhenRead = false
        val comparingPreferences =
            object : SharedPreferences by preferences {
                override fun getString(key: String?, defValue: String?): String? {
                    val value = preferences.getString(key, defValue)
                    if (signOutWhenRead) {
                        signOutWhenRead = false
                        signOut = thread {
                            store.clear()
                            signedOut.countDown()
                        }
                        // 잠금이 없으면 이 대기 안에 로그아웃이 끝난다. 그러면 그 뒤에 옛 세션이 다시 저장된다.
                        signedOut.await(200, TimeUnit.MILLISECONDS)
                    }
                    return value
                }
            }
        store = OidcSessionStateStore(comparingPreferences)
        store.save(LOADED)

        signOutWhenRead = true
        val saved = store.saveIfUnchanged(expectedAuthStateJson = LOADED, authStateJson = REFRESHED)
        signOut?.join(5_000)

        assertTrue(saved)
        assertTrue(signedOut.await(0, TimeUnit.MILLISECONDS))
        assertNull(store.load())
    }

    @Test
    fun saveIfUnchangedSavesWhenStoreIsUnchanged() {
        val store = OidcSessionStateStore(preferences)
        store.save(LOADED)

        val saved = store.saveIfUnchanged(expectedAuthStateJson = LOADED, authStateJson = REFRESHED)

        assertTrue(saved)
        assertEquals(REFRESHED, store.load())
    }

    // 로그아웃한 뒤 다른 계정으로 다시 로그인해서 저장소의 세션이 바뀐 경우다.
    @Test
    fun saveIfUnchangedDoesNotOverwriteAnotherAccountSession() {
        val store = OidcSessionStateStore(preferences)
        store.save(OTHER_ACCOUNT)

        val saved = store.saveIfUnchanged(expectedAuthStateJson = LOADED, authStateJson = REFRESHED)

        assertFalse(saved)
        assertEquals(OTHER_ACCOUNT, store.load())
    }

    @Test
    fun saveIfUnchangedDoesNotSaveWhenStoreWasCleared() {
        val store = OidcSessionStateStore(preferences)
        store.save(LOADED)
        store.clear()

        val saved = store.saveIfUnchanged(expectedAuthStateJson = LOADED, authStateJson = REFRESHED)

        assertFalse(saved)
        assertNull(store.load())
    }

    private companion object {
        const val LOADED = """{"account":"a","refresh":"1"}"""
        const val REFRESHED = """{"account":"a","refresh":"2"}"""
        const val OTHER_ACCOUNT = """{"account":"b","refresh":"1"}"""
    }
}
