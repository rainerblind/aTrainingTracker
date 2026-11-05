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
 */

package com.atrainingtracker.trainingtracker.ui.dialogs

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract and behavioral tests for [FirstJubileeCelebrationDialog] and [FirstJubileeActionHelper]
 * (REQ-REV-003, REQ-REV-005, TST-REV-003, TST-REV-006 / ATT-2308).
 */
class FirstJubileeCelebrationDialogTest {

    private fun resolveSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app", relativePath),
            File("../app", relativePath)
        )
        return candidates.firstOrNull { it.exists() }
            ?: File(relativePath)
    }

    @Test
    fun testFirstJubileeCelebrationDialog_sourceContractVerification() {
        val file = resolveSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/dialogs/FirstJubileeCelebrationDialog.kt")
        assertTrue("FirstJubileeCelebrationDialog.kt must exist at ${file.absolutePath}", file.exists())
        val content = file.readText()

        // 1. Trophy icon
        assertTrue("Dialog must use EmojiEvents icon", content.contains("EmojiEvents"))

        // 2. Multi-channel actions: Google Play Review, Feedback Email, Pro Appeal
        assertTrue("Dialog must contain In-App Review action", content.contains("onRequestInAppReview"))
        assertTrue("Dialog must contain email feedback action", content.contains("onSendFeedbackEmail"))
        assertTrue("Dialog must contain Pro supporter appeal action", content.contains("onNavigateToSupporter"))

        // 3. Free vs Pro tier gating: supporter appeal note must be gated by !isProActive
        assertTrue("Supporter appeal must be conditionally gated by !isProActive", content.contains("if (!isProActive)"))

        // 4. In-App Review with ReviewManagerFactory
        assertTrue("Action helper must use ReviewManagerFactory", content.contains("ReviewManagerFactory"))

        // 5. Store fallback URI
        assertTrue("Action helper must have Play Store market fallback", content.contains("market://details?id="))

        // 6. Context unwrapping helper
        assertTrue("Dialog file must contain findActivity helper", content.contains("fun Context.findActivity()"))

        // 7. BuildConfig.DEBUG immediate fallback
        assertTrue("Action helper must check BuildConfig.DEBUG", content.contains("BuildConfig.DEBUG"))

        // 8. Rapid completion (< 300ms) review flow fallback
        assertTrue("Action helper must handle rapid completion fallback", content.contains("< 300"))
    }

    @Test
    fun testContext_findActivity_unwrapsContextWrappers() {
        val mockActivity = mockk<Activity>()
        assertEquals(mockActivity, mockActivity.findActivity())

        val wrapper1 = mockk<ContextWrapper> {
            every { baseContext } returns mockActivity
        }
        assertEquals(mockActivity, wrapper1.findActivity())

        val wrapper2 = mockk<ContextWrapper> {
            every { baseContext } returns wrapper1
        }
        assertEquals(mockActivity, wrapper2.findActivity())

        val plainContext = mockk<Context>()
        val nonActivityWrapper = mockk<ContextWrapper> {
            every { baseContext } returns plainContext
        }
        assertNull(plainContext.findActivity())
        assertNull(nonActivityWrapper.findActivity())
    }

    @Test
    fun testFirstJubileeActionHelper_debugBuildLaunchesPlayStoreFallback() {
        mockkStatic(Uri::class)
        val mockMarketUri = mockk<Uri>(relaxed = true)
        every { Uri.parse("market://details?id=com.atrainingtracker") } returns mockMarketUri
        every { mockMarketUri.toString() } returns "market://details?id=com.atrainingtracker"

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().addFlags(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().action } returns Intent.ACTION_VIEW

        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.packageName } returns "com.atrainingtracker.debug"

        val intentSlot = slot<Intent>()
        every { mockContext.startActivity(capture(intentSlot)) } returns Unit

        FirstJubileeActionHelper.launchInAppReviewOrStore(mockContext)

        assertTrue("Intent must be captured", intentSlot.isCaptured)
        val intent = intentSlot.captured
        assertEquals(Intent.ACTION_VIEW, intent.action)

        unmockkConstructor(Intent::class)
        unmockkStatic(Uri::class)
    }

    @Test
    fun testFirstJubileeActionHelper_openPlayStorePage_fallsBackToWebWhenMarketThrows() {
        mockkStatic(Uri::class)
        val mockMarketUri = mockk<Uri>(relaxed = true)
        val mockWebUri = mockk<Uri>(relaxed = true)
        every { Uri.parse("market://details?id=com.atrainingtracker") } returns mockMarketUri
        every { Uri.parse("https://play.google.com/store/apps/details?id=com.atrainingtracker") } returns mockWebUri

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().addFlags(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().action } returns Intent.ACTION_VIEW

        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.packageName } returns "com.atrainingtracker"

        val capturedIntents = mutableListOf<Intent>()
        every { mockContext.startActivity(capture(capturedIntents)) } throws android.content.ActivityNotFoundException() andThen Unit

        FirstJubileeActionHelper.openPlayStorePage(mockContext)

        assertEquals(2, capturedIntents.size)
        assertEquals(Intent.ACTION_VIEW, capturedIntents[0].action)
        assertEquals(Intent.ACTION_VIEW, capturedIntents[1].action)

        unmockkConstructor(Intent::class)
        unmockkStatic(Uri::class)
    }

    @Test
    fun testFirstJubileeActionHelper_feedbackEmailIntentCreation() {
        mockkStatic(Uri::class)
        val mockUri = mockk<Uri>(relaxed = true)
        every { Uri.parse(any()) } returns mockUri
        every { mockUri.toString() } returns "mailto:rainer.blind@gmail.com"

        mockkConstructor(Intent::class)
        var capturedSubject: String? = null
        every { anyConstructed<Intent>().setData(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(Intent.EXTRA_SUBJECT, any<String>()) } answers {
            capturedSubject = secondArg()
            self as Intent
        }
        every { anyConstructed<Intent>().addFlags(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().action } returns Intent.ACTION_SENDTO

        val mockContext = mockk<Context>(relaxed = true)
        val intentSlot = slot<Intent>()

        every { mockContext.startActivity(capture(intentSlot)) } returns Unit

        FirstJubileeActionHelper.launchFeedbackEmail(mockContext)

        assertTrue(intentSlot.isCaptured)
        val intent = intentSlot.captured
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertTrue("Subject must mention aTrainingTracker Feedback", capturedSubject?.contains("aTrainingTracker Feedback") == true)

        unmockkConstructor(Intent::class)
        unmockkStatic(Uri::class)
    }

    @Test
    fun testSupporterDebugSection_containsJubileePreviewTrigger() {
        val file = resolveSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SupporterDebugSection.kt")
        assertTrue("SupporterDebugSection.kt must exist at ${file.absolutePath}", file.exists())
        val content = file.readText()

        assertTrue("Must check BuildConfig.DEBUG", content.contains("BuildConfig.DEBUG"))
        assertTrue("Must include preview jubilee dialog trigger", content.contains("showPreviewJubileeDialog"))
        assertTrue("Must contain debug_jubilee_preview_button", content.contains("debug_jubilee_preview_button"))
        assertTrue("Must present FirstJubileeCelebrationDialog in preview", content.contains("FirstJubileeCelebrationDialog"))
    }
}
