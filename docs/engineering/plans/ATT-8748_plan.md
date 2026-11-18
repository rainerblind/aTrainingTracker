# Stage 3: Implementation Plan - ATT-8748: Enlarge Lieblingsstrecken Card Preview Map to 175x120 dp

**DOC-ID**: `DOC-PLN-ATT-8748` | **Ticket**: [ATT-8748](https://rainerblind.atlassian.net/browse/ATT-8748) (`1 SP`) | **Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8748`  
**Requirement Mapping**: `REQ-UI-493` (`REQ-UI-493.3`, refining `REQ-UI-244`) | **Test Mapping**: `TST-UI-493` (`TST-UI-493.3a`–`c`, `TST-UI-493.5`)  
**Author**: `--as agent1` | **Date**: 2026-11-18

---

## 1. Traceability & System Invariants
* **Requirements & Tests**: `REQ-UI-493` (`docs/requirements/req_ui_design_system_part5.md`) $\leftrightarrow$ `TST-UI-493` (`docs/tests/tst_ui_design_system_part3.md`).
* **Preserved Invariants**:
  1. `GoogleMapOptions().liteMode(true)` (`MapPreviewLiteModeTest`), `DarkMapAntiFlashOverlay` (`DarkMapAntiFlashOverlayTest`), and `DarkMapStyle.resolveMapProperties` (`DarkMapStyleResolutionTest`).
  2. TopStart Merge/Delete `DropdownMenu` in `ClusterItem` (`GlobalDeleteContextMenuAuditTest`, `WorkoutClusterSplitMapPreviewTest`).
  3. `IntrinsicSize.Min` bottom alignment in `ClusterItem` (`L280`) and `UnclusteredWorkoutItem` (`L496`) without `BoxWithConstraints` (`SubcomposeLayout`) inside `Row`; `maxLines = 1` + `TextOverflow.Ellipsis` on equipment strings.
  4. Zero SQLite/DataStore/Repository changes, zero localized UI strings (`Localization Parity: N/A - no UI strings`), zero `src/main` reads in `ClusterItemThumbnailComposeTest.kt` (`REQ-PRO-049.1`), and `app/source-reading-baseline.txt` (`109` entries) preserved without expansion.
  5. `KnownLocationsScreen.kt` (*Lieblingsorte*, `ATT-8749`) untouched; no new SWE.2 subsystems (`docs/architecture.md` unchanged).

---

## 2. UI Consistency (`docs/design_guidelines.md`)
* **Spacing & Shapes (§5.2, §5.3)**: Use `12.dp` horizontal `Spacer` (canonical `4/8/12/16/24.dp` scale, replacing `16.dp`) and `RoundedCornerShape(12.dp)` on the `175.dp × 120.dp` thumbnail `Surface` in both `ClusterItem` and `UnclusteredWorkoutItem`.
* **Narrow-Screen Guard (`360.dp`, §5.20)**: Derive thumbnail width outside `IntrinsicSize.Min` via `LocalConfiguration.current.screenWidthDp` (`resolveClusterThumbnailWidthDp`: `175.dp` when `screenWidthDp >= 387`, clamping down to `110..174.dp` so `screenWidthDp - 40.dp (card/list padding) - 12.dp (spacer) - thumbnailWidth >= 160.dp`), avoiding `BoxWithConstraints` inside `Row(Modifier.height(IntrinsicSize.Min))`.
* **Camera Framing & Anti-Flash (§5.4, §6)**: Seed `rememberCameraPositionState` initial position to `bounds.center` (when non-null) to prevent world-zoom `(0, 0)` flash, and frame via `CameraUpdateFactory.newLatLngBounds(bounds, THUMBNAIL_BOUNDS_PADDING_PX)` (`18 px`, within `16..20 px`) inside `try/catch (IllegalStateException)`.
* **Previews & `Rule 8.5`**: Add `private fun` Light & Dark `@Preview` composables; capture and `read_file`-inspect `docs/engineering/walkthroughs/img/ATT-8748_lieblingsstrecken_preview.png`.

---

## 3. Atomic Step Sequence (Stage 4 Construction — Post-Gate 3 `PASS` Only per `Rule 4.1`)

### Step 1: Enlarge & Unify Landscape Map Thumbnail in `WorkoutClusterComponents.kt` (`REQ-UI-493.3`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt`
* **Changes**:
  1. Add internal constants & pure sizing helper: `THUMBNAIL_WIDTH_DP = 175`, `THUMBNAIL_HEIGHT_DP = 120`, `THUMBNAIL_BOUNDS_PADDING_PX = 18` (`16..20 px`), and `internal fun resolveClusterThumbnailWidthDp(screenWidthDp: Int): Int` enforcing `>= 160.dp` metadata budget on narrow (`360.dp`) viewports.
  2. Extract shared `private`/`internal` composable `ClusterMapPreviewThumbnail(...)` used by both `ClusterItem` (`L294`) and `UnclusteredWorkoutItem` (`L534`) (eliminating duplication while preserving `liteMode(true)`, `DarkMapStyle`, `DarkMapAntiFlashOverlay`, polylines, Start/End/Apex markers, and click/long-click overlay).
  3. Apply `Modifier.size(width = thumbnailWidthDp.dp, height = 120.dp)` (`175.dp × 120.dp` on `411.dp`), change `Spacer(Modifier.width(16.dp))` (`L292`, `L532`) to `12.dp`, seed `rememberCameraPositionState` to `bounds?.center`, and guard `cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, THUMBNAIL_BOUNDS_PADDING_PX))` with `try/catch (IllegalStateException)`.
  4. Add `private fun` Light/Dark `@Preview` composables (`Rule 8.5`, `WorkoutClusterPreview`).

### Step 2: Detekt Baseline Cleanup (`REQ-UI-493.3`)
* **File**: `app/detekt-baseline.xml`
* **Changes**: Remove obsolete `<ID>MagicNumber:WorkoutClusterComponents.kt$40</ID>` (`L2404`) and ensure no new `MagicNumber` / `LongMethod` baseline regressions are introduced.

### Step 3: Behavioral Compose UI Tests, Legacy Contract Alignment & `Rule 8.5` Screenshot (`TST-UI-493.3a/b/c`)
* **Files**:
  * Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterItemThumbnailComposeTest.kt` (**0** `src/main` reads per `REQ-PRO-049.1`) using `createComposeRule()` (Robolectric) to verify `ClusterItem` and `UnclusteredWorkoutItem` at `411.dp` (`175.dp × 120.dp` thumbnail bounds, `12.dp` horizontal gap, `maxLines = 1` equipment truncation, `THUMBNAIL_BOUNDS_PADDING_PX in 16..20`, `try/catch` + bounds-center camera state) and `360.dp` (responsive clamping keeping metadata `>= 160.dp`, zero `IntrinsicSize.Min` / `SubcomposeLayout` exceptions), plus render combined Light/Dark `@Preview` PNG `docs/engineering/walkthroughs/img/ATT-8748_lieblingsstrecken_preview.png`.
  * Update `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterItemAndFilterPolishTest.kt` (`L138–163`) to assert `175.dp × 120.dp` thumbnail contract (`THUMBNAIL_WIDTH_DP = 175` / `120.dp`) with **zero** new `src/main` reads (`SourceTextReadingAuditContractTest` baseline `105` preserved).

### Step 4: Tier 1 Targeted Regression Verification (`TST-UI-493.5`)
* **Command**: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.map.MapPreviewLiteModeTest" --tests "com.atrainingtracker.trainingtracker.ui.map.DarkMapAntiFlashOverlayTest" --tests "com.atrainingtracker.trainingtracker.ui.map.DarkMapStyleResolutionTest" --tests "com.atrainingtracker.trainingtracker.ui.GlobalDeleteContextMenuAuditTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"`
