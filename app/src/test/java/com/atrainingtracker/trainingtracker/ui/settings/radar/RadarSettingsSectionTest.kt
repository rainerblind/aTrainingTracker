/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.settings.radar

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.radar.RadarScenario
import com.atrainingtracker.banalservice.devices.radar.RadarState
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.audio.radar.RadarAlertManager
import com.atrainingtracker.trainingtracker.settings.RadarEdgeSide
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Behavioral Robolectric Compose UI tests and resource contract checks for [RadarSettingsSection]
 * (REQ-PRO-132.4, TST-PRO-132.4, REQ-CON-031.5, TST-CON-031.5b, REQ-TRK-062.5b, TST-TRK-062.5).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h2200dp-xhdpi")
class RadarSettingsSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var configFlow: MutableStateFlow<TuningConfig>
    private lateinit var radarStateFlow: MutableStateFlow<RadarState>
    private lateinit var simulatingFlow: MutableStateFlow<Boolean>
    private lateinit var mockDataStore: TuningPreferencesDataStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        configFlow = MutableStateFlow(TuningConfig())
        radarStateFlow = MutableStateFlow(RadarState())
        simulatingFlow = MutableStateFlow(false)

        mockDataStore = mockk(relaxed = true)
        every { mockDataStore.tuningConfigFlow } returns configFlow
        every { mockDataStore.getTuningConfig() } answers { configFlow.value }
        coEvery { mockDataStore.updateRadarEdgeSide(any()) } coAnswers {
            configFlow.value = configFlow.value.copy(radarEdgeSide = firstArg())
        }
        coEvery { mockDataStore.updateRadarSpatialAudioEnabled(any()) } coAnswers {
            configFlow.value = configFlow.value.copy(radarSpatialAudioEnabled = firstArg())
        }
        coEvery { mockDataStore.updateRadarBrakeLightEnabled(any()) } coAnswers {
            configFlow.value = configFlow.value.copy(radarBrakeLightEnabled = firstArg())
        }

        mockkObject(RadarStateRepository)
        every { RadarStateRepository.radarState } returns radarStateFlow

        mockkObject(RadarTrafficSimulator)
        every { RadarTrafficSimulator.isSimulatingFlow } returns simulatingFlow
        every { RadarTrafficSimulator.start(any()) } just Runs
        every { RadarTrafficSimulator.stop() } just Runs

        mockkObject(RadarAlertManager.Companion)
        every { RadarAlertManager.releasePreviewSynthesizer() } just Runs

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.uploadStravaRadarSummary() } returns false
        every { TrainingApplication.setUploadStravaRadarSummary(any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun renderSection(
        initialConfig: TuningConfig = TuningConfig(),
        onSimulationToggled: ((Boolean) -> Unit)? = null
    ) {
        configFlow.value = initialConfig
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        RadarSettingsSection(
                            tuningDataStore = mockDataStore,
                            onSimulationToggled = onSimulationToggled
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun toggleSwitchForTitle(titleResId: Int): SemanticsNodeInteraction {
        val titleNode = composeTestRule.onNodeWithText(context.getString(titleResId))
        titleNode.performScrollTo()
        val titleBounds = titleNode.getBoundsInRoot()
        val titleCenterY = (titleBounds.top.value + titleBounds.bottom.value) / 2f

        val switches = composeTestRule.onAllNodes(isToggleable())
        val count = switches.fetchSemanticsNodes().size
        var bestIndex = -1
        var bestDelta = Float.MAX_VALUE
        for (i in 0 until count) {
            val switchBounds = switches[i].getBoundsInRoot()
            val switchCenterY = (switchBounds.top.value + switchBounds.bottom.value) / 2f
            val delta = kotlin.math.abs(switchCenterY - titleCenterY)
            if (delta < bestDelta) {
                bestDelta = delta
                bestIndex = i
            }
        }
        assertTrue(
            "Expected a toggleable Switch vertically aligned with title $titleResId (bestDelta=${bestDelta}dp)",
            bestIndex >= 0 && bestDelta < 48f
        )
        return switches[bestIndex]
    }

    /**
     * TST-PRO-132.4 / TST-TRK-062.5: Verifies clear-state selector visibility is bound to
     * radarEdgeSide != FULL_SCREEN and clicking Full Screen updates DataStore and hides it.
     */
    @Test
    fun testRadarClearMode_conditionalVisibility_andEdgeSideClickUpdatesDataStore() {
        renderSection(TuningConfig(radarEdgeSide = RadarEdgeSide.RIGHT))

        val clearModeTitle = context.getString(R.string.radar_overlay_clear_mode)
        composeTestRule.onNodeWithText(clearModeTitle)
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(context.getString(R.string.radar_overlay_mode_fullscreen))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        coVerify { mockDataStore.updateRadarEdgeSide(RadarEdgeSide.FULL_SCREEN) }
        composeTestRule.onNodeWithText(clearModeTitle).assertDoesNotExist()
    }

    /**
     * TST-PRO-132.4: Verifies spatial audio sub-controls appear only when radarSpatialAudioEnabled
     * is true and toggling the switch persists the updated state.
     */
    @Test
    fun testSpatialAudio_conditionalSubControls_andToggleCallback() {
        renderSection(TuningConfig(radarSpatialAudioEnabled = false))

        val orientationTitle = context.getString(R.string.radar_traffic_orientation_title)
        val laneChangeTitle = context.getString(R.string.radar_lane_change_tick_title)
        composeTestRule.onNodeWithText(orientationTitle).assertDoesNotExist()
        composeTestRule.onNodeWithText(laneChangeTitle).assertDoesNotExist()

        toggleSwitchForTitle(R.string.radar_spatial_audio_title)
            .assertIsOff()
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        coVerify { mockDataStore.updateRadarSpatialAudioEnabled(true) }
        composeTestRule.onNodeWithText(orientationTitle)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(laneChangeTitle)
            .performScrollTo()
            .assertIsDisplayed()
    }

    /**
     * TST-PRO-132.4 / TST-CON-031.5b: Verifies retired 3-way RadarLightControlMode selectors are
     * absent from RadarSettingsSection while the brake-light toggle remains visible and interactive.
     */
    @Test
    fun testRadarSettingsSection_retires3WayControlMode_retainsInteractiveBrakeLight() {
        renderSection(TuningConfig(radarBrakeLightEnabled = true))

        composeTestRule.onNodeWithText(context.getString(R.string.radar_light_control_mode_title)).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.radar_light_control_mode_manual)).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.radar_light_control_mode_fixed)).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.radar_light_control_mode_smart)).assertDoesNotExist()

        composeTestRule.onNodeWithText(context.getString(R.string.radar_light_settings_title))
            .performScrollTo()
            .assertIsDisplayed()

        toggleSwitchForTitle(R.string.radar_brake_light_title)
            .assertIsOn()
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        coVerify { mockDataStore.updateRadarBrakeLightEnabled(false) }
    }

    /**
     * TST-PRO-132.4: Verifies Traffic Simulator Start/Stop buttons dispatch simulator actions
     * and invoke onSimulationToggled with the exact boolean transition.
     */
    @Test
    fun testTrafficSimulator_startAndStop_invokesSimulatorAndCallback() {
        val callbackEvents = mutableListOf<Boolean>()
        renderSection(onSimulationToggled = { callbackEvents.add(it) })

        composeTestRule.onNodeWithText(context.getString(R.string.radar_simulator_start))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        verify { RadarTrafficSimulator.start(RadarScenario.SINGLE_CAR) }
        assertEquals(listOf(true), callbackEvents)

        simulatingFlow.value = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.radar_simulator_stop))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        verify { RadarTrafficSimulator.stop() }
        assertEquals(listOf(true, false), callbackEvents)
    }

    private fun getXmlString(file: File, stringName: String): String? {
        val target = if (file.exists()) file else File("app/" + file.path)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(target)
        val nodes = doc.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? org.w3c.dom.Element ?: continue
            if (el.getAttribute("name") == stringName) {
                return el.textContent
            }
        }
        return null
    }

    /**
     * TST-TRK-063.7a / ATT-7738: German audio alert mode labels must be <= 12 characters
     * to fit comfortably on 360-412dp single-line segmented button rows without ellipsis.
     */
    @Test
    fun testRadarSettingsStrings_alertModeLabelsFitBudget() {
        val deFile = File("src/main/res/values-de/strings_radar.xml")
        val alertModes = listOf(
            "radar_alert_mode_all",
            "radar_alert_mode_approach_fast",
            "radar_alert_mode_fast_only",
            "radar_alert_mode_muted"
        )
        for (key in alertModes) {
            val text = getXmlString(deFile, key)
            assertNotNull("String $key must exist in values-de", text)
            assertTrue(
                "German alert mode label '$text' for $key must be <= 12 chars (got ${text!!.length})",
                text.length <= 12
            )
        }
    }

    /**
     * TST-TRK-073.4a / ATT-7738: Close-pass warning threshold labels across all 9 supported locales
     * must be <= 16 characters to fit 3-button segmented rows without ellipsis.
     */
    @Test
    fun testRadarSettingsStrings_closePassLabelsFitBudget() {
        val locales = listOf("values", "values-de", "values-es", "values-fr", "values-it", "values-ja", "values-nl", "values-pl", "values-pt")
        val thresholdKeys = listOf(
            "radar_close_pass_threshold_1_0m",
            "radar_close_pass_threshold_1_5m",
            "radar_close_pass_threshold_2_0m"
        )
        for (loc in locales) {
            val file = File("src/main/res/$loc/strings_radar.xml")
            for (key in thresholdKeys) {
                val text = getXmlString(file, key)
                assertNotNull("String $key must exist in $loc", text)
                assertTrue(
                    "Close-pass label '$text' for $key in $loc must be <= 16 chars (got ${text!!.length})",
                    text.length <= 16
                )
            }
        }
    }

    /**
     * TST-CON-031.5e / ATT-7738: In German and Dutch, section heading and control title
     * must be disambiguated so 'Rücklicht-Steuerung' / 'Achterlichtbediening' does not appear back-to-back.
     */
    @Test
    fun testRadarSettingsStrings_deAndNlLightTitlesDisambiguated() {
        val deFile = File("src/main/res/values-de/strings_radar.xml")
        val deSettingsTitle = getXmlString(deFile, "radar_light_settings_title")
        val deControlModeTitle = getXmlString(deFile, "radar_light_control_mode_title")
        assertNotEquals(
            "In German, radar_light_settings_title and radar_light_control_mode_title must not be identical",
            deSettingsTitle,
            deControlModeTitle
        )

        val nlFile = File("src/main/res/values-nl/strings_radar.xml")
        val nlSettingsTitle = getXmlString(nlFile, "radar_light_settings_title")
        val nlControlModeTitle = getXmlString(nlFile, "radar_light_control_mode_title")
        assertNotEquals(
            "In Dutch, radar_light_settings_title and radar_light_control_mode_title must not be identical",
            nlSettingsTitle,
            nlControlModeTitle
        )
    }

    /**
     * ATT-7738: Dynamic helper descriptions for audio alert modes must exist.
     */
    @Test
    fun testRadarSettingsStrings_alertModeDescriptionsExist() {
        assertNotEquals(0, R.string.radar_alert_mode_all_desc)
        assertNotEquals(0, R.string.radar_alert_mode_approach_fast_desc)
        assertNotEquals(0, R.string.radar_alert_mode_fast_only_desc)
        assertNotEquals(0, R.string.radar_alert_mode_muted_desc)
    }
}
