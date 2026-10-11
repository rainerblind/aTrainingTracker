# ATT-3104 Walkthrough: Remove Remaining Grey Container Surfaces from In-Ride Fork Decision Card

## Overview
- **Issue**: [ATT-3104](https://atrainingtracker.atlassian.net/browse/ATT-3104)
- **Requirement**: `REQ-UI-337`
- **Test Specification**: `TST-UI-297`
- **Sprint**: `2026-41.8`
- **Status**: Verified

## Problem Addressed
During Sprint 2026-41.7 Joint Review of ATT-2962, the athlete observed that grey/tinted container surfaces still lingered on the in-ride fork decision HUD card (`ForkDecisionCard.kt`).

Forensic analysis revealed three compounding factors that created the lingering grey container appearance:
1. `ForkDirectionGroup` explicitly specified `tonalElevation = 1.dp`, triggering Material 3 `surfaceColorAtElevation(1.dp)` to inject an elevation tint overlay on top of the surface, washing out pure white (`#FFFFFF`) in light mode and deep pitch black (`#000000`) in AMOLED mode.
2. The outer `Card` specified `elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)`. In Compose Material 3, `CardElevation` couples shadow elevation to tonal elevation, applying a 6dp (~11%) tonal elevation tint across the entire outer card.
3. `ForkDirectionGroup` used `color = MaterialTheme.colorScheme.surface` with full opacity (alpha = 1.0f) rather than matching the outer card's `overlayAlpha`, producing an optical discontinuity where the outer card frame was translucent while the inner group appeared as an opaque, tinted grey patch.

## Architectural Changes & Solution

1. **Outer Container Migration to Decoupled Surface (`ForkDecisionCard.kt`)**:
   - Replaced outer `Card` with `Surface`:
     ```kotlin
     Surface(
         shape = RoundedCornerShape(16.dp),
         color = MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha),
         border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
         shadowElevation = 6.dp,
         tonalElevation = 0.dp,
         modifier = Modifier
             .fillMaxWidth()
             .padding(horizontal = 16.dp, vertical = 6.dp)
     )
     ```
   - Explicitly decoupled `shadowElevation = 6.dp` (preserving drop shadow depth over the map or telemetry HUD) from `tonalElevation = 0.dp` (completely eliminating Material 3 elevation tint overlays).

2. **Inner Direction Group Zero Tonal Elevation & Opacity Uniformity**:
   - Updated `ForkDirectionGroup` to enforce `tonalElevation = 0.dp`, eliminating the 1.dp elevation overlay tint.
   - Added parameter `overlayAlpha: Float` to `ForkDirectionGroup` and set `color = MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha)`.
   - Forwarded `overlayAlpha = overlayAlpha` when instantiating `ForkDirectionGroup` within `ForkDecisionCard`.

3. **Pure Surface Palette Compliance**:
   - In Light Mode: Container surfaces, nested rows, and directional columns render on clean pure white (`LightSurface = #FFFFFF`).
   - In Dark Mode: Deep dark surfaces (`DarkSurface = #16161A`).
   - In AMOLED Mode: Pure AMOLED black (`AmoledSurface = #000000`).
   - Zero usage of `surfaceVariant` for any container backgrounds.

4. **Preservation of Structural Elements & Dividers**:
   - Retained 0.5dp `VerticalDivider` (`outlineVariant.copy(alpha = 0.5f)`) separating direction icons from route metrics.
   - Retained 0.5dp `HorizontalDivider` (`outlineVariant.copy(alpha = 0.4f)`) separating multiple routes under the same heading.
   - Preserved directional grouping, distance countdown, route selection, and dismiss button callbacks.

5. **Contract Tests (`ForkDecisionCardTest.kt`)**:
   - Added contract test `forkDecisionCard_enforcesZeroTonalElevationAndCleanSurfaces` verifying:
     - Outer container and direction groups strictly enforce `tonalElevation = 0.dp` (`TST-UI-297.1`).
     - Outer container specifies `shadowElevation = 6.dp`.
     - Zero usage of `surfaceVariant` for container backgrounds (`TST-UI-297.2`).
     - `ForkDirectionGroup` supports and applies `overlayAlpha` to surface color (`TST-UI-297.3`).

## Verification & Test Results
- **Targeted Test Suite**:
  - `ForkDecisionCardTest`: 100% pass across all 6 test cases (`BUILD SUCCESSFUL in 13s`).
- **Clean-Room Full Suite Regression**:
  - `./gradlew testDebugUnitTest` executed with 100% pass rate across all modules (zero failures, zero regressions).

## ASPICE Traceability
- **Requirement**: `REQ-UI-337` in `docs/requirements.md` (Verified)
- **Test Specification**: `TST-UI-297` in `docs/tests.md` (Verified)
- **Implementation Plan**: `docs/engineering/plans/ATT-3104_plan.md`
- **Analysis**: `docs/engineering/analysis/ATT-3104_analysis.md`
