# Stage 3: Implementation Plan - ATT-9168: Fix Invisible Offscreen `WorkoutHeader` Intercepting Clicks in `MapDetailLayout`

**Ticket**: `ATT-9168` (`1 SP`, `Bug`, `severity-major`) | **Epic**: `ATT-68` | **Target Release**: `V5.0.0`  
**Sprint**: `465` (`2026-11-20_01_Früh`) | **Branch**: `feature/ATT-9168` | **Req/Test**: `REQ-REV-015` / `TST-REV-016`  
**Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-20

---

## 1. Architecture Rationale & System Invariants
- In `MapDetailLayout.kt`, when `showHeader == false`, `snapshotHeader` (`WorkoutHeader`) is currently always composed inside the `.zIndex(1f)` upper `Column` and placed at `(0, 0)` via `layout(0, 0) { placeable.place(0, 0) }`, leaving clickable hit-boxes and duplicate TalkBack nodes directly over `Sichtbare Diagramme` and the `Tempo` multi-source layers icon in tabbed `Übersicht`.
- **Invariants**: `MapDetailLayout.kt <= 1,500` LoC (`1,062` baseline -> `~1,085`); `0` edits to `WorkoutDetailTabbedContent.kt` or `WorkoutHeader.kt`; standalone `showHeader = true` visual parity preserved; `REQ-REV-006.5` UI-thread `GraphicsLayer` rasterization + `NonCancellable` share dispatching preserved; `Localization Parity: N/A - no new or modified UI strings`.
- **UI Consistency**: Pure hit-test/semantics/graphics-layer fix (`0` new UI controls, `0` new strings, `0` theme changes).

## 2. Atomic Implementation Steps

### Step 1: Isolate Offscreen `snapshotHeader` On Demand in `MapDetailLayout.kt` (`REQ-REV-015.1`)
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt` (`1,062 -> ~1,085 / 1,500` LoC)
- **Changes**:
  - Remove the `else { Box(...) { Box(drawWithContent { headerLayer.record ... }) { header() } } }` block (`:692-711`) from inside the `.zIndex(1f)` upper `Column`.
  - When `!showHeader && (isSharing || externalSnapshotTrigger)` is `true`, compose the offscreen `snapshotHeader` capture container **outside** the `.zIndex(1f)` `Column` inside `BoxWithConstraints`, wrapped with `.zIndex(-1f)`, `.clearAndSetSemantics {}`, full parent width (`Modifier.fillMaxWidth()`, `constraints.copy(minHeight = 0)`), and off-viewport placement (`layout(0, 0) { placeable.place(0, -placeable.height - 1000) }`) so `headerLayer` records at full device width while remaining non-interactive and invisible to accessibility/TalkBack.

### Step 2: Frame-Synchronized `GraphicsLayer` Capture Gate in `MapDetailLayout.kt` (`REQ-REV-015.2`)
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
- **Changes**:
  - Add `var isHeaderLayerRecorded by remember { mutableStateOf(false) }` and `var readyToTakeMapSnapshot by remember { mutableStateOf(false) }`.
  - Inside `headerLayer.record { ... }` (when `!showHeader`), set `isHeaderLayerRecorded = true` (or inside `drawWithContent` after `headerLayer.record`).
  - In `LaunchedEffect(isSharing, showHeader, isHeaderLayerRecorded)`: when `!isSharing`, reset `readyToTakeMapSnapshot = false` (and `isHeaderLayerRecorded = false` when `!showHeader && !externalSnapshotTrigger`); when `isSharing`, if `showHeader || isHeaderLayerRecorded`, await `withFrameNanos {}` (ensuring the recorded frame is committed) and set `readyToTakeMapSnapshot = true`.
  - Pass `shouldTakeSnapshot = readyToTakeMapSnapshot` to `ATrainingTrackerMap` (`:370`), covering both `onSelectMapSnapshot = { isSharing = true }` and `externalSnapshotTrigger = true` flows without blank `0x0` `headerLayer` captures.

### Step 3: Behavioral Compose UI & Snapshot Tests (`TST-REV-016.1`–`.2`) + Living Docs
- **Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutOffscreenHeaderUiTest.kt` (new Robolectric `@GraphicsMode(GraphicsMode.Mode.NATIVE)` suite using `androidx.compose.ui.test.junit4.v2.createComposeRule`), `docs/requirements/req_aftermath_reviews.md`, `docs/tests/tst_aftermath_reviews.md`, `docs/requirements.md`, `docs/tests.md`
- **Changes**:
  - Test `TST-REV-016.1`: Render `MapDetailLayout(showHeader = false, showMap = false, showTelemetryCharts = true, ...)` with a clickable `snapshotHeader` and real `TelemetryChartStack` (`Sichtbare Diagramme` + multi-source `Tempo` layers icon); assert `snapshotHeader` semantics do NOT exist when idle and clicking both controls opens their menus (`0` deprecation warnings, `0` `src/main` reads).
  - Test `TST-REV-016.2`: Trigger `externalSnapshotTrigger = true` (`showHeader = false`) and assert `headerLayer` records full-width non-zero bounds synchronized with the draw pass while zero duplicate `WorkoutHeader` semantics exist (`.clearAndSetSemantics {}`).
  - Tag tests with `// REQ-REV-015` / `// TST-REV-016`; update `Component` in `tst_aftermath_reviews.md` to cite `MapDetailLayoutOffscreenHeaderUiTest.kt` and promote `REQ-REV-015` / `TST-REV-016` to `[Implemented]` (Stage 4) and `[Verified]` (Stage 5).

### Step 4: Locked Incremental Verification & Governance (`Rule 7.5`)
- **Commands**:
  - `python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutOffscreenHeaderUiTest" --tests "com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutSnapshotContractTest" --tests "com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutElevationVisibilityUiTest" --tests "com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutCollapsingHeaderContractTest" --tests "com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutTest" --tests "com.atrainingtracker.trainingtracker.ui.map.WorkoutShareBottomSheetContractTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailHeaderClickContractTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.UiReflectionOnlyTestAuditContractTest"`
  - `python3 tools/verify_requirement_governance.py --repo-root . --base-ref refs/heads/sprint/2026-11-20_01_Früh`
