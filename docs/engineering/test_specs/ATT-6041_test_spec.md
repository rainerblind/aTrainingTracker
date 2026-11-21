# Stage 2: Requirement & Test Specification - ATT-6041: Cards & Graphs UI Deduplication (Marginal Histograms, X-Axis Ticks & PathPreviewMap)

**Ticket**: [ATT-6041](https://issue-tracker/ATT-6041)  
**Sub-task**: Pending creation (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467)  
**Requirement Mapping**: `REQ-UI-2789` (`REQ-UI-2789.1`..`REQ-UI-2789.4` — refines `REQ-UI-220`, `REQ-UI-251`, `REQ-UI-431`, `REQ-UI-443`, `REQ-UI-446`, `REQ-UI-493.3`, `REQ-UI-505.3`, `REQ-MAP-068`)  
**Test Spec ID**: `TST-UI-2789` (`TST-UI-2789.1`..`TST-UI-2789.3`)  
**Branch**: `feature/ATT-6041`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-21  

---

## 1. Requirement Specification (`REQ-UI-2789`)

### 1.1 Problem Statement & Rationale
* `ElevationProfile.kt` and `TelemetryMetricGraph.kt` duplicate 5 marginal histogram `Canvas` loops and 4 time/distance X-axis tick loops, while `WorkoutClusterComponents.kt` (`ClusterMapPreviewThumbnail` for `ClusterItem` & `UnclusteredWorkoutItem`) duplicates ~190 LoC of Lite-Mode `GoogleMap` boilerplate instead of delegating to `PathPreviewMap.kt`. Consolidating these into parameterized helpers reduces duplication and gives cluster thumbnails `REQ-MAP-068` Web Mercator camera seeding/retry without altering visual output or touching the interactive `ClusterNamingDialog` map.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. **Parameterized Marginal Histogram Helper (`REQ-UI-2789.1`)**: A shared `DrawScope` helper (`TelemetryChartCanvasHelpers.kt`) SHALL consolidate all 5 marginal histogram loops (`ElevationProfile.kt:1091-1134`; `TelemetryMetricGraph.kt:726-759, 761-799, 1497-1532, 1664-1707`) across both `LinearHistogramBin` and `TelemetryHistogramBin` inputs. It SHALL filter out-of-domain bins and clamp `[rangeMin, rangeMax]` to `[dataMin, dataMax]` before `valueToY` (`Rule 70`), skip zero-height clamped bins (`if (rectHeight <= 0f) continue`) prior to any optional `minBarHeightPx`, draw unconditional `alpha = 0.15f` background tracks (`Rule 69`), gate foreground bars on `durationSec > 0L && barWidthPx > 0f`, and parameterize `cornerRadiusPx` (`0f` sharp vs `2.dp`), `foregroundAlpha` (`1.0f` vs `0.85f`), and optional `onBinDrawn` callback (for `AlignedMarginalZoneDistribution` `"${bin.percentage.toInt()}%"` text overlay).
2. **Parameterized X-Axis Tick Helper (`REQ-UI-2789.2`)**: A shared `DrawScope` helper (`TelemetryChartCanvasHelpers.kt`) SHALL consolidate all 4 time/distance X-axis tick `while` loops (`ElevationProfile.kt:777-839`, `TelemetryMetricGraph.kt:906-976`), accepting `(startValue, visibleSpan, step, plotStartX, plotWidth, formatLabel, shouldRenderLabel, onTickInBounds, onLabelRendered)` so `ElevationProfile` preserves bottom ticks drawn only on rendered labels + `24.dp` spacing + locale distance labels, while `TelemetryMetricGraph` preserves full-height vertical gridlines on every in-bounds tick + `12.dp` spacing + `TelemetryMetricUtils.formatMilestoneLabel`.
3. **`PathPreviewMap` Extension & Cluster Thumbnail Delegation (`REQ-UI-2789.3`)**: `PathPreviewMap.kt` SHALL add optional styled `additionalPaths` (`PreviewPolyline` supporting color/alpha `0.2f`, stroke width `4f`/`6f`, `RoundCap`/`JointType.ROUND`, `zIndex = 10f`), `boundsPaddingPx: Int = PREVIEW_BOUNDS_PADDING_PX` threaded into BOTH `estimateInitialCameraPosition(..., boundsPaddingPx, density)` AND `CameraUpdateFactory.newLatLngBounds(resolvedBounds, boundsPaddingPx)`, and `onMapLongClick: (() -> Unit)? = null` rendering a transparent `combinedClickable` overlay `Box` above `GoogleMap` + `DarkMapAntiFlashOverlay` in `PathPreviewLiveMap`. `buildCompositePreviewBounds` SHALL include valid `additionalPaths` points when `targetBounds == null` while `actualStart`/`actualEnd` fallback MUST NOT read `additionalPaths`; `PathPreviewInspectionCanvas` SHALL render `additionalPaths` with graceful fallback for non-decodable stub polylines (`"sample_route"`/`"sample_workout"`) and `combinedClickable` support. `ClusterItem` and `UnclusteredWorkoutItem` (`WorkoutClusterComponents.kt`) SHALL delegate via `ClusterMapPreviewThumbnail` to `PathPreviewMap` with `boundsPaddingPx = THUMBNAIL_BOUNDS_PADDING_PX` (`18`). `ClusterNamingDialog` (`ImportBackupTabsScreen.kt:992-1022`) MUST remain interactive, non-Lite-Mode, and untouched.
4. **Contract, LoC, Localization & Visual Invariants (`REQ-UI-2789.4`)**: `AlignedMarginalLinearDistribution`/`AlignedMarginalZoneDistribution` MUST remain in `TelemetryMetricGraph.kt`; legacy tokens checked by `WorkoutClusterSplitMapPreviewTest`, `WorkoutLapsIconContractTest`, `ClusterItemAndFilterPolishTest`, `ClusterNamingDialogLabelsVisualContractTest`, and `HardcodedStringsAndUnitsContractTest` (`R.string.legend`) MUST remain intact; zero `Color(0x...)` outside `Color.kt`; if `TelemetryMetricGraph.kt <= 1,500` LoC, remove it from `MAX_MONOLITH_FILE_LOC_BASELINE`; **Localization Parity**: `N/A - no UI strings`; dedicated `Rule 8.5` PNGs (`docs/engineering/walkthroughs/img/ATT-6041_*.png`) MUST be captured without modifying historical PNG assets.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (`TST-UI-2789.1`)**: *Given* `ElevationProfile` and `TelemetryMetricGraph` rendering portrait/landscape marginal histograms and time/distance X-axis ticks, *When* out-of-domain bins, empty `durationSec == 0L` bins, thin bins, or tight tick spacings occur, *Then* the shared `DrawScope` helpers enforce Rules 69/70 clamping, exact per-caller corner/alpha/text-overlay/gridline semantics, and identical visual output.
* **Criterion 2 (`TST-UI-2789.2`)**: *Given* `ClusterItem` (multi-polyline heatmap + authoritative green route + long-click menu) and `UnclusteredWorkoutItem`, *When* rendered via `PathPreviewMap`, *Then* `boundsPaddingPx = 18` flows into both initial camera seeding and `newLatLngBounds`, `additionalPaths` contribute to composite bounds without synthesizing Start/End fallback markers, `onMapLongClick` triggers context menu, `175×120 dp` / `360 dp` clamping holds, and `ClusterNamingDialog` stays interactive and untouched.
* **Criterion 3 (`TST-UI-2789.3`)**: *Given* Stage 4/5 verification, *When* Light/Dark Robolectric PNGs (`ATT-6041_*.png`) and targeted contract/unit suites run, *Then* 100% pass with zero reflection-only/source-grep UI tests (`REQ-PRO-132`/`REQ-PRO-049.1`) and clean `read_file` 4-point visual inspection.

---

## 2. Test Specification (`TST-UI-2789`)

### Test Case 1: Marginal Histogram & X-Axis Tick Helper Characterization (`TST-UI-2789.1`)
* **Scope**: Unit & Behavioral Compose Test (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryChartCanvasHelpersTest.kt`)
* **Action**: Exercise domain-clamping/Rule 69-70 geometry helper + Compose `Canvas` rendering across all 5 histogram modes (portrait/landscape, linear/zoned, `0L` empty bins, out-of-range bins, `0.85f` vs `1.0f` alpha, `"%d%%"` overlay) and all 4 X-axis tick modes (time/distance, `onTickInBounds` vs `onLabelRendered`, collision thresholds).
* **Expected Result**: Out-of-domain bins produce zero rects; in-domain empty bins draw `0.15f` track only; non-empty bins draw proportional fg bar; ticks & gridlines match legacy coordinates 1:1.

### Test Case 2: `PathPreviewMap` Multi-Polyline, Padding & Cluster Thumbnail Parity (`TST-UI-2789.2`)
* **Scope**: Behavioral Robolectric Compose & Unit Tests (`PathPreviewMapBoundsTest.kt`, `ClusterItemThumbnailComposeTest.kt`)
* **Action**: Verify `boundsPaddingPx` in `estimateInitialCameraPosition` and `resolvePreviewBounds` with `additionalPaths` (confirming `actualStart`/`actualEnd` fallback ignores `additionalPaths`), test `ClusterItem` click + long-click overlay dispatch, stub-polyline inspection fallback, and `411 dp`/`360 dp` layout bounds.
* **Expected Result**: All bounds, camera-seeding, click/long-click, and layout assertions pass without touching `ClusterNamingDialog`.

### Test Case 3: `Rule 8.5` Visual Inspection & Targeted Contract Regression (`TST-UI-2789.3`)
* **Scope**: Robolectric PNG Capture (`docs/engineering/walkthroughs/img/ATT-6041_*.png`) + Tier-1 Targeted Suites
* **Command**: `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.components.workoutlaps.WorkoutLapsIconContractTest" --tests "com.atrainingtracker.trainingtracker.localization.HardcodedStringsAndUnitsContractTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.UiReflectionOnlyTestAuditContractTest"`
* **Expected Result**: 100% pass, historical PNGs unmodified, new `ATT-6041_*.png` verified via `read_file` against the 4-point checklist (plus live `emulator-5554` check in Stage 5/6).

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-2789.1` | Parameterized marginal histogram `DrawScope` helper (5 loops, Rules 69/70) | `TST-UI-2789.1` | Unit & Compose Test |
| `REQ-UI-2789.2` | Parameterized X-axis tick `DrawScope` helper (4 loops, time & distance) | `TST-UI-2789.1` | Unit & Compose Test |
| `REQ-UI-2789.3` | `PathPreviewMap` multi-polyline/padding/long-click + cluster delegation (`ClusterNamingDialog` excluded) | `TST-UI-2789.2` | Robolectric Compose & Unit Test |
| `REQ-UI-2789.4` | Contract guards, LoC ratchet, zero hex colors, `Rule 8.5` PNG inspection | `TST-UI-2789.3` | Contract Test & Visual Audit |
