# Stage 3: Implementation Plan - ATT-30: Snapshot with track on picture

**Ticket**: [ATT-30](https://rainerblind.atlassian.net/browse/ATT-30)  
**Sub-task**: [ATT-5439](https://rainerblind.atlassian.net/browse/ATT-5439) (`[Impl-Plan]`)  
**Parent Epic**: None (Standalone Feature)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.6`  
**Requirement Mapping**: `REQ-UI-447`  
**Test Mapping**: `TST-UI-447`  
**Branch**: `feature/ATT-30`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Problem Description & Background
Athletes want to share workouts with a personal photo overlaid with their 2D GPS track silhouette, key metrics, and branding footer (Strava/Garmin style). `ATT-30` adds a share mode selection bottom sheet in `MapDetailLayout` and a memory-safe photo overlay composer, preserving the classic composite snapshot.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-447` (*Workout Share Mode Selection Bottom Sheet & Photo Track Overlay Snapshot Composer*)
* **Test Mapping**: `TST-UI-447` (*Workout Share Mode Selection & Photo Track Overlay Verification Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Regressions**: Existing `combineWorkoutAndShare` and non-workout `MapDetailLayout` callers (Routes, Segments, Climbs) remain 100% intact.
2. **Memory Safety**: Photographic bitmaps are safely sampled (`inSampleSize`) before composition to prevent `OutOfMemoryError`.
3. **Contrast Compliance**: Scrim gradient ensures WCAG AAA contrast ($\ge 7.0:1$) for white typography across bright and dark photos.
4. **Human Decision Gate**: Parent ticket completion reserved for human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes
* **`PhotoTrackOverlayComposer.kt`** (`helpers/`): Decodes photo with safe downsampling, projects GPS polyline using cosine-latitude equirectangular scaling ($\Delta x = \Delta\lambda \cdot \cos(\phi_{\text{mean}})$, $\Delta y = -\Delta\phi$) with drop shadow and start/finish dots, draws translucent gradient scrim ($0.0 \rightarrow 0.85$ alpha), renders white metrics typography and branding footer with supporter tier pill.
* **`WorkoutShareBottomSheet.kt`** (`ui/map/`): `AppModalBottomSheet` presenting Option A (Karten- & Analyse-Snapshot) and Option B (Foto mit Track-Overlay). Supports `PickVisualMedia` and accepts optional `attachedPhotos: List<Uri>` for future `ATT-43` integration. Displays interactive preview with Share CTA.
* **`MapDetailLayout.kt` & Callers**: Exposes optional `workoutData: WorkoutData? = null`. When present, tapping Share opens `WorkoutShareBottomSheet`; when null, triggers legacy snapshot. Wired in `TrackOnMapScreen.kt` and `WorkoutDetailTabbedContent.kt`.
* **UI Consistency**: Reuses `AppModalBottomSheet` and `BottomSheetDesign`. Uses `MaterialTheme.colorScheme` and `TTAlpha`. No one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Localization Parity**: Add share strings (`share_workout_title`, `share_option_map_snapshot`, `share_option_photo_track`, `share_choose_photo`, `share_preview_title`, `share_cta_button`, `share_error_no_gps`) across all 9 locales.
2. **Step 2: PhotoTrackOverlayComposer**: Create `PhotoTrackOverlayComposer.kt` with cosine-latitude projection, scrim, metric formatting, branding footer, and FileProvider dispatch.
3. **Step 3: WorkoutShareBottomSheet**: Create `WorkoutShareBottomSheet.kt` with Option A/B cards, photo picker launcher, and composed image preview with Share CTA.
4. **Step 4: MapDetailLayout & Caller Wiring**: Wire `workoutData` and bottom sheet into `MapDetailLayout.kt`, `TrackOnMapScreen.kt`, and `WorkoutDetailTabbedContent.kt`.
5. **Step 5: Unit & Contract Tests**: Implement `PhotoTrackOverlayComposerTest.kt` and `WorkoutShareBottomSheetContractTest.kt`. Verify with `TranslationParityTest` and targeted unit test suite.

---

## 6. Verification & Rollback Plan
* **Targeted Verification**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.helpers.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.build.TranslationParityTest"`
* **Rollback**: Atomic squash-commit on `feature/ATT-30` allows clean rollback via git.
