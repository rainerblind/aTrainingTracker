# Stage 2: Requirement & Test Specification - ATT-5475: [Feature] Workout Photo Picker: Pre-Filter Photos Taken During Workout Time Window with 'Show All Photos' Button

**Ticket**: [ATT-5475](https://rainerblind.atlassian.net/browse/ATT-5475)  
**Parent Epic**: None (*Standalone Feature*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.7`  
**Requirement Mapping**: `REQ-UI-455` (*Workout-Time Pre-Filtered Photo Picker with Show All Photos Fallback Button*)  
**Test Spec ID**: `TST-UI-455`  
**Branch**: `feature/ATT-5475`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-11-06  

---

## 1. Requirement Specification (REQ-UI-455)

### 1.1 Problem Statement & Rationale
Currently, clicking "Add Photo" on a completed workout launches Android's generic system photo picker (`PickMultipleVisualMedia`), requiring athletes to sift through their entire photo gallery. REQ-UI-455 introduces an in-app bottom sheet pre-filtering photos taken chronologically during the workout time window (`[workoutStartMs - 5m .. workoutEndMs + 5m]`), while preserving the full system library via a prominent fallback button (Sprint 2026-45.6 user observation, ATT-43).

### 1.2 Functional & Architectural Requirements
1. **Pre-Filtered Photo Query (`REQ-UI-455.1`)**: In `WorkoutPhotoManager.kt`, implement `queryWorkoutTimePhotos(context, startMs, endMs, toleranceMs = 300_000L)` querying `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` with temporal bounds on `DATE_TAKEN` (fallback `DATE_ADDED * 1000L`).
2. **`WorkoutTimePhotoPickerSheet` UI (`REQ-UI-455.2`)**: Composable hosted in `AppModalBottomSheet(scrollable = false)` per Section 5.8, rendering a 3-column `LazyVerticalGrid` with downsampled thumbnail cards, formatted capture timestamps (`HH:mm`), and multi-select checkboxes for 1-tap attachment.
3. **Stacked Full-Width Action Buttons (`REQ-UI-455.3`)**: Adhere to `Rule 73`:
   - Primary CTA: `Button(Modifier.fillMaxWidth())` "Hinzufügen (<count>)" / "Add (<count>)".
   - Secondary CTA: Prominent `OutlinedButton(Modifier.fillMaxWidth())` *"Alle Bilder anzeigen"* launching `PickMultipleVisualMedia`.
   - Dismiss CTA: `TextButton(Modifier.fillMaxWidth())` "Abbrechen" in `AppModalBottomSheet` action slot.
4. **Automatic Fallback on Permission Denial or 0 Matches (`REQ-UI-455.4`)**: Check `READ_MEDIA_IMAGES` / `READ_MEDIA_VISUAL_USER_SELECTED` / `READ_EXTERNAL_STORAGE`. If denied or if 0 photos match the workout time window, automatically fall back to `PickMultipleVisualMedia`.
5. **9-Language Localization (`REQ-UI-455.5`)**: Localize `workout_photos_time_picker_title`, `workout_photos_show_all`, `workout_photos_add_count`, and `workout_photos_no_time_photos` across all 9 locales.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Matching Photos in Window)**:
  * *Given* a workout and granted media permissions,
  * *When* the athlete taps "Add Photos" and photos exist in the workout time window ($\pm 5\text{ min}$),
  * *Then* `WorkoutTimePhotoPickerSheet` opens showing matching photos with timestamps and selection checkboxes.
* **Criterion 2 (Selection & Batch Attachment)**:
  * *Given* $K \ge 1$ selected photos,
  * *When* clicking "Hinzufügen ($K$)",
  * *Then* all $K$ photos are attached via `WorkoutPhotoManager.addPhoto` and the sheet closes.
* **Criterion 3 (Show All Photos Button)**:
  * *Given* `WorkoutTimePhotoPickerSheet` is displayed,
  * *When* clicking *"Alle Bilder anzeigen"*,
  * *Then* the sheet dismisses and system `PickMultipleVisualMedia` launches.
* **Criterion 4 (Automatic Fallback)**:
  * *Given* media permission is denied or 0 matching photos are found,
  * *When* tapping "Add Photos",
  * *Then* system `PickMultipleVisualMedia` launches automatically.

---

## 2. Test Specification (TST-UI-455)

### Test Case 1: `WorkoutPhotoManagerTest` (`TST-UI-455.1`)
* **Scope**: MediaStore temporal query filtering and fallback logic for `DATE_TAKEN` / `DATE_ADDED`.
* **Expected Result**: Photos within `[startMs - 5m .. endMs + 5m]` returned in chronological order; out-of-window photos excluded.

### Test Case 2: `WorkoutPhotoPickerContractTest` (`TST-UI-455.2`)
* **Scope**: Manifest declarations and fallback decision logic on permission denial or empty result.
* **Expected Result**: Seamless dispatch to `PickMultipleVisualMedia`.

### Test Case 3: `WorkoutTimePhotoPickerSheetTest` (`TST-UI-455.3`)
* **Scope**: UI layout verification with `AppModalBottomSheet(scrollable = false)` and `Rule 73` stacked buttons.
* **Expected Result**: Multi-selection state management, downsampled thumbnail loading, and CTA callbacks.

### Test Case 4: 9-Language Localization Parity (`TST-UI-455.4`)
* **Scope**: `TranslationParityTest` verifying 100% string coverage across 9 locales without decorative emojis.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-455.1` | Unit | `WorkoutPhotoManager.queryWorkoutTimePhotos` | `REQ-UI-455.1` | Specified |
| `TST-UI-455.2` | Contract | `WorkoutPhotoStrip` permission/fallback | `REQ-UI-455.4`, `REQ-UI-455.5` | Specified |
| `TST-UI-455.3` | Compose | `WorkoutTimePhotoPickerSheet` | `REQ-UI-455.2`, `REQ-UI-455.3` | Specified |
| `TST-UI-455.4` | Localization | `TranslationParityTest` | `REQ-UI-455.5` | Specified |
| `TST-UI-455.5` | Governance | `verify_requirement_governance.py` | Core Governance | Specified |
