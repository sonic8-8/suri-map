package com.surimap.ui.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}
