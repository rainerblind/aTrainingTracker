# Stage 4 Implementation Report: ATT-2947 - Align Live Climb cockpit sheet visual design with Live Segment popup

**Ticket**: [ATT-2947](https://atrainingtracker.atlassian.net/browse/ATT-2947)  
**Sub-task**: [ATT-3017](https://atrainingtracker.atlassian.net/browse/ATT-3017) (`[Implementation]`)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2947`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary

This deliverable concludes Stage 4 (Software Construction & Implementation) for [ATT-2947](https://atrainingtracker.atlassian.net/browse/ATT-2947) in accordance with requirement `REQ-UI-328` and test specification `TST-UI-288`.

We refactored `LiveClimbSheet.kt` to harmonize its visual layout hierarchy, spacing, and typography with the Strava Live Segment popup design system (`SegmentHeader.kt` / `SegmentLiveDetails.kt`). The live climb sheet now renders a prominent 32dp terrain icon, bold `titleLarge` climb name, right-aligned category chip, structured subtitle status row, standardized horizontal divider, asymmetric 2-column live telemetry HUD, and seamlessly integrated elevation profile canvas.

---

## 2. Implemented Changes

### 2.1 UI Component Architecture (`LiveClimbSheet.kt`)
* **Top Header Row**:
  - Replaced ad-hoc leading category badge with leading 32dp `Icons.Default.Terrain` icon tinted with `MaterialTheme.colorScheme.primary`.
  - Upgraded climb name from `titleMedium` to `MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)` with single-line truncation (`TextOverflow.Ellipsis`) and `Modifier.weight(1f)`.
  - Positioned `ClimbCategoryChip` right-aligned on the far right of the top row.
* **Subtitle Row**:
  - Positioned `ClimbStatusBadge` on the left showing distance to start (when `APPROACHING`), "On Climb" (when `ON_CLIMB`), or "Summit" (when `FINISHED`).
  - Positioned route climb counter (`R.string.climb_route_counter`) or total climb ascent gain on the right with `MaterialTheme.typography.labelLarge`, `FontWeight.Bold`, `color = MaterialTheme.colorScheme.primary`.
* **Standardized Divider**:
  - Added standard `HorizontalDivider` with 0.5dp thickness, `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)`, and vertical padding.
* **Live Telemetry HUD**:
  - Replaced symmetric 3-column `ClimbHudMetric` layout with an asymmetric 2-column progression matching `SegmentLiveDetails`:
    - *Left Column*: Remaining distance to summit in `titleLarge` bold; remaining elevation gain in `bodyLarge` bold (`color = MaterialTheme.colorScheme.onSurfaceVariant`).
    - *Right Column*: Instantaneous gradient percentage in `headlineMedium` bold monospace font (`color = MaterialTheme.colorScheme.primary`), labeled with "Grade" in `labelSmall`.
* **Elevation Profile Presentation**:
  - Placed `ClimbProfileCanvas` beneath the live telemetry HUD with consistent height (64dp) and smooth contrast.

### 2.2 Unit & Contract Tests (`LiveClimbSheetLayoutTest.kt`)
* Extended `LiveClimbSheetLayoutTest.kt` with targeted test cases:
  - `testLiveClimbSheet_composableExistsAndIsPublic`: Verifies signature and accessibility.
  - `testLiveClimbSheet_structuralDesignAndTelemetryContracts`: Verifies background, profile canvas, category chip, status badge, and HUD metrics.
  - `testLiveClimbSheet_harmonizedHeaderTokensAndTypography`: Verifies 32dp Terrain icon, `titleLarge` bold title, and `labelLarge` route counter.
  - `testLiveClimbSheet_asymmetricTelemetryHudTokens`: Verifies `headlineMedium` monospace font for grade, `titleLarge` distance, and `bodyLarge` elevation.
  - `testLiveClimbSheet_localizationParityAcrossAll9Locales`: Verifies all climb strings exist and are non-empty across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 3. Verification & Targeted Test Suite

Targeted unit and contract tests executed cleanly:
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.climbs.LiveClimbSheetLayoutTest"
```
Result: **BUILD SUCCESSFUL in 12s** (All tests green).
