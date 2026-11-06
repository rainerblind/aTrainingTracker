# Stage 3: Implementation Plan - ATT-5475: [Feature] Workout Photo Picker: Pre-Filter Photos Taken During Workout Time Window with 'Show All Photos' Button

**Ticket**: [ATT-5475](https://rainerblind.atlassian.net/browse/ATT-5475)  
**Sub-task**: [ATT-5557](https://rainerblind.atlassian.net/browse/ATT-5557) (`[Impl-Plan]`)  
**Parent Epic**: None (*Standalone Feature*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.7`  
**Requirement Mapping**: `REQ-UI-455` (*Workout-Time Pre-Filtered Photo Picker with Show All Photos Fallback Button*)  
**Test Mapping**: `TST-UI-455`  
**Branch**: `feature/ATT-5475`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-06  

---

## 1. Problem Description & Background
Currently, tapping "Add Photo" on a workout launches Android's generic system photo picker (`PickMultipleVisualMedia`), forcing athletes to browse their entire library. REQ-UI-455 introduces `WorkoutTimePhotoPickerSheet` querying `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` for photos taken within the workout's time window ($\pm 5\text{ min}$ tolerance matching `WorkoutPhotoManager.kt:248`), with a prominent Rule 73 stacked button *"Alle Bilder anzeigen"* and automatic fallback on permission denial or 0 matches.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-455` (`REQ-UI-455.1` query, `REQ-UI-455.2` sheet UI, `REQ-UI-455.3` Rule 73 buttons, `REQ-UI-455.4` fallback, `REQ-UI-455.5` 9-locale parity)
* **Test Mapping**: `TST-UI-455` (`TST-UI-455.1` unit, `TST-UI-455.2` contract, `TST-UI-455.3` UI, `TST-UI-455.4` translation, `TST-UI-455.5` clean-room)

---

## 3. System Invariants & Preserved Behavior
1. **Unconditional Fallback**: `PickMultipleVisualMedia` launches automatically if permissions are denied or zero photos match.
2. **Infinite Height Guard**: `WorkoutTimePhotoPickerSheet` sets `scrollable = false` on `AppModalBottomSheet` per Sec 5.8 for `LazyVerticalGrid`.
3. **Rule 73 Button Layout**: Action buttons are stacked full-width at the bottom of sheet content.
4. **Memory & Schema Safety**: Downsampled decoding via `decodeSampledBitmap` avoids OOM; SQLite schema v27 remains invariant.

---

## 4. Proposed Architectural Changes
* **Permissions & Query**: Declare `READ_MEDIA_IMAGES` (API 33+), `READ_MEDIA_VISUAL_USER_SELECTED` (API 34+), and `READ_EXTERNAL_STORAGE` (`maxSdkVersion="32"`). Implement `calculateWorkoutTimeWindow` and `queryWorkoutTimePhotos` in `WorkoutPhotoManager.kt` querying `EXTERNAL_CONTENT_URI` with temporal bounds. Document in `docs/architecture.md` Sec 3.2.
* **Selection Sheet & Photo Strip**: Create `WorkoutTimePhotoPickerSheet` (`AppModalBottomSheet(scrollable = false)`) with 3-column `LazyVerticalGrid` and Rule 73 stacked buttons ("Hinzufügen (<count>)", *"Alle Bilder anzeigen"*, "Abbrechen"). In `WorkoutPhotoStrip.kt`, pass timestamps from `WorkoutSummary.kt` / `WorkoutDetailTabbedContent.kt` and coordinate permissions, time query, and fallback.
* **UI Consistency**: Reference: `FirstJubileeCelebrationDialog.kt` (Rule 73 stacked buttons). Reused: `AppModalBottomSheet`, `decodeSampledBitmap`. Theme tokens: `primary`, `surfaceVariant`, `onSurfaceVariant`, `RoundedCornerShape(8.dp)`. No one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Manifest & Contract Tests**: Add media permissions to `AndroidManifest.xml`, update `ManifestAndBuildConfigContractTest.kt` and `docs/architecture.md`.
2. **Temporal MediaStore Querying**: Add `TimeFilteredPhoto` and `queryWorkoutTimePhotos` in `WorkoutPhotoManager.kt`; unit test bounds filtering, `DATE_ADDED` fallback, and sorting in `WorkoutPhotoManagerTest.kt`.
3. **Selection Sheet & Photo Strip Wiring**: Implement `WorkoutTimePhotoPickerSheet.kt`, update `WorkoutPhotoStrip.kt`, wire timestamps in `WorkoutSummary.kt` and `WorkoutDetailTabbedContent.kt`, create `WorkoutPhotoPickerContractTest.kt`.
4. **9-Language Localization**: Add `workout_photos_*` strings across all 9 locales; verify with `TranslationParityTest`.
5. **Targeted Verification**:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutPhotoManagerTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"
   ```

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted tests during construction, followed by full unit suite and on-device ADB inspection in Stage 5.
* **Rollback**: Clean git revert on `feature/ATT-5475`; no database migrations to roll back.
