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

package com.atrainingtracker.trainingtracker.dialogs

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.helpers.ProcessExitReasonHelper.KillDiagnosis
import com.atrainingtracker.trainingtracker.helpers.ProcessExitReasonHelper.KillReason
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs

/**
 * Behavioral Compose UI layout verification, German 360dp / 1.3x font-scale non-truncation,
 * and 9-language localization parity tests for pure Compose [StartOrResumeAlertDialog] /
 * [StartOrResumeDialogContent] (REQ-UI-329, TST-UI-289, REQ-UI-2801 / ATT-2948, ATT-3165, ATT-8129).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class StartOrResumeDialogLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun resolveResourceFile(locale: String): File {
        val candidates = listOf(
            File("app/src/main/res/$locale/strings.xml"),
            File("src/main/res/$locale/strings.xml"),
            File("../app/src/main/res/$locale/strings.xml")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Cannot resolve strings.xml for locale $locale")
    }

    private fun assertNoVisualOverflow(
        node: SemanticsNodeInteraction,
        labelName: String,
        expectedFontScale: Float,
        maxAllowedLines: Int = 1
    ) {
        node.assertIsDisplayed()
        val semanticsNode = node.fetchSemanticsNode("Expected node for '$labelName'")
        val getTextLayout = semanticsNode.config.getOrNull(SemanticsActions.GetTextLayoutResult)
        assertNotNull("Text node '$labelName' must expose GetTextLayoutResult", getTextLayout)
        val results = mutableListOf<TextLayoutResult>()
        val ok = getTextLayout!!.action?.invoke(results) == true
        assertTrue("GetTextLayoutResult failed for '$labelName'", ok && results.isNotEmpty())
        val layout = results.first()
        assertEquals(
            "Dialog must actually render at requested fontScale=$expectedFontScale",
            expectedFontScale,
            layout.layoutInput.density.fontScale,
            0.05f
        )
        assertTrue(
            "Button '$labelName' lineCount=${layout.lineCount} must be <= $maxAllowedLines",
            layout.lineCount <= maxAllowedLines
        )
        assertFalse(
            "Button '$labelName' must not overflow vertically at 360dp/fontScale=$expectedFontScale",
            layout.didOverflowHeight
        )
        for (line in 0 until layout.lineCount) {
            assertFalse(
                "Button '$labelName' line $line must not ellipsize at 360dp/fontScale=$expectedFontScale",
                layout.isLineEllipsized(line)
            )
            val lineWidth = layout.getLineRight(line) - layout.getLineLeft(line)
            assertTrue(
                "Button '$labelName' line $line width ($lineWidth px) must fit within maxWidth (${layout.layoutInput.constraints.maxWidth} px)",
                lineWidth <= layout.layoutInput.constraints.maxWidth + 1f
            )
        }
        assertEquals(
            "All characters of '$labelName' must be visibly rendered without truncation",
            labelName.length,
            layout.getLineEnd(layout.lineCount - 1, visibleEnd = true)
        )
    }

    @Test
    fun testStartOrResumeDialog_allKillReasonsRenderContextualTitleAndActions() {
        val cases = listOf(
            KillDiagnosis(KillReason.BATTERY_KILL, 2, false, true) to R.string.unfinished_workout_title_battery,
            KillDiagnosis(KillReason.LOW_MEMORY, 1, false, false) to R.string.unfinished_workout_title_memory,
            KillDiagnosis(KillReason.PERMISSION_REVOKED, 1, false, false) to R.string.unfinished_workout_title_permission,
            KillDiagnosis(KillReason.GENERIC_UNFINISHED, 0, false, false) to R.string.unfinished_workout_title
        )

        val currentDiagnosis = androidx.compose.runtime.mutableStateOf(cases.first().first)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                StartOrResumeDialogContent(
                    diagnosis = currentDiagnosis.value,
                    onStartNew = {},
                    onResume = {},
                    onDisableBatteryOptimization = {}
                )
            }
        }

        for ((diagnosis, expectedTitleRes) in cases) {
            currentDiagnosis.value = diagnosis
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(context.getString(expectedTitleRes)).assertIsDisplayed()
            composeTestRule.onNodeWithText(context.getString(R.string.start_new_workout)).assertIsDisplayed()
            composeTestRule.onNodeWithText(context.getString(R.string.resume_workout)).assertIsDisplayed()
            if (diagnosis.shouldShowBatteryButton) {
                composeTestRule.onNodeWithText(context.getString(R.string.action_disable_battery_optimization))
                    .assertIsDisplayed()
            } else {
                composeTestRule.onNodeWithText(context.getString(R.string.action_disable_battery_optimization))
                    .assertDoesNotExist()
            }
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "de-w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun testStartOrResumeAlertDialog_german360dp_1_0x_stackedFullWidthNoTruncation() {
        verifyGermanStackedButtonsNonTruncation(fontScale = 1.0f)
    }

    @Test
    @Config(sdk = [34], qualifiers = "de-w360dp-h800dp-xhdpi", fontScale = 1.3f)
    fun testStartOrResumeAlertDialog_german360dp_1_3x_stackedFullWidthNoTruncation() {
        verifyGermanStackedButtonsNonTruncation(fontScale = 1.3f)
    }

    private fun verifyGermanStackedButtonsNonTruncation(fontScale: Float) {
        val diagnosis = KillDiagnosis(
            reason = KillReason.BATTERY_KILL,
            escalationLevel = 1,
            isStravaActive = false,
            shouldShowBatteryButton = true
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                StartOrResumeAlertDialog(
                    diagnosis = diagnosis,
                    onDismissRequest = {},
                    onStartNew = {},
                    onResume = {},
                    onDisableBatteryOptimization = {}
                )
            }
        }

        val batteryText = context.getString(R.string.action_disable_battery_optimization)
        val resumeText = context.getString(R.string.resume_workout)
        val startNewText = context.getString(R.string.start_new_workout)

        val batteryNode = composeTestRule.onNodeWithText(batteryText)
        val resumeNode = composeTestRule.onNodeWithText(resumeText)
        val startNewNode = composeTestRule.onNodeWithText(startNewText)

        assertNoVisualOverflow(batteryNode, batteryText, expectedFontScale = fontScale, maxAllowedLines = if (fontScale > 1.0f) 2 else 1)
        assertNoVisualOverflow(resumeNode, resumeText, expectedFontScale = fontScale, maxAllowedLines = 1)
        assertNoVisualOverflow(startNewNode, startNewText, expectedFontScale = fontScale, maxAllowedLines = 1)

        // Verify stacked vertical full-width ergonomics (docs/design_guidelines.md §2.8, ATT-3165)
        val resumeBounds = resumeNode.getUnclippedBoundsInRoot()
        val startNewBounds = startNewNode.getUnclippedBoundsInRoot()
        assertTrue(
            "Primary Resume button must stack above secondary Start New button (resume.bottom=${resumeBounds.bottom}, startNew.top=${startNewBounds.top})",
            resumeBounds.bottom.value <= startNewBounds.top.value
        )
        val resumeWidth = (resumeBounds.right - resumeBounds.left).value
        val startNewWidth = (startNewBounds.right - startNewBounds.left).value
        assertTrue(
            "Stacked full-width buttons must match width (resume=$resumeWidth dp, startNew=$startNewWidth dp)",
            abs(resumeWidth - startNewWidth) < 2f
        )
        assertTrue(
            "Dialog must preserve full-width phone ergonomics via usePlatformDefaultWidth=false (button width=$resumeWidth dp)",
            resumeWidth >= 240f
        )
    }

    @Test
    fun testKillDiagnosis_allStateCombinationsInstantiable() {
        val batteryDiagnosis = KillDiagnosis(
            reason = KillReason.BATTERY_KILL,
            escalationLevel = 2,
            isStravaActive = true,
            shouldShowBatteryButton = true
        )
        assertEquals(KillReason.BATTERY_KILL, batteryDiagnosis.reason)
        assertEquals(2, batteryDiagnosis.escalationLevel)
        assertTrue(batteryDiagnosis.isStravaActive)
        assertTrue(batteryDiagnosis.shouldShowBatteryButton)

        val memoryDiagnosis = KillDiagnosis(
            reason = KillReason.LOW_MEMORY,
            escalationLevel = 1,
            isStravaActive = false,
            shouldShowBatteryButton = false
        )
        assertEquals(KillReason.LOW_MEMORY, memoryDiagnosis.reason)
        assertFalse(memoryDiagnosis.shouldShowBatteryButton)

        val permDiagnosis = KillDiagnosis(
            reason = KillReason.PERMISSION_REVOKED,
            escalationLevel = 1,
            isStravaActive = false,
            shouldShowBatteryButton = false
        )
        assertEquals(KillReason.PERMISSION_REVOKED, permDiagnosis.reason)

        val genericDiagnosis = KillDiagnosis(
            reason = KillReason.GENERIC_UNFINISHED,
            escalationLevel = 0,
            isStravaActive = false,
            shouldShowBatteryButton = false
        )
        assertEquals(KillReason.GENERIC_UNFINISHED, genericDiagnosis.reason)
    }

    @Test
    fun testRecoveryAndGpsDialogs_9LanguageLocalizationParity() {
        val locales = listOf(
            "values",
            "values-de",
            "values-es",
            "values-fr",
            "values-it",
            "values-ja",
            "values-nl",
            "values-pl",
            "values-pt"
        )

        val requiredKeys = listOf(
            "unfinished_workout_title",
            "unfinished_workout_title_battery",
            "unfinished_workout_title_memory",
            "unfinished_workout_title_permission",
            "kill_reason_battery_stage1",
            "kill_reason_battery_stage1_strava",
            "kill_reason_battery_stage2",
            "kill_reason_battery_stage3",
            "kill_reason_low_memory",
            "kill_reason_permission_revoked",
            "action_disable_battery_optimization",
            "start_or_resume_dialog_message",
            "start_new_workout",
            "resume_workout",
            "gps_disabled_on_device",
            "gps_goto_settings"
        )

        val factory = DocumentBuilderFactory.newInstance()
        for (locale in locales) {
            val file = resolveResourceFile(locale)
            assertTrue("strings.xml must exist for locale $locale", file.exists())

            val doc = factory.newDocumentBuilder().parse(file)
            val stringNodes = doc.getElementsByTagName("string")
            val stringsMap = mutableMapOf<String, String>()

            for (i in 0 until stringNodes.length) {
                val node = stringNodes.item(i)
                val key = node.attributes.getNamedItem("name")?.nodeValue
                val text = node.textContent
                if (key != null) {
                    stringsMap[key] = text
                }
            }

            for (key in requiredKeys) {
                val text = stringsMap[key]
                assertNotNull("Missing key '$key' in locale '$locale'", text)
                assertFalse("Key '$key' is blank in locale '$locale'", text!!.trim().isEmpty())
            }
        }
    }
}
