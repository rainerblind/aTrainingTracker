# Stage 3: Implementation Plan — ATT-8726

**Ticket**: `ATT-8726` (`1 SP`, `Bug`, `severity-major`, `tier-a`, `REQ-TRK-073.1`, `TST-TRK-073.1`) | **Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8726` | **Date**: 2026-11-24

## 1. Architectural Design, Compose State Safety & Upgrade-Path Behavior (SWE.2)
- **Pure State-Transition Helper in `RadarOverlayMath` (`RadarEdgeOverlay.kt`)**:
  - Preserve existing `resolveRailMaxRange(radarState: RadarState): Float` completely untouched for stateless contract compatibility (`TST-TRK-073.1`).
  - Add pure, idempotent helper `resolveLatchedRailMaxRange(previousMaxRangeMeters: Float, radarState: RadarState): Float`:
    ```kotlin
    fun resolveLatchedRailMaxRange(
        previousMaxRangeMeters: Float,
        radarState: RadarState
    ): Float {
        if (radarState.connectionState != RadarConnectionState.CONNECTED || radarState.targets.isEmpty()) {
            return MAX_RADAR_RANGE_METERS
        }
        val instantMaxRange = resolveRailMaxRange(radarState)
        return if (previousMaxRangeMeters > MAX_RADAR_RANGE_METERS) {
            MAX_RADAR_RANGE_V2_METERS
        } else {
            instantMaxRange
        }
    }
    ```
  - Guarantees: output is always strictly in `{140.0f, 175.0f}`; upward expansion `140 -> 175` is immediate; shrinking `175 -> 140` occurs strictly on `targets.isEmpty()` (All Clear) or `connectionState != CONNECTED`; idempotent under repeated recompositions with the same `RadarState`.
- **Single Hoisted Session Holder (`RadarRangeLatchSession` in `RadarEdgeOverlay.kt`)**:
  - Mirror `RadarThreatHysteresisSession` (`RadarEdgeOverlay.kt:504-543`): a private plain non-Compose class holding `private var latchedMaxRangeMeters: Float = RadarOverlayMath.MAX_RADAR_RANGE_METERS` with `fun resolve(radarState: RadarState): Float`.
  - Instantiated once at top-level of `RadarEdgeOverlay` (above the `if (edgeSide == RadarEdgeSide.FULL_SCREEN)` split) via:
    ```kotlin
    val rangeLatchSession = remember { RadarRangeLatchSession() }
    val maxRangeMeters = remember(radarState) { rangeLatchSession.resolve(radarState) }
    ```
  - Strictly adheres to `coding_standards.md` §1.10 (Rule 66: zero `MutableState` writes during composition) and ensures All Clear (`targets.isEmpty()`), `DISCONNECTED_ERROR`, and mid-approach `edgeSide`/`markerStyle` switches are handled uniformly from one source of truth.
- **Consumer Wiring (`RadarEdgeOverlay.kt`)**:
  - Pass `maxRangeMeters: Float` into `RadarFullScreenThreatOverlay(...)` (replacing `L624` stateless call; automatically flows into both `RadarFullScreenVehicleMarker` and `RadarTopDownVehicleMarker`) and into `RadarVerticalRail(...)` (replacing `L1000` stateless call).
  - Add non-invasive `.testTag("RadarRailTarget_${target.targetId}")` to the `RadarVerticalRail` target dot `Box` (`L1039-1052`) so Robolectric Compose UI tests verify monotonic upward Y-bounds on `LEFT`/`RIGHT` rails with zero visual or gesture impact.
- **UI Consistency (`docs/design_guidelines.md`) & Upgrade-Path Behavior (`coding_standards.md` §5.6)**:
  - Zero new UI controls, colors, typography, spacing, or string changes; preserves `LEFT`/`RIGHT` dot geometry (`REQ-TRK-073.2`), `FULL_SCREEN` capsule/top-down markers (`REQ-TRK-089`, `REQ-TRK-098`), and 100% touch pass-through (`REQ-TRK-062.3`).
  - Existing users immediately benefit on live V1/ANT+ and simulator rides with zero preference or DB migration; V1 approaches starting `<= 140 m` continue to use full `140.0 m` scale unchanged.

## 2. Atomic Implementation Steps & Monolith LoC Budget
| Step | Target Files & LoC Plan | Change Summary | Verification |
| :--- | :--- | :--- | :--- |
| **1. Latch Helper & Wiring** | `RadarEdgeOverlay.kt` (`1,334 -> ~1,370 / 1,500`, `+~36` LoC) | Add `RadarOverlayMath.resolveLatchedRailMaxRange`, private `RadarRangeLatchSession`, hoist `maxRangeMeters` in `RadarEdgeOverlay`, wire into `RadarFullScreenThreatOverlay` & `RadarVerticalRail`, add `RadarRailTarget_${target.targetId}` testTag | `RadarEdgeOverlayTest`, `RadarTopDownVehicleMarkerTest`, `ArchitectureConformanceContractTest` |
| **2. Unit & Behavioral Compose Tests** | `RadarEdgeOverlayTest.kt` (`1,813 -> ~1,960` LoC) | Add pure latch state-machine unit tests (`TST-TRK-073.1a`) and Robolectric Compose UI monotonic-Y + All-Clear-reset tests covering `RIGHT`/`LEFT` rail, `FULL_SCREEN` `COMPACT_BADGE`, and `FULL_SCREEN` `TOP_DOWN_VEHICLES` (`TST-TRK-073.1b`); `0` `src/main/` file reads | Incremental `--rerun` Gradle suite |
| **3. Verification, Emulator Smoke & Living Docs** | `docs/requirements/req_tracking_cockpit_part2.md`, `docs/tests/tst_tracking_cockpit_part2.md`, `docs/engineering/walkthroughs/ATT-8726_walkthrough.md`, `docs/engineering/test_evidence/tickets/ATT-8726/` | Run targeted `--rerun` tests, `emulator-5554` install/launch smoke + `Rule 8.5` Light/Dark PNG inspection via `read_file`, promote `REQ-TRK-073` / `TST-TRK-073` to `[Verified]`, run `verify_requirement_governance.py --sync-indexes` & `auto_gate.py --ticket ATT-8726 --tier tier-a --write-bundle --advance-jira` | `auto_gate.py`, `verify_requirement_governance.py` |

## 3. Verification Command & Hard Invariants
- **Incremental Targeted `--rerun` (Rule 7.5)**: `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.ui.tracking.radar.RadarEdgeOverlayTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.radar.RadarTopDownVehicleMarkerTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.SystemLiveTrackingRequirementsContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"`
- **Hard Invariants**: `RadarEdgeOverlay.kt <= 1,500` LoC; stateless `resolveRailMaxRange(radarState)` preserved; `0` Compose snapshot writes during composition; `0` clickable/pointer modifiers added to rail/markers (`REQ-TRK-062.3`); `0` string/color/DB/decoder changes; `0` `src/main/` test file reads (`REQ-PRO-049.1`); **`Rule 4.1` Code Lock strictly honored — zero edits to `app/src/main/...` until Gate 3 `PASS`**.
