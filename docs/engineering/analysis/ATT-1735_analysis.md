# Stage 1 Analysis: ATT-1735 - [LiveSegment] Harmonize background color across LiveSegment popup and header

**Ticket**: [ATT-1735](https://atrainingtracker.atlassian.net/browse/ATT-1735)  
**Sub-task**: [ATT-1765](https://atrainingtracker.atlassian.net/browse/ATT-1765) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Branch**: `feature/ATT-1735`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

During on-device testing and the sprint review of Sprint `2026-40.5` (evaluating ticket [ATT-1644](https://atrainingtracker.atlassian.net/browse/ATT-1644)), the user observed an unappealing visual contrast in the tracking cockpit view:
> *"The popup background is relatively dark while the fields/sections of the segment header are stark white. The entire popup must share a single, unified background color."*

In the active tracking cockpit ([SensorGridScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt)), the LiveSegment popup ([LIveSegmentSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt)) appears as a bottom sheet overlaying the map and sensor grid. Because different layers within the popup hierarchy independently defined or defaulted their background colors (e.g. `BottomSheetScaffold` defaulting to `surfaceContainerLow` while inner headers explicitly applied `MaterialTheme.colorScheme.surface`), visual seams and contrasting color patches appeared across the drag handle, segment header, live metrics, and elevation profile.

---

## 2. Root Cause Analysis & Architectural Investigation

### 2.1 Color Mismatch Across Component Hierarchy
1. **[SensorGridScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt#L119-L135)**:
   - `BottomSheetScaffold` did not specify `sheetContainerColor`, defaulting to `BottomSheetDefaults.ContainerColor` (`MaterialTheme.colorScheme.surfaceContainerLow`).
   - The child container `Box(modifier = Modifier.fillMaxWidth().sheetContour())` did not apply an explicit background color.
2. **[MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt#L93-L118)**:
   - The root `Column` had no background color specified.
   - `MinimumDragHandle()` was positioned directly inside `Column`, showing the outer `BottomSheetScaffold` container background.
   - The slotted header was enclosed in a nested `Surface` with `color = MaterialTheme.colorScheme.surface` and `shape = BottomSheetDesign.SheetShape`.
   - The elevation profile was enclosed in another nested `Surface` with `color = MaterialTheme.colorScheme.surface`.
3. **[LIveSegmentSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt#L47-L50)**:
   - The `header` slot applied an explicit `background(MaterialTheme.colorScheme.surface)`.
   - In Light mode, `surface` evaluates to pure white (`#FFFFFF`), while in Dark mode without pure AMOLED, it evaluates to `#16161A`, contrasting against outer container colors.
4. **[SegmentHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentHeader.kt)**:
   - `SegmentHeader` used a transparent surface, but nested within the white/contrasting surface of `LIveSegmentSheet.kt` and `MapDetailLayout.kt`.

### 2.2 Architectural Defect Summary
The popup was not rendered as a single cohesive container surface. Instead, multiple fragmented layers each established independent backgrounds, resulting in a dark drag handle zone, a stark white inner header block, and a separate elevation profile block.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Unify the background color across the entire LiveSegment popup in `SensorGridScreen.kt`, `LIveSegmentSheet.kt`, `MapDetailLayout.kt`, and `SegmentHeader.kt`.
  * Ensure `BottomSheetScaffold.sheetContainerColor` matches the unified container color (`MaterialTheme.colorScheme.surface`).
  * Ensure the root container of `LiveSegmentSheet` / `MapDetailLayout` continuously applies `MaterialTheme.colorScheme.surface` with `shape = BottomSheetDesign.SheetShape`.
  * Eliminate disparate inner background patches, ensuring `MinimumDragHandle`, `SegmentHeader`, `SegmentLiveDetails`, and `ElevationProfile` share the identical background color.
  * Guarantee high-contrast text and icon legibility across Light Mode, Dark Mode, and AMOLED mode.
  * Update living specifications `docs/requirements.md` (`REQ-UI-196`) and `docs/tests.md` (`TST-UI-150`).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying sensor grid tile backgrounds or sensor field colors.
  * Changing `SegmentHeader` metrics, category badges, or PR layout.
  * Modifying full-screen detail screen map rendering or status bar styling.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**:
  - `REQ-UI-196` (*LiveSegment & Bottom Sheets: Refined Subtle Drag Handle & Harmonized Popup Spacing*), targeting `LIveSegmentSheet.kt`, `MapDetailLayout.kt`, `BottomSheetDesign.kt`, and `SensorGridScreen.kt`.
* **Historical Origin & Commit Trace**:
  - Ticket `ATT-1644` (Commit `e7ae455c`, Sprint `2026-40.5`) and ticket `ATT-1588` (Commit `69c84e1b`, Sprint `2026-40.4`).
* **Root Reason for Existing Formulation**:
  - `ATT-1588` added `sheetContour()` and `BottomSheetDesign.SheetShape` to give bottom sheets distinct boundaries.
  - `ATT-1644` refined the drag handle dimensions (32dp x 3dp) and harmonized vertical spacing, but left the container color uncoordinated between `BottomSheetScaffold` and the nested `Surface` components.
* **Preservation of Core Invariants**:
  - Drag handle dimensions (32dp x 3dp), subtle `outlineVariant` color, and 8dp/4dp padding remain 100% intact.
  - Top contour clipping (`sheetContour()`) and 1dp outline border stroke remain preserved.
  - Full-screen detail view layouts continue to function with zero visual regression.

---

## 5. Architectural Strategy & High-Level Solution

1. **Unify `BottomSheetScaffold` Container in `SensorGridScreen.kt`**:
   - Explicitly configure `sheetContainerColor = MaterialTheme.colorScheme.surface`.
   - Ensure the sheet content `Box` applies `.background(MaterialTheme.colorScheme.surface, shape = BottomSheetDesign.SheetShape)`.
2. **Unify `MapDetailLayout.kt` Sheet Hierarchy**:
   - In `MapDetailLayout.kt`, when `!useStatusBarsPadding` (bottom sheet mode), apply `background(MaterialTheme.colorScheme.surface)` across the entire `Column`.
   - Remove disjoint inner background differences so `MinimumDragHandle`, `header`, and `ElevationProfile` seamlessly share the same surface.
3. **Harmonize `LIveSegmentSheet.kt`**:
   - Maintain `MaterialTheme.colorScheme.surface` as the single unified surface token across header and content without inner color disparities.
4. **Verification**:
   - Add unit tests verifying that `SensorGridScreen.kt`, `LIveSegmentSheet.kt`, and `MapDetailLayout.kt` consistently utilize `MaterialTheme.colorScheme.surface` for bottom sheet containers.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Material 3 semantic color tokens ensure 100% adaptive parity across Light, Dark, and AMOLED modes.
  2. Text and icon contrast ratios conform to WCAG 2.1 AA standards.
  3. No SQLite, database, or threading impact.
* **Risk Rating**: **LOW**
  - Pure Compose declarative styling harmonization without state machine or domain changes.
