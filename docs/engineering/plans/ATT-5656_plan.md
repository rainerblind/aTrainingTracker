# Stage 3: Implementation Plan - ATT-5656: WorkoutShareBottomSheet 3-Tier Photo Preselection

**Ticket**: [ATT-5656](https://rainerblind.atlassian.net/browse/ATT-5656)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (Good and consistent UI) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5656`  
**Requirement Mapping**: `REQ-UI-447` (`REQ-UI-447.6`)  
**Test Mapping**: `TST-UI-447` (`TST-UI-447.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
In `WorkoutShareBottomSheet.kt`, selecting Option B ("Foto mit Track & Daten") or tapping "Foto auswählen" in preview currently launches the generic system photo picker directly. Although callers pass `attachedPhotos: List<Uri>`, it is unused. ATT-5656 implements a 3-tier photo cascade: Tier 1 (Workout-attached photos) $\rightarrow$ Tier 2 (Time-window photos) $\rightarrow$ Tier 3 (All photos / System picker) with seamless switching and Rule 73 stacked buttons.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-447.6` (*3-Tier Photo Selection Cascade*)
* **Test Mapping**: `TST-UI-447.6` (`TST-UI-447.6a`–`e`) (*Tier 1, Tier 2, Tier 3 Fallback, Rule 73 Buttons & 9-Locale Parity*)

---

## 3. System Invariants & Preserved Behavior
1. **Option A Snapshot Invariant**: Option A (Map & Analytics composite snapshot) remains 100% untouched.
2. **Preview Fast-Path**: When `initialPhotoUri != null`, the sheet composes the overlay immediately into `PREVIEW`.
3. **Rule 73 Button Hierarchy**: Full-width stacked buttons with `spacedBy(8.dp)` across preview and selection tiers; no `actions =` slot on `AppModalBottomSheet`.
4. **Bounded Grid Height**: LazyVerticalGrids are wrapped in bounded boxes (`120.dp` / `240.dp`) to avoid infinite height exceptions in bottom sheets.
5. **Memory Safety**: Downsampled decoding (`maxDim = 240` for thumbnails) prevents OOM.

---

## 4. Proposed Architectural Changes

### Component 1: `WorkoutShareBottomSheet.kt`
* Add internal state enums: `ShareSheetState { MODE_SELECTION, PHOTO_SELECTION, PREVIEW }` and `PhotoCascadeTier { ATTACHED, TIME_WINDOW }`.
* Option B click: If `attachedPhotos.isNotEmpty()`, enter Tier 1 (`ATTACHED`). Else, query `WorkoutPhotoManager.queryWorkoutTimePhotos(context, startMs, endMs)`. If matching photos found, enter Tier 2 (`TIME_WINDOW`). If 0 photos found or permission denied, launch `PickVisualMedia`.
* In `PHOTO_SELECTION`: Render 3-column thumbnail grid with capture timestamps for Tier 2. Render stacked full-width `OutlinedButton`s below to switch between Tier 1, Tier 2, and Tier 3 (`workout_photos_show_all`).
* In `PREVIEW`: "Foto auswählen" re-enters cascade (Tier 1 if `attachedPhotos.isNotEmpty()`, else Tier 2/3).

### Component 2: Resource Strings (`strings.xml` across all 9 locales)
* Add `share_photos_attached_subtitle`, `share_photos_show_time_window`, `share_photos_show_attached` across `values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`.

### UI Consistency (Rule 23)
* **Reference screen**: `WorkoutTimePhotoPickerSheet.kt` (`AppModalBottomSheet(scrollable = false)`).
* **Reused components**: `TimeFilteredPhotoGridItem` pattern, downsampled bitmap decoding, Material 3 `OutlinedButton`.
* **Theme tokens**: `MaterialTheme.colorScheme.primary`, `surfaceVariant`, `outlineVariant`.
* **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add Strings Across All 9 Locales (`REQ-UI-447.6`, `TST-UI-447.6e`)
* Files: `app/src/main/res/values*/strings.xml` (all 9 locale variants).
* Add `share_photos_attached_subtitle`, `share_photos_show_time_window`, `share_photos_show_attached`.

### Step 2: Implement 3-Tier Cascade in `WorkoutShareBottomSheet.kt` (`REQ-UI-447.6`)
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/WorkoutShareBottomSheet.kt`.
* Implement state machine, permission check/request, Tier 1 & 2 grids, tier switching buttons, and preview re-selection.

### Step 3: Expand Architecture Contract Tests (`TST-UI-447.6d`)
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/WorkoutShareBottomSheetContractTest.kt`.
* Verify 3-tier cascade strings, Rule 73 stacked buttons, and backward compatibility.

### Step 4: Targeted Test Suite & Requirement Governance Verification
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`.
* Run: `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit & contract tests, `TranslationParityTest`, requirement governance check.
* **Rollback**: Clean `git restore` on `feature/ATT-5656` branch.
