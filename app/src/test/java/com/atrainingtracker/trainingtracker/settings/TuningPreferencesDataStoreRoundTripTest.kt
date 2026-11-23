/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.settings

import android.content.Context
import androidx.datastore.preferences.core.preferencesOf
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.trainingtracker.climbs.ClimbScoringFormula
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldVariant
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontFamily
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontWeight
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Round-trip persistence, [BoundedPref], dependent clamp, and legacy fallback/mirror
 * characterization tests for [TuningPreferencesDataStore] (REQ-SET-121 / TST-SET-123).
 */
// Linked test specs: TST-SET-123, TST-SET-123.1, TST-SET-123.2, TST-SET-123.3
@OptIn(ExperimentalCoroutinesApi::class)
class TuningPreferencesDataStoreRoundTripTest {

    private fun TestScope.createStore(
        backing: InMemoryPreferencesDataStore = InMemoryPreferencesDataStore()
    ): Pair<TuningPreferencesDataStore, InMemoryPreferencesDataStore> {
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        val store = TuningPreferencesDataStore.createForTesting(
            context = mockContext,
            testScope = backgroundScope,
            dataStoreOverride = backing
        )
        return store to backing
    }

    /**
     * TST-SET-123.1: Verifies that saveTuningConfig persists all non-derived, non-hardcoded fields
     * of TuningConfig (including the 4 Auto-Pause keys) and tuningConfigFlow emits an equal snapshot
     * verified across every backing field via reflection.
     */
    @Test
    fun saveTuningConfig_roundTripsAllPersistedFieldsIncluding4AutoPauseKeys_TST_SET_123_1() =
        runTest(UnconfinedTestDispatcher()) {
            val (store, _) = createStore()
            val defaults = TuningConfig()

            val custom = TuningConfig(
                elevationXAxisDomain = ProfileXAxisDomain.TIME,
                telemetryXAxisDomain = ProfileXAxisDomain.DISTANCE,
                cockpitFontFamily = CockpitFontFamily.MONOSPACE,
                cockpitFontWeight = CockpitFontWeight.BOLD,
                sensorFieldVariant = SensorFieldVariant.ELEVATED_CARDS,
                fullDimFactor = 0.15f,
                mediumDimFactor = 0.70f,
                slopeFlatThreshold = 3.5f,
                slopeSteepThreshold = 9.0f,
                wakeupDurationSec = 30,
                downwardDelaySec = 7,
                gpsAccuracyThresholdMeters = 120.0f,
                altitudeFilterWindowSec = 42,
                slopeMinSpeedMps = 1.2f,
                postWorkoutErtsSmoothingEnabled = true,
                rawSensorRetentionPolicy = RawSensorRetentionPolicy.DAYS_7,
                recordSmartphoneRawTelemetryEnabled = true,
                paceCeilingMinKm = 4.5f,
                showLiveClimbs = false,
                climbMinLengthMeters = 800.0f,
                climbMinGradientPercent = 4.5f,
                climbScoringFormula = ClimbScoringFormula.STRAVA_GARMIN,
                turnPromptsEnabled = false,
                turnAudioAlertsEnabled = false,
                turnCueCountdownDistanceMeters = 180.0f,
                offRouteCorridorThresholdMeters = 90.0f,
                sensorFieldCornerRadius = 12.0f,
                sensorFieldBorderThickness = 2.5f,
                sensorFieldBorderContrast = 0.65f,
                routeSelectionRadiusKm = 4.5f,
                navigationCueTransparency = 0.75f,
                navigationCueDismissDurationSec = 8,
                elevationSmoothingSigmaMeters = 33.0f,
                // mapFollowMe* are permanently hardcoded per REQ-MAP-050
                suppressAntMissingAdapterDialog = true,
                autoFollowUniqueRouteEnabled = false,
                clusterTolEndpoints = 320.0f,
                clusterTolApex = 250.0f,
                clusterTolDistance = 0.12f,
                clusterTolAltitudePos = 180.0f,
                clusterUseSportType = false,
                clusterUseAltitudePos = false,
                clusterScoreThreshold = 1.9f,
                autoPauseBikeEnabled = false,
                autoPauseRunEnabled = true,
                autoPauseOtherEnabled = true,
                startInAutoPauseEnabled = true,
                autoPauseBikeSpeedThreshold = 5,
                autoPauseBikeDebounceSec = 6,
                autoPauseBikeResumeDebounceSec = 7,
                autoPauseRunSpeedThreshold = 4,
                autoPauseRunDebounceSec = 9,
                autoPauseRunResumeDebounceSec = 11,
                autoPauseOtherSpeedThreshold = 3,
                autoPauseOtherDebounceSec = 19,
                autoPauseOtherResumeDebounceSec = 23,
                // Derived legacy Auto-Pause fields match Bike values
                autoPauseSpeedThresholdKmh = 5.0f,
                autoPauseDebounceSec = 6,
                enableCogganPowerMetrics = false,
                showCogganPowerInWorkoutSummary = false,
                enablePmcModel = true,
                enableAerobicEfficiencyModel = true,
                showAerobicEfficiencyInWorkoutSummary = false,
                enableCriticalPowerModel = true,
                showCriticalPowerInWorkoutSummary = false,
                enableCardioPowerDynamics = true,
                showCardioPowerInWorkoutSummary = false,
                enableMorningSleepAndWellness = true,
                enableSleepDurationAndQuality = false,
                enableMorningEnergy = false,
                enableMuscleSoreness = true,
                enableContextTags = true,
                showMorningCheckOnStartScreen = false,
                morningCheckCutoffHour = 10,
                dfaA1ThresholdT1 = 0.95f,
                dfaA1ThresholdT2 = 0.65f,
                hrvArtifactGateThresholdPercent = 4.0f,
                radarEdgeSide = RadarEdgeSide.LEFT,
                radarClearMode = RadarClearMode.AUTO_HIDE_3S,
                radarAudioAlertMode = RadarAudioAlertMode.FAST_ONLY,
                radarAlertVolume = 55,
                radarSpeakerBoost = false,
                radarAudioRoute = RadarAudioRoute.FORCE_SPEAKER,
                radarHapticAlert = false,
                radarLightDefaultMode = RadarLightMode.PELOTON,
                radarLightControlMode = RadarLightControlMode.MANUAL,
                radarBrakeLightEnabled = false,
                radarPelotonAudioSync = false,
                radarSmartUrbanMute = true,
                radarSpatialAudioEnabled = false,
                radarSpeedPitchModulation = false,
                radarTrafficOrientation = RadarTrafficOrientation.LEFT_HAND,
                radarLaneChangeTickEnabled = false,
                radarClosePassThresholdM = 2.0f,
                defaultPoolLengthMeters = 50.0f,
                defaultPoolLengthUnit = PoolLengthUnit.YARDS
            )

            // Guard against future TuningConfig additions silently falling back to defaults in tests
            val excludedFromDiffCheck = setOf(
                "mapFollowMeInitialZoom",
                "mapFollowMeSpeedZoomEnabled",
                "mapFollowMeCruisingZoom",
                "mapFollowMeTiltAngle",
                "mapFollowMeLookaheadPaddingPercent",
                "mapFollowMeSpeedSmoothingAlpha",
                "mapFollowMeBearingSmoothingAlpha",
                "autoPauseSpeedThresholdKmh",
                "autoPauseDebounceSec"
            )
            val fields = TuningConfig::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
            assertEquals("Expected all 102 TuningConfig backing fields to be inspected", 102, fields.size)
            for (field in fields) {
                field.isAccessible = true
                if (field.name !in excludedFromDiffCheck) {
                    assertNotEquals(
                        "Test fixture field '${field.name}' must differ from TuningConfig() default",
                        field.get(defaults),
                        field.get(custom)
                    )
                }
            }

            store.saveTuningConfig(custom)
            val reloaded = store.tuningConfigFlow.first()

            for (field in fields) {
                field.isAccessible = true
                assertEquals(
                    "Round-trip mismatch on TuningConfig.${field.name}",
                    field.get(custom),
                    field.get(reloaded)
                )
            }
            assertEquals(custom, reloaded)
        }

    /**
     * TST-SET-123.2: Verifies BoundedPref descriptors, dependent clamps, 0 -> DEFAULT rule,
     * DFA a1 threshold gap invariant, and legacy key fallbacks/mirrors preserve exact behavior.
     */
    @Test
    fun boundedPref_dependentClampsZeroRuleAndLegacyMirrors_preserveExactBehavior_TST_SET_123_2() =
        runTest(UnconfinedTestDispatcher()) {
            val backing = InMemoryPreferencesDataStore(
                preferencesOf(
                    TuningPreferencesDataStore.KEY_FULL_DIM_FACTOR to 0.45f,
                    TuningPreferencesDataStore.KEY_MEDIUM_DIM_FACTOR to 0.30f, // < fullDim -> clamps >= 0.45f
                    TuningPreferencesDataStore.KEY_SLOPE_FLAT to 4.5f,
                    TuningPreferencesDataStore.KEY_SLOPE_STEEP to 3.0f, // < slopeFlat -> clamps >= 4.5f
                    TuningPreferencesDataStore.KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC to 0, // 0 -> DEFAULT (4)
                    TuningPreferencesDataStore.KEY_RADAR_ALERT_VOLUME to 200, // > 100 -> 100
                    TuningPreferencesDataStore.KEY_PROFILE_X_AXIS_DOMAIN to ProfileXAxisDomain.TIME.name,
                    TuningPreferencesDataStore.KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH to 4.2f,
                    TuningPreferencesDataStore.KEY_AUTO_PAUSE_DEBOUNCE_SEC to 9
                )
            )
            val (store, _) = createStore(backing)

            val fromLegacyRead = store.tuningConfigFlow.first()
            assertEquals(0.45f, fromLegacyRead.fullDimFactor, 0.0001f)
            assertEquals(0.45f, fromLegacyRead.mediumDimFactor, 0.0001f)
            assertEquals(4.5f, fromLegacyRead.slopeFlatThreshold, 0.0001f)
            assertEquals(4.5f, fromLegacyRead.slopeSteepThreshold, 0.0001f)
            assertEquals(TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC, fromLegacyRead.navigationCueDismissDurationSec)
            assertEquals(100, fromLegacyRead.radarAlertVolume)
            assertEquals(ProfileXAxisDomain.TIME, fromLegacyRead.elevationXAxisDomain)
            // Legacy auto-pause fallback + debounce clamp (bike max = 20, run max = 20, other max = 60)
            assertEquals(4, fromLegacyRead.autoPauseBikeSpeedThreshold)
            assertEquals(9, fromLegacyRead.autoPauseBikeDebounceSec)

            // Write out-of-range + inverted dependent values through saveTuningConfig
            store.saveTuningConfig(
                TuningConfig(
                    fullDimFactor = 0.48f,
                    mediumDimFactor = 0.25f,
                    slopeFlatThreshold = 4.0f,
                    slopeSteepThreshold = 2.0f,
                    navigationCueDismissDurationSec = 0,
                    dfaA1ThresholdT1 = 0.60f,
                    dfaA1ThresholdT2 = 0.60f, // violates 0.05f gap -> T1 bumped to 0.65f
                    autoPauseBikeResumeDebounceSec = 99, // max 20
                    autoPauseRunResumeDebounceSec = 0, // min 1
                    autoPauseOtherResumeDebounceSec = 999 // max 60
                )
            )
            val saved = store.tuningConfigFlow.first()
            assertEquals(0.48f, saved.fullDimFactor, 0.0001f)
            assertEquals(0.48f, saved.mediumDimFactor, 0.0001f)
            assertEquals(4.0f, saved.slopeFlatThreshold, 0.0001f)
            assertEquals(4.0f, saved.slopeSteepThreshold, 0.0001f)
            assertEquals(TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC, saved.navigationCueDismissDurationSec)
            assertEquals(0.65f, saved.dfaA1ThresholdT1, 0.0001f)
            assertEquals(0.60f, saved.dfaA1ThresholdT2, 0.0001f)
            assertEquals(20, saved.autoPauseBikeResumeDebounceSec)
            assertEquals(1, saved.autoPauseRunResumeDebounceSec)
            assertEquals(60, saved.autoPauseOtherResumeDebounceSec)

            // Verify single-setter legacy mirrors & clamps
            store.updateAutoPauseBikeSpeedThreshold(5)
            store.updateAutoPauseBikeDebounceSec(12)
            store.updateNavigationCueDismissDurationSec(0)
            val prefsAfter = backing.data.first()
            assertEquals(5.0f, prefsAfter[TuningPreferencesDataStore.KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH] ?: -1f, 0.0001f)
            assertEquals(12, prefsAfter[TuningPreferencesDataStore.KEY_AUTO_PAUSE_DEBOUNCE_SEC])
            assertEquals(
                TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC,
                prefsAfter[TuningPreferencesDataStore.KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC]
            )
        }

    /**
     * TST-SET-123.3: Verifies BoundedPref utility contract directly and confirms TuningPreferencesDataStore
     * retains a non-public constructor.
     */
    @Test
    fun boundedPref_contractAndPrivateConstructor_verified_TST_SET_123_3() {
        val intPref = BoundedPref(TuningPreferencesDataStore.KEY_MORNING_CHECK_CUTOFF_HOUR, 8, 6, 11)
        assertEquals(6, intPref.clamp(2))
        assertEquals(11, intPref.clamp(20))
        assertEquals(9, intPref.clamp(9))
        assertEquals(8, intPref.read(preferencesOf()))
        assertEquals(11, intPref.read(preferencesOf(TuningPreferencesDataStore.KEY_MORNING_CHECK_CUTOFF_HOUR to 99)))

        val publicCtors = TuningPreferencesDataStore::class.java.declaredConstructors.filter {
            !it.isSynthetic && Modifier.isPublic(it.modifiers)
        }
        assertTrue("TuningPreferencesDataStore must keep non-public constructors", publicCtors.isEmpty())
    }
}
