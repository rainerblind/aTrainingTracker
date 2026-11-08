# Stage 2: Requirement & Test Specification - ATT-6213: Reorderable WorkoutDetails

**Ticket**: [ATT-6213](https://rainerblind.atlassian.net/browse/ATT-6213)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath (WorkoutSummaries, Details & Graphs)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-UI-499` (*Reorderable WorkoutDetails & Photos-First Card Ordering*), refining `REQ-UI-255`  
**Test Spec ID**: `TST-UI-499`  
**Branch**: `feature/ATT-6213`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Requirement Specification (`REQ-UI-499`)

### 1.1 Problem Statement & Rationale
In `WorkoutSummary.kt`, `WorkoutDetails` (*Zeit aktiv / Distanz / Höhenmeter*) was hardcoded above `workoutSectionsOrder.forEach`, preventing athletes from positioning Photos (`PHOTOS`) or other sections directly below `WorkoutHeader` and above `WorkoutDetails`. Unifying `WorkoutSectionType` with `WorkoutDetailSectionType` allows `WorkoutDetails` to be reordered freely while retaining `WorkoutHeader` as the sole top identity anchor.

### 1.2 Functional & Architectural Requirements
* **REQ-UI-499.1 (Domain Enum Unification)**: `WorkoutSectionType` SHALL define `DETAILS(R.string.workout_detail_section_details)`. Bidirectional mapping with `WorkoutDetailSectionType.DETAILS` SHALL be 1:1. `DEFAULT_ORDER` SHALL contain 12 items with `DETAILS` at index 0.
* **REQ-UI-499.2 (Self-Healing Migration)**: `fromSerializedString` SHALL insert `DETAILS` at index 1 if `PHOTOS` is at index 0; otherwise at index 0.
* **REQ-UI-499.3 (Preferences Persistence)**: `WorkoutCardSectionPreferences` SHALL include `val showDetails: Boolean = true`, persisted via `WORKOUT_CARD_SHOW_DETAILS` in `MyPreferenceManager.kt`.
* **REQ-UI-499.4 (Dynamic Execution & Dividers)**: `WorkoutSummary.kt` SHALL render `WorkoutDetails` inside `workoutSectionsOrder.forEach` under `WorkoutSectionType.DETAILS` (gated by `showDetails`). Exactly one `HorizontalDivider` SHALL separate `WorkoutHeader` from the first active section.
* **REQ-UI-499.5 (Settings Row & Modularity)**: `WorkoutMasksAndCardsSection.kt` SHALL map `DETAILS` to `MatrixFeatureRow` with checkbox and drag handle while strictly maintaining file size $< 400$ lines.
* **REQ-UI-499.6 (Exhaustive Callers)**: `TrackOnMapScreen.kt` `when` blocks SHALL handle `WorkoutSectionType.DETAILS`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Photos Above Details)**:
  * *Given* a workout with attached photos and `workoutSectionsOrder` where `PHOTOS` precedes `DETAILS`,
  * *When* `WorkoutSummary` renders the workout card,
  * *Then* `WorkoutHeader` appears at the top, followed immediately by `WorkoutPhotoStrip`, and `WorkoutDetails` renders below photos.
* **Criterion 2 (Reorderable in Settings)**:
  * *Given* Workout Card settings in `WorkoutMasksAndCardsSection`,
  * *When* the user reorders sections,
  * *Then* `DETAILS` can be dragged to any vertical position and toggled via `showDetails`.
* **Criterion 3 (Backward-Compatible Migration)**:
  * *Given* a stored order lacking `DETAILS`,
  * *When* deserialized by `fromSerializedString`,
  * *Then* `DETAILS` is placed at index 1 if `PHOTOS` was at index 0, or at index 0 otherwise.

### 1.4 System Invariants
* `WorkoutHeader` remains permanently and non-configurably anchored at the top of every card.
* Existing installations and default configurations retain identical visual metric presentation (`Header -> Details -> ...`).
* `WorkoutMasksAndCardsSection.kt` remains $< 400$ lines.
* 100% 9-language localization parity without hardcoded strings.

---

## 2. Test Specification (`TST-UI-499`)

### Test Case 1: Domain & Self-Healing Migration (`TST-UI-499.1`, `TST-UI-499.2`)
* **Scope**: Unit Test in `WorkoutSectionOrderPersistenceTest.kt`
* **Checks**: `DEFAULT_ORDER.size == 12`, `DEFAULT_ORDER[0] == DETAILS`, `fromSerializedString` with `PHOTOS` at index 0 inserts `DETAILS` at index 1, fallback with `DESCRIPTION` at index 0 inserts `DETAILS` at index 0, round-trip serialization.

### Test Case 2: Summary Contract & Invariants (`TST-UI-499.3`, `TST-UI-499.4`)
* **Scope**: Contract Tests in `WorkoutSummaryDynamicOrderContractTest.kt` & `WorkoutSummarySectionsTest.kt`
* **Checks**: `WorkoutDetails` is executed within `workoutSectionsOrder.forEach` under `WorkoutSectionType.DETAILS`; `WorkoutHeader` is the sole anchor preceding the loop; `WorkoutCardSectionPreferences` includes `showDetails = true`.

### Test Case 3: Settings Modularity & Localization Parity (`TST-UI-499.5`)
* **Scope**: Localization & Line-Limit Tests
* **Checks**: `TranslationParityTest` passes across all 9 locales for `workout_detail_section_details`; `WorkoutMasksAndCardsSection.kt` line count is $< 400$ lines.

### Test Case 4: Tier 1 Targeted Regression Suite (`TST-UI-499.6`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Goal**: 100% pass rate across affected packages.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Test Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-499.1` | Unit | `WorkoutSectionOrderPersistenceTest` | `REQ-UI-499.1` | Specified |
| `TST-UI-499.2` | Unit / Migration | `WorkoutSectionOrderPersistenceTest` | `REQ-UI-499.2` | Specified |
| `TST-UI-499.3` | Contract | `WorkoutSummaryDynamicOrderContractTest` | `REQ-UI-499.3`, `REQ-UI-499.4` | Specified |
| `TST-UI-499.4` | Contract | `WorkoutSummarySectionsTest` | `REQ-UI-499.3` | Specified |
| `TST-UI-499.5` | Modularity / L10n | `TranslationParityTest` | `REQ-UI-499.5`, `REQ-LOC-001` | Specified |
| `TST-UI-499.6` | Tier 1 Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
