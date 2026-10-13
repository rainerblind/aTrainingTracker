# Stage 2 Requirement & Test Specification: ATT-2947 - Align Live Climb cockpit sheet visual design with Live Segment popup

**Ticket**: [ATT-2947](https://atrainingtracker.atlassian.net/browse/ATT-2947)  
**Sub-task**: [ATT-3015](https://atrainingtracker.atlassian.net/browse/ATT-3015) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2947`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-328)

### 1.1 Requirement Definition
* **Requirement ID**: `REQ-UI-328`
* **Title**: Live Climb Cockpit Sheet Visual Design Alignment with Live Segment System
* **Type**: Functional & UI Specification
* **Target Release**: `V4.9.39`
* **Status**: Specified
* **Amends/Complements**: Amends and harmonizes `REQ-MAP-027` (*ClimbPro Real-Time Live Climb Cockpit Sheet with Dynamically Colored Gradient Profile & Elevation Pacing*, ATT-1281) with `REQ-UI-196` (*Live Segment Sheet Architecture*).
* **Parent Ticket**: ATT-2947

### 1.2 Description
The system shall refactor `LiveClimbSheet.kt` to harmonize layout hierarchy, spacing, and typography with the Strava Live Segment popup (`SegmentHeader.kt` / `SegmentLiveDetails.kt`):

1. *Top Header Row (`LiveClimbSheet.kt`)*:
   - Dedicated 32dp terrain/climb icon (`Icons.Default.Terrain`, tint = `MaterialTheme.colorScheme.primary`).
   - High-prominence climb title rendered in `MaterialTheme.typography.titleLarge`, `FontWeight.Bold`, single-line with `TextOverflow.Ellipsis`, assigned `Modifier.weight(1f)`.
   - Right-aligned `ClimbCategoryChip` badge in the top row.
2. *Subtitle Status & Route Counter Row (`LiveClimbSheet.kt`)*:
   - Left: Live climb status label/badge (`ClimbStatusBadge`), displaying approaching distance (when `APPROACHING`), "On Climb" (when `ON_CLIMB`), or "Summit" (when `FINISHED`).
   - Right: Route climb counter (e.g. "Climb 2 of 5") or total ascent gain formatted in `MaterialTheme.typography.labelLarge`, `FontWeight.Bold`, `color = MaterialTheme.colorScheme.primary`.
3. *Standardized Section Divider*:
   - `HorizontalDivider` with 0.5dp thickness, `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)`, and `16.dp` horizontal padding.
4. *Live Telemetry HUD Progression*:
   - Left column: Remaining distance to summit in `MaterialTheme.typography.titleLarge` bold and remaining elevation gain in `MaterialTheme.typography.bodyLarge` bold (`color = MaterialTheme.colorScheme.onSurfaceVariant`).
   - Right column (end-aligned): Current instantaneous gradient percentage in `MaterialTheme.typography.headlineMedium` bold monospace font (`color = MaterialTheme.colorScheme.primary`), labeled with "Grade" in `MaterialTheme.typography.labelSmall`.
5. *Elevation Profile Presentation*:
   - Seamlessly integrated `ClimbProfileCanvas` (height: 64dp) displaying dynamic grade color bands and real-time rider position marker.
6. *9-Language Localization Parity*:
   - String resources for all labels, counters, and status badges maintained across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
7. *Preservation of System Invariants*:
   - Exact mathematical calculation of distance to summit, remaining elevation gain, and instantaneous gradient.
   - Dynamic elevation profile canvas geometry, gradient color mapping, and rider pin interpolation.
   - BottomSheetScaffold suppression on disabled/inactive tabs (`REQ-UI-327` / `ATT-2945`).
   - 100% full clean-room unit test pass rate across the full test suite.

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: `REQ-MAP-027` (*ClimbPro Real-Time Live Climb Cockpit Sheet with Dynamically Colored Gradient Profile & Elevation Pacing*, ATT-1281).
2. **Historical Origin & Commit Trace**: Introduced in Sprint 2026-40.14 (`ATT-1281`).
3. **Root Reason for Existing Formulation**: The initial implementation focused on profile canvas math and gradient interpolation. The header and HUD metrics were created ad-hoc with inverted chip placement and small typography, divergent from the `SegmentHeader` design standard.
4. **Preservation of Core Invariants**: `ClimbProfileCanvas` math, rider pin animation, `ClimbCategoryChip` color coding, tab gating (`ATT-2945`), and full test pass rate remain strictly preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Scenario 1: Top Header Alignment**:
  - *Given* an active live climb rendered in `LiveClimbSheet`,
  - *When* inspecting the top header row,
  - *Then* it displays a 32dp climb icon on the left,
  - *And* the climb name in `titleLarge` bold font with ellipsis,
  - *And* the climb category chip (`ClimbCategoryChip`) right-aligned on the far right.

* **Scenario 2: Subtitle Status & Route Counter Row**:
  - *Given* an active climb on a route with 5 climbs,
  - *When* approaching climb 2 in 250m,
  - *Then* the second row displays `ClimbStatusBadge` with "Approaching (250 m)" on the left,
  - *And* the route counter "Climb 2 of 5" on the right in `labelLarge` bold primary color.

* **Scenario 3: Standard Divider & Telemetry HUD Layout**:
  - *Given* `LiveClimbSheet` during active climbing (`ON_CLIMB`),
  - *When* inspecting the telemetry metrics,
  - *Then* a 0.5dp divider separates the header from telemetry,
  - *And* the left column displays remaining distance to summit in `titleLarge` bold and remaining elevation in `bodyLarge` bold,
  - *And* the right column displays instantaneous gradient in `headlineMedium` bold monospace font with a "Grade" label.

* **Scenario 4: Elevation Profile Canvas Presentation**:
  - *Given* `LiveClimbSheet` rendered in the cockpit,
  - *When* inspecting the elevation profile,
  - *Then* `ClimbProfileCanvas` renders smoothly with gradient color bands and the rider pin indicator.

---

## 4. Test Specification (TST-UI-288)

### 4.1 Unit & Contract Tests (`LiveClimbSheetLayoutTest.kt`)
1. `testLiveClimbSheet_composableExistsAndIsPublic`: Verifies signature and accessibility.
2. `testLiveClimbSheet_harmonizedHeaderTokensAndTypography`: Verifies 32dp icon, `titleLarge`, right-aligned `ClimbCategoryChip`, and `ClimbStatusBadge`.
3. `testLiveClimbSheet_asymmetricTelemetryHudTokens`: Verifies remaining distance in `titleLarge`, remaining elevation in `bodyLarge`, and current grade in `headlineMedium` monospace font.
4. `testLiveClimbSheet_profileCanvasPreserved`: Verifies `ClimbProfileCanvas` inclusion.
5. `testLiveClimbSheet_localizationParity`: Verifies string tokens exist across all 9 locales.
6. `testCleanRoomFullRegression`: Executes `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 5. Traceability Matrix

| Requirement ID | Test Case ID | Test Class | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-328` (Clause 1-2) | `TST-UI-288.1` | `LiveClimbSheetLayoutTest` | Top header row, 32dp icon, titleLarge, right-aligned category, status row | Specified |
| `REQ-UI-328` (Clause 3-4) | `TST-UI-288.2` | `LiveClimbSheetLayoutTest` | Standard divider, asymmetric telemetry HUD (distance, elevation, monospace grade) | Specified |
| `REQ-UI-328` (Clause 5) | `TST-UI-288.3` | `LiveClimbSheetLayoutTest` | Elevation profile canvas rendering | Specified |
| `REQ-UI-328` (Clause 6) | `TST-UI-288.4` | `LiveClimbSheetLayoutTest` | 9-language localization parity | Specified |
| `REQ-UI-328` (Clause 7) | `TST-UI-288.5` | Full Suite Regression | Clean-room 100% full-suite test pass rate | Specified |
