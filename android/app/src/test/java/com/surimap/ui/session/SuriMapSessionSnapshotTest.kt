package com.surimap.ui.session

import com.surimap.ui.navigation.IncidentContext
import com.surimap.ui.navigation.PolicePhoneContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SuriMapSessionSnapshotTest {
    @Test
    fun `json round trip preserves only domain session fields`() {
        val snapshot =
            SuriMapSessionSnapshot(
                incidentId = "incident-1",
                currentOpId = "op-1",
                currentDutyShiftId = "shift-1",
                policePhoneId = "phone-1",
                apiBaseUrl = "https://k14c106.p.ssafy.io",
                tileBaseUrl = "https://k14c106.p.ssafy.io/tiles",
                objectStorageBaseUrl = "https://k14c106.p.ssafy.io"
            )

        val restored = SuriMapSessionSnapshot.fromJson(snapshot.toJson())

        assertNotNull(restored)
        assertEquals("incident-1", restored?.incidentId)
        assertEquals("phone-1", restored?.policePhoneId)
        assertEquals("https://k14c106.p.ssafy.io", restored?.apiBaseUrl)
    }

    @Test
    fun `police phone context accepts oidc access token from dedicated auth state`() {
        val context =
            SuriMapSessionSnapshot(
                policePhoneId = "phone-1",
                apiBaseUrl = "https://k14c106.p.ssafy.io"
            ).toPolicePhoneContext(
                accessToken = "access-token",
                accessTokenExpiresAtEpochMs = 1779085219000L
            )

        assertNotNull(context)
        assertEquals("access-token", context?.accessToken)
        assertEquals(1779085219000L, context?.accessTokenExpiresAtEpochMs)
    }

    @Test
    fun `json round trip keeps the account that owns the session`() {
        val snapshot =
            requireNotNull(
                SuriMapSessionSnapshot.from(
                    incidentContext = IncidentContext(incidentId = "incident-1"),
                    policePhoneContext = phoneContext(accountId = "account-1")
                )
            )

        val restoredContext =
            requireNotNull(SuriMapSessionSnapshot.fromJson(snapshot.toJson()))
                .toPolicePhoneContext()

        assertEquals("account-1", restoredContext?.accountId)
    }

    @Test
    fun `snapshot does not attach a token issued to another account`() {
        val snapshot =
            requireNotNull(
                SuriMapSessionSnapshot.from(
                    incidentContext = IncidentContext(incidentId = "incident-1"),
                    policePhoneContext = phoneContext(accountId = "account-1")
                )
            )

        val restoredContext = snapshot.toPolicePhoneContext(accessToken = accessToken("account-2"))

        assertEquals("account-1", restoredContext?.accountId)
        assertNull(restoredContext?.accessToken)
    }

    private fun phoneContext(accountId: String): PolicePhoneContext =
        PolicePhoneContext(
            policePhoneId = "phone-1",
            apiBaseUrl = "https://k14c106.p.ssafy.io",
            tileBaseUrl = "https://k14c106.p.ssafy.io/tiles",
            objectStorageBaseUrl = "https://k14c106.p.ssafy.io",
            accountId = accountId
        )

    private fun accessToken(accountId: String): String {
        val payload =
            java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("""{"accountId":"$accountId"}""".toByteArray())
        return "header.$payload.signature"
    }
}
