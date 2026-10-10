# Stage 4 Implementation Report: ATT-3104 - Remove remaining grey container surfaces from in-ride fork decision card

**Ticket**: [ATT-3104](https://atrainingtracker.atlassian.net/browse/ATT-3104)  
**Sub-task**: [ATT-3141](https://atrainingtracker.atlassian.net/browse/ATT-3141) (`[Implementation]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.8`  
**Branch**: `feature/ATT-3104`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Implementation Summary

Stage 4 (Software Construction & Implementation) for [ATT-3104](https://atrainingtracker.atlassian.net/browse/ATT-3104) has been executed in strict adherence to the approved Stage 3 Implementation Plan and `REQ-UI-337`.

### 1.1 Key Modifications

1. **`ForkDecisionCard.kt` Container Decoupling & Zero Tonal Elevation**:
   - Replaced outer `Card` with `Surface` configuring explicit `shadowElevation = 6.dp` and strict `tonalElevation = 0.dp`. This decouples shadow elevation (retaining depth over the map and telemetry HUD) from surface elevation tinting, completely eliminating implicit Material 3 `surfaceTint` overlays.
   - Updated `ForkDirectionGroup` to enforce `tonalElevation = 0.dp`, eliminating the previously hardcoded `1.dp` M3 elevation overlay tint.
   - Added parameter `overlayAlpha: Float` to `ForkDirectionGroup` and set `color = MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha)`. This guarantees uniform surface transparency between outer container and inner groups.
   - Preserved `outlineVariant` borders (`0.8f` for outer container, `0.6f` for direction group) and 0.5dp line dividers (`0.5f` vertical, `0.4f` horizontal).
   - Ensured all nested rows (`ForkRouteRow`), direction columns, and route columns remain clean surfaces without conflicting grey backgrounds.

2. **`ForkDecisionCardTest.kt` Contract Tests**:
   - Added contract test `forkDecisionCard_enforcesZeroTonalElevationAndCleanSurfaces` verifying:
     - Outer container and direction groups strictly enforce `tonalElevation = 0.dp` (`TST-UI-297.1`).
     - Outer container specifies `shadowElevation = 6.dp`.
     - Zero usage of `surfaceVariant` for container backgrounds (`TST-UI-297.2`).
     - `ForkDirectionGroup` supports and applies `overlayAlpha` to surface color (`TST-UI-297.3`).

---

## 2. Test Execution

* Executed targeted tests via `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.ForkDecisionCardTest"`:
  ```
  BUILD SUCCESSFUL in 13s
  32 actionable tasks: 5 executed, 27 up-to-date
  ```
  All 6 test cases passed cleanly with 0 failures and 0 errors.

---

## 3. Invariants & Governance Verification

- `ForkDecisionState` and `ForkBranchOption` models unchanged.
- `onRouteSelected` and `onDismiss` callbacks fully preserved.
- Directional grouping and 9-language localization parity fully preserved.
- Code modularity: `ForkDecisionCard.kt` is ~310 lines, well below the 400-line modularity threshold.
