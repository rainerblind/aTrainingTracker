/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.accessibility

import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.accessibility.AccessibilityEventCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Unit tests verifying runtime resilience against NoSuchMethodError on defective
 * Android 14 (API 34) builds missing setAccessibilityDataSensitive / isAccessibilityDataSensitive
 * in AccessibilityEventCompat$Api34Impl (ATT-1347, REQ-UI-164, TST-UI-116).
 */
class AccessibilityEventCompatResilienceTest {

    private lateinit var api34Class: Class<*>
    private lateinit var setMethod: Method
    private lateinit var isMethod: Method

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        api34Class = Class.forName("androidx.core.view.accessibility.AccessibilityEventCompat\$Api34Impl")
        setMethod = api34Class.getDeclaredMethod(
            "setAccessibilityDataSensitive",
            AccessibilityEvent::class.java,
            java.lang.Boolean.TYPE
        ).apply { isAccessible = true }

        isMethod = api34Class.getDeclaredMethod(
            "isAccessibilityDataSensitive",
            AccessibilityEvent::class.java
        ).apply { isAccessible = true }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * TST-UI-116.1: When the platform framework omits setAccessibilityDataSensitive(boolean)
     * and throws NoSuchMethodError, Api34Impl catches LinkageError, logs a diagnostic warning,
     * and returns cleanly without unhandled exceptions.
     */
    @Test
    fun testSetAccessibilityDataSensitive_whenPlatformMethodThrowsNoSuchMethodError_isSuppressedCleanly() {
        val mockEvent = mockk<AccessibilityEvent>(relaxed = true)
        every {
            mockEvent.setAccessibilityDataSensitive(any())
        } throws NoSuchMethodError(
            "No virtual method setAccessibilityDataSensitive(Z)V in class Landroid/view/accessibility/AccessibilityEvent;"
        )

        // Invoke Api34Impl.setAccessibilityDataSensitive(mockEvent, true)
        setMethod.invoke(null, mockEvent, true)

        // Verify warning was logged to Android logcat
        verify(atLeast = 1) {
            Log.w(
                "AccessibilityEventCompat",
                match { it.contains("setAccessibilityDataSensitive missing on platform framework") },
                any<LinkageError>()
            )
        }
    }

    /**
     * TST-UI-116.2: When the platform framework omits isAccessibilityDataSensitive()
     * and throws NoSuchMethodError, Api34Impl catches LinkageError, logs a warning,
     * and safely returns false.
     */
    @Test
    fun testIsAccessibilityDataSensitive_whenPlatformMethodThrowsNoSuchMethodError_returnsFalseSafely() {
        val mockEvent = mockk<AccessibilityEvent>(relaxed = true)
        every {
            mockEvent.isAccessibilityDataSensitive()
        } throws NoSuchMethodError(
            "No virtual method isAccessibilityDataSensitive()Z in class Landroid/view/accessibility/AccessibilityEvent;"
        )

        // Invoke Api34Impl.isAccessibilityDataSensitive(mockEvent)
        val result = isMethod.invoke(null, mockEvent) as Boolean

        // Must return false cleanly
        assertFalse("Expected false when platform method is missing", result)

        // Verify warning was logged
        verify(atLeast = 1) {
            Log.w(
                "AccessibilityEventCompat",
                match { it.contains("isAccessibilityDataSensitive failed on platform") },
                any<LinkageError>()
            )
        }
    }

    /**
     * TST-UI-116.3: When running on an intact Android 14+ platform, platform methods
     * are invoked normally without behavioral divergence.
     */
    @Test
    fun testAccessibilityDataSensitive_whenPlatformMethodIntact_invokesSuccessfully() {
        val mockEvent = mockk<AccessibilityEvent>(relaxed = true)
        every { mockEvent.setAccessibilityDataSensitive(true) } returns Unit
        every { mockEvent.isAccessibilityDataSensitive() } returns true

        // Invoke setter
        setMethod.invoke(null, mockEvent, true)
        verify(exactly = 1) { mockEvent.setAccessibilityDataSensitive(true) }

        // Invoke getter
        val result = isMethod.invoke(null, mockEvent) as Boolean
        assertTrue("Expected true from intact platform", result)
    }

    /**
     * TST-UI-116.4: Safety invariant verification: Fatal non-linkage JVM errors
     * (e.g. OutOfMemoryError) must NOT be caught or swallowed by Api34Impl.
     */
    @Test
    fun testApi34Impl_doesNotSwallowFatalOutOfMemoryError() {
        val mockEvent = mockk<AccessibilityEvent>(relaxed = true)
        every {
            mockEvent.setAccessibilityDataSensitive(any())
        } throws OutOfMemoryError("Simulated fatal JVM out-of-memory error")

        try {
            setMethod.invoke(null, mockEvent, true)
            fail("Expected OutOfMemoryError to propagate without being caught")
        } catch (e: InvocationTargetException) {
            assertTrue(
                "Target exception must be OutOfMemoryError",
                e.targetException is OutOfMemoryError
            )
        }
    }

    /**
     * TST-UI-116.5 & TST-UI-116.6: End-to-end delegation through AccessibilityEventCompat public API.
     */
    @Test
    fun testAccessibilityEventCompat_publicApiDelegation_doesNotCrash() {
        val mockEvent = mockk<AccessibilityEvent>(relaxed = true)

        // On current JVM test runner (Build.VERSION.SDK_INT may be < 34), verify public API call completes cleanly
        AccessibilityEventCompat.setAccessibilityDataSensitive(mockEvent, true)
        val isSensitive = AccessibilityEventCompat.isAccessibilityDataSensitive(mockEvent)

        // Verify safe default or invocation without crash
        if (Build.VERSION.SDK_INT < 34) {
            assertFalse(isSensitive)
        }
    }
}
