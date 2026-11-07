# Stage 3: Implementation Plan - ATT-5760: Dynamic Photo Thumbnail Resolution

**Ticket**: [ATT-5760](https://rainerblind.atlassian.net/browse/ATT-5760)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5760`  
**Requirement Mapping**: `REQ-UI-457` (`REQ-UI-457.1`–`REQ-UI-457.4`) | **Test Mapping**: `TST-UI-457` (`TST-UI-457.1`–`TST-UI-457.3`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Description & Background
In `WorkoutPhotoStrip.kt`, `PhotoThumbnailCard` previously decoded photo bitmaps using a static downsample target `PhotoTrackOverlayComposer.decodeSampledBitmap(context, photo.uri, maxDim = 240)`. When rendered on `LARGE` (full width ~1080px physical) or `MEDIUM` (half width ~540px physical) tiles, this 240px bitmap produced severe upscaling blur (~4.5x magnification). This plan dynamically calculates `maxDim` based on physical screen pixels and re-decodes bitmaps when thumbnail size changes, while bounding resolution to $\le 1200\text{px}$ to prevent OOM.

---

## 2. Invariants & Preserved Behavior
1. **OOM & Memory Bounds (`REQ-UI-457.4`)**: Target max dimension is strictly clamped to $[256 \dots 1200\text{px}]$, ensuring memory consumption remains $< 4\text{MB}$ per thumbnail.
2. **Background IO Decoding (`REQ-UI-457.3`)**: Bitmap decoding remains strictly asynchronous on `Dispatchers.IO` via `PhotoTrackOverlayComposer.decodeSampledBitmap`.
3. **Layout Stability (`REQ-UI-414.6`)**: `calculatePhotoItemDimensions` layout contracts and aspect ratios remain unchanged.
4. **Reactive Reloading on Size Switch (`REQ-UI-457.2`)**: `remember` and `LaunchedEffect` key on `(photo.filePath, targetMaxDim)`, reloading at higher resolution whenever thumbnail size changes.

---

## 3. UI Consistency & Architectural Strategy
* **Reference Screen**: `WorkoutPhotoStrip.kt` in `ui/aftermath/photos/` and `WorkoutPhotoPreviewBottomSheet.kt`.
* **Theme & Performance Tokens**: Unchanged M3 card surfaces, progress spinner, and asynchronous image state management.
* **New One-Off Styles**: None. Pure bitmap decode parameterization and state key binding.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement Dynamic Max Dimension Helper (`REQ-UI-457.1`, `REQ-UI-457.4`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/photos/WorkoutPhotoStrip.kt`
* **Changes**: Add top-level `calculatePhotoDecodeMaxDim(dimensions: PhotoItemDimensions, density: Density): Int` calculating `(maxOf(dimensions.width, dimensions.height).value * density.density).roundToInt().coerceIn(256, 1200)`.

### Step 2: Update `PhotoThumbnailCard` State & LaunchedEffect Keying (`REQ-UI-457.2`, `REQ-UI-457.3`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/photos/WorkoutPhotoStrip.kt`
* **Changes**:
  - Read `LocalDensity.current` and compute `targetMaxDim = remember(dimensions, density) { calculatePhotoDecodeMaxDim(dimensions, density) }`.
  - Update `bitmap` and `isLoading` remember keys to `(photo.filePath, targetMaxDim)`.
  - Update `LaunchedEffect(photo.filePath, targetMaxDim)` to pass `maxDim = targetMaxDim` to `decodeSampledBitmap`.

### Step 3: Extend Unit & Contract Tests (`TST-UI-457.1`, `TST-UI-457.2`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/photos/PhotoThumbnailSizeTest.kt`
* **Changes**:
  - Add unit test verifying `calculatePhotoDecodeMaxDim` across densities (1.0f, 2.0f, 2.75f, 3.0f) and size presets.
  - Add contract test verifying `PhotoThumbnailCard` computes `targetMaxDim` and keys `LaunchedEffect(photo.filePath, targetMaxDim)`.

---

## 5. Verification Commands (`TST-UI-457.3`)
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.photos.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* `python3 tools/verify_requirement_governance.py`
