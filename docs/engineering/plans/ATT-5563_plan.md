# Stage 3: Implementation Plan - ATT-5563: Steigungsverteilung

**Ticket**: [ATT-5563](https://rainerblind.atlassian.net/browse/ATT-5563)  
**Sub-task**: [ATT-5585](https://rainerblind.atlassian.net/browse/ATT-5585) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5563`  
**Requirement Mapping**: `REQ-UI-453` | **Test Mapping**: `TST-UI-453`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
Sprint Review of `ATT-5487` established three refinements: (1) independent visibility switches in Expert Settings (default: `true` in Details, `false` in Summary); (2) replace "Zonen" / "Zones" with *Steigungsverteilung* / *Slope Distribution* across all 9 locales; (3) display threshold labels `<2%`..`≥25%` in Column Chart and Stacked Bar, while preserving `Z1`..`Z7` for HR/Power.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-453` (*Grade / Slope Distribution Card & 1% Telemetry Histogram*)
  * `REQ-UI-453.1` (Threshold Labels `<2%`..`≥25%` in Column Chart and Stacked Bar)
  * `REQ-UI-453.3` (Nomenclature: *Steigungsverteilung* / *Slope Distribution*)
  * `REQ-UI-453.4` (Independent Visibility Toggles: Details default `true`, Summary default `false`; Zonen Tab availability guard)
  * `REQ-UI-453.5` (9-Language Localization Parity without inline emojis)
* **Test Mapping**: `TST-UI-453` (`TST-UI-453.1` .. `TST-UI-453.6`)

---

## 3. System Invariants & Preserved Behavior
1. **Strict File Size Constraint**: `WorkoutMasksAndCardsSection.kt` must remain strictly < 400 lines (`TST-UI-377.5`, currently 383 lines).
2. **Physiological Label Preservation**: Heart Rate (`Z1`..`Z5`) and Power (`Z1`..`Z7`) zone badges remain strictly intact.
3. **Color & Threshold Parity**: Retains canonical 7-zone colors (`TTColor.Zone1..Zone7`) matching `ElevationProfile.kt`.
4. **Database & Schema Invariance**: Zero SQLite table alterations; preferences stored purely in AndroidX DataStore.
5. **Clean Migration**: Default values (`true` for details, `false` for list) ensure seamless backward-compatible upgrade.

---

## 4. Proposed Architectural Changes
* **Preferences Layer (`MyPreferenceManager.kt`)**: Add `showGradeDistribution` to `WorkoutCardSectionPreferences` (default: `false`) and `WorkoutDetailPreferences` (default: `true`); persist via DataStore keys.
* **Settings UI (`WorkoutMasksAndCardsSection.kt`)**: Render compact `SubordinateTuningToggleItem` under `showZoneAnalysis` in summary and details blocks.
* **Component Label Abstraction**: Add `customBadgeLabel: String? = null` to `ZoneTimeEntry` (`val badgeLabel: String get() = customBadgeLabel ?: "Z$zoneIndex"`). Pass thresholds in `ZoneDistributionCalculator.calculateGradeDistribution`; render `entry.badgeLabel` in `ZoneDistributionColumnChart` and `ZoneDistributionStackedBar`.
* **Call Sites & Tab Availability**: Guard card with `showGradeDistribution` in `WorkoutSummary.kt`, `WorkoutDetailTabbedContent.kt`, and `TrackOnMapScreen.kt` (`hasZonesData`).
* **UI Consistency**: Reuses `SubordinateTuningToggleItem` with `MaterialTheme.typography.bodySmall`, onSurfaceVariant, and switch scale `0.6f`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Preference Schema & Persistence**:
   - Update `WorkoutCardSectionPreferences`, `WorkoutDetailPreferences`, and `MyPreferenceManager.kt`.
   - Update `WorkoutDetailPreferencesTest.kt`.
2. **Step 2: Settings UI in `WorkoutMasksAndCardsSection.kt`**:
   - Add `SubordinateTuningToggleItem` in summary and details blocks; verify file line count < 400 lines (`WorkoutMasksAndCardsLayoutTest`).
3. **Step 3: Call Site Guarding & Tab Availability**:
   - Guard rendering in `WorkoutSummary.kt`, `WorkoutDetailTabbedContent.kt`, and `TrackOnMapScreen.kt`.
4. **Step 4: Badge Label Decoupling & Threshold Strings**:
   - Add `customBadgeLabel` to `ZoneTimeEntry`; pass threshold labels in `ZoneDistributionCalculator.calculateGradeDistribution`.
   - Use `entry.badgeLabel` in `ZoneDistributionColumnChart.kt` and `ZoneDistributionStackedBar.kt`.
   - Update `GradeZoneDistributionCardTest.kt`.
5. **Step 5: 9-Language Localization**:
   - Update `aftermath_grade_zones_title` and add `settings_workout_card_grade_distribution` across all 9 `strings.xml`.
   - Verify with `TranslationParityTest`.
6. **Step 6: Targeted Verification**:
   - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*" --tests "com.atrainingtracker.trainingtracker.preferences.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted package tests, `TranslationParityTest`, `WorkoutMasksAndCardsLayoutTest`, and `verify_requirement_governance.py`.
* **Rollback**: Branch isolation (`feature/ATT-5563`) allows full clean revert without impacting `develop` or sprint branch.
