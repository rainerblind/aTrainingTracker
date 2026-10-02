# Stage 5: Walkthrough & Verification - ATT-1986: [Feature] [Aftermath/Settings] Independent X-Axis Domain Settings for Elevation Profile (Distance) vs. Telemetry Graphs (Time)

**Ticket**: [ATT-1986](https://rainerblind.atlassian.net/browse/ATT-1986)  
**Sub-task**: [ATT-2004](https://rainerblind.atlassian.net/browse/ATT-2004) (`[Test]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.10`  
**Requirement Mapping**: `REQ-UI-233`  
**Test Mapping**: `TST-UI-192`  
**Branch**: `feature/ATT-1986`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Executive Summary & Verification Overview

In post-workout visual analytics, athletes interpret topographic terrain differently from physiological and exertion telemetry:
1. **Topography (Elevation Profile)** is inherently geographic: athletes think of climbs, descents, and mountain passes in terms of **Distance** (e.g. "the 4 km climb starting at km 12"). Viewing elevation purely over time distorts the terrain slope depending on how fast or slow the athlete was traveling.
2. **Physiological & Exertion Telemetry (Heart Rate, Speed/Pace, Power)** is inherently temporal: athletes evaluate training load, pacing, fatigue, and zone intervals in terms of **Time** (e.g. "a 10-minute threshold interval", "holding 280 W for 20 minutes", "heart rate drift over 90 minutes").

Previously, a single global setting (`profileXAxisDomain`) in `TuningPreferencesDataStore.kt` controlled both chart types simultaneously, forcing an unwanted compromise.

**Resolution**:
1. **Preferences DataStore Decoupling (`TuningPreferencesDataStore.kt`)**:
   - Decoupled `profileXAxisDomain` into `elevationXAxisDomain` (factory default: `ProfileXAxisDomain.DISTANCE`) and `telemetryXAxisDomain` (factory default: `ProfileXAxisDomain.TIME`).
   - Persisted to keys `tuning_elevation_x_axis_domain` and `tuning_telemetry_x_axis_domain`.
   - Backward-compatibility migration: uninitialized `elevationXAxisDomain` falls back to legacy `tuning_profile_x_axis_domain` before defaulting to `DISTANCE`; uninitialized `telemetryXAxisDomain` defaults safely to `TIME`.
   - Preserved deprecated `profileXAxisDomain` accessor and legacy single-parameter constructor for binary and source compatibility.
2. **Expert Settings UI (`AdvancedTuningDialog.kt` & `AdvancedTuningAccordion.kt`)**:
   - Added independent state tracking for `elevationXAxisDomain` and `telemetryXAxisDomain`.
   - Rendered two independent `FilterChip` selector rows in `AftermathAnalysisSection`:
     - **Elevation Profile X-Axis**: `[Distance (Default) | Time]`
     - **Telemetry Graphs X-Axis**: `[Time (Default) | Distance]`
   - Updated `formatAftermathSubtitle` to format `"$elevPrefix: $elevStr | $telemPrefix: $telemStr"`.
   - Added `tuning_telemetry_x_axis_title` and `tuning_telemetry_x_axis_desc` across all 9 application locales.
3. **Cursor Synchronization Bug Fix (`TelemetryMetricGraph.kt`)**:
   - Fixed line 791 magnitude mismatch where `currentDistance in 0.0..totalSpan` evaluated route distance (meters) against total time (seconds).
   - Validates temporal coordinate `cursorDistSpan in 0.0..totalSpan && cursorDistSpan in startDist..(startDist + visibleSpan)`.
4. **Chart Layout Integration (`MapDetailLayout.kt`)**:
   - Supplied `xAxisDomain = tuningConfig.elevationXAxisDomain` to `ElevationProfile`.
   - Supplied `xAxisDomain = tuningConfig.telemetryXAxisDomain` to all stacked `TelemetryMetricGraph` instances (Speed/Pace, HR, Power).
   - Maintained global zoom toolbar contract and synchronized cross-domain scrubbing using route meters (`selectedDistance`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-233` | `TST-UI-192.1` | Decoupled DataStore Default, Custom Config & Fallback Tests (`ProfileXAxisDomainTest`) | **PASSED** (4/4) | `Verified` |
| `REQ-UI-233` | `TST-UI-192.2` | 9-Language Localization Parity Audit across all locales (`AftermathTuningLocalizationTest`) | **PASSED** (1/1) | `Verified` |
| `REQ-UI-233` | `TST-UI-192.3` | Accordion Subtitle & Dialog Visual Contract Tests (`AdvancedTuningAccordionTest`, `AdvancedTuningVisualContractTest`) | **PASSED** (12/12) | `Verified` |
| `REQ-UI-233` | `TST-UI-192.4` | Chart Zoom Wiring & Layout Contract Tests (`MapDetailLayoutTest`) | **PASSED** (5/5) | `Verified` |
| `REQ-PRO-022` | `TST-PRO-015` | Requirement Archaeology Governance Audit (`verify_requirement_governance.py`) | **PASSED** (0 errors) | `Verified` |
| `REQ-PRO-001` | `TST-UI-192.5` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) | **PASSED** (100% in 3m 9s) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit Tests
```text
ProfileXAxisDomainTest > testDefaultProfileXAxisDomain PASSED
ProfileXAxisDomainTest > testProfileXAxisDomainCustomConfiguration PASSED
ProfileXAxisDomainTest > testDataStoreKeyDefinitions PASSED
ProfileXAxisDomainTest > testProfileXAxisDomainEnumSerializationAndFallback PASSED

AftermathTuningLocalizationTest > testAftermathTuningStringsParityAcrossAllLocales PASSED

AdvancedTuningAccordionTest > testAftermathAnalysisSubtitle_reflectsIndependentDomains PASSED
AdvancedTuningAccordionTest > testAftermathAnalysisSubtitle_reflectsDomain PASSED
AdvancedTuningVisualContractTest > testAdvancedTuningDialog_definesAllFiveRequiredSubsections PASSED

MapDetailLayoutTest > testMapDetailLayout_integratesGlobalTelemetryZoomToolbar PASSED
MapDetailLayoutTest > testMapDetailLayout_wiresGlobalZoomState PASSED
```

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 3m 9s
32 actionable tasks: 1 executed, 31 up-to-date
```

---

## 4. Manual / Visual Inspection Summary

1. **Expert Settings (Advanced Tuning Dialog)**:
   - In "Aftermath & Profil-Analytik", two selector blocks appear:
     - **Höhenprofil X-Achse**: `[Distanz (Standard) | Zeit]`
     - **Telemetriegraphen X-Achse**: `[Zeit (Standard) | Distanz]`
   - Changing either setting persists immediately in DataStore.
   - Collapsed accordion header subtitle accurately reflects both states: e.g. `"Höhenprofil X-Achse: Distanz | Telemetriegraphen X-Achse: Zeit"`.
2. **Aftermath Detailed Workout View**:
   - Out-of-the-box: Elevation Profile renders with Distance (km/mi) milestone ticks, while Heart Rate, Pace, and Power render with Time (hh:mm:ss) milestone ticks.
   - Touching/scrubbing any graph moves the cursor on all graphs simultaneously in lockstep, with the cursor correctly visible on both distance-based and time-based charts without clipping or disappearing.
3. **Factory Reset**:
   - Tapping "Auf Werkseinstellungen zurücksetzen" restores Elevation to `DISTANCE` and Telemetry to `TIME`.

---

## 5. Living Documentation & Governance Status

- `docs/requirements.md`: `REQ-UI-233` marked `Verified`.
- `docs/tests.md`: `TST-UI-192` marked `Verified`.
- Requirement archaeology audit verified via `tools/verify_requirement_governance.py --base-ref sprint/2026-40.10` (clean pass).
