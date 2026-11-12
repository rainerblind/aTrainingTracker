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

package com.atrainingtracker.trainingtracker.ui.map

import android.graphics.Bitmap
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral contract tests verifying caller-thread GraphicsLayer rasterization and exception-safe
 * snapshot cleanup in MapDetailLayout (REQ-REV-006.5 / TST-REV-007.5 / ATT-6685).
 */
class MapDetailLayoutSnapshotContractTest {

    @Test
    fun testMapDetailLayout_onSnapshotReady_rasterizesOnCallerThreadWithoutDefaultDispatcher_TST_REV_007_5() = runBlocking {
        val callerThread = Thread.currentThread()
        val headerBmp = mockk<Bitmap>(relaxed = true)
        val mapBmp = mockk<Bitmap>(relaxed = true)
        val elevationBmp = mockk<Bitmap>(relaxed = true)
        val analyticsBmp = mockk<Bitmap>(relaxed = true)

        var headerCaptureThread: Thread? = null
        var elevationCaptureThread: Thread? = null
        var analyticsCaptureThread: Thread? = null
        var sharedBitmaps: List<Bitmap?>? = null
        var errorCaught: Throwable? = null
        var finallyInvoked = false

        executeSnapshotCaptureAndShare(
            captureHeader = {
                headerCaptureThread = Thread.currentThread()
                headerBmp
            },
            captureElevation = {
                elevationCaptureThread = Thread.currentThread()
                elevationBmp
            },
            captureAnalytics = {
                analyticsCaptureThread = Thread.currentThread()
                analyticsBmp
            },
            mapBitmap = mapBmp,
            shareAction = { h, m, e, a ->
                sharedBitmaps = listOf(h, m, e, a)
            },
            onError = { errorCaught = it },
            onFinally = { finallyInvoked = true }
        )

        assertSame("Header layer must rasterize on caller coroutine thread without switching to Dispatchers.Default", callerThread, headerCaptureThread)
        assertSame("Elevation layer must rasterize on caller coroutine thread", callerThread, elevationCaptureThread)
        assertSame("Analytics layer must rasterize on caller coroutine thread", callerThread, analyticsCaptureThread)
        assertEquals(listOf(headerBmp, mapBmp, elevationBmp, analyticsBmp), sharedBitmaps)
        assertEquals(null, errorCaught)
        assertTrue("onFinally must always execute to reset isSharing and invoke onSnapshotComplete", finallyInvoked)
    }

    @Test
    fun testMapDetailLayout_onSnapshotReady_whenRasterizationThrows_invokesErrorAndFinally_TST_REV_007_5() = runBlocking {
        val mapBmp = mockk<Bitmap>(relaxed = true)
        var errorCaught: Throwable? = null
        var finallyInvoked = false

        executeSnapshotCaptureAndShare(
            captureHeader = { throw IllegalStateException("HardwareBitmap rasterization failure") },
            captureElevation = { null },
            captureAnalytics = { null },
            mapBitmap = mapBmp,
            shareAction = { _, _, _, _ -> },
            onError = { errorCaught = it },
            onFinally = { finallyInvoked = true }
        )

        assertNotNull("Exception must be caught and routed to onError", errorCaught)
        assertTrue("onFinally must still execute when captureHeader throws", finallyInvoked)
    }

    @Test
    fun testMapDetailLayout_onSnapshotReady_whenCancellationExceptionThrown_doesNotInvokeOnError_TST_REV_007_6() = runBlocking {
        val mapBmp = mockk<Bitmap>(relaxed = true)
        var errorCaught: Throwable? = null
        var finallyInvoked = false

        executeSnapshotCaptureAndShare(
            captureHeader = { null },
            captureElevation = { null },
            captureAnalytics = { null },
            mapBitmap = mapBmp,
            shareAction = { _, _, _, _ ->
                throw CancellationException("rememberCoroutineScope cancelled")
            },
            onError = { errorCaught = it },
            onFinally = { finallyInvoked = true }
        )

        assertEquals("CancellationException must be safely ignored without routing to onError", null, errorCaught)
        assertTrue("onFinally must always execute to reset isSharing and invoke onSnapshotComplete", finallyInvoked)
    }

    @Test
    fun testMapDetailLayout_onSnapshotReady_whenForgottenCoroutineScopeException_doesNotInvokeOnError_TST_REV_007_6() = runBlocking {
        val mapBmp = mockk<Bitmap>(relaxed = true)
        var errorCaught: Throwable? = null
        var finallyInvoked = false

        val forgottenException = Class.forName("androidx.compose.runtime.ForgottenCoroutineScopeException")
            .getDeclaredConstructor()
            .apply { isAccessible = true }
            .newInstance() as CancellationException

        executeSnapshotCaptureAndShare(
            captureHeader = { null },
            captureElevation = { null },
            captureAnalytics = { null },
            mapBitmap = mapBmp,
            shareAction = { _, _, _, _ ->
                throw forgottenException
            },
            onError = { errorCaught = it },
            onFinally = { finallyInvoked = true }
        )

        assertEquals("ForgottenCoroutineScopeException must be safely ignored without routing to onError", null, errorCaught)
        assertTrue("onFinally must always execute when ForgottenCoroutineScopeException occurs", finallyInvoked)
    }

    @Test
    fun testMapDetailLayout_onSnapshotReady_whenNonCancellationExceptionThrownInShareAction_invokesOnError_TST_REV_007_6() = runBlocking {
        val mapBmp = mockk<Bitmap>(relaxed = true)
        var errorCaught: Throwable? = null
        var finallyInvoked = false

        executeSnapshotCaptureAndShare(
            captureHeader = { null },
            captureElevation = { null },
            captureAnalytics = { null },
            mapBitmap = mapBmp,
            shareAction = { _, _, _, _ ->
                throw IllegalStateException("File I/O failure during saveAndShare")
            },
            onError = { errorCaught = it },
            onFinally = { finallyInvoked = true }
        )

        assertNotNull("Non-cancellation exceptions must still be caught and routed to onError", errorCaught)
        assertTrue(errorCaught is IllegalStateException)
        assertTrue("onFinally must always execute", finallyInvoked)
    }
}
