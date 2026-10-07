# Stage 2: Requirement & Test Specification - ATT-2384: Define and Harmonize Default Section Visibility and Order for Workout Cards and Details

**Ticket**: [ATT-2384](https://rainerblind.atlassian.net/browse/ATT-2384)  
**Sub-task**: [ATT-2612](https://rainerblind.atlassian.net/browse/ATT-2612) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-285` (*Harmonized 9-Section Architecture and Default Visibility Matrix for Workout Summaries and Details*)  
**Test Spec ID**: `TST-UI-245`  
**Branch**: `feature/ATT-2384`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Requirement Specification (REQ-UI-285)

### 1.1 Problem Statement & Rationale
In the advanced tuning settings (*Trainingsliste & Details*, `WorkoutMasksAndCardsSection.kt`), athletes can customize section visibility and vertical sequence for summary cards (`WorkoutSummary.kt`) and detailed aftermath screens (`TrackOnMapScreen.kt`). Currently, the export status section is hardcoded at the bottom of summary cards and missing entirely from details, and the legacy default order and visibility settings clutter list cards with lap splits while omitting zone analytics. 

The system shall integrate `EXPORT_STATUS` as a fully modular, reorderable 9th section, establish an ergonomically optimized default sequence (`DEFAULT_ORDER`), and unify clean-installation default visibility matrices across list cards and detail views.

### 1.2 Functional & Architectural Requirements

1. **9-Section Domain Model & DEFAULT_ORDER (`WorkoutSectionType.kt`)**:
   - `WorkoutSectionType` SHALL include enum constant `EXPORT_STATUS(R.string.export_status)`.
   - `WorkoutSectionType.DEFAULT_ORDER` SHALL be defined in exact sequence:
     1. `DESCRIPTION` (Notes & custom titles — 0 dp if blank)
     2. `EXTREMA` (Core summary metrics)
     3. `MAP` (GPS route map preview / interactive map)
     4. `ELEVATION` (Elevation profile directly below map)
     5. `CHARTS` (Telemetry charts directly below elevation)
     6. `ZONES` (Heart rate & power training zones Z1–Z5)
     7. `LAPS` (Laps breakdown & split table)
     8. `EXPORT_STATUS` (File, Dropbox, Google Drive export badges)
     9. `STRAVA` (Strava activity link & sync status badge)
   - `fromSerializedString(serialized: String?)` SHALL parse all 9 section types and automatically append any missing enum values to guarantee self-healing backwards compatibility for existing installations.

2. **Harmonized Default Visibility Matrix (`MyPreferenceManager.kt`)**:
   - **`WorkoutCardSectionPreferences` (Journal List Cards)** SHALL default to:
     - `showDescription = true`
     - `showExtrema = true`
     - `showMapPreview = true`
     - `showElevationProfile = false` (prevents list card vertical bloat)
     - `showTelemetryCharts = false` (prevents list scrolling performance degradation)
     - `showZoneAnalysis = true` (immediate Z1–Z5 intensity visualization)
     - `showLaps = false` (splits reserved for deep detail analysis)
     - `showExportStatus = true` (0 dp when no export pending/complete)
     - `showStrava = true`
     - `lapDisplayMode = LapDisplayMode.VISUALIZER_ONLY`
   - **`WorkoutDetailPreferences` (Workout Details Screen)** SHALL default to `true` across all 9 sections (`showDescription`, `showExtrema`, `showMap`, `showElevationProfile`, `showTelemetryCharts`, `showZoneAnalysis`, `showLaps`, `showExportStatus`, `showStrava`).
   - `dataStore` keys `WORKOUT_CARD_SHOW_EXPORT_STATUS` and `WORKOUT_DETAIL_SHOW_EXPORT_STATUS` SHALL be persisted and default to `true`.

3. **Settings Matrix Feature Row (`WorkoutMasksAndCardsSection.kt`)**:
   - `WorkoutMasksAndCardsSection.kt` SHALL render a `MatrixFeatureRow` for `WorkoutSectionType.EXPORT_STATUS` with 48dp touch targets, localized title `R.string.export_status`, alternating row background, and independent List/Detail checkbox toggles.

4. **Modular Section Execution in Presentation Layer**:
   - **`WorkoutSummary.kt`**: `ExportStatus` SHALL be executed within `workoutSectionsOrder.forEach` when `type == WorkoutSectionType.EXPORT_STATUS && preferences.showExportStatus`. The fixed hardcoded call to `ExportStatus(...)` after the loop SHALL be removed.
   - **`TrackOnMapScreen.kt`**: `ExportStatus` SHALL be rendered when `type == WorkoutSectionType.EXPORT_STATUS && activeDetailPrefs.showExportStatus` in the post-map analytics content section (before `STRAVA`).

### 1.3 Acceptance Criteria (Given-When-Then)

* **AC-1 (Default Order Definition)**:
  * *Given* a clean installation or call to `WorkoutSectionType.DEFAULT_ORDER`,
  * *When* inspecting the sequence,
  * *Then* it SHALL contain exactly 9 sections in order: `DESCRIPTION`, `EXTREMA`, `MAP`, `ELEVATION`, `CHARTS`, `ZONES`, `LAPS`, `EXPORT_STATUS`, `STRAVA`.
* **AC-2 (Self-Healing Backward Compatibility)**:
  * *Given* a legacy serialized string without `EXPORT_STATUS` (e.g. `"DESCRIPTION,EXTREMA,MAP"`),
  * *When* parsed via `WorkoutSectionType.fromSerializedString`,
  * *Then* `EXPORT_STATUS` and all missing sections SHALL be appended at the end without throwing exceptions.
* **AC-3 (List Card Default Visibility)**:
  * *Given* initial `WorkoutCardSectionPreferences`,
  * *When* checking default values,
  * *Then* `showElevationProfile`, `showTelemetryCharts`, and `showLaps` SHALL be `false`, while `showDescription`, `showExtrema`, `showMapPreview`, `showZoneAnalysis`, `showExportStatus`, and `showStrava` SHALL be `true`.
* **AC-4 (Detail Screen Default Visibility)**:
  * *Given* initial `WorkoutDetailPreferences`,
  * *When* checking default values,
  * *Then* all 9 section toggles SHALL be `true`.
* **AC-5 (Dynamic Summary Card Slotting)**:
  * *Given* a custom order where `EXPORT_STATUS` is moved before `MAP`,
  * *When* rendering `WorkoutSummary`,
  * *Then* `ExportStatus` SHALL render before the map preview, and NOT as a fixed footer.
* **AC-6 (Dynamic Detail Screen Slotting)**:
  * *Given* `TrackOnMapScreen`,
  * *When* `activeDetailPrefs.showExportStatus` is `true`,
  * *Then* `ExportStatus` SHALL render dynamically in `analyticsContent` according to `sectionsOrder`.
* **AC-7 (Tuning Dialog Matrix Consistency)**:
  * *Given* `WorkoutMasksAndCardsSection`,
  * *When* rendered,
  * *Then* 9 feature rows SHALL appear with functioning List and Detail checkbox toggles and reorder buttons.

### 1.4 System Invariants & Chesterton's Fence Audit
* **Requirement Archaeology**: Refines and amends `REQ-UI-255` (*Customizable Workout Journal Card & Detail View Section Visibility and Reordering*).
* **Historical Origin**: Sprint 2026-40.13 (`ATT-2176`), refined in `ATT-2383`.
* **Root Reason for Existing Formulation**: `REQ-UI-255` introduced the initial 8-section reordering engine. Export status was previously treated as an auxiliary footer.
* **Preservation of Core Invariants**: 0 dp empty state handling for notes/description and inactive export statuses, self-healing deserialization, reorder controls in tuning dialog, and 100% full-suite test pass rate remain strictly preserved.

---

## 2. Test Specification (TST-UI-245)

### Test Case 1: `WorkoutSectionOrderPersistenceTest.kt` (`TST-UI-245.1`)
* **Scope**: Unit & Serialization Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/WorkoutSectionOrderPersistenceTest.kt`
* **Preconditions**: Headless JVM unit test.
* **Action**:
  1. Verify `DEFAULT_ORDER.size == 9` and matches the exact sequence: `DESCRIPTION`, `EXTREMA`, `MAP`, `ELEVATION`, `CHARTS`, `ZONES`, `LAPS`, `EXPORT_STATUS`, `STRAVA`.
  2. Verify `fromSerializedString` with legacy 8-section string appends `EXPORT_STATUS` at the end.
  3. Verify roundtrip serialization with all 9 sections.
* **Expected Result**: All assertions pass.

### Test Case 2: `WorkoutPreferencesDefaultMatrixTest.kt` (`TST-UI-245.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/WorkoutPreferencesDefaultMatrixTest.kt`
* **Action**:
  1. Verify `WorkoutCardSectionPreferences` default constructor:
     - `showElevationProfile == false`, `showTelemetryCharts == false`, `showLaps == false`.
     - `showDescription == true`, `showExtrema == true`, `showMapPreview == true`, `showZoneAnalysis == true`, `showExportStatus == true`, `showStrava == true`.
  2. Verify `WorkoutDetailPreferences` default constructor:
     - All 9 booleans are `true`.
* **Expected Result**: Defaults strictly match the harmonized visibility matrix.

### Test Case 3: `WorkoutMasksAndCardsSectionContractTest.kt` (`TST-UI-245.3`)
* **Scope**: Architectural & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/WorkoutSectionReorderContractTest.kt`
* **Action**:
  1. Verify `WorkoutMasksAndCardsSection.kt` references `WorkoutSectionType.EXPORT_STATUS` and `R.string.export_status`.
  2. Verify `WorkoutSummary.kt` handles `WorkoutSectionType.EXPORT_STATUS` inside the section order loop.
  3. Verify `TrackOnMapScreen.kt` handles `WorkoutSectionType.EXPORT_STATUS`.
* **Expected Result**: Architectural invariants confirmed.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-245.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate across the full suite with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-245.1` | Unit/Serialization | `WorkoutSectionOrderPersistenceTest` | `REQ-UI-285.1` | Specified |
| `TST-UI-245.2` | Unit | `WorkoutPreferencesDefaultMatrixTest` | `REQ-UI-285.2` | Specified |
| `TST-UI-245.3` | Contract | `WorkoutSectionReorderContractTest` | `REQ-UI-285.3`, `REQ-UI-285.4` | Specified |
| `TST-UI-245.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
