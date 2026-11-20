package com.surimap.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeepRefreshedOidcSessionTest {

    @Test
    fun returnsTokenWhenRefreshedSessionIsSavedOverLoadedSession() {
        val saves = mutableListOf<Pair<String, String>>()

        val token = keep { expected, authStateJson ->
            saves += expected to authStateJson
            true
        }

        assertEquals("fresh-token", token)
        assertEquals(listOf(LOADED to REFRESHED), saves)
    }

    // 갱신하는 동안 로그아웃했거나 다른 계정으로 로그인해서 저장소 값이 바뀐 경우다.
    @Test
    fun returnsNoTokenWhenStoreChangedDuringRefresh() {
        val token = keep { _, _ -> false }

        assertNull(token)
    }

    private fun keep(saveIfUnchanged: (String, String) -> Boolean): String? =
        keepRefreshedOidcSession(
            loadedAuthStateJson = LOADED,
            refreshedAuthStateJson = REFRESHED,
            freshAccessToken = "fresh-token",
            saveIfUnchanged = saveIfUnchanged
        )

    private companion object {
        const val LOADED = """{"account":"a","refresh":"1"}"""
        const val REFRESHED = """{"account":"a","refresh":"2"}"""
    }
}
