# Stage 3: Implementation Plan - ATT-9067: Round Layers icon on Workout Details map does nothing when tapped

**Ticket**: `ATT-9067` (`1 SP`, `Bug`, `severity-minor`) | **Epic**: `ATT-68` | **Target Release**: `V5.0.0`  
**Sprint**: `466` (`2026-11-20_02_Spät`) | **Branch**: `feature/ATT-9067` | **Req/Test**: `REQ-REV-016` / `TST-REV-017`  
**Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-20

---

## 1. Architecture Rationale & System Invariants
- In `MapDetailLayout.kt` (`mapBox`), `onInteractiveCanvasTouch(true)` wraps the outer `Box` enclosing both `ATrainingTrackerMap` and `FLOATING CONTROLS` (`overlay()` + `topEndActions`), coupling taps on the round map **Layers** button inside `WorkoutDetailTabbedContent` to mid-gesture `HorizontalPager(userScrollEnabled)` flips, while `TrackOnMapScreen.kt` duplicates the inline `showTrackMenu` / `DropdownMenu` block without `testTag`s.
- **Invariants**: `MapDetailLayout.kt <= 1,500` LoC (`1,096` -> `~1,106`); `TrackOnMapScreen.kt <= 1,500` LoC (`1,030` -> `~990`); `0` edits to `WorkoutDetailTabbedContent.kt`; `ATrainingTrackerMap` canvas panning still emits `onInteractiveCanvasTouch(true)` (`REQ-UI-408.4`); `controlsTopOffsetDp` (`REQ-UI-491`), `showFloatingShareButton = workoutData == null` & `44.dp` `CircleShape` (`REQ-UI-500.5`), `REQ-MAP-013` persistence, and `REQ-REV-015` offscreen `snapshotHeader` isolation remain intact; `Localization Parity: N/A - no new or modified UI strings`.
- **UI Consistency**: Preserves existing Material 3 `CircleShape` `44.dp` `Surface` + `Icons.Default.Layers` + `DropdownMenu` with `Checkbox` and `12.dp` `TrackType` swatch (`0` new UI strings, `0` `FilterChip`s).

## 2. Atomic Implementation Steps

### Step 1: Isolate `FLOATING CONTROLS` from `ATrainingTrackerMap` Canvas Touch Detection in `MapDetailLayout.kt` (`REQ-REV-016.1`)
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt` (`1,096 -> ~1,106 / 1,500` LoC)
- **Changes**:
  - Remove `if (onInteractiveCanvasTouch != null) Modifier.pointerInput(Unit) { ... }` from `mapBox`'s outer `Box` (`:362-375`).
  - Wrap `ATrainingTrackerMap(...)` (`:376-428`) in an inner `Box(modifier = Modifier.fillMaxSize().then(if (onInteractiveCanvasTouch != null) Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); onInteractiveCanvasTouch(true) } } } else Modifier))` so map-canvas touches continue locking `HorizontalPager`.
  - On the sibling `FLOATING CONTROLS` `Box` (`:431-484`), add `.zIndex(1f)` above `GoogleMap` `AndroidView` and chain (when `onInteractiveCanvasTouch != null`) `Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); onInteractiveCanvasTouch(false) } } }` **without** consuming pointer events, so `Surface(onClick)` and `overlay()` handle clicks normally with stable `HorizontalPager(userScrollEnabled)`.

### Step 2: Deduplicate & Hoist `TrackLayersMenuButton` with `testTag`s in `TrackOnMapScreen.kt` (`REQ-REV-016.2`)
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt` (`1,030 -> ~990 / 1,500` LoC)
- **Changes**:
  - Add `internal object TrackOnMapTestTags` (`LAYERS_BUTTON = "track_layers_button"`, `LAYERS_MENU = "track_layers_menu"`, `layerMenuItem(type: TrackType) = "track_layers_item_${type.name}"`) and extract `internal fun TrackLayersMenuButton(availableTrackTypes: Set<TrackType>, enabledTrackTypes: Set<TrackType>, onToggleTrackType: (TrackType) -> Unit, modifier: Modifier = Modifier)`.
  - Hold `var showTrackMenu by remember { mutableStateOf(false) }` inside `TrackLayersMenuButton`, attach `.testTag(TrackOnMapTestTags.LAYERS_BUTTON)` on the `44.dp` `CircleShape` `Surface(onClick = { showTrackMenu = true })`, `.testTag(TrackOnMapTestTags.LAYERS_MENU)` on `DropdownMenu`, and `.testTag(TrackOnMapTestTags.layerMenuItem(type))` on each `DropdownMenuItem`.
  - Replace both inline blocks (`:434-491` and `:584-647`) with `if (showTechnicalTracks) { TrackLayersMenuButton(availableTrackTypes, enabledTrackTypes, onToggleTrackType) }`, preserving `controlsTopOffsetDp` (`REQ-UI-491`), `REQ-UI-500.5`, and `REQ-MAP-013`.

### Step 3: Behavioral Robolectric Compose UI Tests (`TST-REV-017.1`–`.2`) + Living Docs
- **Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapLayersUiTest.kt` (new `@GraphicsMode(GraphicsMode.Mode.NATIVE)` `v2.createComposeRule` suite; `0` deprecation warnings, `0` `src/main` reads), `docs/requirements/req_aftermath_reviews.md`, `docs/tests/tst_aftermath_reviews.md`, `docs/requirements.md`, `docs/tests.md`
- **Changes**:
  - `TST-REV-017.1`: Host `WorkoutDetailTabbedContent` + `MapDetailLayout(showMap = true)` + `TrackLayersMenuButton`; verify (a) tapping `track_layers_button` (including inner `< width - 32.dp` coordinates) does NOT emit `onInteractiveCanvasTouch(true)` and keeps `HorizontalPager(userScrollEnabled)` constant while opening `track_layers_menu` (`TrackType.entries`) and toggling `track_layers_item_GPS` / `track_layers_item_ATT_FUSED`, and (b) touching the underlying `ATrainingTrackerMap` canvas DOES emit `onInteractiveCanvasTouch(true)`.
  - `TST-REV-017.2`: Verify standalone `MapDetailLayout(showMap = true, showHeader = true)` (`useStatusBarsPadding = true`) + `TrackLayersMenuButton` opens `track_layers_menu` and toggles `TrackType`.
  - Tag tests with `// REQ-REV-016` / `// TST-REV-017`; update `Component` in `tst_aftermath_reviews.md` to cite `TrackOnMapLayersUiTest.kt` and promote `REQ-REV-016` / `TST-REV-017` to `[Implemented]` (Stage 4) and `[Verified]` (Stage 5).

### Step 4: Locked Incremental Verification & Governance (`Rule 7.5`)
- **Commands**:
  - `python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapLayersUiTest" --tests "com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutTest" --tests "com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutOffscreenHeaderUiTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.UiReflectionOnlyTestAuditContractTest"`
  - `python3 tools/verify_requirement_governance.py --repo-root . --base-ref refs/heads/sprint/2026-11-20_02_Spät`
