# Stage 2: Requirement & Test Specification - ATT-5809: Uniform Share Button in WorkoutHeader

**Ticket**: [ATT-5809](https://atrainingtracker.atlassian.net/browse/ATT-5809)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5809`  
**Requirement Mapping**: `REQ-UI-447.1` (*Share Mode Selection Bottom Sheet & Persistent Header Trigger*)  
**Test Spec ID**: `TST-UI-447.4` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Requirement Specification (REQ-UI-447.1)

### 1.1 Problem Statement & Rationale
Workout image sharing (`WorkoutShareBottomSheet`) is currently fragmented and hard to discover: absent from `WorkoutHeader` in the workout list (`WorkoutSummary.kt`), hidden on Tab 1/3/4 of multi-tab workout details (`WorkoutDetailTabbedContent.kt`) because the floating share button only lives on the map canvas in `MapDetailLayout.kt`, and omitted from Option A snapshots due to `header = {}`. Adding a uniform Share button to `WorkoutHeader` makes sharing prominent across all views.

### 1.2 Functional & Architectural Requirements
1. **Header Action Parameter**: `WorkoutHeader.kt` SHALL expose `onShareWorkout: (() -> Unit)? = null` and render an `IconButton` (`size = 32.dp`, `Icons.Default.Share`, `tint = MaterialTheme.colorScheme.primary`, `contentDescription = stringResource(R.string.share)`) adjacent to `onEditWorkout` in both portrait and landscape orientations.
2. **Portrait Spacer Expansion**: In `WorkoutHeader.kt` portrait layout, the top action button spacer SHALL dynamically expand to ~104dp when both edit and share actions are present (`hasEdit && hasShare`), preventing title collision and truncation.
3. **Workout List Integration**: In `WorkoutSummary.kt`, `WorkoutHeader` SHALL wire `onShareWorkout` to open `WorkoutShareBottomSheet` pre-populated with `attachedPhotos`. Option A (*Karten- & Analyse-Snapshot*) SHALL navigate to map details (`onMapClick()`).
4. **Workout Details Integration & Header Snapshot Capture**: In `WorkoutDetailTabbedContent.kt`, `WorkoutHeader` SHALL wire `onShareWorkout` to open `WorkoutShareBottomSheet` across all tabs. When Option A is selected, `WorkoutDetailTabbedContent.kt` SHALL supply the `WorkoutHeader` composable into `MapDetailLayout` `header` parameter (capturing it in `headerLayer` snapshot output) and initiate snapshot generation.
5. **Suppression of Redundant Map Floating Button**: In `MapDetailLayout.kt`, the floating share button on the map canvas SHALL be suppressed when `workoutData != null` in multi-tab details, while remaining active for standalone route/segment maps (`workoutData == null`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Header Action in List & Details)**:
  * *Given* a workout displayed in `WorkoutSummary` or `WorkoutDetailTabbedContent`,
  * *When* the header renders,
  * *Then* a 32dp Share button is visible next to the Edit button in both portrait and landscape.
* **Criterion 2 (Option A Snapshot Header Capture)**:
  * *Given* an athlete triggering Option A from `WorkoutDetailTabbedContent`,
  * *When* snapshot composition executes,
  * *Then* `WorkoutHeader` is rendered within `headerLayer` at the top of the generated image.
* **Criterion 3 (Map Floating Button Suppression)**:
  * *Given* `MapDetailLayout` rendered with `workoutData != null` in multi-tab details,
  * *When* the map displays,
  * *Then* the floating share button on the map canvas is hidden, avoiding redundant share CTAs.

### 1.4 System Invariants
* Non-workout map callers (`RouteOnMapScreen`, `SegmentOnMapScreen`) retain their floating share button.
* `WorkoutHeader` export menu (FIT/TCX/GPX/CSV/GC), Strava upload, and edit buttons remain 100% operational.
* Touch targets $\ge 32\text{dp}$; WCAG AAA text contrast; 100% 9-language translation parity.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-447.1` targeting `WorkoutHeader.kt`, `WorkoutSummary.kt`, `WorkoutDetailTabbedContent.kt`, and `MapDetailLayout.kt`.
* **Historical Origin & Commit Trace**: `ATT-30` (`584284ff`, `REQ-UI-447`), `ATT-3920` (`REQ-UI-408`), `ATT-1393` (`REQ-UI-255`), `ATT-5809`.
* **Root Reason for Existing Formulation**: Snapshot sharing originally lived solely inside `MapDetailLayout`. When `ATT-3920` introduced multi-tab details defaulting to Tab 1 without a map, sharing became stranded on Tab 2.
* **Preservation of Core Invariants**: Option A & B sharing pipelines remain 100% intact; safe downsampled bitmap decoding avoids OOM; non-workout maps unaffected; 100% 9-language localization parity preserved.

---

## 2. Test Specification (TST-UI-447.4)

### Test Case 1: `testWorkoutHeader_shareActionContract` (`[TST-UI-447.4]`)
* **Scope**: Contract Test (`WorkoutShareBottomSheetContractTest.kt` / `WorkoutHeaderExportMenuContractTest.kt`).
* **Assertions**:
  1. `WorkoutHeader.kt` accepts `onShareWorkout: (() -> Unit)? = null`.
  2. Renders `Icons.Default.Share` with `size(32.dp)` and `tint = MaterialTheme.colorScheme.primary`.
  3. Portrait spacer accommodates ~104dp when both edit and share buttons are active.
  4. `WorkoutDetailTabbedContent.kt` supplies non-empty header composable to `MapDetailLayout`.
  5. `MapDetailLayout.kt` conditionally suppresses floating share button when `workoutData != null`.

### Test Case 2: 9-Language Localization & Specifier Audit (`[TST-UI-447.5]`)
* **Scope**: `TranslationParityTest.kt`.
* **Assertions**: `R.string.share` present and valid across all 9 locales.

### Test Case 3: Tier 1 Targeted Regression Suite (`[TST-UI-447.5]`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.workoutheader.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertions**: 100% pass rate across all touched packages and build integrity tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-447.4]` | Contract | `WorkoutHeader.kt` share button, spacer expansion, details header plumbing, map floating button suppression | `REQ-UI-447.1` | Specified |
| `[TST-UI-447.5]` | Localization | `TranslationParityTest`: Parity for `R.string.share` across all 9 locales | `REQ-UI-447.5`, `REQ-LOC-001` | Specified |
| `[TST-UI-447.5]` | Regression | Tier 1 targeted suite (`workoutheader.*`, `ui.map.*`, `ThemeColorCentralizationAuditTest`, `build.*`) | `REQ-PRO-001` | Specified |
