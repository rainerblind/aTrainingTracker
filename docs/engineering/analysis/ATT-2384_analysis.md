# Stage 1 Analysis: ATT-2384 - Define and Harmonize Default Section Visibility and Order for Workout Cards and Details

**Ticket**: [ATT-2384](https://rainerblind.atlassian.net/browse/ATT-2384)  
**Sub-task**: [ATT-2611](https://rainerblind.atlassian.net/browse/ATT-2611) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2384`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Statement & Motivation

In the advanced tuning settings (*Trainingsliste & Details*, `WorkoutMasksAndCardsSection.kt`), athletes can customize section visibility and vertical sequencing for both the workout summary cards in the journal list (`WorkoutSummary.kt`) and the full-screen workout details view (`TrackOnMapScreen.kt`).

However, several architectural inconsistencies and non-harmonized defaults currently exist:
1. **Unintegrated Export Status**: The Export Status section (`ExportStatus.kt`) is hardcoded as a fixed footer after the dynamic section loop in `WorkoutSummary.kt` and is completely absent from `TrackOnMapScreen.kt`. It is not represented in `WorkoutSectionType`, meaning users cannot reposition or toggle it.
2. **Suboptimal Legacy Default Sequence**: The current `DEFAULT_ORDER` (`DESCRIPTION -> EXTREMA -> LAPS -> STRAVA -> MAP -> ELEVATION -> CHARTS -> ZONES`) places Laps and Strava ahead of the map and telemetry analytics, separating the core spatial and visual telemetry charts from primary extrema.
3. **Non-Harmonized Default Visibility**:
   - In workout list cards, `LAPS` defaults to `true` (creating vertically bloated cards that consume excessive screen real estate in the journal), while `ZONES` defaults to `false` (depriving athletes of immediate Z1–Z5 intensity visualization).
   - In details, section visibility and ordering lack total structural alignment with the list cards.

Ticket `ATT-2384` establishes a production-grade 9-section architecture with an ergonomically tuned default sequence and unified default visibility matrices for clean installations and resets.

---

## 2. Root Cause Analysis (Forensic Investigation & Architectural Gap Analysis)

### 2.1 Domain Model: `WorkoutSectionType.kt`
Currently, `WorkoutSectionType` contains 8 enum values:
```kotlin
enum class WorkoutSectionType(@StringRes val titleRes: Int) {
    DESCRIPTION, EXTREMA, LAPS, STRAVA, MAP, ELEVATION, CHARTS, ZONES
}
```
* **Gap**: `EXPORT_STATUS(R.string.export_status)` is missing. String resource `R.string.export_status` already exists in all 9 application locales.
* **Default Order**: Currently `[DESCRIPTION, EXTREMA, LAPS, STRAVA, MAP, ELEVATION, CHARTS, ZONES]`.
* **Required New Order (`DEFAULT_ORDER`)**:
  1. `DESCRIPTION` (Notes / title at the top — 0 dp if empty)
  2. `EXTREMA` (Core metrics: distance, duration, avg/max speed, HR, power, cadence, elevation, calories)
  3. `MAP` (GPS route map preview / interactive map)
  4. `ELEVATION` (Elevation profile directly below map)
  5. `CHARTS` (Telemetry charts directly below elevation with synchronized touch scrubber cursor)
  6. `ZONES` (Heart rate & power zones Z1–Z5)
  7. `LAPS` (Laps breakdown & split table)
  8. `EXPORT_STATUS` (Local file, Dropbox, Google Drive export status indicators)
  9. `STRAVA` (Strava activity link & sync status badge)

### 2.2 Preference Models & Defaults: `MyPreferenceManager.kt`
* **`WorkoutCardSectionPreferences`**:
  * Currently defaults to `showLaps = true`, `showZoneAnalysis = false`.
  * Missing `showExportStatus: Boolean = true`.
  * **Required Defaults for List Cards**:
    - `showDescription`: `true`
    - `showExtrema`: `true`
    - `showMapPreview`: `true`
    - `showElevationProfile`: `false` (avoids list clutter)
    - `showTelemetryCharts`: `false` (performance preservation in list)
    - `showZoneAnalysis`: `true` (instant intensity recognition)
    - `showLaps`: `false` (prevents card bloat; details screen is for deep lap inspection)
    - `showExportStatus`: `true` (0 dp when no export pending/complete)
    - `showStrava`: `true`
* **`WorkoutDetailPreferences`**:
  * Missing `showExportStatus: Boolean = true`.
  * **Required Defaults for Details**: All 9 sections default to `true`.
* **DataStore Keys**:
  * Introduce `WORKOUT_CARD_SHOW_EXPORT_STATUS = booleanPreferencesKey("workout_card_show_export_status")`.
  * Introduce `WORKOUT_DETAIL_SHOW_EXPORT_STATUS = booleanPreferencesKey("workout_detail_show_export_status")`.

### 2.3 Settings UI: `WorkoutMasksAndCardsSection.kt`
* The matrix table currently maps 8 enum entries.
* It must add a `MatrixFeatureRow` for `WorkoutSectionType.EXPORT_STATUS` with 48dp touch targets, localized title `R.string.export_status`, and alternating background shading.

### 2.4 Presentation Layer: `WorkoutSummary.kt` & `TrackOnMapScreen.kt`
* **`WorkoutSummary.kt`**:
  * Handle `WorkoutSectionType.EXPORT_STATUS` inside the `workoutSectionsOrder.forEach` loop, checking `preferences.showExportStatus`.
  * Remove the hardcoded trailing invocation `ExportStatus(exportStatuses = workoutData.exportStatuses)` at line 416.
* **`TrackOnMapScreen.kt`**:
  * Handle `WorkoutSectionType.EXPORT_STATUS` in the post-map analytics content section (before `STRAVA`), checking `activeDetailPrefs.showExportStatus`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Add `EXPORT_STATUS` to `WorkoutSectionType` and update `DEFAULT_ORDER` to the 9-section sequence.
  * Update `WorkoutCardSectionPreferences` and `WorkoutDetailPreferences` with `showExportStatus` and harmonized default visibility.
  * Update DataStore preference reading/writing in `MyPreferenceManager.kt`.
  * Add `EXPORT_STATUS` row to `WorkoutMasksAndCardsSection.kt`.
  * Dynamically slot `ExportStatus` in `WorkoutSummary.kt` inside the section order loop.
  * Add `ExportStatus` rendering in `TrackOnMapScreen.kt`.
  * Update unit and contract tests (`WorkoutSectionOrderPersistenceTest.kt`, `WorkoutSectionReorderContractTest.kt`, etc.).
  * Clean-room full regression test suite pass.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modifications to Google Drive, Dropbox, or Strava upload protocols or background work managers.
  * No alterations to GPS tracking, sensor data capture, or telemetry database schemas.
  * No redesign of `ExportStatusGroupData` or export status badge components.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

This ticket refines and amends existing requirement `REQ-UI-255` (*Aftermath: User-Customizable Section Reordering, Unified Order Persistence & Modular Section Architecture for Workout Summaries and Details*):

1. **Original Requirement ID & Target**:
   - `REQ-UI-255` (and interfaces with `REQ-UI-229` / `REQ-UI-240`).
2. **Historical Origin & Commit Trace**:
   - Implemented in Sprint 2026-40.13 (`ATT-2176`), refined in `ATT-2383` (Sprint 2026-41.1).
3. **Root Reason for Existing Formulation**:
   - Previous formulation introduced user-customizable reordering for the original 8 aftermath sections, but treated export status as a static post-loop element because export synchronization was initially external to card reordering.
   - List card default visibility enabled laps by default before lap visualization modes and dense split tables were expanded.
4. **Preservation of Core Invariants**:
   - Self-healing deserialization in `WorkoutSectionType.fromSerializedString`: missing enum values are appended dynamically, preserving custom orders from previous versions.
   - Drag/reorder controls in `WorkoutMasksAndCardsSection.kt` and `AdvancedTuningDialog.kt` remain fully functional.
   - 0 dp empty state handling for notes/description and export status remains preserved.
   - 100% full-suite clean-room test pass rate is strictly maintained.

---

## 5. Architectural Deliverable & Governance Gate Check

- **Requirement ID to formulate**: `REQ-UI-285` (*Harmonized 9-Section Architecture and Default Visibility Matrix for Workout Summaries and Details*).
- **Test ID to formulate**: `TST-UI-245` (*Workout Section 9-Item Default Sequence and Visibility Matrix Verification*).
- **Risk Assessment**: Very low. `fromSerializedString` already implements self-healing list repair by appending missing enums. Existing user preferences will cleanly absorb `EXPORT_STATUS` without migration crashes or preference corruption.
