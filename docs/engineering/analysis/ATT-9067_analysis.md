# Stage 1 Analysis: ATT-9067 - Round Layers icon on Workout Details map does nothing when tapped

**Ticket**: [ATT-9067](https://issue-tracker/ATT-9067)  
**Sub-task**: [TBD](https://issue-tracker/TBD) (`[Analysis]`)  
**Parent Epic**: [ATT-68](https://issue-tracker/ATT-68) (*Workouts & Aftermath (WorkoutSummaries, Details & Graphs)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_02_Spät` (Sprint 466)  
**Branch**: `feature/ATT-9067` (from `sprint/2026-11-20_02_Spät` @ `392e312c`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-20  

---

## 1. Problem Statement & Motivation
- **Observed Defect** (PO on-device review of `ATT-8080`, Pixel 10, build `5.0.0-dev-906504b8`): In Workout Details, tapping the round **Layers** icon (`Icons.Default.Layers`) in the top-right corner of the embedded map does nothing—the technical track selector `DropdownMenu` (`Best`, `GPS`, `Google Fused`, `ATT Fused`) does not open.
- **Expected Behavior**: Tapping the round **Layers** button opens the `DropdownMenu` listing all `TrackType.entries` (`BEST`, `GPS`, `FUSED`, `ATT_FUSED`) and allows toggling visible tracks (`REQ-MAP-011`, `REQ-MAP-012`, `REQ-UI-500`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Check vs. `ATT-9168` (`ba5e86e4`, landed in `392e312c`)**:
  - In `906504b8`, `MapDetailLayout.kt` placed `snapshotHeader` (`WorkoutHeader`) at `(0, 0)` inside `.zIndex(1f)` when `showHeader = false`. However, `WorkoutDetailTabbedContent.kt:219-233` passes `onClicked = null, canDelete = false, menuEnabled = false, onOpenTabsConfig = null, onChangeCluster = null, onEditWorkout = null, onShareWorkout = null, onClusterClick = null`. In `WorkoutHeader.kt:134-145, 201-214, 511`, `Surface` has no `combinedClickable`, the cluster `Button` is left-aligned, and only `!data.recordingErrors.isNullOrBlank()` places a `32.dp` `IconButton` at `TopEnd` (`4.dp` padding, overlapping the upper-right quadrant of the `44.dp` Layers button at `16.dp` padding). Thus `ATT-9168` resolved occlusion for workouts with `recordingErrors`, **not** for normal workouts.
- **Distinct Findings in Live `392e312c` Codebase**:
  1. **`mapBox` Pointer-Interceptor Encloses Floating Controls (`MapDetailLayout.kt:361-486`)**: `mapBox` attaches `Modifier.pointerInput(Unit)` (`awaitPointerEvent(PointerEventPass.Initial); onInteractiveCanvasTouch(true)`) to the outer `Box` enclosing **both** `ATrainingTrackerMap` (`:376-428`) and `FLOATING CONTROLS` (`overlay()` + `topEndActions`, `:431-484`). Unlike `chartInteractiveTouchDetector` (`:92-116`, which excludes the `52.dp` right overlay margin via `onInteractiveCanvasTouch(false)`), tapping the round Layers button (`x in [width-60dp, width-16dp]`) triggers `isTouchingInteractiveCanvas = true` on `DOWN`, flipping `activeTabRequiresGestureIsolation` (`WorkoutDetailTabbedContent.kt:250-265`) and recomposing `HorizontalPager(userScrollEnabled = ...)` (`:306-312`) mid-gesture (`true -> false` on `DOWN` for `x < width-32dp`, `false -> true` on `UP`).
  2. **Duplicated Inline `mapTopEndActions` State & Zero Behavioral UI Coverage (`TrackOnMapScreen.kt:434-491, 584-647`)**: `TrackOnMapScreen` keeps `showTechnicalTracks = true` in all callers (`WorkoutSummariesListFragment.kt:210`, `WorkoutSummariesTabbedScreen.kt:337`, `PeriodMapScreen.kt:258`, `WorkoutClusterHeatmapScreen.kt:413`, `WorkoutClustersScreen.kt:172`), wires `Surface(onClick = { showTrackMenu = true })` (`:438-454`) and `DropdownMenu` over `TrackType.entries` (`:455-488`), and duplicates this 55-line block between tabbed and fallback paths (`:584-647`) without a single Robolectric `createComposeRule()` test verifying `TrackOnMapScreen`/`WorkoutDetailTabbedContent` -> `MapDetailLayout(showMap = true)` Layers click & menu opening.
- **Independent Peer Review & Verification**:
  - Verified live call sites in `TrackOnMapScreen.kt` (`1,030` LoC), `WorkoutDetailTabbedContent.kt` (`1,102` LoC), `MapDetailLayout.kt` (`1,096` LoC), `WorkoutHeader.kt`, and `TrackOnMapAftermathViewModel.kt:102-119, 257`.
  - Confirmed `showTechnicalTracks = true`, `onToggleTrackType` wiring, and `TrackType.entries` rendering are non-empty; confirmed `ATT-9168` does not account for normal workouts lacking `recordingErrors`; confirmed `mapBox` (`:364-372`) lacks `FLOATING CONTROLS` isolation compared to `chartInteractiveTouchDetector` (`:99-115`).
  - **Stage 2/4 Execution Mandate**: Reproduce/verify live Compose hit-testing & `DropdownMenu` opening via a behavioral Robolectric `createComposeRule()` test hosting `WorkoutDetailTabbedContent` + `MapDetailLayout(showMap = true)` + `TrackOnMapScreen` `mapTopEndActions` before/after isolating `FLOATING CONTROLS` from `onInteractiveCanvasTouch(true)` and hoisting/deduplicating the Layers button + `testTag`s.

---

## 3. Scope Grounding & Non-Goals
- **In-Scope Goals**:
  - Isolate `MapDetailLayout.kt` `FLOATING CONTROLS` (`topEndActions` / `overlay`) from `mapBox`'s `onInteractiveCanvasTouch(true)` canvas detector (wrapping only `ATrainingTrackerMap` and resetting `onInteractiveCanvasTouch(false)` on floating controls).
  - Hoist/deduplicate `TrackOnMapScreen.kt` Layers `Surface` + `DropdownMenu` (`:434-491`, `:584-647`) into one helper (`TrackLayersMenuButton`) with deterministic `testTag`s, and add Robolectric Compose UI verification.
- **Out-of-Scope Non-Goals**:
  - `ATT-9066` (Speed multi-source overlay icon on `Übersicht` tab), `RouteOnMapScreen` / `MapScreenWithTrack` refactorings, or `MapDetailLayout` `snapshotHeader` changes already completed in `ATT-9168`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: `REQ-MAP-012` (*Track Selection Menu*, `docs/requirements/req_maps_navigation.md:202-216`) and `REQ-UI-500` (*MapDetailLayout Unified Top-End Floating Actions Stacking*, `docs/requirements/req_ui_design_system_part5.md:520-565`), targeting `MapDetailLayout.kt`, `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt`.
- **Historical Origin & Commit Trace**: `d307d5d9` (introduced multi-track `Layers` FAB), `b185d9b1` (`ATT-6214` / `REQ-UI-500`: moved Layers into `MapDetailLayout.topEndActions` inside `mapBox`), `3652c31c` (`ATT-8080`: replaced `NETWORK` with `ATT_FUSED`), `ba5e86e4` (`ATT-9168` / `REQ-REV-015`: offscreen `snapshotHeader` isolation).
- **Root Reason for Existing Formulation**: `onInteractiveCanvasTouch(true)` was attached to `mapBox` (`MapDetailLayout.kt:364`) to disable `HorizontalPager` swipe while panning the map (`REQ-UI-408.4`), inadvertently covering floating `topEndActions` buttons placed inside `mapBox`.
- **Preservation of Core Invariants**: Map canvas panning still sets `onInteractiveCanvasTouch(true)` outside the `32.dp` edge gutter; `showFloatingShareButton = workoutData == null`, `controlsTopOffsetDp` (`REQ-UI-491`), `44.dp` `CircleShape` Layers styling (`REQ-UI-500.5`), `TrackType.entries` (`BEST`, `GPS`, `FUSED`, `ATT_FUSED`), and `enabledTrackTypes` persistence (`REQ-MAP-013`) remain intact.

---

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: Refines `REQ-MAP-012` / `REQ-UI-500` via net-new sub-requirement or `REQ-UI-*` in Stage 2; preserves `REQ-REV-015`. `SYS.2` / `SYS.3` (`REQ-PRO-131.4`): None (pure Compose UI pointer-isolation and button/menu wiring; zero hardware/HAL/BLE/ANT+/REST impact). Localization parity: N/A (`0` string changes).
- **Affected Architecture Elements**: `MapDetailLayout.kt` (`1,096 / 1,500` LoC), `TrackOnMapScreen.kt` (`1,030 / 1,500` LoC); `WorkoutDetailTabbedContent.kt` (`1,102 / 1,500` LoC, verify `0` or minimal edits).
- **Verification Impact & Test Surface**: New behavioral Robolectric `createComposeRule()` UI test verifying `TrackOnMapScreen` / `WorkoutDetailTabbedContent` -> `MapDetailLayout(showMap = true)` round `Layers` tap opens `DropdownMenu` (`Best`, `GPS`, `Google Fused`, `ATT Fused`) and invokes `onToggleTrackType` without triggering `onInteractiveCanvasTouch(true)`; plus `MapDetailLayoutTest`, `MapDetailLayoutOffscreenHeaderUiTest`, `WorkoutDetailTabbedContentTest`, `ArchitectureConformanceContractTest`, `SourceTextReadingAuditContractTest`, `UiReflectionOnlyTestAuditContractTest`.
- **Risk Assessment & Invariants**: `LOW` risk (`1 SP`). Invariants: `MapDetailLayout.kt <= 1,500` LoC, `TrackOnMapScreen.kt <= 1,500` LoC, map-pan gesture isolation preserved (`ATrainingTrackerMap` still triggers `onInteractiveCanvasTouch(true)`), `showFloatingShareButton = workoutData == null`, `0` `src/main` file reads in tests.

---

## 6. System Invariants & Risk Assessment
- **Core Invariants**: `HorizontalPager` gesture isolation for map panning (`REQ-UI-408.4`), `top = 16.dp` + `controlsTopOffsetDp` docking (`REQ-UI-500` / `REQ-UI-491`), `TrackType.BEST` default + 4-provider menu (`REQ-MAP-011`/`012`), `REQ-REV-015` offscreen `snapshotHeader` isolation, and `<= 1,500` LoC per Compose file.
- **Risk Rating**: `LOW` (`1 SP`) — localized to `MapDetailLayout.kt` `mapBox` pointer wrapper and `TrackOnMapScreen.kt` `topEndActions` button/menu helper.
