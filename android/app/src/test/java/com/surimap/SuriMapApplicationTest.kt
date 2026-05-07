package com.surimap

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SuriMapApplicationTest {
    @Test
    fun applicationBootstrapsInRobolectric() {
        val application = RuntimeEnvironment.getApplication()

        assertTrue(application is SuriMapApplication)
    }
}
