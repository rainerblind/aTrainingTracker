# Stage 3: Implementation Plan - ATT-8733: Redesign Full-Screen Radar Vehicle Markers with Legible Badges and Distinct V2 Vehicle Size Icons

**Ticket**: [ATT-8733](https://atrainingtracker.atlassian.net/browse/ATT-8733) (`2 SP`) | **Parent Epic**: [ATT-1286](https://atrainingtracker.atlassian.net/browse/ATT-1286)  
**Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8733`  
**Requirement Mapping**: `REQ-TRK-089` (`REQ-TRK-089.1`..`REQ-TRK-089.3`), refining `REQ-TRK-062` | **Test Mapping**: `TST-TRK-089`  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-18

---

## 1. Traceability, Architecture Scope & System Invariants
* **Living Docs**: `REQ-TRK-089` [Planned] (`docs/requirements/req_tracking_cockpit_part2.md`) $\leftrightarrow$ `TST-TRK-089` [Planned] (`docs/tests/tst_tracking_cockpit.md`).
* **SWE.2 / SWE.3 & Reachability**: Isolated Compose UI refinement in `RadarEdgeOverlay.kt` (already mounted at `TrackingTabsScreen.kt:1107`) + `TTColor` tokens in `Color.kt`. Zero DB, ViewModel, Repository, Service, or signature changes (`docs/architecture.md` & `detailed_design/` untouched).
* **Preserved Invariants**:
  1. **100% Touch Pass-Through (`REQ-TRK-062.3`)**: Zero `.clickable`, `.pointerInput`, or `.pointerInteropFilter` modifiers on `RadarFullScreenThreatOverlay`, vehicle markers, or lane guides (`HorizontalPager`, `GoogleMap`, and edge back-swipe remain unhindered).
  2. **Clear-Badge Suppression (`ATT-8679`) & Conditional Pulse**: `RadarStatusBadgePill` stays absent when clear (`!isDisconnectError && !hasActiveThreats`); `rememberInfiniteTransition` remains gated strictly on `overallThreatLevel == ThreatLevel.FAST_APPROACHING`.
  3. **Edge Rail & Contract Purity**: `LEFT`/`RIGHT` `RadarVerticalRail` dot geometry (`REQ-TRK-073.2`) unchanged; zero inline `Color(0x...)` outside `Color.kt`; all `@Preview` functions strictly `private` (`DeadCodeEliminationContractTest`); `0` `src/main` reads in `RadarEdgeOverlayTest.kt` (`SourceTextReadingAuditContractTest`); 9-locale reuse of `radar_vehicle_size_*` (zero new keys).

---

## 2. UI Consistency (`docs/design_guidelines.md`)
* **Reference Component**: `RadarFullScreenThreatOverlay` & `RadarStatusBadgePill` (`RadarEdgeOverlay.kt`), `TTColor` (`Color.kt`).
* **Marker Layout & Sizing (§5.2 / §5.3)**: Replace fixed `28×20` / `32×22` / `36×26.dp` boxes with content-sized `Surface` capsules (`RoundedCornerShape(12.dp)`, `shadowElevation = 4.dp`, `widthIn(min = ...)` + `wrapContentWidth()`, `padding(horizontal = 8.dp, vertical = 4.dp)`) and bold tabular-numeral (`fontFeatureSettings = "tnum"`) `11–13.sp` distance text (`maxLines = 1, softWrap = false`) so `15m`, `128m`, and `160m` never clip. Measure actual marker `IntSize` via `onSizeChanged` so `xOffsetPx`/`yOffsetPx` clamp strictly inside `[0, usablePx - markerPx]` even at `0.85f`/`1.0f` edges.
* **V2 Icons & Theme Contrast (§5.4 / §5.5)**: Map `VehicleSizeCategory` to `androidx.compose.material.icons.filled.*` (`material-icons-extended` already in `app/build.gradle:271`): `CAR` $\to$ `Icons.Filled.DirectionsCar` (`16.dp`, `12.sp`), `TRUCK_OR_BUS` $\to$ `Icons.Filled.LocalShipping` (`18.dp`, `13.sp`, wider min width), `UNKNOWN_OR_LIGHT` $\to$ `Icons.Filled.TwoWheeler` (`15.dp`, `11.sp`). Apply `RadarOverlayMath.resolveThreatColor` (`TTColor.RadarThreatAmber` / pulsing `TTColor.RadarThreatRed`) with high-contrast outer contrast border/scrim & subtle non-interactive lane-guide/depth tokens sourced strictly from `Color.kt` (`TTColor`).
* **Localization & Previews (§4)**: Map `UNKNOWN_OR_LIGHT` accessibility to existing `R.string.radar_vehicle_size_light` (eliminating hardcoded `"Target"`). Add `private` `@Preview`s for `FULL_SCREEN` Light, Dark, and Map-backdrop states with mixed V2 targets.

---

## 3. Atomic Step Sequence (Stage 4 Construction)
* **Gate Lock (`Rule 4.1`)**: Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` and verify `GATE_PASSED` before editing `app/src/`.
1. **Step 1 — Add Radar Marker Contrast/Guide Tokens (`Color.kt`, `REQ-TRK-089.2`)**:
   * Add centralized `TTColor` tokens for full-screen marker contrast rim/scrim and subtle translucent lane guides (`RadarFullScreenLaneGuide`, `RadarMarkerStrokeLight`, `RadarMarkerStrokeDark` as needed), keeping `0` `Color(0x...)` literals out of `RadarEdgeOverlay.kt`.
2. **Step 2 — Redesign `RadarFullScreenThreatOverlay` & Add `private` `@Preview`s (`RadarEdgeOverlay.kt`, `REQ-TRK-089.1/.2/.3`)**:
   * Extract a single parameterized helper/spec for `VehicleSizeCategory` (icon vector, icon size, min marker width, font size, localized `R.string.radar_vehicle_size_*` description) with no duplicated per-variant UI blocks.
   * Render non-interactive subtle lane guides (zero pointer modifiers), proximity perspective scaling toward `0m`, and `Surface` capsule markers (`shadowElevation`, contrast rim, `Row` with `Icon` + `tnum` `11–13.sp` `Text`, `onSizeChanged` bounds clamping). Keep `RadarVerticalRail` and `RadarStatusBadgePill` unchanged.
   * Add `private` `@Preview` composables for `FULL_SCREEN` (Light, Dark, Map-like backdrop) and check `app/detekt-baseline.xml` if method complexity thresholds shift.
3. **Step 3 — Robolectric Compose Tests & `Rule 8.5` Screenshot (`RadarEdgeOverlayTest.kt`, `TST-TRK-089.1/.2/.3`)**:
   * Add `createComposeRule()` tests (`0` `src/main` reads) verifying: (a) `15m`/`128m`/`160m` render unclipped within container bounds near `0.85f`/`1.0f` edges; (b) `TRUCK_OR_BUS` bounds exceed `CAR`/`UNKNOWN_OR_LIGHT`; (c) distinct localized `radar_vehicle_size_{car,truck,light}` semantics (zero `"Target"`); (d) `assertHasNoClickAction()` on markers/guides, clear-badge suppression (`ATT-8679`), `FAST_APPROACHING`-only pulse, and unchanged `LEFT`/`RIGHT` rail bounds.
   * Capture composite Light/Dark/Map PNG at `docs/engineering/walkthroughs/img/ATT-8733_radar_fullscreen_markers_preview.png` and inspect via `read_file`.

---

## 4. Verification & Rollback Plan
* **Batched `Rule 7.5` Test Run (`--max-workers=1 --rerun-tasks`) + Lint**:
  ```bash
  ./gradlew :app:testDebugUnitTest --max-workers=1 --rerun-tasks \
    --tests "com.atrainingtracker.trainingtracker.ui.tracking.radar.RadarEdgeOverlayTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" \
    --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest" \
    --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest" \
    --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" \
    --tests "com.atrainingtracker.trainingtracker.build.SystemArchitectureDesignContractTest" \
    --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
  ./gradlew :app:lintDebug
  ```
* **Governance & Visual Gate**: `python3 tools/verify_requirement_governance.py --repo-root .` (exit `0`) + `read_file` inspection of `ATT-8733_radar_fullscreen_markers_preview.png`.
* **Rollback**: Isolated to `feature/ATT-8733`; `sprint/2026-11-18_01_Früh` remains untouched until Stage 6 PASS.
