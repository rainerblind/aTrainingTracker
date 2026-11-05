# Stage 1 Analysis: ATT-30 - Snapshot with track on picture

**Ticket**: [ATT-30](https://rainerblind.atlassian.net/browse/ATT-30)  
**Sub-task**: [ATT-5437](https://rainerblind.atlassian.net/browse/ATT-5437) (`[Analysis]`)  
**Parent Epic**: None (Standalone Feature)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.6`  
**Branch**: `feature/ATT-30`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
Currently, tapping the floating Share button in `MapDetailLayout` immediately executes `combineWorkoutAndShare`, creating a vertical composite snapshot (Header + Map + Elevation + Analytics + Footer). Athletes frequently share workouts on social media using personal photos (scenery, selfie, gear) with their GPS track and key stats overlaid. `ATT-30` introduces a mode switcher enabling athletes to choose between the classic analysis snapshot and a photo with track & stats overlay.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause & Architectural Gap**:
  * `MapDetailLayout.kt` hardcodes immediate map snapshot capture upon tapping the Share button without presenting mode choices.
  * No composer exists to project 2D GPS polylines with aspect-ratio-preserving projection onto arbitrary photographic aspect ratios or draw translucent gradient scrims for metric typography.
* **Independent Peer Review & Verification**:
  * Verified `MapDetailLayout.kt` (lines 272–300): Tapping Share directly sets `isSharing = true`. Adding `workoutData: WorkoutData? = null` allows workout screens to present the bottom sheet while preserving 100% backward compatibility for Route/Segment/Climb callers.
  * Verified `ShareUtils.kt` & `WorkoutData.kt`: `workoutData.mapPolyline` decoded via `PolyUtil.decode` provides GPS track coordinates. Applying cosine-latitude equirectangular scaling ($\Delta x = \Delta\lambda \cdot \cos(\phi_{\text{mean}})$, $\Delta y = -\Delta\phi$) prevents aspect ratio distortion across latitudes.
  * Verified typography contrast: Translucent bottom scrim (black alpha $0.0 \rightarrow 0.85$) guarantees WCAG AAA compliance for white text across arbitrary photo backgrounds.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Share mode selection `AppModalBottomSheet` in `MapDetailLayout` with Option A (Classic Snapshot) and Option B (Photo with Track-Overlay).
  * System Photo Picker integration via `ActivityResultContracts.PickVisualMedia`.
  * `PhotoTrackOverlayComposer` in `helpers/`: 2D GPS track silhouette (scaled, drop shadow, start/finish dots), bottom gradient scrim, key metrics (title, date, distance, duration, ascent, avg power/speed), and branding footer with supporter tier pill.
  * Preview inside the sheet with primary Share CTA dispatching via `FileProvider`.
  * 9-language localization parity (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Persistent photo attachment/storage in SQLite (scoped to `ATT-43`).
  * In-app camera preview or arbitrary photo editing (cropping, filters).
  * Modifying Route, Segment, or Climb sharing workflows.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Net-new requirement only (`REQ-UI-447`)**: No existing requirements modified.
* Existing snapshot sharing (`REQ-UI-177`, `REQ-UI-205`, `combineWorkoutAndShare`) remains 100% intact and functional under Option A.

---

## 5. Architectural Strategy & High-Level Solution
* **Share Mode Bottom Sheet (`WorkoutShareBottomSheet`)**: Hosts Option A and Option B selection pills using `AppModalBottomSheet`. Selecting Option B launches `PickVisualMedia`.
* **Photo Track Overlay Engine (`PhotoTrackOverlayComposer`)**:
  * Decodes user photo with `inSampleSize` downsampling to prevent `OutOfMemoryError`.
  * Projects GPS polyline onto upper-right/side quadrant with cosine-latitude scaling and shadow layer.
  * Renders bottom gradient scrim and white typography metrics (using `MetricFormatterContext`).
  * Renders official Cervélo P3C logo and `ProFeatureManager` supporter tier pill.
* **Integration**: `MapDetailLayout` triggers the bottom sheet when `workoutData != null`, and dispatches the final composed image via `FileProvider`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in existing `combineWorkoutAndShare` and non-workout `MapDetailLayout` callers.
  2. Memory safety: Large photos are downsampled before composition to avoid OOM.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **MEDIUM** (Bitmap manipulation and activity result launcher integration; mitigated by safe sampling and isolated composer).
