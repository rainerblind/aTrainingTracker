# Stage 3 Implementation Plan - ATT-8679: Remove Green Connected Status Circle/Pill (`RadarStatusBadgePill`)

**DOC-ID**: `DOC-PLN-ATT-8679` | **Ticket**: [ATT-8679](https://atrainingtracker.atlassian.net/browse/ATT-8679) (`1 SP`) | **Parent Epic**: [ATT-1286](https://atrainingtracker.atlassian.net/browse/ATT-1286)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8679`  
**Requirement Mapping**: `REQ-TRK-062` (`.2`/`.3`/`.4`/`.6`), `REQ-TRK-064` (`.1`/`.2`) | **Test Mapping**: `TST-TRK-062`, `TST-TRK-064`  
**Author**: `--as agent1` | **Date**: 2026-11-18

---

## 1. Traceability, Architecture Scope & System Invariants
* **Living Docs**: `REQ-TRK-062` & `REQ-TRK-064` (`docs/requirements/req_tracking_cockpit_part2.md`) $\leftrightarrow$ `TST-TRK-062` & `TST-TRK-064` (`docs/tests/tst_tracking_cockpit.md`).
* **SWE.2 / SWE.3 Impact**: `N/A` — UI-only change in `RadarEdgeOverlay.kt` + resource cleanup (`ic_device_available.xml`, `values*/strings_radar.xml`). Zero DB, ViewModel, Repository, or service state/concurrency changes (`docs/architecture.md` & `detailed_design/` untouched).
* **Preserved Invariants**:
  1. `DISCONNECTED_ERROR` retains amber hazard `RadarStatusBadgePill` (`ic_device_not_available`) and 1-tap `onReconnectClick` (`REQ-TRK-062.4`).
  2. Active threats (`APPROACHING` / `FAST_APPROACHING`, unsnoozed or snoozed) retain `RadarStatusBadgePill` (`targets.size`, muted bell/countdown) and `onBadgeClick` (`REQ-TRK-064.2`).
  3. Upward target kinematics (`140m`/`175m -> 0m`), 100% touch pass-through on rail/full-screen targets, `.navigationBarsPadding()`, and `TrackingTabsScreen` visibility gating (`isRadarOverlayVisible`) stay intact.
  4. `radar_overlay_clear_mode*` keys remain untouched; `radar_overlay_clear` removed cleanly across all 9 locales (`TranslationParityTest`); zero `src/main` file reads in UI tests (`REQ-PRO-049.1`).

## 2. UI Consistency (`docs/design_guidelines.md`)
* **HUD Overlay & Spacing (§5.2 / §5.7)**: Guarding `Spacer(8.dp)` + `RadarStatusBadgePill` + `Spacer(6.dp)` on `showStatusBadge` prevents an orphan `14.dp` downward gap above `RadarVerticalRail` in `LEFT`/`RIGHT` clear states. `FULL_SCREEN` clear renders zero overlay chrome and zero touch targets.
* **Color & Icons (§5.4 / §5.5)**: Removes `TTColor.RadarClearGreen` circular badge + `ic_device_available`; preserves `TTColor.RadarDisconnectedAmber` (`ic_device_not_available`), `TTColor.RadarThreatAmber`, and pulsing `TTColor.RadarThreatRed`.
* **Previews & Visual Inspection (`Rule 8.5`)**: Existing 4 `@Preview` composables (`RadarEdgeOverlay.kt:796–898`) remain strictly `private fun`; capture Light/Dark PNG `docs/engineering/walkthroughs/img/ATT-8679_radar_edge_overlay_preview.png` (`ClearLight`, `ApproachingDark`, `FastThreatAmoled`, `Disconnected`) and inspect via `read_file`.

## 3. Atomic Step Sequence (Stage 4 Construction)
* **Gate Lock (`Rule 4.1`)**: Run `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` and verify `GATE_PASSED` before any `app/src/` edit.
1. **Step 1 — Suppress Clear-State `RadarStatusBadgePill` & Guard Rail Spacers (`RadarEdgeOverlay.kt`, `REQ-TRK-062.2/.3/.4`)**:
   * Compute `val showStatusBadge = isDisconnectError || hasActiveThreats`.
   * In `FULL_SCREEN` (`L314–330`), render `RadarStatusBadgePill(...)` strictly inside `if (showStatusBadge)`.
   * In `LEFT`/`RIGHT` (`L344–361`), wrap `Spacer(8.dp)`, `RadarStatusBadgePill(...)`, and `Spacer(6.dp)` inside `if (showStatusBadge)`; attach `.testTag("RadarVerticalRail")` on `RadarVerticalRail` (`L716`) for deterministic Compose bounds testing.
   * Simplify `RadarStatusBadgePill` (`L481–672`): remove unused `isClear` parameter plus the `CONNECTING`, `snoozeState.isSnoozed && isClear`, and `isClear` branches (retaining `isDisconnectError`, `FAST_APPROACHING`, `APPROACHING`, and `isSnoozedWithThreats`); update KDoc (`L237`) and verify `app/detekt-baseline.xml` if needed.
2. **Step 2 — Remove Orphaned Drawable & 9-Locale String Resource (`REQ-TRK-062.6`)**:
   * Delete `app/src/main/res/drawable/ic_device_available.xml` (only referenced at `RadarEdgeOverlay.kt:656`).
   * Remove `<string name="radar_overlay_clear">...</string>` across all 9 `app/src/main/res/values*/strings_radar.xml` files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`), leaving `radar_overlay_clear_mode*` untouched.
3. **Step 3 — Behavioral Compose UI Tests & `Rule 8.5` Preview Capture (`RadarEdgeOverlayTest.kt`, `TST-TRK-062.3/.4/.6/.9`, `TST-TRK-064.2`)**:
   * Annotate `RadarEdgeOverlayTest` with `@RunWith(RobolectricTestRunner::class)`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, `@Config(sdk = [34], qualifiers = "w1080dp-h2200dp-xhdpi")` and add `createComposeRule()` (`0` `src/main` reads per `REQ-PRO-049.1`):
     - Assert `onNodeWithTag("RadarStatusBadge").assertDoesNotExist()` when `CONNECTED` + clear road (both unsnoozed and `audioSnoozeState.isSnoozed == true`) across `LEFT`, `RIGHT`, `FULL_SCREEN` (and `isRadarOverlayVisible` for Start/Tracking).
     - Assert `LEFT`/`RIGHT` `SLIM_RAIL` `RadarVerticalRail` `boundsInRoot().top` sits at `0.dp` (no `14.dp` shift); assert `FULL_SCREEN` clear has zero clickable/badge nodes; assert `AUTO_HIDE_3S` keeps zero clear badge after 3s timeout.
     - Assert `DISCONNECTED_ERROR` renders `RadarStatusBadge` + invokes `onReconnectClick`; assert `APPROACHING`/`FAST_APPROACHING` (unsnoozed & snoozed) renders `RadarStatusBadge` with target count + invokes `onBadgeClick`.
   * Capture composite Light/Dark PNG `docs/engineering/walkthroughs/img/ATT-8679_radar_edge_overlay_preview.png` (`ClearLight`, `ApproachingDark`, `FastThreatAmoled`, `Disconnected`) and inspect via `read_file`.

## 4. Verification & Rollback Plan
* **Tier-1 Commands**:
  * `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.radar.RadarEdgeOverlayTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"`
  * `./gradlew :app:lintDebug` (verify zero `UnusedResources` regressions from removed `ic_device_available.xml` / `radar_overlay_clear`).
* **Rollback**: Isolated to `feature/ATT-8679` (`sprint/2026-11-18_01_Früh` untouched until Stage 6 `PASS`).
