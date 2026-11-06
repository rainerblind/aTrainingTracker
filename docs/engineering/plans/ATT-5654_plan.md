# Stage 3: Implementation Plan - ATT-5654: Steigungsverteilung als sortierbare Bereichslisten-Einträge

**Ticket**: [ATT-5654](https://rainerblind.atlassian.net/browse/ATT-5654)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (Laps) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5654`  
**Requirement Mapping**: `REQ-UI-453` (`REQ-UI-453.4`), `REQ-UI-255`, `REQ-UI-408` (`REQ-UI-408.1`, `REQ-UI-408.2`)  
**Test Mapping**: `TST-UI-453` (`TST-UI-453.4`), `TST-UI-371` (`TST-UI-371.1`, `TST-UI-371.2`), `TST-UI-377` (`TST-UI-377.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
In ATT-5563, Steigungsverteilung (`GradeZoneDistributionCard`) was added as a subordinate switch under *Zonenkarten-Anzeigemodus* in `WorkoutMasksAndCardsSection.kt`. This creates architectural coupling between terrain metrics and physiological zones, prevents independent reordering in Summary Cards (`WorkoutSummary.kt`) and Detail Tabs (`WorkoutDetailTabsConfigSheet.kt`), and introduces competing configuration surfaces contrary to Design Guidelines Section 3.2. ATT-5654 establishes `GRADE_DISTRIBUTION` as a first-class sortable section in both views.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-UI-453.4` (Independent Reorderable Sections), `REQ-UI-255` (Customizable Section Ordering), `REQ-UI-408.1` (Detail Tab Sections), `REQ-UI-408.2` (Wire Schema Migration)
* **Tests**: `TST-UI-453.4` (Reorder & Decoupled Rendering), `TST-UI-371.1` (Default Tab Sections), `TST-UI-371.2` (Wire Migration), `TST-UI-377.5` (File Size < 400 Lines)

---

## 3. System Invariants & Preserved Behavior
1. **Single Authoritative Surface**: Reorderable matrix governs Summary Cards; Tab Config governs Workout Details (Design Guidelines Sec 3.2).
2. **Backward Compatibility**: Existing DataStore keys (`showGradeDistribution` in `WorkoutCardSectionPreferences` [default `false`] and `WorkoutDetailPreferences` [default `true`]) preserved without data loss.
3. **Upgrade Path Safety (Rule 66)**: Persisted tab configs lacking `GRADE_DISTRIBUTION` auto-migrate it into `tab_zones` directly following `ZONES`.
4. **File Size Constraint**: `WorkoutMasksAndCardsSection.kt` line count remains strictly `< 400` lines.
5. **Localization Parity**: String `settings_workout_card_grade_distribution` verified across all 9 locales.

---

## 4. Proposed Architectural Changes
* **Component 1 (`WorkoutSectionType.kt`)**: Add `GRADE_DISTRIBUTION(R.string.settings_workout_card_grade_distribution)` to `WorkoutSectionType`; update `DEFAULT_ORDER` (index 7, after `ZONES`).
* **Component 2 (`WorkoutDetailTabConfig.kt`)**: Add `GRADE_DISTRIBUTION` to `WorkoutDetailSectionType` with bidirectional enum mapping (`toWorkoutDetailSectionType()` / `toWorkoutSectionTypeOrNull()`); add to `DEFAULT_TABS[2]` (`tab_zones`); add `hasGradeDistribution` to `WorkoutDetailSectionAvailability`; in `deserializeTabs`, auto-inject `GRADE_DISTRIBUTION` after `ZONES` in `tab_zones` when missing.
* **Component 3 (`WorkoutMasksAndCardsSection.kt`)**: Add `GRADE_DISTRIBUTION` to `buildMatrixFeatureRow` bound to `prefs.showGradeDistribution`; include in `activeCardCount`; delete both `SubordinateTuningToggleItem`s under `ZoneDisplayModeSelector` (lines 307–311, 369–373).
* **Component 4 (`WorkoutSummary.kt` & `WorkoutDetailTabbedContent.kt`)**: Render `GradeZoneDistributionCard` exclusively at `WorkoutSectionType.GRADE_DISTRIBUTION` / `WorkoutDetailSectionType.GRADE_DISTRIBUTION`, removing it from `ZONES`.
* **Component 5 (`TrackOnMapScreen.kt`)**: Wire `hasGradeDistribution` in `fromWorkoutState` and add `WorkoutSectionType.GRADE_DISTRIBUTION` handling to fallback loops.
* **UI Consistency (Rule 23)**: Reuses `MatrixFeatureRow`, `WorkoutDetailTabsConfigSheet`, and existing typography/color tokens; no new one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Enums & Bidirectional Mapping (`REQ-UI-255`, `REQ-UI-408.1`)**: Add `GRADE_DISTRIBUTION` to `WorkoutSectionType.kt` and `WorkoutDetailSectionType.kt`; update `DEFAULT_ORDER` and `DEFAULT_TABS[2]`.
2. **Tab Availability & Migration (`REQ-UI-408.2`, `TST-UI-453.4b`)**: Add `hasGradeDistribution` in `WorkoutDetailTabConfig.kt`; implement auto-migration after `ZONES` in `deserializeTabs`.
3. **Settings Matrix & Subordinate Removal (`REQ-UI-453.4`, `TST-UI-453.4c`)**: Wire `GRADE_DISTRIBUTION` in `buildMatrixFeatureRow` in `WorkoutMasksAndCardsSection.kt`; remove subordinate switches; verify `< 400` lines.
4. **Decoupled Card Rendering (`REQ-UI-453.4`, `TST-UI-453.4d`)**: Decouple `GradeZoneDistributionCard` from `ZONES` in `WorkoutSummary.kt`, `WorkoutDetailTabbedContent.kt`, and `TrackOnMapScreen.kt`.
5. **Contract & Unit Tests**: Update `WorkoutSectionOrderPersistenceTest`, `WorkoutDetailTabsTest`, `WorkoutSummaryDynamicOrderContractTest`, `TrackOnMapScreenSectionSlottingContractTest`, `WorkoutSectionReorderContractTest`, and `WorkoutMasksAndCardsLayoutTest`.
6. **Targeted Verification**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*" --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"` and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit and contract tests, governance verification exit code 0.
* **Rollback**: Clean `git restore` on `feature/ATT-5654`.
