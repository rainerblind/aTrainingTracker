# Stage 3: Implementation Plan - ATT-6041: Marginal Histograms, X-Axis Ticks & PathPreviewMap Cluster Thumbnail Consolidation

**Ticket**: [ATT-6041](https://issue-tracker/ATT-6041)  
**Sub-task**: Pending creation (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467)  
**Requirement Mapping**: `REQ-UI-2789` (`REQ-UI-2789.1`..`REQ-UI-2789.4`)  
**Test Mapping**: `TST-UI-2789` (`TST-UI-2789.1`..`TST-UI-2789.3`)  
**Branch**: `feature/ATT-6041`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-21  

---

## 1. Problem Description & Background
* `ElevationProfile.kt` and `TelemetryMetricGraph.kt` duplicate 5 marginal histogram `Canvas` loops and 4 time/distance X-axis tick `while` loops, while `WorkoutClusterComponents.kt` (`ClusterMapPreviewThumbnail` for `ClusterItem` and `UnclusteredWorkoutItem`) duplicates ~190 LoC of Lite-Mode `GoogleMap` boilerplate instead of delegating to `PathPreviewMap.kt`.

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-2789` (`REQ-UI-2789.1`: marginal histogram helper; `.2`: X-axis tick helper; `.3`: `PathPreviewMap` multi-polyline/padding/long-click + cluster delegation excluding `ClusterNamingDialog`; `.4`: contract guards, LoC ratchet, zero hex colors, `Rule 8.5` PNGs).
* **Test Mapping**: `TST-UI-2789` (`TST-UI-2789.1`: `TelemetryChartCanvasHelpersTest.kt`; `.2`: `PathPreviewMapBoundsTest.kt` + `ClusterItemThumbnailComposeTest.kt`; `.3`: `Rule 8.5` `ATT-6041_*.png` + Tier-1 contract/unit regression).
* **Living-Doc Headroom Guard**: `tst_ui_design_system_part2.md` is at `3,990 / 4,000` lines; Stage 4/5 updates MUST stay `<= 4` net lines (`REQ-PRO-093.4`).

## 3. System Invariants, UI Consistency & SWE.2/SWE.3 Scope
1. **UI Consistency (`docs/design_guidelines.md`)**: Pure visual-parity refactoring (no new controls, dialogs, mode switchers, or UI strings — `Localization Parity: N/A`). Preserves §2.1 stacked plot alignment (`50.dp` start, `52.dp` portrait / `72/28%` landscape), §2.7 Rules 69/70 histogram track continuity & clamping, §5.2–§5.4 `175×120 dp` (`>= 160 dp` metadata on `360 dp`) cluster cards with `12.dp` corners and `TTColor`/`MaterialTheme.colorScheme` tokens (zero `Color(0x...)` outside `Color.kt`), and Light/Dark/AMOLED parity (`DarkMapStyle` + `DarkMapAntiFlashOverlay`).
2. **Contract Guards & Explicit Exclusions**: Keep `AlignedMarginalLinearDistribution`/`AlignedMarginalZoneDistribution` declared in `TelemetryMetricGraph.kt`; preserve `PathPreviewMap.kt` tokens (`path`, `paths`, `allPaths.forEach`, `boundsBuilder.include(it)`, `apexIconRes`) for `WorkoutClusterSplitMapPreviewTest` & `WorkoutLapsIconContractTest`; preserve `WorkoutClusterComponents.kt` constants (`THUMBNAIL_WIDTH_DP = 175`, `THUMBNAIL_HEIGHT_DP = 120`, `THUMBNAIL_BOUNDS_PADDING_PX = 18`, `cluster_naming__new_*`) and zero `onSplitRequest`/`CallSplit` for `ClusterItemAndFilterPolishTest` & `ClusterNamingDialogLabelsVisualContractTest`; preserve `R.string.legend` in `ElevationProfile.kt` (`HardcodedStringsAndUnitsContractTest`). `ClusterNamingDialog` (`ImportBackupTabsScreen.kt:992-1022`), scrubbing gestures, and `MapDetailLayout.kt` (`ATT-8134`) remain strictly untouched.
3. **SWE.2 / SWE.3 Scope (`Rule 8.8`, `REQ-PRO-114`)**: Zero updates required to `docs/architecture.md` or `docs/architecture/detailed_design/` (internal UI helper & Lite-Mode preview consolidation; no new subsystem, service, DB, repository, prefix family, or concurrency state machine).

## 4. Proposed Architectural Changes
### Component 1: `ui/map/TelemetryChartCanvasHelpers.kt` (New) + `ElevationProfile.kt` & `TelemetryMetricGraph.kt`
* **`computeMarginalBarBounds(...)` + `inline fun <T> DrawScope.drawMarginalHistogramBars(...)`**: Zero-allocation `DrawScope` helper parameterizing `dataMin`/`dataMax`, `stripStartPx`/`stripWidthPx`, `valueToY`, bin field accessors (`LinearHistogramBin` Double & `TelemetryHistogramBin` Int), `cornerRadiusPx` (`0f` vs `2.dp.toPx()`), `backgroundAlpha = 0.15f`, `foregroundAlpha` (`1.0f` vs `0.85f`), `minBarHeightPx` (`0f` vs `1f` applied strictly *after* `rectHeight <= 0f -> continue` to preserve Rule 70), `durationSec > 0L && barWidthPx > 0f` foreground gating, and optional `onBinDrawn` callback (`"${bin.percentage.toInt()}%"` overlay in `AlignedMarginalZoneDistribution`).
* **`computeXAxisTickValues(...)` + `DrawScope.drawXAxisTicks(...)`**: Shared `ceil`/stride loop parameterizing `(startValue, visibleSpan, step, plotStartX, plotWidth, paint, labelY, initialLastDrawnRightX, formatLabel, shouldRenderLabel, onTickInBounds, onLabelRendered)` so `ElevationProfile` draws bottom ticks only on rendered labels (`24.dp` spacing, locale formatter) and `TelemetryMetricGraph` draws full-height gridlines for every in-bounds tick (`12.dp` spacing, `TelemetryMetricUtils.formatMilestoneLabel`).

### Component 2: `ui/map/PathPreviewMap.kt` + `ui/clusters/WorkoutClusterComponents.kt`
* Add `data class PreviewPolyline(val points: List<LatLng>, val color: Color, val widthPx: Float = POLYLINE_STROKE_WIDTH_PX, val roundCaps: Boolean = false, val zIndex: Float = 0f)` and backward-compatible parameters on `PathPreviewMap`: `additionalPaths: List<PreviewPolyline> = emptyList()`, `boundsPaddingPx: Int = PREVIEW_BOUNDS_PADDING_PX`, `onMapLongClick: (() -> Unit)? = null`.
* Thread `boundsPaddingPx` into BOTH `estimateInitialCameraPosition(..., boundsPaddingPx, density)` (`:338`) and `CameraUpdateFactory.newLatLngBounds(resolvedBounds, boundsPaddingPx)` (`:445`); include valid `additionalPaths` coordinates in `buildCompositePreviewBounds` when `targetBounds == null` while keeping `actualStart`/`actualEnd` fallback strictly off `additionalPaths`.
* Render `additionalPaths` in `PathPreviewMapOverlays` (`RoundCap`, `JointType.ROUND`, `zIndex`) and `PathPreviewInspectionCanvas` (with graceful stub fallback for non-decodable test strings `"sample_route"`/`"sample_workout"` + `combinedClickable`). Render a transparent `combinedClickable` overlay `Box` above `GoogleMap` + `DarkMapAntiFlashOverlay` in `PathPreviewLiveMap` when `onMapLongClick != null`; add `tiltGesturesEnabled = false, rotationGesturesEnabled = false` to `previewMapUiSettings()`.
* Refactor `ClusterMapPreviewThumbnail` (`WorkoutClusterComponents.kt`) to decode `previewPolylines`/`authoritativePolyline` into `additionalPaths` and delegate directly to `PathPreviewMap` (`boundsPaddingPx = THUMBNAIL_BOUNDS_PADDING_PX`), deleting ~190 LoC of redundant `ClusterMapPreviewLiveMap`/`Polylines`/`Markers`/`InspectionContent`.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Pre-Check (`Rule 4.1`)**: Confirm `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` returns `0`.
2. **Step 1 (`REQ-UI-2789.1`)**: Create `TelemetryChartCanvasHelpers.kt` with `drawMarginalHistogramBars` + pure clamping helper; migrate all 5 histogram loops in `ElevationProfile.kt` (`1` site) and `TelemetryMetricGraph.kt` (`4` sites), keeping `AlignedMarginal*` in `TelemetryMetricGraph.kt`.
3. **Step 2 (`REQ-UI-2789.2`)**: Add `drawXAxisTicks` + tick math helper in `TelemetryChartCanvasHelpers.kt`; migrate all 4 time/distance tick loops in `ElevationProfile.kt` (`2` sites) and `TelemetryMetricGraph.kt` (`2` sites), preserving `R.string.legend`.
4. **Step 3 (`REQ-UI-2789.3`)**: Extend `PathPreviewMap.kt` (`PreviewPolyline`, `additionalPaths`, `boundsPaddingPx`, `onMapLongClick`, inspection fallback, composite bounds) and migrate `ClusterMapPreviewThumbnail` (`ClusterItem` & `UnclusteredWorkoutItem`) in `WorkoutClusterComponents.kt`; verify `ImportBackupTabsScreen.kt` untouched (`git diff`).
5. **Step 4 (`REQ-UI-2789.4` / `TST-UI-2789`)**: Check `TelemetryMetricGraph.kt` LoC (`<= 1,500` -> remove from `ArchitectureConformanceContractTest.MAX_MONOLITH_FILE_LOC_BASELINE`); author behavioral unit/Compose tests (`TelemetryChartCanvasHelpersTest.kt`, expanded `PathPreviewMapBoundsTest.kt` & `ClusterItemThumbnailComposeTest.kt`, tagged `TST-UI-2789.1`..`.3`, zero reflection-only/source-grep UI tests per `ATT-9179`); capture dedicated Light/Dark `docs/engineering/walkthroughs/img/ATT-6041_*.png` (leaving historical PNGs unmodified) and inspect via `read_file`.

## 6. Verification & Rollback Plan
* **Tier-1 Command**: `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.components.workoutlaps.WorkoutLapsIconContractTest" --tests "com.atrainingtracker.trainingtracker.localization.HardcodedStringsAndUnitsContractTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.UiReflectionOnlyTestAuditContractTest"` + `python3 tools/verify_requirement_governance.py --repo-root . --base-ref sprint/2026-11-20_03_Nacht`.
* **Visual & Target Verification**: Inspect dedicated `ATT-6041_*.png` via `read_file` (4-point `Rule 8.5` checklist); live `emulator-5554` verification in Stage 5/6.
* **Rollback**: Isolated on `feature/ATT-6041` until Stage 6 `PASS`.
