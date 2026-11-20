# Stage 2: Requirement & Test Specification - ATT-8995

**Ticket**: [ATT-8995](https://rainerblind.atlassian.net/browse/ATT-8995) | **Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-20_02_Spät` | **Branch**: `feature/ATT-8995`  
**Requirement Mapping**: `REQ-UI-2788` | **Test Spec ID**: `TST-UI-2788` | **Date**: 2026-11-20

---

## 1. Requirement Specification (`REQ-UI-2788`)

### 1.1 Attributes & Traceability
- **Living Documentation Shards**: `docs/requirements/req_ui_design_system_part5.md` (`REQ-UI-2788` `[Planned]`) & `docs/tests/tst_ui_design_system_part3.md` (`TST-UI-2788` `[Planned]`) — `0` edits to `docs/tests/tst_tracking_cockpit.md` or `docs/tests/tst_ui_design_system_part2.md`.
- **System Requirement(s)**: Derived (SW-only, `[SWE-Derived]` UI layout refinement; `0` external hardware/BLE/ANT+/FGS/cloud/data-exchange boundary changes).
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
- **Localization Parity**: `N/A - no new or modified UI strings` (`0` edits to `res/values*/strings*.xml`).

### 1.2 Functional & Visual Requirements
1. **`REQ-UI-2788.1` (Sparkline Minimum Heights, Monotonic Graph-Only Scaling & Compact X-Axis Leading)**:
   - `resolveSparklineHeight(viewSize, isGraphOnly)` in `SensorSparklineView.kt` SHALL resolve minimum container heights as:
     - `isGraphOnly == false` (Value + Graph): `46.dp` (`XSMALL`/`SMALL`), `52.dp` (`NORMAL`), `60.dp` (`LARGE`), `68.dp` (`XLARGE+`) — yielding `>= 34.dp` measured Canvas height (`>= 28.dp` net curve amplitude after `2 * 3.dp` `verticalPaddingPx`) at `ViewSize.NORMAL`.
     - `isGraphOnly == true` (Graph-only): `48.dp` (`XSMALL`/`SMALL`), `56.dp` (`NORMAL`), `68.dp` (`LARGE`), `84.dp` (`XLARGE+`) — strictly monotonically taller than `isGraphOnly == false` across every `ViewSize` so disabling Value never shrinks the Graph.
   - The 3 X-axis label `Text`s (`SensorSparklineView.kt:362-379`) SHALL apply `lineHeight = 12.sp` paired with `platformStyle = PlatformTextStyle(includeFontPadding = false)` on `MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp, platformStyle = PlatformTextStyle(includeFontPadding = false))` to eliminate wasted vertical leading without clipping `10.sp` glyphs.
   - The sparkline `Canvas` (`SensorSparklineView.kt:201-205`) SHALL attach `.testTag("sensor_sparkline_canvas")` for exact Compose layout-bound assertions.
2. **`REQ-UI-2788.2` (Flexible Value+Graph Vertical Weight & Constrained-Card Safety)**:
   - In `SensorFieldView.kt:504-508`, Value+Graph mode (`showValue == true && showGraph == true`) SHALL replace rigid `Modifier.height(...)` with `Modifier.heightIn(min = resolveSparklineHeight(fieldState.viewSize, isGraphOnly = false)).weight(1f).padding(horizontal = 4.dp)`.
   - Inside `Row(Modifier.height(IntrinsicSize.Min))` (`SensorGridScreen.kt:749,1122`), the tile SHALL expand cleanly to at least the resolved minimum height; alongside a taller neighbor, `.weight(1f)` SHALL claim surplus vertical space; inside externally fixed-height tiles (`132.dp`/`116.dp`), `.weight(1f)` SHALL shrink gracefully after Value+Bar without clipping Value text or X-axis labels.
3. **`REQ-UI-2788.3` (Dialog Preview Parity & Governance Invariants)**:
   - `EditSensorFieldDialog.kt:526` SHALL update its sparkline preview container height from `44.dp` to `52.dp` (1:1 WYSIWYG parity with `ViewSize.NORMAL`).
   - `0` edits to `strings*.xml`, protected monoliths, or `docs/tests/tst_tracking_cockpit.md`; `0` Compose snapshot-state writes (`Rule 66`); `0` new `src/main` reads in tests.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: Extends `REQ-TRK-059.5`, `REQ-TRK-059.9`, `REQ-TRK-059.10`, and `REQ-UI-438`.
- **Historical Origin & Commit Trace**: `ATT-1294` introduced `SensorSparklineView`; `ATT-6695` (`ef243477`) added the `-T / -T/2 / Now` X-axis label row; `ATT-6689` (`82e21ca5`) added `heightIn(min = ...).weight(1f)` for Graph-only mode to prevent `0px` collapse in `IntrinsicSize.Min` rows.
- **Root Reason for Existing Formulation**: `ATT-6695` underestimated inherited `16.sp` M3 `labelSmall` leading + `6.dp` Canvas padding and kept Value+Graph on rigid `.height(...)`.
- **Preservation of Core Invariants**: Preserves `0px` collapse immunity in `IntrinsicSize.Min` rows (`REQ-TRK-059.10`), X-axis `-T / -T/2 / Now` legibility & 9-locale parity, Dynamic Zone gradient & DFA alpha 1 inverted polarity (`REQ-TRK-082`, `ATT-8436`), placeholder mode (`REQ-TRK-059.11`), and `SensorGridScreen` 50% map-split scroll cap (`REQ-UI-358`).

---

## 3. Acceptance Criteria (Given-When-Then)
- **AC-1 (`REQ-UI-2788.1`)**: *Given* a `ViewSize.NORMAL` `SensorFieldView` tile with `showValue = true` and `showGraph = true` in a `Row(Modifier.height(IntrinsicSize.Min))`, *When* rendered via `v2.createComposeRule()`, *Then* `sensor_sparkline_canvas` measures `>= 34.dp` (`>= 28.dp` net curve amplitude after `6.dp` padding) and `-2:00`, `-1:00`, `Now` labels remain unclipped.
- **AC-2 (`REQ-UI-2788.1`)**: *Given* every `ViewSize` tier (`XSMALL..XLARGE`), *When* `resolveSparklineHeight(size, isGraphOnly)` is evaluated, *Then* `isGraphOnly == false` returns `46/52/60/68.dp` and `isGraphOnly == true` returns strictly taller `48/56/68/84.dp`.
- **AC-3 (`REQ-UI-2788.2`)**: *Given* a multi-column `Row(Modifier.height(IntrinsicSize.Min))` pairing a `NORMAL` Value+Graph tile beside an `XLARGE` neighbor, *When* rendered, *Then* `sensor_sparkline_canvas` expands vertically beyond its single-tile `NORMAL` baseline.
- **AC-4 (`REQ-UI-2788.2` & `.3`)**: *Given* fixed-height `132.dp` (`NORMAL`) and `116.dp` (`SMALL`) tiles in Light and Dark/AMOLED themes, *When* rendered, *Then* primary Value text and X-axis labels remain unclipped (`Rule 8.5` PNG verified) and `EditSensorFieldDialog` preview matches `52.dp`.

---

## 4. Test Specification (`TST-UI-2788`)

| Test Case ID | Req ID | Test Class & Target Method | Verification Assertion |
| :--- | :--- | :--- | :--- |
| `TST-UI-2788.1` | `REQ-UI-2788.1` | `SensorSparklineViewTest#testResolveSparklineHeight` & `SensorFieldViewGraphOnlyLayoutTest#testResolveSparklineHeight_graphOnlyMode_enforcesStrictlyPositiveHeights` | Assert exact `46/52/60/68.dp` (`isGraphOnly = false`) and strictly greater `48/56/68/84.dp` (`isGraphOnly = true`) across `XSMALL..XLARGE`. |
| `TST-UI-2788.2` | `REQ-UI-2788.1`, `REQ-UI-2788.2` | `SensorFieldViewSparklineLayoutComposeTest` (`androidx.compose.ui.test.junit4.v2.createComposeRule()`) | Measure `onNodeWithTag("sensor_sparkline_canvas").getUnclippedBoundsInRoot().height` in `IntrinsicSize.Min` (`>= 34.dp` at `NORMAL`), multi-column surplus expansion (`expandedHeight > singleTileHeight`), and fixed `132.dp`/`116.dp` cards (Value & X-axis labels visible and within card bounds). |
| `TST-UI-2788.3` | `REQ-UI-2788.1`..`.3` | `SensorFieldViewSparklineLayoutComposeTest#testCaptureSparklineValueGraphComparisonScreenshot_tstUi2788_3`, `SensorFieldViewDynamicZoneScreenshotTest`, `EditSensorFieldDialogScreenshotTest`, & 4 Contract Suites | Capture & `read_file`-audit Light/Dark PNG (`docs/engineering/walkthroughs/img/ATT-8995_sparkline_value_graph_layout.png`); confirm `ArchitectureConformance`, `DeadCodeElimination`, `SourceTextReadingAudit`, and `UiReflectionOnlyTestAudit` PASS. |
