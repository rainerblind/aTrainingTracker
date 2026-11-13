# Stage 3: Implementation Plan - ATT-7517: Equal-Width Workout Classification Tiles & Grouped Cloud Sync Card

**Ticket**: [ATT-7517](https://rainerblind.atlassian.net/browse/ATT-7517)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7517`  
**Requirement Mapping**: `REQ-REV-013` (`REQ-REV-013.1`–`REQ-REV-013.4`)  
**Test Mapping**: `TST-REV-014` (`TST-REV-014.1`–`TST-REV-014.3`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Description & Background
In `EditWorkoutScreen.kt`, three checkbox controls (`Pendeln`, `Indoor Trainer`, `Wettkampf`) span two awkward rows (`2 + 1`), wasting ~52 dp of vertical space with small 24x24dp non-label click targets. Bare checkboxes blur workout tags with cloud export toggles (Strava, Health Connect), and Health Connect incorrectly renders generic `Icons.Default.Favorite` instead of official brand assets. RPE 1–10 / Feel 1–5 redesign is explicitly deferred to ATT-7515 per Ceremony 1 Q2 agreement.

---

## 2. Traceability & Requirements Mapping
* **REQ-REV-013.1**: 3 equal-width toggle tiles (`Commute`, `Indoor`, `Race`) on single row with `weight(1f)`, `RoundedCornerShape(8.dp)`, min height `42.dp`, `Modifier.toggleable(role = Role.Checkbox)`, leading icons, and Race `tertiaryContainer` styling $\rightarrow$ `TST-REV-014.2`.
* **REQ-REV-013.2**: Commute/Indoor mutual exclusivity in `EditWorkoutViewModel` $\rightarrow$ `TST-REV-014.1`.
* **REQ-REV-013.3**: Grouped Cloud Sync card (`Surface`, `RoundedCornerShape(10.dp)`) with full-row toggleability, `logo_square_strava` (0.38f disabled alpha), `ic_health_connect`, and trailing Checkboxes $\rightarrow$ `TST-REV-014.2`.
* **REQ-REV-013.4**: 9-language localization parity for concise `indoor_short` with zero emojis $\rightarrow$ `TST-REV-014.3`.

---

## 3. System Invariants & Preserved Behavior
1. **Unconditional Field Presence**: Retains all workout metadata sections pursuant to `REQ-UI-239` (`EditWorkoutFieldsLayoutTest`).
2. **Data & Dispatch Integrity**: `WorkoutData` persistence, Strava disabled state (`0.38f` alpha when `!stravaMappingAvailable`), and Health Connect manual sync dispatch preserved.
3. **Scope Bounding Invariance**: Zero modifications to `WorkoutRpeFeelSection.kt` (deferred to ATT-7515).
4. **9-Language Parity**: 100% token parity across all 9 canonical locales without emojis.

---

## 4. Proposed Architectural Changes

### Component 1: ViewModel Layer (`EditWorkoutViewModel.kt`)
* Harden `updateIsCommute(isChecked)` and `updateIsTrainer(isChecked)` mutual exclusivity: checking Commute clears Trainer; checking Trainer clears Commute. `updateIsRace(isChecked)` remains independent.

### Component 2: Presentation Layer (`EditWorkoutScreen.kt`)
* Replace 2 tag checkbox rows (`L290-313`) with single `Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp))` hosting three `weight(1f)` toggle tiles (`defaultMinSize(minHeight = 42.dp)`). Active race tile styled with `tertiaryContainer` / `tertiary` border matching `WorkoutHeader.kt`.
* Group Strava and Health Connect (`L315-370`) inside an outlined `Surface(shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, outlineVariant))` with full-row toggleable rows, `logo_square_strava`, and `ic_health_connect`.

### UI Consistency (`docs/design_guidelines.md` §1 & §5)
* **Reference Component**: `WorkoutRpeFeelSection.kt` (Feel tiles), `WorkoutHeader.kt` (Race trophy badge).
* **Reused Tokens**: `RoundedCornerShape(8.dp)`, `RoundedCornerShape(10.dp)`, `MaterialTheme.colorScheme.tertiaryContainer`, `TTColor` / M3 theme colors. Full-surface tap targets $\ge 42\text{ dp}$.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Locale Localization (`res/values*/strings.xml`)
* Define `indoor_short` ("Indoor" across all 9 locales: EN, DE, ES, FR, IT, JA, NL, PL, PT).

### Step 2: ViewModel Exclusivity & Unit Tests (`EditWorkoutViewModel.kt`, `EditWorkoutViewModelTest.kt`)
* Verify mutual exclusivity in `EditWorkoutViewModel.kt`; author unit tests in `EditWorkoutViewModelTest.kt` (`TST-REV-014.1`).

### Step 3: UI Construction (`EditWorkoutScreen.kt`)
* Implement 3 equal-width classification tiles (`Variant B`) and grouped outlined cloud sync card (`TST-REV-014.2`).

### Step 4: Layout Contract & Parity Unit Tests
* Author `EditWorkoutClassificationLayoutContractTest.kt` (`TST-REV-014.2`) and run `TranslationParityTest.kt` (`TST-REV-014.3`).
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Zero failures across `EditWorkoutViewModelTest`, layout contract tests, and `TranslationParityTest`.
* **Rollback**: Clean revert via `git reset --hard feature/ATT-7517` before branch merge.
