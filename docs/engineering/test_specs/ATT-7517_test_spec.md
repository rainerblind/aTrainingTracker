# Stage 2: Requirement & Test Specification - ATT-7517: Equal-Width Workout Classification Tiles & Grouped Cloud Sync Card

**Ticket**: [ATT-7517](https://rainerblind.atlassian.net/browse/ATT-7517)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7517`  
**Requirement Mapping**: `REQ-REV-013` | **Test Spec ID**: `TST-REV-014`  
**Author**: AI Agent 2 (Flash Agent) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (REQ-REV-013)

### 1.1 Problem Statement & Rationale
In `EditWorkoutScreen.kt`, three checkbox controls (`Pendeln`, `Indoor Trainer`, `Wettkampf`) currently span two awkward rows (`2 + 1`), wasting ~52 dp of vertical space with small non-label click targets. Bare checkboxes blur workout tags with external cloud sync options (Strava, Health Connect), and Health Connect renders a generic blue heart instead of official brand assets. The system SHALL replace tag checkboxes with a unified single row of 3 equal-width icon-assisted toggle tiles (`Commute`, `Indoor`, `Race`), group cloud sync into a dedicated outlined Card with full-row click targets, and localize concise `"Indoor"` (`indoor_short`) across all 9 locales.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Review

### 1.3 Functional & Architectural Requirements
* **REQ-REV-013.1** (*3 Equal-Width Classification Tiles*): `EditWorkoutScreen.kt` SHALL render `Commute`, `Indoor`, and `Race` tags within a single `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp))` positioned beneath the Sport and Equipment selectors using `Modifier.weight(1f)`, `RoundedCornerShape(8.dp)`, and min height `42.dp`. Each tile SHALL provide full-surface toggling via `Modifier.toggleable(role = Role.Checkbox)`. Commute uses `Icons.Default.Commute`, Indoor uses `Icons.Default.Home`, and Race uses `Icons.Filled.EmojiEvents` styled with `tertiaryContainer` background / `tertiary` border when active.
* **REQ-REV-013.2** (*Commute / Indoor Mutual Exclusivity*): `EditWorkoutViewModel` SHALL enforce mutual exclusivity between `Commute` and `Indoor` (`updateIsCommute(true)` clears `trainer = false`; `updateIsTrainer(true)` clears `commute = false`), while `Race` remains freely combinable with either.
* **REQ-REV-013.3** (*Grouped Cloud Sync Card*): When Strava and/or Health Connect sync is enabled, `EditWorkoutScreen.kt` SHALL group them inside an outlined `Surface`/`Card` (`RoundedCornerShape(10.dp)`) with full-row `Modifier.toggleable(...)` tap targets, compact trailing selection indicators, official brand icons (`logo_square_strava` with `0.38f` disabled alpha, `ic_health_connect`), and `Color.Unspecified` tint.
* **REQ-REV-013.4** (*9-Language Localization Parity*): Concise token `indoor_short` SHALL be defined across all 9 locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) with zero emojis.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Toggle Tiles & Exclusivity)**: *Given* `EditWorkoutScreen`, *When* tapping `Commute`, `Indoor`, or `Race`, *Then* all 3 sit on a single equal-width row, the entire tile area toggles state, `Commute` and `Indoor` mutually exclude, and `Race` toggles independently.
* **Criterion 2 (Grouped Cloud Sync Card)**: *Given* Strava/Health Connect active, *When* rendering sync controls, *Then* they render in an outlined card with brand vector assets and full-row tap targets.
* **Criterion 3 (9-Locale Parity)**: *Given* `TranslationParityTest`, *When* checking `indoor_short`, *Then* 100% parity across all 9 locales.

### 1.5 System Invariants & Scope Bounding
* Zero changes to `WorkoutRpeFeelSection.kt` (Borg CR10 and Feel cards strictly bounded to ATT-7515).
* Data integrity preserved: `WorkoutData` persistence, Strava disabled alpha (`0.38f`), and Health Connect sync dispatch.

---

## 2. Test Specification (TST-REV-014)

### Test Case 1: `testEditWorkoutViewModel_commuteAndTrainerMutualExclusivity` (`TST-REV-014.1`)
* **Scope**: Unit Test (`EditWorkoutViewModelTest.kt`)
* **Action**: Verify `updateIsCommute(true)` clears `trainer`, `updateIsTrainer(true)` clears `commute`, and `updateIsRace(true)` toggles independently.

### Test Case 2: `testEditWorkoutScreen_tagTilesAndGroupedSyncCardContract` (`TST-REV-014.2`)
* **Scope**: UI Layout Contract Test (`EditWorkoutFieldsLayoutTest.kt` / `EditWorkoutClassificationLayoutContractTest.kt`)
* **Action**: Verify single row with 3 equal tiles (`weight(1f)`), `Modifier.toggleable(role = Role.Checkbox)`, `logo_square_strava`, `ic_health_connect`, and outlined sync card container.

### Test Case 3: `testIndoorShortLocalizationParityAcross9Locales` (`TST-REV-014.3`)
* **Scope**: Localization Parity Test (`EditWorkoutStringsParityContractTest.kt`, `TranslationParityTest.kt`)
* **Action**: Assert `indoor_short` exists in all 9 `values*/strings.xml` without missing translations or inline emojis.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-REV-014.1` | Unit | `EditWorkoutViewModel.kt` | `REQ-REV-013.2` | Specified |
| `TST-REV-014.2` | Contract | `EditWorkoutScreen.kt` | `REQ-REV-013.1`, `REQ-REV-013.3` | Specified |
| `TST-REV-014.3` | L10n | `res/values*/strings.xml` | `REQ-REV-013.4` | Specified |
