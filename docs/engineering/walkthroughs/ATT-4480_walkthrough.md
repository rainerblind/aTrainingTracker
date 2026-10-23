# Stage 5 Walkthrough: ATT-4480 — True Line-Density PolylineHeatmapTileProvider & Thermal Color LUT

## 1. Executive Summary
Replaced Google Maps Utils' point-cloud Gaussian `HeatmapTileProvider` with `PolylineHeatmapTileProvider`, a high-performance Web Mercator line-density `TileProvider` that eliminates synthetic point interpolation (`densifyPath`) and speed-dependent vertex density bias. Added a pure JVM bitwise ARGB `ThermalColorLut` (electric blue -> magenta -> fiery orange -> incandescent white), updated `PeriodMapUtils.kt` (`PeriodMapVisuals.heatmapProvider: TileProvider?`), and migrated `MapContentScope.kt` to line-density rasterization with thread-safe multi-worker rendering.

## 2. Key Architectural Changes
| File / Directory | Summary of Changes |
| :--- | :--- |
| `ThermalColorLut.kt` | 256-entry ARGB thermal color lookup table using bitwise integer math (`((a and 0xFF) shl 24) or ...`), bypassing unmocked Android framework dependencies for pure JVM testability. |
| `PolylineHeatmapTileProvider.kt` | Custom `TileProvider` computing Web Mercator tile bounds with stroke margin, $O(1)$ polyline bounding-box culling (`TileProvider.NO_TILE` on disjoint bounds), distance-to-segment anti-aliased line rasterization, dynamic range scaling, and PNG compression. |
| `PeriodMapUtils.kt` | Decoupled `PeriodMapVisuals` to accept generic `TileProvider?` instead of point-cloud `HeatmapTileProvider?`; replaced `createHeatmapProvider` with `PolylineHeatmapTileProvider` for Week, Month, and Year periods, eliminating `densifyPath` overhead. |
| `MapContentScope.kt` | Migrated `produceState<TileProvider?>` to instantiate `PolylineHeatmapTileProvider` off the main thread with zoom-adaptive stroke widths. |
| `MapUtils.kt` | Annotated legacy `createHeatmapProvider` with `@Deprecated` pointing callers to `PolylineHeatmapTileProvider`. |
| `PolylineHeatmapTileProviderTest.kt` | 6 unit tests covering empty paths, disjoint culling, anti-aliased line rasterization, thermal LUT progression, coordinate wrapping, and thread concurrency. |
| `PeriodMapVisualsTest.kt` | 2 unit tests verifying null provider for Day and active `PolylineHeatmapTileProvider` for Week/Month/Year. |

## 3. Verification & Test Results
* **Tier 1 Unit & Contract Tests**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.build.*"` -> `BUILD SUCCESSFUL` (100% pass rate).
* **Resource Compilation Gate**: `./gradlew processDebugResources` -> `BUILD SUCCESSFUL`.
* **Requirement Governance Audit**: `python3 tools/verify_requirement_governance.py` -> `EXIT CODE 0` (clean pass).
* **Target Release Version**: `V4.9.40` (FixVersion assigned to parent ticket upon sprint acceptance per Rule 8).

## 4. Requirement & Test Traceability
| Requirement ID | Test ID | Status | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-056` | `TST-MAP-058` | `Verified` | `PolylineHeatmapTileProviderTest` (empty polylines, disjoint culling, pixel rasterization, LUT mapping, multi-threaded safety) |
| `REQ-PER-017` | `TST-PER-023` | `Verified` | `PeriodMapVisualsTest` (Day returns null; Week, Month, Year return active `PolylineHeatmapTileProvider` with calibrated opacity/stroke) |
