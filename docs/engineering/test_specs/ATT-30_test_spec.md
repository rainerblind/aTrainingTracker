# Stage 2: Requirement & Test Specification - ATT-30: Snapshot with track on picture

**Ticket**: [ATT-30](https://rainerblind.atlassian.net/browse/ATT-30)  
**Sub-task**: [ATT-5438](https://rainerblind.atlassian.net/browse/ATT-5438) (`[Req & Test Spec]`)  
**Parent Epic**: None (Standalone Feature)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.6`  
**Requirement Mapping**: `REQ-UI-447` (*Workout Share Mode Selection Bottom Sheet & Photo Track Overlay Snapshot Composer*)  
**Test Spec ID**: `TST-UI-447`  
**Branch**: `feature/ATT-30`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-UI-447)

### 1.1 Problem Statement & Rationale
Athletes sharing activities on social channels prefer overlaying their GPS track and key stats on personal photos (scenery/selfie) alongside the classic vertical summary. REQ-UI-447 introduces a share mode bottom sheet in `MapDetailLayout` and a memory-safe photo track overlay composer.

### 1.2 Functional & Architectural Requirements
* **Share Mode Bottom Sheet (`REQ-UI-447.1`)**: Tapping the floating Share button in `MapDetailLayout` when `workoutData != null` SHALL open `WorkoutShareBottomSheet` (using `AppModalBottomSheet`) offering *Option A (Karten- & Analyse-Snapshot)* and *Option B (Foto mit Track-Overlay)*. Option A executes `combineWorkoutAndShare`. Option B launches `ActivityResultContracts.PickVisualMedia`.
* **2D GPS Track Projection (`REQ-UI-447.2`)**: `PhotoTrackOverlayComposer` SHALL project decoded LatLng points onto the photo using cosine-latitude equirectangular scaling ($\Delta x = \Delta\lambda \cdot \cos(\phi_{\text{mean}})$, $\Delta y = -\Delta\phi$) with drop shadow and start/finish markers, preserving aspect ratio across all latitudes.
* **Bottom Gradient Scrim & Typography (`REQ-UI-447.3`)**: A dark gradient scrim ($0.0 \rightarrow 0.85$ alpha) across the lower 25–30% of the photo SHALL host title, date, distance, duration, ascent, and avg speed/power in white typography with WCAG AAA contrast ($\ge 7.0:1$).
* **Official Branding Watermark (`REQ-UI-447.4`)**: The footer bar SHALL render the Cervélo P3C logo, "Tracked with aTrainingTracker", and supporter tier pill per `REQ-UI-445.5`.
* **Preview, Share Dispatch & 9-Locale Parity (`REQ-UI-447.5`)**: Composed preview inside the sheet with a primary Share CTA dispatching via `FileProvider`. 100% localization parity across all 9 application locales.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Mode Selection)**: *Given* a workout in `MapDetailLayout`, *when* tapping Share, *then* `WorkoutShareBottomSheet` displays Option A and Option B.
* **AC-2 (Option A)**: *Given* Option A is selected, *when* triggered, *then* `combineWorkoutAndShare` executes as before.
* **AC-3 (Option B & Overlay)**: *Given* Option B is selected and a photo picked, *when* composed, *then* track silhouette, metrics scrim, and branding watermark render with WCAG AAA contrast.
* **AC-4 (Share Dispatch)**: *Given* preview is approved, *when* tapping Share, *then* image is shared via system send intent.

### 1.4 System Invariants
* Zero regression to existing `combineWorkoutAndShare` callers (`RouteOnMapScreen`, `SegmentOnMapScreen`, or `workoutData == null`).
* Safe bitmap decoding with `inSampleSize` downsampling preventing `OutOfMemoryError`.
* Parent ticket Human Decision Gate strictly preserved.

---

## 2. Test Specification (TST-UI-447)

### Test Case 1: `testEquirectangularProjection_preservesTrueAspectRatio` (`TST-UI-447.1`)
* **Scope**: Unit Test (`app/src/test/.../helpers/PhotoTrackOverlayComposerTest.kt`)
* **Precondition**: Track polyline spanning high latitudes (e.g. 60°N).
* **Action**: Project polyline coordinates to canvas bounds.
* **Expected Result**: Horizontal width scales by $\cos(\text{lat})$ preventing north-south elongation.

### Test Case 2: `testMetricsScrimContrast_wcagAaaCompliant` (`TST-UI-447.2`)
* **Scope**: Unit Test (`app/src/test/.../helpers/PhotoTrackOverlayComposerTest.kt`)
* **Precondition**: White and dark photo backgrounds.
* **Action**: Evaluate luminance contrast of white metric text against gradient scrim.
* **Expected Result**: Contrast ratio $\ge 7.0:1$ across all text bounds.

### Test Case 3: `testShareModeBottomSheet_delegatesOptionACorrectly` (`TST-UI-447.3`)
* **Scope**: UI Contract Test (`app/src/test/.../ui/map/WorkoutShareBottomSheetContractTest.kt`)
* **Action**: Simulate Option A and Option B user taps.
* **Expected Result**: Option A triggers `combineWorkoutAndShare`; Option B invokes photo picker launcher; non-workout callers bypass sheet.

### Test Case 4: 9-Language Localization Parity (`TST-UI-447.4`)
* **Scope**: Integration Test (`TranslationParityTest.kt`)
* **Expected Result**: 100% parity across `values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt` for all new share strings.

### Test Case 5: Clean-Room Regression Suite (`TST-UI-447.5`)
* **Scope**: Targeted Test Suite: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.helpers.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-447.1` | Unit | `PhotoTrackOverlayComposer.projectTrack` | `REQ-UI-447.2` | Specified |
| `TST-UI-447.2` | Unit | `PhotoTrackOverlayComposer.composeOverlay` | `REQ-UI-447.3`, `REQ-UI-447.4` | Specified |
| `TST-UI-447.3` | Contract | `WorkoutShareBottomSheet` | `REQ-UI-447.1` | Specified |
| `TST-UI-447.4` | Parity | `TranslationParityTest` | `REQ-UI-447.5`, `REQ-UI-106` | Specified |
| `TST-UI-447.5` | Regress | Targeted unit test suite | `REQ-PRO-001` | Specified |
