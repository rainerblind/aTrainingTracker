# Stage 2: Requirement & Test Specification - ATT-5760: Dynamic Photo Thumbnail Resolution

**Ticket**: [ATT-5760](https://rainerblind.atlassian.net/browse/ATT-5760)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5760`  
**Requirement Mapping**: `REQ-UI-457` (refining `REQ-UI-414.6`) | **Test Spec ID**: `TST-UI-457`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Requirement Specification (`REQ-UI-457`)

### 1.1 Problem Statement & Rationale
In `WorkoutPhotoStrip.kt` (`PhotoThumbnailCard`), photos were previously decoded with a static downsampling constant `PhotoTrackOverlayComposer.decodeSampledBitmap(context, photo.uri, maxDim = 240)`. When rendered on `LARGE` (full width, ~360–400dp / ~1080px physical) or `MEDIUM` (half width, ~180dp / ~540px physical) responsive tiles, upscaling a 240px bitmap creates severe magnification blur (~4.5x upscaling). The system SHALL dynamically compute `maxDim` based on physical screen pixels and re-decode bitmaps when thumbnail size changes, while bounding resolution to $\le 1200\text{px}$ to prevent OOM.

### 1.2 Functional & Architectural Requirements
1. **Dynamic Resolution Calculation**: `calculatePhotoDecodeMaxDim(dimensions: PhotoItemDimensions, density: Density): Int` SHALL compute downsample target `(maxOf(dimensions.width, dimensions.height).value * density.density).roundToInt()`, clamped to `[256 .. 1200]` pixels (`REQ-UI-457.1`).
2. **Dimension-Keyed State & Coroutine Reloading**: In `PhotoThumbnailCard`, `remember` and `LaunchedEffect` SHALL be keyed on `(photo.filePath, targetMaxDim)` (or `dimensions`), guaranteeing sharp reloads when thumbnail size changes in settings (`REQ-UI-457.2`).
3. **Asynchronous Background IO Execution**: Bitmap decoding SHALL execute asynchronously on `Dispatchers.IO` via `PhotoTrackOverlayComposer.decodeSampledBitmap(context, photo.uri, maxDim = targetMaxDim)`, keeping the UI thread unblocked (`REQ-UI-457.3`).
4. **OOM & Layout Stability**: Clamping to $\le 1200\text{px}$ limits memory to $< 4\text{MB}$ per thumbnail. Layout dimensions from `calculatePhotoItemDimensions` remain invariant (`REQ-UI-457.4`).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1 (Sharp High-Resolution Large Photo)**: *Given* an athlete viewing a photo strip with `PhotoThumbnailSize.LARGE` on a 2.75x–3.0x density device, *When* `PhotoThumbnailCard` renders, *Then* `maxDim` is computed as ~990–1080px, producing a sharp, unblurred thumbnail.
* **AC-2 (Dynamic Reload on Size Change)**: *Given* the athlete switches thumbnail size from `MEDIUM` to `LARGE` in settings, *When* returning to the photo strip, *Then* `LaunchedEffect` detects the dimension change and re-decodes at the higher resolution.
* **AC-3 (OOM Protection Clamping)**: *Given* a high-density tablet with physical dimensions exceeding 1200px, *When* `calculatePhotoDecodeMaxDim` executes, *Then* the target is clamped to 1200px.
* **Invariants**: Decode range $[256 \dots 1200\text{px}]$; background IO decoding; layout aspect ratio unchanged; zero database/settings schema mutations.

---

## 2. Test Specification (`TST-UI-457`)

### Test Case 1: Dynamic Max Dimension Density Scaling & Clamping (`TST-UI-457.1`)
* **Scope**: Unit Test (`PhotoThumbnailSizeTest.kt`)
* **Action**: Verify `calculatePhotoDecodeMaxDim` across `SMALL`, `MEDIUM`, `LARGE` and densities (1.0x, 2.0x, 2.75x, 3.0x), verifying bounds `[256, 1200]`.

### Test Case 2: Dimension-Keyed LaunchedEffect Structural Contract (`TST-UI-457.2`)
* **Scope**: Contract Test (`PhotoThumbnailSizeTest.kt`)
* **Action**: Verify `PhotoThumbnailCard` computes `targetMaxDim` via `calculatePhotoDecodeMaxDim` and keys `LaunchedEffect` on `targetMaxDim` or `dimensions`.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-UI-457.3`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.photos.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Goal**: Verify 100% pass rate across photo aftermath packages.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-457.1` | Unit | `calculatePhotoDecodeMaxDim` | `REQ-UI-457.1`, `REQ-UI-457.4` | Specified |
| `TST-UI-457.2` | Contract | `PhotoThumbnailCard` | `REQ-UI-457.2`, `REQ-UI-457.3` | Specified |
| `TST-UI-457.3` | Regression | Photo Aftermath Suite | `REQ-PRO-001` | Specified |
