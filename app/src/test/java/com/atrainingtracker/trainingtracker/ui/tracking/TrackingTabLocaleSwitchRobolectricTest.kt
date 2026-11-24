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

package com.atrainingtracker.trainingtracker.ui.tracking

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager.TrackingViewsDbHelper
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabConfigHeader
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabPreviewHeader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Field
import java.util.Locale

/**
 * Robolectric SQLite + Compose UI tests verifying that newly created and legacy persisted default
 * tracking tab rows dynamically re-localize across locale switches while user-renamed custom tab
 * names remain strictly preserved (`REQ-TRK-097`, `TST-TRK-097.2`, `TST-TRK-097.3`).
 */
// Linked test specs: TST-TRK-097
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "de-w412dp-h915dp-xhdpi")
class TrackingTabLocaleSwitchRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var baseContext: Context
    private var db: SQLiteDatabase? = null

    @Before
    fun setUp() {
        baseContext = ApplicationProvider.getApplicationContext()
        setStaticField(TrainingApplication::class.java, "cAppContext", baseContext)
        resetSingletons()
    }

    @After
    fun tearDown() {
        db?.close()
        db = null
        resetSingletons()
        setStaticField(TrainingApplication::class.java, "cAppContext", null)
    }

    private fun resetSingletons() {
        TrackingViewsRepository.resetForTesting(null)
        try {
            val instanceField: Field = TrackingViewsDatabaseManager::class.java.getDeclaredField("cInstance")
            instanceField.isAccessible = true
            instanceField.set(null, null)
        } catch (_: Exception) {
        }
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (_: Exception) {
        }
    }

    private fun createLocalizedContext(languageTag: String): Context {
        val locale = Locale.forLanguageTag(languageTag)
        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(locale)
        return baseContext.createConfigurationContext(config)
    }

    @Test
    fun repository_reLocalizesFreshlyCreatedAndLegacyDefaultTabsWhilePreservingCustomNames_tstTrk097_2() = runBlocking {
        // 1. Seed real SQLite DB in German (simulating initial install or legacy German DB)
        val germanContext = createLocalizedContext("de")
        val helper = TrackingViewsDbHelper(germanContext)
        val memoryDb = SQLiteDatabase.create(null)
        db = memoryDb
        helper.onCreate(memoryDb)

        val dbManager = TrackingViewsDatabaseManager.getInstance(germanContext)
        dbManager.setDatabaseForTesting(memoryDb)

        // Verify BIKE_POWER seeded in German initially
        val initialGermanViews = TrackingViewsRepository.getInstance(germanContext)
            .getTrackingViewsFlow(ActivityType.BIKE_POWER)
            .first()
        assertEquals(listOf("Standard", "Leistung", "Karte", "Runden"), initialGermanViews.map { it.name })

        // Rename Tab 2 ("Leistung") -> custom user title "Race Cockpit" and add a legacy French row
        dbManager.updateNameOfTabView(initialGermanViews[1].tabViewId, "Race Cockpit")
        memoryDb.insert(
            TrackingViewsDbHelper.VIEWS_TABLE,
            null,
            ContentValues().apply {
                put(TrackingViewsDbHelper.ACTIVITY_TYPE, ActivityType.BIKE_POWER.name)
                put(TrackingViewsDbHelper.NAME, "Tours") // legacy French default for Laps
                put(TrackingViewsDbHelper.LAYOUT_NR, 5)
                put(TrackingViewsDbHelper.SHOW_LAP_BUTTON, 1)
                put(TrackingViewsDbHelper.SHOW_MAP, 0)
                put(TrackingViewsDbHelper.SHOW_LIVE_SEGMENTS, 0)
                put(TrackingViewsDbHelper.SHOW_ELEVATION_PROFILE, 0)
            }
        )

        // 2. Switch locale to Spanish -> Japanese -> Portuguese -> English -> German
        val expectedByLocale = mapOf(
            "es" to listOf("predeterminado", "Race Cockpit", "Mapa", "Vueltas", "Vueltas"),
            "ja" to listOf("デフォルト", "Race Cockpit", "マップ", "ラップ", "ラップ"),
            "pt" to listOf("padrão", "Race Cockpit", "Mapa", "Voltas", "Voltas"),
            "en" to listOf("default", "Race Cockpit", "Map", "Laps", "Laps"),
            "de" to listOf("Standard", "Race Cockpit", "Karte", "Runden", "Runden")
        )

        for ((lang, expectedNames) in expectedByLocale) {
            RuntimeEnvironment.setQualifiers("+$lang")
            val activeContext = ApplicationProvider.getApplicationContext<Context>()
            TrackingViewsRepository.resetForTesting(null)
            val views = TrackingViewsRepository.getInstance(activeContext)
                .getTrackingViewsFlow(ActivityType.BIKE_POWER)
                .first()
            assertEquals("Locale '$lang' must localize unmodified defaults & preserve custom title", expectedNames, views.map { it.name })

            // Single-row lookup symmetry
            val singleMapTab = TrackingViewsRepository.getInstance(activeContext)
                .getTrackingViewInfoFlow(initialGermanViews[2].tabViewId)
                .first()
            assertEquals(expectedNames[2], singleMapTab?.name)
        }
    }

    @Test
    fun composeUi_relocalizesRetainedGermanTabViewInfoAcrossLocaleSwitchesAndPreservesCustom_tstTrk097_3() {
        var activeLanguageTag by mutableStateOf("de")

        // Simulate retained TrackingTabsViewModel emitting stale German SQLite names ("Standard", "Karte", "Runden")
        // plus a user-renamed custom tab ("Intervalle").
        val legacyDefaultTab = TrackingViewInfo(
            tabViewId = 1L,
            name = "Standard",
            showMap = false,
            showLapButton = false,
            showLiveSegments = false,
            showElevationProfile = false
        )
        val legacyMapTab = TrackingViewInfo(
            tabViewId = 2L,
            name = "Karte",
            showMap = true,
            showLapButton = false,
            showLiveSegments = false,
            showElevationProfile = true
        )
        val legacyLapsTab = TrackingViewInfo(
            tabViewId = 3L,
            name = "Runden",
            showMap = false,
            showLapButton = true,
            showLiveSegments = false,
            showElevationProfile = false
        )
        val customTab = TrackingViewInfo(
            tabViewId = 4L,
            name = "Intervalle",
            showMap = false,
            showLapButton = true,
            showLiveSegments = false,
            showElevationProfile = false
        )

        composeTestRule.setContent {
            val localizedContext = createLocalizedContext(activeLanguageTag)
            val localizedConfig = localizedContext.resources.configuration
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfig
            ) {
                ATrainingTrackerTheme {
                    Column {
                        Text(text = resolveTrackingTabTitle(legacyDefaultTab.name))
                        Text(text = resolveTrackingTabTitle(legacyLapsTab.name))
                        Text(text = resolveTrackingTabTitle(customTab.name))
                        TrackingTabPreviewHeader(
                            viewInfo = legacyMapTab,
                            onToggleMode = {},
                            onExitConfig = {}
                        )
                        TrackingTabConfigHeader(
                            viewInfo = legacyDefaultTab,
                            tabIndex = 0,
                            tabCount = 4,
                            onUpdateTabName = { _, _ -> },
                            onAddTabRelative = { _, _ -> },
                            onDeleteTab = {},
                            onCopyTab = {},
                            onToggleMode = {}
                        )
                    }
                }
            }
        }

        // German baseline
        composeTestRule.onAllNodesWithText("Standard").assertCountEquals(2)
        composeTestRule.onNode(hasSetTextAction() and hasText("Standard")).assertIsDisplayed()
        composeTestRule.onNodeWithText("Karte").assertIsDisplayed()
        composeTestRule.onNodeWithText("Runden").assertIsDisplayed()
        composeTestRule.onNodeWithText("Intervalle").assertIsDisplayed()

        // Switch to Spanish (reproduces 2026-11-21_sweep.md es case)
        composeTestRule.runOnIdle { activeLanguageTag = "es" }
        composeTestRule.onAllNodesWithText("predeterminado").assertCountEquals(2)
        composeTestRule.onNode(hasSetTextAction() and hasText("predeterminado")).assertIsDisplayed()
        composeTestRule.onNodeWithText("Mapa").assertIsDisplayed()
        composeTestRule.onNodeWithText("Vueltas").assertIsDisplayed()
        composeTestRule.onNodeWithText("Intervalle").assertIsDisplayed()

        // Switch to Japanese (reproduces 2026-11-21_sweep.md ja case)
        composeTestRule.runOnIdle { activeLanguageTag = "ja" }
        composeTestRule.onAllNodesWithText("デフォルト").assertCountEquals(2)
        composeTestRule.onNode(hasSetTextAction() and hasText("デフォルト")).assertIsDisplayed()
        composeTestRule.onNodeWithText("マップ").assertIsDisplayed()
        composeTestRule.onNodeWithText("ラップ").assertIsDisplayed()
        composeTestRule.onNodeWithText("Intervalle").assertIsDisplayed()

        // Switch to Portuguese (reproduces 2026-11-21_sweep.md pt case)
        composeTestRule.runOnIdle { activeLanguageTag = "pt" }
        composeTestRule.onAllNodesWithText("padrão").assertCountEquals(2)
        composeTestRule.onNode(hasSetTextAction() and hasText("padrão")).assertIsDisplayed()
        composeTestRule.onNodeWithText("Mapa").assertIsDisplayed()
        composeTestRule.onNodeWithText("Voltas").assertIsDisplayed()
        composeTestRule.onNodeWithText("Intervalle").assertIsDisplayed()
    }
}
