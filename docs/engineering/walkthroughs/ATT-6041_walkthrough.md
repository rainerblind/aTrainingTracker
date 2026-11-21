# Stage 5: Walkthrough & Verification - ATT-6041: Cards & Graphs UI Deduplication (Marginal Histograms, X-Axis Ticks & PathPreviewMap)

**Ticket**: [ATT-6041](https://issue-tracker/ATT-6041)  
**Sub-task**: Pending creation (`[Test]`)  
**Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467)  
**Requirement Mapping**: `REQ-UI-2789` (`REQ-UI-2789.1`..`REQ-UI-2789.4`)  
**Test Mapping**: `TST-UI-2789` (`TST-UI-2789.1`..`TST-UI-2789.3`)  
**Branch**: `feature/ATT-6041` (verified code SHA `2b4277ca`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-21  

---

## 1. Executive Summary & Verification Overview
* **Implemented Changes (`2b4277ca`)**:
  * `TelemetryChartCanvasHelpers.kt` (`+279` LoC): Zero-allocation `inline fun <T> DrawScope.drawMarginalHistogramBars(...)` (+ `computeMarginalBarVerticalBounds`) and `DrawScope.drawXAxisTicks(...)` (+ `computeXAxisTickValues`, `formatElevationDistanceTickLabel`) unifying all 5 marginal histogram loops and 4 time/distance X-axis tick loops under Rules 69 & 70.
  * `ElevationProfile.kt` (`1,454 -> 1,405` LoC, **`-49` LoC**) & `TelemetryMetricGraph.kt` (`1,836 -> 1,728` LoC, **`-108` LoC**, well below `1,965` LoC baseline): Migrated all 5 histogram loops & 4 tick loops while keeping `AlignedMarginalLinearDistribution`/`AlignedMarginalZoneDistribution` in `TelemetryMetricGraph.kt` and `R.string.legend` in `ElevationProfile.kt`.
  * `PathPreviewMap.kt` (`676 -> 822` LoC, **`+146` LoC**) & `WorkoutClusterComponents.kt` (`1,359 -> 1,195` LoC, **`-164` LoC**): Added `PreviewPolyline`, `additionalPaths` (composite bounds without Start/End fallback pollution), dual `boundsPaddingPx` threading (`estimateInitialCameraPosition` + `newLatLngBounds` + `remember`/`MapEffect` keys), `onMapLongClick` overlay + stub-polyline inspection fallback, and hardened `decodePolylineSafely` (`IndexOutOfBoundsException`); delegated `ClusterMapPreviewThumbnail` (`ClusterItem` & `UnclusteredWorkoutItem`) to `PathPreviewMap` via memoized `buildClusterThumbnailPolylines`. Interactive `ClusterNamingDialog` (`ImportBackupTabsScreen.kt:992-1022`) and `MapDetailLayout`/scrubbing (`ATT-8134`) remain strictly untouched.
* **Rule 7.5 Doc-Only Fast Path**: Zero production code, test code, or PNG assets modified since Gate 4 `PASS` (`ATT-9433` @ `2b4277ca`); Gate 4 verified test evidence (`277/277` across `61` suites) is reused directly without redundant Gradle execution.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-2789.1` | `TST-UI-2789.1` | Unit & Robolectric Compose `Canvas` (`TelemetryChartCanvasHelpersTest`) | **PASSED** (`7/7`; Rules 69/70, 5 histogram configs) | `Verified` |
| `REQ-UI-2789.2` | `TST-UI-2789.1` | Unit & Robolectric Compose `Canvas` (`TelemetryChartCanvasHelpersTest`, `ElevationProfile*`, `TelemetryMetricGraph*`) | **PASSED** (`4` tick configs, gridline/label separation) | `Verified` |
| `REQ-UI-2789.3` | `TST-UI-2789.2` | Robolectric Compose UI & Unit (`ClusterThumbnailPathPreviewDelegationTest`, `PathPreviewMapBoundsTest`, `ClusterItemThumbnailComposeTest`) | **PASSED** (`4/4`, `8/8`, `5/5`; `ClusterNamingDialog` untouched) | `Verified` |
| `REQ-UI-2789.4` | `TST-UI-2789.3` | Contract Suites, Safety Linters & `Rule 8.5` PNG Visual Critique | **PASSED** (`277/277` across 61 suites, `4/4` linters, 2 Light/Dark PNGs) | `Verified` |

---

## 3. Automated Test & Visual Evidence (Gate 4 @ `2b4277ca`, Reused per `Rule 7.5`)

### Tier 1 Targeted UI, Map, Cluster & Contract Scope (`ATT-9433` @ `2b4277ca`)
```text
TelemetryChartCanvasHelpersTest: 7/7 PASS (behavioral Compose + math, TST-UI-2789.1/.3)
ClusterThumbnailPathPreviewDelegationTest: 4/4 PASS (behavioral Compose + bounds, TST-UI-2789.2/.3)
PathPreviewMapBoundsTest: 8/8 PASS | ClusterItemThumbnailComposeTest: 5/5 PASS | MapPreviewLiteModeTest: 6/6 PASS
ClusterItemAndFilterPolishTest: 7/7 PASS | WorkoutClusterSplitMapPreviewTest: 4/4 PASS | ClusterNamingDialogLabelsVisualContractTest: 5/5 PASS
WorkoutLapsIconContractTest: 5/5 PASS | HardcodedStringsAndUnitsContractTest: 8/8 PASS | ThemeColorCentralizationAuditTest: 5/5 PASS
ArchitectureConformanceContractTest: 11/11 PASS | UiReflectionOnlyTestAuditContractTest: 4/4 PASS
ElevationProfile* & TelemetryMetricGraph* suites: 100% PASS
TOTAL (Gate 4 ATT-9433 @ 2b4277ca): 277/277 PASS across 61 suites (0 fail, 0 errors, 0 skipped)
Safety Linters: check_secrets_hygiene, lint_cursor_safety, lint_lifecycle_safety, lint_safe_enum -> 4/4 PASS
```

### `Rule 8.5` Visual Inspection (`read_file` Verified by `agent1` & `agent2` — 4-Point Checklist PASS)
* `docs/engineering/walkthroughs/img/ATT-6041_charts_marginal_ticks_preview.png`: Light/Dark `ElevationProfile` + `TelemetryMetricGraph` + `AlignedMarginalLinearDistribution` + `AlignedMarginalZoneDistribution` — 1:1 horizontal plot alignment (`50.dp`/`52.dp`), uninterrupted `0.15f` empty-bin tracks (`Rule 69`), zero out-of-domain stacking at `y = 0` (`Rule 70`), crisp ticks & `"9%"` overlay.
* `docs/engineering/walkthroughs/img/ATT-6041_cluster_thumbnails_preview.png`: Light/Dark `ClusterItem` (multi-polyline heatmap `alpha = 0.2f` + authoritative `TTColor.RouteSelected` `zIndex = 10f`) and `UnclusteredWorkoutItem` via `PathPreviewMap` at `175×120 dp` (`12.dp` corners/spacer, Start/End/Apex markers, single-line metadata). Historical PNGs (`ATT-8736`, `ATT-8748`) unmodified. Live `emulator-5554` target verification deferred to Stage 6 Cold Review per `Rule 3.3` & `Rule 7.5` Doc-Only Fast Path.

---

## 4. Living Documentation & Branch Integrity
* **Living Documentation**: `REQ-UI-2789` (`docs/requirements/req_ui_design_system_part2.md` `v1.0.3`, `3,983 <= 4,000` lines) and `TST-UI-2789` (`docs/tests/tst_ui_design_system_part2.md` `v1.0.5`, `3,993 <= 4,000` lines) promoted to `[Verified]`; master indexes synchronized (`docs/requirements.md` & `docs/tests.md` `v1.0.101`).
* **Clean-Checkout Governance**: `verify_requirement_governance.py --repo-root . --base-ref f8c03d6f --check-verified-evidence --test-results-dir /tmp/empty_check_6041` exits `0`.
* **Branch State (`Rule 3.3`)**: `feature/ATT-6041` preserved intact for Stage 6 Cold Review (NOT merged onto `sprint/2026-11-20_03_Nacht`).
