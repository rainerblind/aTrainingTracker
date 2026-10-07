# Stage 5: Walkthrough & Verification - ATT-2384: Define and Harmonize Default Section Visibility and Order for Workout Cards and Details

**Ticket**: [ATT-2384](https://rainerblind.atlassian.net/browse/ATT-2384)  
**Sub-task**: [ATT-2615](https://rainerblind.atlassian.net/browse/ATT-2615) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-285`  
**Test Mapping**: `TST-UI-245`  
**Branch**: `feature/ATT-2384`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Executive Summary & Verification Overview

Ticket `ATT-2384` defines and harmonizes the default section order and visibility matrix across workout summary cards in the journal list (`WorkoutSummary.kt`) and the full-screen aftermath detail view (`TrackOnMapScreen.kt`). 

Previously, the export status section was hardcoded at the bottom of summary cards as a non-reorderable footer and was missing entirely from the details view. In addition, list cards defaulted to showing dense lap splits while hiding heart rate and power training zones.

Under `ATT-2384`, `EXPORT_STATUS` is integrated as a first-class customizable section in `WorkoutSectionType`, a refined 9-section default sequence (`DEFAULT_ORDER`) is established, and ergonomically tuned default visibility matrices are applied for new installations and preference resets.

---

## 2. Key Architectural & Implementation Enhancements

### 1. 9-Section Domain Model & DEFAULT_ORDER (`WorkoutSectionType.kt`)
- Added `EXPORT_STATUS(R.string.export_status)` to `WorkoutSectionType`.
- Established the canonical 9-section `DEFAULT_ORDER`:
  1. `DESCRIPTION` (Notes & custom titles — 0 dp if empty)
  2. `EXTREMA` (Core performance metrics: distance, duration, speed/pace, power, cadence, HR, ascent, calories)
  3. `MAP` (GPS route map preview / interactive map)
  4. `ELEVATION` (Elevation profile directly beneath the map)
  5. `CHARTS` (Telemetry charts directly beneath elevation with synchronized touch cursor)
  6. `ZONES` (Heart rate & power training zones Z1–Z5)
  7. `LAPS` (Laps breakdown & split table)
  8. `EXPORT_STATUS` (Local file, Dropbox, Google Drive export badges)
  9. `STRAVA` (Strava activity link & sync status badge)
- Ensured self-healing backwards compatibility in `fromSerializedString(serialized: String?)`, automatically appending `EXPORT_STATUS` and any newly added enum values to saved user configurations without data loss.

### 2. Harmonized Default Visibility Matrix (`MyPreferenceManager.kt`)
- **`WorkoutCardSectionPreferences` (Journal List Cards)**:
  - `showDescription`: `true`
  - `showExtrema`: `true`
  - `showMapPreview`: `true`
  - `showElevationProfile`: `false` (prevents visual clutter in the list)
  - `showTelemetryCharts`: `false` (preserves 60/120fps list scrolling performance)
  - `showZoneAnalysis`: `true` (instant intensity recognition)
  - `showLaps`: `false` (dense split tables reserved for detail view)
  - `showExportStatus`: `true` (0 dp when no export pending/complete)
  - `showStrava`: `true`
  - `lapDisplayMode`: `LapDisplayMode.VISUALIZER_ONLY`
- **`WorkoutDetailPreferences` (Workout Details Screen)**:
  - All 9 sections default to `true` (`showDescription`, `showExtrema`, `showMap`, `showElevationProfile`, `showTelemetryCharts`, `showZoneAnalysis`, `showLaps`, `showExportStatus`, `showStrava`).
- Added persistent DataStore keys `WORKOUT_CARD_SHOW_EXPORT_STATUS` and `WORKOUT_DETAIL_SHOW_EXPORT_STATUS`.

### 3. Tuning UI Integration (`WorkoutMasksAndCardsSection.kt`)
- Added `MatrixFeatureRow` for `WorkoutSectionType.EXPORT_STATUS` with:
  - 48dp minimum touch targets for List and Detail checkboxes.
  - Alternating row background shading.
  - Reorder up/down arrow buttons.
  - Standardized localized label `@string/export_status`.

### 4. Presentation Layer Modularization (`WorkoutSummary.kt` & `TrackOnMapScreen.kt`)
- **`WorkoutSummary.kt`**:
  - `ExportStatus` executes within the `workoutSectionsOrder.forEach` loop when `type == WorkoutSectionType.EXPORT_STATUS && preferences.showExportStatus`.
  - Removed the hardcoded trailing invocation of `ExportStatus(...)` outside the loop.
- **`TrackOnMapScreen.kt`**:
  - `ExportStatus` executes conditionally in `metadataContent` (if moved before `MAP`) and dynamically in `analyticsContent` (when placed after `MAP` in `postMapSections`).

---

## 3. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-285.1` | `TST-UI-245.1` | 9-Section Domain Model & DEFAULT_ORDER (`WorkoutSectionOrderPersistenceTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-285.2` | `TST-UI-245.2` | Default Visibility Matrices for Cards & Details (`WorkoutPreferencesDefaultMatrixTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-285.3` | `TST-UI-245.3` | Settings Tuning Matrix Feature Row Contract (`WorkoutSectionReorderContractTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-285.4` | `TST-UI-245.3` | Dynamic Modular Slotting in Summary and Details (`WorkoutSummaryDynamicOrderContractTest.kt`, `WorkoutSectionReorderContractTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-245.4` | Full Clean-Room Regression Suite (`./gradlew testDebugUnitTest`) | **PASSED** | `Verified` |

---

## 4. Automated Test Evidence

### Targeted Unit & Contract Tests
```text
> Task :app:compileDebugUnitTestKotlin
> Task :app:testDebugUnitTest

WorkoutSectionOrderPersistenceTest > testDefaultWorkoutSectionsOrder PASSED
WorkoutSectionOrderPersistenceTest > testWorkoutDisplayContextValues PASSED
WorkoutSectionOrderPersistenceTest > testSerializationAndDeserializationRoundtrip PASSED
WorkoutSectionOrderPersistenceTest > testDeserializationFallback_nullOrBlank PASSED
WorkoutSectionOrderPersistenceTest > testDeserializationFallback_invalidCorruptedString PASSED
WorkoutSectionOrderPersistenceTest > testSelfHealing_missingSectionsAppended PASSED
WorkoutSectionOrderPersistenceTest > testSelfHealing_deduplication PASSED

WorkoutPreferencesDefaultMatrixTest > testWorkoutCardSectionPreferences_defaultVisibility PASSED
WorkoutPreferencesDefaultMatrixTest > testWorkoutDetailPreferences_defaultVisibility PASSED

WorkoutSectionReorderContractTest > testReorderingLogic_moveUpAndDown PASSED
WorkoutSectionReorderContractTest > testReorderingBoundaries_disabledEdgeCases PASSED
WorkoutSectionReorderContractTest > testAdvancedTuningDialog_structuralContract PASSED
WorkoutSectionReorderContractTest > testWorkoutSummaryAndTrackOnMap_exportStatusContract PASSED

WorkoutSummaryDynamicOrderContractTest > testWorkoutSummary_permanentTopIdentityAnchorContract PASSED
WorkoutSummaryDynamicOrderContractTest > testWorkoutSummary_dynamicOrderIterationContract PASSED
WorkoutSummaryDynamicOrderContractTest > testOrderResolution_customSequence PASSED

BUILD SUCCESSFUL in 37s
```

### Full Clean-Room Regression Suite Pass
```text
Total Tests Executed: 2005
Failures: 0
Skipped: 0
Build Duration: 8m 48s
BUILD SUCCESSFUL
```

---

## 5. UI Consistency & Architectural Alignment (Rule 23)

* **Reference Screen / Baseline**: The Workout Tuning Screen (`WorkoutMasksAndCardsSection.kt`) and Journal Cards (`WorkoutSummary.kt`):
  * **Matrix Consistency**: The new `EXPORT_STATUS` row utilizes identical layout styling: 48dp minimum touch targets for checkboxes, alternating background shading `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)`, `MaterialTheme.colorScheme.primary` move icon buttons, and typography matching all existing rows.
  * **Zero-Height Collapse Invariant**: Like `DESCRIPTION` (which occupies 0 dp when blank), `ExportStatus` checks `if (activeExports.isNotEmpty())` and collapses to 0 dp when no export operations are active, ensuring zero unnecessary vertical whitespace.
  * **Preservation of Core Invariants**: 100% full-suite clean-room regression test pass rate, self-healing backwards compatibility, and 9-language translation parity are strictly maintained.
