# Stage 3 Implementation Plan: ATT-8995 - Adequate SensorFieldView Sparkline Canvas Height with Value + Graph

**Ticket**: [ATT-8995](https://rainerblind.atlassian.net/browse/ATT-8995) | **Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-20_02_Spät` | **Branch**: `feature/ATT-8995`  
**Requirement Mapping**: `REQ-UI-2788` (`REQ-UI-2788.1`..`.3`) | **Test Spec**: `TST-UI-2788` (`TST-UI-2788.1`..`.3`)  
**Date**: 2026-11-20 | **Author**: AI Agent 1 (`--as agent1`)

---

## 1. Architecture & SWE.2 Component Design
- **Pure UI Layout Refinement (`com.atrainingtracker.trainingtracker.ui.tracking`)**: Zero new classes/services, zero SQLite/schema changes, zero `strings*.xml` edits (preserving cached `R_jar` for fast incremental Kotlin builds), and zero edits to protected monoliths (`TrackerService.java`, `WorkoutRepository.kt`, `DeviceManager.java`, `DevicesDatabaseManager.java`).
- **Layout Geometry Contract**:
  - `SensorSparklineView.kt`: `Column` containing `Canvas(Modifier.fillMaxWidth().weight(1f).testTag("sensor_sparkline_canvas"))` above the single-line X-axis `Row` (`-T`, `-T/2`, `Now`) styled at `fontSize = 10.sp`, `lineHeight = 12.sp`, `PlatformTextStyle(includeFontPadding = false)` (~`12.dp` row height, verified warning-free under Compose BOM `2026.09.00`).
  - `resolveSparklineHeight(viewSize, isGraphOnly)`:
    - `isGraphOnly == false` (Value + Graph): `46.dp` (`<= SMALL`), `52.dp` (`NORMAL`), `60.dp` (`LARGE`), `68.dp` (`>= XLARGE`) -> `>= 34.dp` measured Canvas height (`>= 28.dp` net curve amplitude after `2 * 3.dp` `verticalPaddingPx`) at `ViewSize.NORMAL`.
    - `isGraphOnly == true` (Graph-only): `48.dp` (`<= SMALL`), `56.dp` (`NORMAL`), `68.dp` (`LARGE`), `84.dp` (`>= XLARGE`) -> strictly monotonically taller than `isGraphOnly == false` across all `ViewSize` tiers.
  - `SensorFieldView.kt:504-508`: Replaces rigid `.height(...)` in Value+Graph mode with `Modifier.heightIn(min = resolveSparklineHeight(fieldState.viewSize, isGraphOnly = false)).weight(1f).padding(horizontal = 4.dp)` so `IntrinsicSize.Min` rows expand cleanly, multi-column neighbors claim surplus vertical height, and fixed-height cards (`116.dp`/`132.dp`) shrink gracefully without clipping Value text or X-axis labels.
  - `EditSensorFieldDialog.kt:526`: Aligns sparkline preview container height `44.dp -> 52.dp` (1:1 WYSIWYG parity with `ViewSize.NORMAL`).

---

## 2. Atomic Implementation Steps

| Step | Req / Test | Target Files | Atomic Action |
| :--- | :--- | :--- | :--- |
| **Step 0** | Code Lock | Jira `ATT-938x` | Verify Stage 3 Gate PASS via `jira_util.py check-gate` before touching `app/`. |
| **Step 1** | `REQ-UI-2788.1` / `TST-UI-2788.1` | `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorSparklineView.kt` | Update `resolveSparklineHeight` to `46/52/60/68.dp` (`isGraphOnly = false`) and `48/56/68/84.dp` (`isGraphOnly = true`); add `Modifier.testTag("sensor_sparkline_canvas")` to `Canvas`; tighten X-axis `Text` style to `fontSize = 10.sp, lineHeight = 12.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)` (confirming `0` deprecation warnings on Compose BOM `2026.09.00`). |
| **Step 2** | `REQ-UI-2788.2`, `REQ-UI-2788.3` / `TST-UI-2788.2` | `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt` | Switch Value+Graph sparkline modifier (`SensorFieldView.kt:504-508`) to `Modifier.heightIn(min = resolveSparklineHeight(fieldState.viewSize, isGraphOnly = false)).weight(1f).padding(horizontal = 4.dp)`; align `EditSensorFieldDialog.kt:526` preview height `44.dp -> 52.dp`. |
| **Step 3** | `TST-UI-2788.1`..`.3` | `SensorSparklineViewTest.kt`, `SensorFieldViewGraphOnlyLayoutTest.kt`, `SensorFieldViewSparklineLayoutComposeTest.kt` (new) | Update unit expectations in `SensorSparklineViewTest` & `SensorFieldViewGraphOnlyLayoutTest` (asserting `isGraphOnly = true > isGraphOnly = false` monotonicity); create Robolectric `androidx.compose.ui.test.junit4.v2.createComposeRule()` `SensorFieldViewSparklineLayoutComposeTest` using distinct parent tile `testTag` + `hasAnyAncestor(...)` to assert: (a) `NORMAL` Value+Graph in `Row(IntrinsicSize.Min)` has `sensor_sparkline_canvas` `getUnclippedBoundsInRoot().height >= 34.dp` (`>= 28.dp` net curve height) & visible `-2:00`/`-1:00`/`Now`, (b) multi-column `NORMAL` beside `XLARGE` expands beyond single-tile baseline, (c) fixed `132.dp`/`116.dp` cards keep Value & X-axis labels unclipped; capture Light & Dark/AMOLED PNG `docs/engineering/walkthroughs/img/ATT-8995_sparkline_value_graph_layout.png` and visually inspect via `read_file` (`Rule 8.5`). |
| **Step 4** | Governance & Living Docs | `docs/requirements/req_ui_design_system_part5.md`, `docs/tests/tst_ui_design_system_part3.md`, `docs/requirements.md`, `docs/tests.md` | Promote `REQ-UI-2788` & `TST-UI-2788` to `[Implemented]` (`0` edits to `tst_tracking_cockpit.md`, `tst_ui_design_system_part2.md`, `strings*.xml`, or monoliths); run locked incremental Gradle verification + 4 Python safety linters + `verify_requirement_governance.py`. |

---

## 3. Verification Plan & Invariants
- **Targeted Gradle Suites (`parallel_sprint_lock.py`, incremental `--rerun`, NEVER `--rerun-tasks`)**:
  - `SensorSparklineViewTest`, `SensorFieldViewGraphOnlyLayoutTest`, `SensorFieldViewLayoutTest`, `SensorFieldViewSparklineLayoutComposeTest` (new), `SensorFieldViewDynamicZoneScreenshotTest`, `EditSensorFieldDialogScreenshotTest`, `ArchitectureConformanceContractTest`, `DeadCodeEliminationContractTest`, `SourceTextReadingAuditContractTest`, `UiReflectionOnlyTestAuditContractTest` + `:app:assembleDebug`.
- **Invariants**: `0` new Kotlin compiler/deprecation warnings; `0` `strings*.xml` edits; `0` edits to `tst_tracking_cockpit.md` / `tst_ui_design_system_part2.md` / protected monoliths; `0` Compose snapshot-state writes (`Rule 66`); `0` `src/main` test reads; `Rule 8.5` PNG inspected via `read_file`.
