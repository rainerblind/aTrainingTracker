# Stage 2: Requirement & Test Specification - ATT-5811: Move 'Add Photo' Button to Section Header

**Ticket**: [ATT-5811](https://atrainingtracker.atlassian.net/browse/ATT-5811)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5811`  
**Requirement Mapping**: `REQ-UI-448.1` (*Workout Photos Strip & Neutral Summary Styling*)  
**Test Spec ID**: `TST-UI-448.1` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-07  

---

## 1. Requirement Specification (REQ-UI-448.1)

### 1.1 Problem Statement & Rationale
In `WorkoutPhotoStrip.kt`, the "Add Photo" action was embedded as an `OutlinedCard` (`add_photo_tile`) inside `LazyRow`, inheriting large responsive thumbnail dimensions (`MEDIUM` ~175dp, `LARGE` ~360dp per `ATT-5655`). When workouts have 0 photos, this empty tile consumes ~1/4 screen height, pushing summary telemetry offscreen. With photos present, it consumes row slots and displaces photos. Moving the action to the header creates a compact, clean layout.

### 1.2 Functional & Architectural Requirements
1. **Header Placement**: In `WorkoutPhotoStrip.kt`, the "Add Photo" action SHALL be rendered in the section header `Row` (trailing slot of `Arrangement.SpaceBetween`) as a compact `IconButton` (`Icons.Default.AddPhotoAlternate`, `20.dp`, neutral `onSurfaceVariant`, `contentDescription = stringResource(R.string.workout_photos_add)`) when `showAddButton == true`.
2. **Conditional LazyRow**: The `LazyRow` container SHALL be rendered only when `photos.isNotEmpty()`, displaying photo thumbnails starting at index 0 without an oversized empty tile.
3. **Empty State Compactness**: When `photos.isEmpty() && showAddButton == true`, `WorkoutPhotoStrip` SHALL render only the compact single-line header row without vertical card bloat.
4. **Visibility Toggle**: When `showAddButton == false`, no add button SHALL be rendered. When `photos.isEmpty() && !showAddButton`, `WorkoutPhotoStrip` SHALL return early and render nothing.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Header Action & Empty Strip)**:
  * *Given* a workout with 0 attached photos and `showAddButton == true`,
  * *When* `WorkoutPhotoStrip` renders,
  * *Then* only the single-line header row is displayed with title "Fotos" and trailing compact "Add Photo" `IconButton`, with zero `LazyRow` card height overhead.
* **Criterion 2 (Photos Populated & Header Add)**:
  * *Given* a workout with 2 attached photos and `showAddButton == true`,
  * *When* `WorkoutPhotoStrip` renders,
  * *Then* the header displays title "Fotos (2)" and "Add Photo" button, while `LazyRow` renders the 2 photo thumbnails starting at index 0 without `add_photo_tile`.
* **Criterion 3 (Add Button Hidden)**:
  * *Given* `showAddButton == false`,
  * *When* `WorkoutPhotoStrip` renders,
  * *Then* the "Add Photo" `IconButton` is absent from the header row.

### 1.4 System Invariants
* Zero regression on thumbnail downsampling (`REQ-UI-457`), time-filter sheet (`REQ-UI-455`), or caller signatures.
* Neutral color tokens (`onSurfaceVariant`) strictly enforced without primary blue (`REQ-UI-448.1`).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-448.1` targeting `WorkoutPhotoStrip.kt`.
* **Historical Origin & Commit Trace**: `ATT-43` (commit `5c9d9653`), `ATT-5474` (commit `8aed20fc`), `ATT-5655` (responsive sizing).
* **Root Reason for Existing Formulation**: MVP used an in-carousel `OutlinedCard` to mimic a photo gallery card. Responsive thumbnail enlargement in `ATT-5655` bloated this tile into an oversized empty box.
* **Preservation of Core Invariants**: No `MaterialTheme.colorScheme.primary` (neutral styling per Guideline 5.17); full photo picker permissions & time-filter sheet flow intact (`REQ-UI-455`); thumbnail downsampling safe (`REQ-UI-457`); caller signatures unchanged; 100% 9-language translation parity.

---

## 2. Test Specification (TST-UI-448.1)

### Test Case 1: `testWorkoutPhotoStrip_headerAddButtonAndConditionalLazyRowContract` (`[TST-UI-448.1]`)
* **Scope**: UI / Source Contract Test (`WorkoutPhotoUiContractTest.kt`).
* **Assertions**:
  1. Header contains `Icons.Default.AddPhotoAlternate` with `tint = MaterialTheme.colorScheme.onSurfaceVariant` and `contentDescription = stringResource(R.string.workout_photos_add)`.
  2. `LazyRow` is wrapped in `if (photos.isNotEmpty())`.
  3. `add_photo_tile` is removed from `LazyRow`.
  4. Zero usage of `MaterialTheme.colorScheme.primary`.

### Test Case 2: 9-Language Localization & Specifier Audit (`[TST-UI-448.5]`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`).
* **Assertions**: `workout_photos_add` and `workout_photos_title` present and identical across all 9 locales.

### Test Case 3: Tier 1 Targeted Regression Suite (`[TST-UI-448.6]`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertions**: 100% pass rate across all touched and theme audit suites.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-448.1]` | Contract | `WorkoutPhotoUiContractTest`: Header button, conditional `LazyRow`, no `add_photo_tile` | `REQ-UI-448.1` | Specified |
| `[TST-UI-448.5]` | Localization | `TranslationParityTest`: Parity for `workout_photos_add` across 9 locales | `REQ-UI-448.5`, `REQ-UI-106` | Specified |
| `[TST-UI-448.6]` | Regression | Tier 1 targeted suite (`ui.aftermath.*`, `ThemeColorCentralizationAuditTest`, `build.*`) | `REQ-PRO-001` | Specified |
