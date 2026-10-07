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

import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityWindowInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Unit tests verifying runtime resilience against NoSuchMethodError on defective
 * Android 14 (API 34) builds missing isRequestFromAccessibilityTool in
 * AccessibilityManagerCompat$Api34Impl (ATT-2584, REQ-UI-164, TST-UI-116).
 */
class AccessibilityManagerCompatResilienceTest {

    private lateinit var managerApi34Class: Class<*>
    private lateinit var isRequestFromAccessibilityToolMethod: Method

    private lateinit var windowApi34Class: Class<*>
    private lateinit var getTransitionTimeMillisMethod: Method
    private lateinit var getLocalesMethod: Method

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        managerApi34Class = Class.forName("androidx.core.view.accessibility.AccessibilityManagerCompat\$Api34Impl")
        isRequestFromAccessibilityToolMethod = managerApi34Class.getDeclaredMethod(
            "isRequestFromAccessibilityTool",
            AccessibilityManager::class.java
        ).apply { isAccessible = true }

        windowApi34Class = Class.forName("androidx.core.view.accessibility.AccessibilityWindowInfoCompat\$Api34Impl")
        getTransitionTimeMillisMethod = windowApi34Class.getDeclaredMethod(
            "getTransitionTimeMillis",
            AccessibilityWindowInfo::class.java
        ).apply { isAccessible = true }

        getLocalesMethod = windowApi34Class.getDeclaredMethod(
            "getLocales",
            AccessibilityWindowInfo::class.java
        ).apply { isAccessible = true }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testIsRequestFromAccessibilityTool_whenPlatformMethodThrowsNoSuchMethodError_returnsFalseSafely() {
        val mockManager = mockk<AccessibilityManager>(relaxed = true)
        every {
            mockManager.isRequestFromAccessibilityTool
        } throws NoSuchMethodError(
            "No virtual method isRequestFromAccessibilityTool()Z in class Landroid/view/accessibility/AccessibilityManager;"
        )

        val result = isRequestFromAccessibilityToolMethod.invoke(null, mockManager) as Boolean

        assertFalse("Should return false fallback on NoSuchMethodError", result)
        verify(atLeast = 1) {
            Log.w(
                "AccessibilityManagerCompat",
                match { it.contains("isRequestFromAccessibilityTool missing on platform framework") },
                any<LinkageError>()
            )
        }
    }

    @Test
    fun testIsRequestFromAccessibilityTool_whenPlatformMethodIntact_invokesSuccessfully() {
        val mockManager = mockk<AccessibilityManager>(relaxed = true)
        every {
            mockManager.isRequestFromAccessibilityTool
        } returns true

        val result = isRequestFromAccessibilityToolMethod.invoke(null, mockManager) as Boolean

        assertTrue("Should return true when platform method exists and returns true", result)
    }

    @Test
    fun testApi34Impl_doesNotSwallowFatalOutOfMemoryError() {
        val mockManager = mockk<AccessibilityManager>(relaxed = true)
        every {
            mockManager.isRequestFromAccessibilityTool
        } throws OutOfMemoryError("Simulated heap exhaustion")

        try {
            isRequestFromAccessibilityToolMethod.invoke(null, mockManager)
            fail("Expected InvocationTargetException containing OutOfMemoryError")
        } catch (e: InvocationTargetException) {
            assertTrue(
                "Target exception must be OutOfMemoryError",
                e.targetException is OutOfMemoryError
            )
        }
    }

    @Test
    fun testAccessibilityWindowInfoCompat_getTransitionTimeMillis_whenThrowsNoSuchMethodError_returnsZero() {
        val mockWindow = mockk<AccessibilityWindowInfo>(relaxed = true)
        every {
            mockWindow.transitionTimeMillis
        } throws NoSuchMethodError("No virtual method getTransitionTimeMillis()J")

        val result = getTransitionTimeMillisMethod.invoke(null, mockWindow) as Long

        assertEquals(0L, result)
        verify(atLeast = 1) {
            Log.w(
                "AccessibilityWindowInfo",
                match { it.contains("getTransitionTimeMillis missing on platform framework") },
                any<LinkageError>()
            )
        }
    }

    @Test
    fun testAccessibilityWindowInfoCompat_getLocales_whenThrowsNoSuchMethodError_returnsNull() {
        val mockWindow = mockk<AccessibilityWindowInfo>(relaxed = true)
        every {
            mockWindow.locales
        } throws NoSuchMethodError("No virtual method getLocales()")

        val result = getLocalesMethod.invoke(null, mockWindow)

        assertNull(result)
        verify(atLeast = 1) {
            Log.w(
                "AccessibilityWindowInfo",
                match { it.contains("getLocales missing on platform framework") },
                any<LinkageError>()
            )
        }
    }
}
