# Stage 3: Implementation Plan - ATT-6213: Reorderable WorkoutDetails

**Ticket**: [ATT-6213](https://rainerblind.atlassian.net/browse/ATT-6213)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath (WorkoutSummaries, Details & Graphs)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-UI-499` (*Reorderable WorkoutDetails & Photos-First Card Ordering*)  
**Test Mapping**: `TST-UI-499`  
**Branch**: `feature/ATT-6213`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Description & Background
In `WorkoutSummary.kt`, `WorkoutDetails` was statically positioned above the dynamic section loop, blocking athletes from placing Photos (`PHOTOS`) or other sections directly beneath `WorkoutHeader` and above `WorkoutDetails`. This plan outlines the atomic construction steps to unify `WorkoutSectionType` with `WorkoutDetailSectionType`, slot `WorkoutDetails` into the dynamic loop, provide intelligent preference migration, and wire the settings matrix while enforcing strict file line limits.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-499` (`REQ-UI-499.1`–`REQ-UI-499.6`), refining `REQ-UI-255`
* **Test Mapping**: `TST-UI-499` (`TST-UI-499.1`–`TST-UI-499.6`)

---

## 3. System Invariants & Preserved Behavior
1. **Top Identity Anchor**: `WorkoutHeader` remains permanently and non-configurably anchored at the top of every card.
2. **Visual Fidelity for Existing Installs**: `DETAILS` defaults to index 0 in `DEFAULT_ORDER` and `showDetails = true`. Legacy orders lacking `DETAILS` migrate gracefully (index 1 if `PHOTOS` is at index 0; index 0 otherwise).
3. **Modularity Constraint**: `WorkoutMasksAndCardsSection.kt` remains strictly $< 400$ lines (currently 394).
4. **Localization Parity**: Uses existing `R.string.workout_detail_section_details` localized across all 9 languages.

---

## 4. Proposed Architectural Changes
* **UI Consistency**: Follows `docs/design_guidelines.md` Section 5. Reuses standard `HorizontalDivider` (`outlineVariant`), `MatrixFeatureRow` drag-and-drop pattern in `WorkoutMasksAndCardsSection.kt`, and `WorkoutDetails.kt` layout without introducing new one-off styles.
* **Component 1 (Domain & Persistence)**: Add `WorkoutSectionType.DETAILS`, update bidirectional mappings in `WorkoutDetailTabConfig.kt`, add `showDetails` to `WorkoutCardSectionPreferences` and DataStore keys in `MyPreferenceManager.kt`.
* **Component 2 (Summary Card & Settings UI)**: Move `WorkoutDetails` inside dynamic loop in `WorkoutSummary.kt` under `WorkoutSectionType.DETAILS` with single header divider; add `DETAILS` to `WorkoutMasksAndCardsSection.kt`; add no-op handling in `TrackOnMapScreen.kt`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Domain Enum Unification & Migration Logic
* Files: `WorkoutSectionType.kt`, `WorkoutDetailTabConfig.kt`, `MyPreferenceManager.kt`
* Changes: Add `DETAILS(R.string.workout_detail_section_details)` to `WorkoutSectionType`; update `DEFAULT_ORDER` (12 items); update `fromSerializedString` with migration logic; update `toWorkoutSectionTypeOrNull` & `toWorkoutDetailSectionType`; add `showDetails = true` to `WorkoutCardSectionPreferences` and DataStore persistence.

### Step 2: Dynamic Execution & Settings Matrix Integration
* Files: `WorkoutSummary.kt`, `WorkoutMasksAndCardsSection.kt`, `TrackOnMapScreen.kt`
* Changes: Remove hardcoded `WorkoutDetails(...)` before loop in `WorkoutSummary.kt`; render under `WorkoutSectionType.DETAILS` within loop; guard against duplicate top dividers; map `DETAILS` in `buildMatrixFeatureRow` & `activeCardCount`; add no-op branch in `TrackOnMapScreen.kt`.

### Step 3: Test Suite Alignment & Contract Updates
* Files: `WorkoutSectionOrderPersistenceTest.kt`, `WorkoutSummaryDynamicOrderContractTest.kt`, `WorkoutSummarySectionsTest.kt`, `WorkoutCardSectionPreferencesTest.kt`
* Changes: Update `DEFAULT_ORDER` assertions (12 items); add migration tests; update anchor contract test (only `WorkoutHeader` precedes loop); verify `showDetails` presence and toggling.

### Step 4: Targeted Unit Tests & Living Documentation Check
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* Check: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit test suite (`TST-UI-499`), modularity line check (`wc -l < 400`), living docs sync.
* **Rollback**: Atomic git commit isolation on `feature/ATT-6213`.
