package com.atrainingtracker.trainingtracker.ui.common

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying runtime resilience against NoClassDefFoundError SelectGesture crash (TST-UI-302, REQ-UI-342).
 */
class ComposeHandwritingResilienceTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<Throwable>()) } returns 0
        every { Log.e(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun disableComposeStylusHandwriting_updatesFieldAndReturnsFalse() {
        // Attempt to disable stylus handwriting flag via resilience component
        val result = ComposeHandwritingResilience.disableComposeStylusHandwriting()
        assertTrue("disableComposeStylusHandwriting() should succeed", result)

        // Verify the value returned by Compose's internal accessor
        val clazz = Class.forName("androidx.compose.foundation.text.handwriting.StylusHandwriting_androidKt")
        val method = clazz.getMethod("isStylusHandwritingSupported")
        val isSupported = method.invoke(null) as Boolean

        assertFalse("isStylusHandwritingSupported() must return false after disable", isSupported)
    }

    @Test
    fun install_isIdempotentAndDoesNotThrow() {
        // Calling install multiple times should execute cleanly and safely without exception
        ComposeHandwritingResilience.install()
        ComposeHandwritingResilience.install()
        ComposeHandwritingResilience.install()
    }
}
