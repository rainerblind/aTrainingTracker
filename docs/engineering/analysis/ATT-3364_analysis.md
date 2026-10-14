# Stage 1 Analysis: ATT-3364 - Harmonize live segment and live climb bottom sheets under unified sheet layout

**Ticket**: [ATT-3364](https://rainerblind.atlassian.net/browse/ATT-3364)  
**Sub-task**: [ATT-3547](https://rainerblind.atlassian.net/browse/ATT-3547) (`[Analysis]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Branch**: `feature/ATT-3364`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Problem Statement & Motivation

During live activity recording in the cockpit, two dynamic entities present real-time tracking feedback via bottom sheets anchored above the navigation bar:
1. **Strava Live Segments** (`LiveSegment`): Notifies the athlete of approaching segments, live time delta against PR/KOM, and distance remaining.
2. **Live Climbs / ClimbPro** (`LiveClimbData`): Notifies the athlete of approaching ascents, remaining distance to summit, remaining vertical elevation gain, and current instantaneous gradient.

However, historical development across different sprints caused their internal implementation and cockpit scaffold mounting to diverge into two parallel, duplicated code paths:

1. **Divergent Architectural Implementations**:
   - `LIveSegmentSheet.kt` (with a casing typo in the filename) acts as a lightweight adapter delegating to `MapDetailLayout.kt` (passing `showMap = false`, `showZoomControls = false`), nesting `SegmentHeader` and `SegmentLiveDetails` into its header slot, and relying on `MapDetailLayout`'s internal `ElevationProfile`.
   - `LiveClimbSheet.kt` implements a parallel 400-line layout from scratch using a custom `Column` with custom icon/title rows, badges, dividers, live telemetry HUD, and its own direct instantiation of `ElevationProfile`.

2. **Duplicated Mounting & Scaffold Container Logic in `SensorGridScreen.kt`**:
   - Lines 284–325 of `SensorGridScreen.kt` mount both sheets inside duplicate conditional `Box` wrappers with identical `.fillMaxWidth().wrapContentHeight().sheetContour().background(MaterialTheme.colorScheme.surface, shape = BottomSheetDesign.SheetShape)` modifier chains and identical `clickable { expand() }` coroutine logic for expanding from partially expanded state.

3. **Casing Typo in Git Tracking**:
   - The file is tracked in git as `LIveSegmentSheet.kt` (capital `I`), causing capitalization inconsistencies across imports and test fixtures.

This violates **Rule 36 (Single Source of Truth & DRY — Shared Code Over Duplication)**:
- Enhancements to tracking bottom sheets (e.g. gesture physics, contour styling, peek height calculations, elevation scrubber animations) must be duplicated across two entity implementations.
- Visual inconsistencies in padding, divider thickness, typography, and peak heights emerge over time.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of the git commit history and file structure reveals why this duplication arose:

1. **Commit Archaeology**:
   - `LIveSegmentSheet.kt` was authored in Sprint 2026-40.1 (`ATT-1644` / `ATT-1735`) when Compose bottom sheet scaffolding was first adapted to show segment elevations. The author reused the full-screen `MapDetailLayout` with `showMap = false` because `MapDetailLayout` already managed elevation profiles and scrub state.
   - `LiveClimbSheet.kt` was introduced in Sprint 2026-40.14 (`ATT-1281` / `REQ-MAP-027`). The author did not reuse `MapDetailLayout` because `MapDetailLayout` carried heavy screen-level map machinery (pan/zoom focus, drag splitters, snapshot layers, status bar padding), and instead assembled a lightweight `Column` embedding `ElevationProfile` directly.
   - When mounting `LiveClimbSheet` in `SensorGridScreen.kt`, the developer copied the existing `Box` wrapper from `LiveSegmentSheet` rather than establishing a unified sheet container.

2. **Structural Comparison**:

| Dimension | `LiveSegmentSheet` | `LiveClimbSheet` |
| :--- | :--- | :--- |
| **Top Container** | `MapDetailLayout` (with `showMap = false`) | `Column` with `16.dp` horizontal, `8.dp` vertical padding |
| **Surface Styling** | Handled in `SensorGridScreen` `Box` + `header` Column | Direct `MaterialTheme.colorScheme.surface` + `Box` in `SensorGridScreen` |
| **Divider** | `HorizontalDivider(0.5.dp, outlineVariant alpha 0.5f)` with `16.dp` padding | `HorizontalDivider(0.5.dp, outlineVariant alpha 0.5f)` with `4.dp` vertical padding |
| **Elevation Profile** | Delegated through `MapDetailLayout` | Direct `ElevationProfile(normalizedPoints, currentDistance, ...)` |
| **Scaffold Mounting** | `SensorGridScreen.kt:285–304` | `SensorGridScreen.kt:305–325` (identical `Box` wrapper) |

3. **Deficiency Summary**:
   - `SensorGridScreen.kt` has 40 lines of copy-pasted `Box` scaffolding for sheet mounting.
   - `LIveSegmentSheet.kt` has a filename casing typo (`LI` instead of `Li`).
   - Both sheets represent the same conceptual domain: a live tracking bottom sheet displaying entity header, status/category badge, divider, telemetry HUD, and an elevation profile with rider position pin.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. *Rename File*: Rename `LIveSegmentSheet.kt` to `LiveSegmentSheet.kt` using `git mv` to enforce clean PascalCase, updating all imports and test reflections.
  2. *Extract Unified Sheet Scaffolding in Cockpit (`SensorGridScreen.kt`)*:
     - Introduce `LiveTrackingSheetContainer` (or unified container composable) that encapsulates `.fillMaxWidth().wrapContentHeight().sheetContour().background(...)` and `clickable { expand() }` in a single reusable component.
     - Replace the duplicate conditional `Box` blocks in `SensorGridScreen.kt` with a single unified sheet slot.
  3. *Harmonize Sheet Layout Architecture*:
     - Provide a shared, unified scaffolding pattern for live tracking sheets (`LiveTrackingSheetScaffold` or shared container components) that standardizes:
       - Unified surface container background and shape (`BottomSheetDesign.SheetShape`).
       - Standardized header row layout and spacing.
       - Canonical 0.5dp `HorizontalDivider` with consistent horizontal margins.
       - Standardized live telemetry HUD row layout.
       - Canonical `ElevationProfile` embedding with live rider position pin.
  4. *Preserve Contract & Layout Tests*:
     - Update `LiveSegmentSheetContractTest.kt`, `LiveSegmentSheetLayoutTest.kt`, and `LiveClimbSheetLayoutTest.kt` to pass 100% against the harmonized architecture.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying live segment detection logic (`LiveSegmentsRepository.kt`) or climb detection algorithms (`ClimbDetector.kt`).
  - Altering `MapDetailLayout.kt` when used for full-screen route or workout aftermath details.
  - Redesigning color palettes or badges (`ClimbCategoryChip`, `SegmentHeader`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement IDs & Targets**:
  - `REQ-MAP-027` (*Persistent Climbs Database & Live ClimbPro Cockpit Sheet*, Section 4: *Live Climb Cockpit Sheet UI*).
  - `REQ-UI-196` (*Live Segment Bottom Sheet Design & Surface Background*).
  - `REQ-UI-270` (*Clamped Bottom Sheet Height to Elevation Profile Content Height*).
  - `REQ-UI-331` / `REQ-UI-340` (*Live Segment Dynamic Position Marker & Type-Safe Finish Evaluation*).
* **Historical Origin & Commit Trace**:
  - `REQ-UI-196` / `REQ-UI-270`: Sprints 2026-40.1 to 2026-40.16 (`ATT-1644`, `ATT-2231`).
  - `REQ-MAP-027`: Sprint 2026-40.14 (`ATT-1281`, commit `ceaf4070`).
* **Root Reason for Existing Formulation**:
  - Live Segments was created first as an extension of `MapDetailLayout`. When Live Climbs was added, it was created independently with direct `ElevationProfile` usage to avoid inheriting irrelevant map features.
* **Preservation of Core Invariants**:
  1. *Visual Precedence Invariant*: When both a segment and climb are active, `showLiveSegments && activeSegment != null` takes precedence over `activeLiveClimb`, exactly as required by `REQ-MAP-027` Section 4.
  2. *Elevation Marker Invariant*: The elevation profile pin must accurately display the rider's progress along the segment/climb (`externalScrubDistance` / `currentDistance`).
  3. *Wrap-Content & Peek Height Invariant*: The sheet must respect `BottomSheetDesign.PeekHeightLiveSegment` without uncontrolled full-screen viewport expansion (`REQ-UI-270`).
  4. *Clean-Room Test Pass Rate*: 100% of unit and layout tests must pass.

---

## 5. Architectural Strategy & High-Level Solution

### Component 1: Filename Normalization
- Execute `git mv app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LiveSegmentSheet.kt`.
- Update imports across all consumers and reflection references in `LiveSegmentSheetLayoutTest.kt` (`LiveSegmentSheetKt`).

### Component 2: Unified Sheet Container in `SensorGridScreen.kt`
- Create `LiveTrackingSheetContainer`:
  ```kotlin
  @Composable
  fun LiveTrackingSheetContainer(
      onExpand: () -> Unit,
      isPartiallyExpanded: Boolean,
      modifier: Modifier = Modifier,
      content: @Composable () -> Unit
  ) {
      Box(
          modifier = modifier
              .fillMaxWidth()
              .wrapContentHeight()
              .sheetContour()
              .background(MaterialTheme.colorScheme.surface, shape = BottomSheetDesign.SheetShape)
              .then(
                  if (isPartiallyExpanded) {
                      Modifier.clickable(onClick = onExpand)
                  } else Modifier
              )
      ) {
          content()
      }
  }
  ```
- In `SensorGridScreen.kt:284-325`, replace the duplicate `Box` blocks with a single `LiveTrackingSheetContainer` wrapping either `LiveSegmentSheet` or `LiveClimbSheet`.

### Component 3: Harmonized Entity Sheet Scaffolding
- Extract shared structure into `LiveTrackingSheetScaffold`:
  - Uniform padding (`16.dp` horizontal, `4–8.dp` vertical).
  - Canonical 0.5dp `HorizontalDivider` with standard opacity (`TTAlpha.Medium`).
  - Slots for header row, secondary metadata/counter, live telemetry HUD, and elevation profile.
- Both `LiveSegmentSheet` and `LiveClimbSheet` compose cleanly through this unified layout, eliminating divergence in padding, margins, and elevation profile rendering.

---

## 6. Risk Assessment & Verification Plan

* **Risk 1: Git case-insensitivity on macOS / Windows developers**:
  - Linux git host cleanly tracks `git mv LIveSegmentSheet.kt LiveSegmentSheet.kt`. Tested and verified via `git status`.
* **Risk 2: Layout & Contract Test Breakage**:
  - `LiveSegmentSheetContractTest.kt` and `LiveSegmentSheetLayoutTest.kt` read file paths directly and inspect reflection class names. Updating them to look for `LiveSegmentSheet.kt` and `LiveSegmentSheetKt` ensures continuous CI compliance.
* **Verification**:
  - Run targeted layout tests: `./gradlew testDebugUnitTest --tests "*SegmentSheet*" --tests "*ClimbSheet*"`.
  - Run full clean-room suite: `./gradlew testDebugUnitTest`.
