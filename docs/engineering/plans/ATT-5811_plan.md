# Stage 3: Implementation Plan - ATT-5811: Move 'Add Photo' Button to Section Header

**Ticket**: [ATT-5811](https://atrainingtracker.atlassian.net/browse/ATT-5811)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5811`  
**Requirement Mapping**: `REQ-UI-448.1` | **Test Mapping**: `TST-UI-448.1`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-07  

---

## 1. Problem Description & Background
In `WorkoutPhotoStrip.kt`, the "Add Photo" action was embedded as an `OutlinedCard` (`add_photo_tile`) inside `LazyRow`, inheriting large responsive thumbnail dimensions (`MEDIUM` ~175dp, `LARGE` ~360dp per `ATT-5655`). When workouts have 0 photos, this empty tile consumes ~1/4 screen height, pushing summary telemetry offscreen. With photos present, it consumes row slots and displaces photos. Moving the action to the header creates a compact, clean layout.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-448.1` (*Workout Photos Strip & Neutral Summary Styling*)
* **Test Mapping**: `TST-UI-448.1` (*Workout Photos Strip & Empty State Contract Test*)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Caller Breaking Changes**: Parameter signatures of `WorkoutPhotoStrip` (`photos`, `onPhotoClick`, `onAddPhotos`, `showAddButton`, `thumbnailSize`, timestamps) remain strictly unchanged for `WorkoutSummary.kt` and `WorkoutDetailTabbedContent.kt`.
2. **Neutral Color Tokens (Design Guideline 5.17)**: Camera icon and "Add Photo" `IconButton` strictly use `MaterialTheme.colorScheme.onSurfaceVariant` without `primary` blue.
3. **Downsample & Sheet Invariants**: Dynamic bitmap downsampling (`REQ-UI-457`) and chronological time-filter picker sheet (`REQ-UI-455`) remain 100% intact.
4. **Visibility Invariance**: `showAddButton = false` hides the button entirely; `photos.isEmpty() && !showAddButton` renders nothing.

---

## 4. Proposed Architectural Changes

### Component 1: `WorkoutPhotoStrip.kt`
* Add trailing `IconButton` (`size(32.dp)`) with `Icons.Default.AddPhotoAlternate` (`20.dp`, `onSurfaceVariant`) in the header `Row` (`Arrangement.SpaceBetween`) when `showAddButton == true`.
* Remove `add_photo_tile` `OutlinedCard` from `LazyRow`.
* Guard `BoxWithConstraints` / `LazyRow` with `if (photos.isNotEmpty())` so 0-photo state collapses into a single sleek header line.

### UI Consistency (Rule 23)
* **Reference component**: `WorkoutSummary.kt` header rows and telemetry section titles.
* **Reused components**: `IconButton`, `Icon`, `Text`, `LazyRow`, `PhotoThumbnailCard`.
* **Theme tokens**: `MaterialTheme.colorScheme.onSurfaceVariant` (neutral dark grey), `onSurface`, typography `titleSmall`, `bodySmall`, 8dp spacing.
* **New one-off styles**: None. Fully compliant with Material 3 and Section 5.17.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `WorkoutPhotoStrip.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/photos/WorkoutPhotoStrip.kt`
* Relocate add button into header `Row` trailing slot via `IconButton`; remove `add_photo_tile` item from `LazyRow`; wrap `BoxWithConstraints` in `if (photos.isNotEmpty())`.

### Step 2: Update Contract Test Suite
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutPhotoUiContractTest.kt`
* Verify header contains `IconButton` with `AddPhotoAlternate` and `onSurfaceVariant`; verify `LazyRow` is guarded by `photos.isNotEmpty()`; verify `add_photo_tile` is removed.

### Step 3: Targeted Verification & Regression Suites
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* Command: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Tier 1 targeted tests (`ui.aftermath.*`, theme audit, build audit) + `verify_requirement_governance.py`.
* **Rollback**: Git branch isolation (`feature/ATT-5811`) allows clean checkout/revert if regressions occur.
