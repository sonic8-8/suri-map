package com.surimap

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SuriMapApplicationTest {
    @Test
    fun applicationBootstrapsInRobolectric() {
        val application = RuntimeEnvironment.getApplication()

        assertTrue(application is SuriMapApplication)
    }
}
