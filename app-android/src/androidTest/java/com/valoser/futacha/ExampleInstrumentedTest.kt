package com.valoser.futacha

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun appContext_usesExpectedPackageAndApplicationClass() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals("com.valoser.futacha", appContext.packageName)
        assertTrue(appContext.applicationContext is FutachaApplication)
    }
}
