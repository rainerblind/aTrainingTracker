# Stage 1 Analysis Report: ATT-2947 - Align Live Climb cockpit sheet visual design with Live Segment popup

**Ticket**: [ATT-2947](https://atrainingtracker.atlassian.net/browse/ATT-2947)  
**Sub-task**: [ATT-3014](https://atrainingtracker.atlassian.net/browse/ATT-3014) (`[Analysis]`)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2947`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Forensic Root Cause Investigation

During tracking and navigation with mock and live GPS replay, a visual discrepancy was identified between the Strava Live Segment bottom sheet (`LiveSegmentSheet.kt` / `SegmentHeader.kt` / `SegmentLiveDetails.kt`) and the Live Climb cockpit sheet (`LiveClimbSheet.kt`).

While `LiveSegmentSheet` follows the app's modern Material 3 design system:
* Standardized 32dp sport/domain icon in the top header row.
* High-prominence, bold title typography (`MaterialTheme.typography.titleLarge`, `FontWeight.Bold`, single-line with ellipsis).
* Right-aligned category/metric badge (`MetricBadge` / category chip) in the top row.
* Dedicated second subtitle row with left-aligned primary status indicator and right-aligned counter/PR time.
* Standard `HorizontalDivider` with 0.5dp thickness and 16dp horizontal padding.
* Asymmetric 2-column live telemetry progression (Left: Bold title/body distance progress and elevation; Right: Bold monospace headline for real-time instantaneous metric).

In contrast, `LiveClimbSheet.kt` was constructed during initial ClimbPro engine prototyping with an ad-hoc layout:
* Inverted chip placement (climb category badge placed on the far left before the climb title).
* Subdued climb title (`titleMedium.copy(fontWeight = FontWeight.Bold)`) under a small route counter label (`labelSmall`).
* Inconsistent horizontal divider placement (profile canvas rendered above the divider and telemetry metrics).
* Symmetric 3-column equal-weight metric tiles (`ClimbHudMetric`) with small typography that lack visual hierarchy and glanceability while cycling.

---

## 2. Chesterton's Fence Requirement Archaeology

1. **Original Requirement ID & Target**:
   * `REQ-MAP-027` (*ClimbPro Real-Time Live Climb Cockpit Sheet with Dynamically Colored Gradient Profile & Elevation Pacing*, ATT-1281).
2. **Historical Origin & Commit Trace**:
   * Introduced in Sprint 2026-40.14 (`ATT-1281`) to deliver real-time live climb tracking with dynamic grade-colored elevation profile canvases.
3. **Root Reason for Existing Formulation**:
   * Initial implementation focused primarily on the mathematical accuracy of the dynamic elevation profile canvas (`ClimbProfileCanvas`), gradient color interpolation, and distance/elevation calculations. Header and metric HUD layouts were quickly drafted without harmonizing with the newly overhauled `LiveSegmentSheet` design guidelines (`REQ-UI-196`).
4. **Preservation of Core Invariants**:
   * **Dynamic Elevation Profile Canvas**: `ClimbProfileCanvas` rendering, gradient band coloring (Cat 4 through HC), rider pin animation, and clipping bounds MUST be preserved.
   * **Telemetry Correctness**: Distance to summit, remaining elevation gain, and instantaneous gradient calculations remain exact.
   * **State Lifecycle**: Gated bottom sheet presentation on enabled tracking tabs (`REQ-UI-327` / `ATT-2945`) MUST NOT be altered.
   * **Test Contracts**: `LiveClimbSheetLayoutTest` contracts (`ClimbProfileCanvas`, `ClimbCategoryChip`, `ClimbStatusBadge`, and metric labels) MUST be preserved.

---

## 3. Scope Bounding & Invariants Enforcement

### 3.1 In Scope
* Refactoring `LiveClimbSheet.kt` to harmonize layout, spacing, and typography with `SegmentHeader.kt` and `SegmentLiveDetails.kt`:
  1. *Top Header Row*: 32dp Terrain/Climb icon (`Icons.Default.Terrain`, primary tint), bold `titleLarge` climb name with single-line ellipsis, and right-aligned `ClimbCategoryChip`.
  2. *Subtitle Row*: Left-aligned `ClimbStatusBadge` (with approaching distance or climb status) and right-aligned route climb counter (`Climb X of Y` in `labelLarge` bold primary).
  3. *Standard Divider*: `HorizontalDivider` with 0.5dp thickness and 16dp horizontal padding.
  4. *Live Telemetry HUD*: Asymmetric 2-column layout (Left: remaining distance in `titleLarge` bold and remaining elevation in `bodyLarge` bold; Right: instantaneous gradient in `headlineMedium` monospace bold).
  5. *Elevation Profile*: Color-coded gradient profile curve seamlessly nested with consistent padding and theme contrast.
* Updating `LiveClimbSheetLayoutTest.kt` to enforce the new visual contracts.
* 9-language localization parity for any refined string resources.

### 3.2 Out of Scope
* Modifying climb detection algorithms or `LiveClimbsRepository` state machine.
* Altering bottom sheet presentation gating (`SensorGridScreen.kt` / `ATT-2945`).
* Database schema modifications in `Climbs.db`.

---

## 4. Proposed Architecture & Design Specification

### 4.1 Component Layout Hierarchy (`LiveClimbSheet.kt`)

```
LiveClimbSheet (Column: fillMaxWidth, background: surface, padding: 16.dp horizontal, 8.dp vertical)
 ├── Top Header Row (Row: spacedBy 12.dp, verticalAlignment: CenterVertically)
 │    ├── Icon (Icons.Default.Terrain, 32.dp, tint: primary)
 │    ├── Text (climb.name, style: titleLarge bold, maxLines: 1, ellipsis, modifier: weight(1f))
 │    └── ClimbCategoryChip (climb.category, right-aligned)
 │
 ├── Subtitle Row (Row: SpaceBetween, verticalAlignment: CenterVertically)
 │    ├── ClimbStatusBadge (APPROACHING / ON_CLIMB / FINISHED)
 │    └── Text (routeIndex / totalRouteClimbs or elevation gain, style: labelLarge bold primary)
 │
 ├── HorizontalDivider (thickness: 0.5.dp, color: outlineVariant alpha 0.5f, padding: vertical 4.dp)
 │
 ├── Telemetry HUD Row (Row: SpaceBetween, verticalAlignment: CenterVertically)
 │    ├── Column (Left)
 │    │    ├── Text (remainingDistance to summit, style: titleLarge bold)
 │    │    └── Text (remainingElevationGain, style: bodyLarge bold, color: onSurfaceVariant)
 │    └── Column (Right: End-aligned)
 │         ├── Text (currentGradePercent, style: headlineMedium bold monospace, color: primary)
 │         └── Text (R.string.climb_grade, style: labelSmall, color: onSurfaceVariant)
 │
 ├── Spacer (height: 6.dp)
 └── ClimbProfileCanvas (height: 64.dp, liveClimb: liveClimb)
```

---

## 5. Verification & Test Strategy

1. **Structural & Contract Unit Tests (`LiveClimbSheetLayoutTest.kt`)**:
   - Verify 32dp climb icon, `titleLarge` typography, right-aligned `ClimbCategoryChip`.
   - Verify `ClimbStatusBadge` presence in subtitle row.
   - Verify asymmetric 2-column telemetry HUD and `headlineMedium` monospace grade presentation.
   - Verify `ClimbProfileCanvas` rendering.
2. **Localization Audit**:
   - Ensure all string tokens exist across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
3. **Full Clean-Room Test Suite**:
   - Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 6. Next Stage Readiness

* Analysis is complete, bounds are locked, and Chesterton's fence invariants are strictly respected.
* Proceeding to Gate 1 audit and Stage 2 Requirement & Test Specification (`REQ-UI-328` / `TST-UI-288`).
