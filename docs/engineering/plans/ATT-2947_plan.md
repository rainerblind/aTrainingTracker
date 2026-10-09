# Stage 3 Implementation Plan: ATT-2947 - Align Live Climb cockpit sheet visual design with Live Segment popup

**Ticket**: [ATT-2947](https://atrainingtracker.atlassian.net/browse/ATT-2947)  
**Sub-task**: [ATT-3016](https://atrainingtracker.atlassian.net/browse/ATT-3016) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2947`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary & Architecture

This implementation plan deconstructs the visual design alignment of `LiveClimbSheet.kt` ([ATT-2947](https://atrainingtracker.atlassian.net/browse/ATT-2947)) with the established Material 3 Live Segment popup design system (`SegmentHeader.kt` / `SegmentLiveDetails.kt`) into atomic, verified construction steps.

The refactored `LiveClimbSheet` establishes:
1. A harmonized top header row with a 32dp terrain icon, high-prominence `titleLarge` bold climb title with single-line ellipsis, and a right-aligned `ClimbCategoryChip`.
2. A subtitle row featuring a left-aligned `ClimbStatusBadge` and right-aligned route climb counter / ascent info.
3. A standardized `HorizontalDivider` with 0.5dp thickness and 16dp horizontal padding.
4. An asymmetric 2-column live telemetry HUD (Left: remaining distance in `titleLarge` and remaining elevation in `bodyLarge`; Right: current gradient in `headlineMedium` monospace bold).
5. A seamlessly integrated `ClimbProfileCanvas` displaying dynamic grade color bands and real-time rider position.

---

## 2. Atomic Implementation Steps

### Step 1: Top Header Row Harmonization (`LiveClimbSheet.kt`)
* In `LiveClimbSheet.kt`, reconstruct the top header `Row`:
  - Leading `Icon` using `Icons.Default.Terrain` (`modifier = Modifier.size(32.dp)`, `tint = MaterialTheme.colorScheme.primary`).
  - Climb Name `Text` using `MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, assigned `Modifier.weight(1f)`.
  - Trailing `ClimbCategoryChip` badge right-aligned in the top row.
* *Verification*: Header composable compiles with 32dp icon, titleLarge, and right-aligned category chip.

### Step 2: Subtitle Status & Route Counter Row (`LiveClimbSheet.kt`)
* Implement the second `Row` with `horizontalArrangement = Arrangement.SpaceBetween`:
  - Left: `ClimbStatusBadge(status = liveClimb.status, distanceToStart = liveClimb.distanceToStart, distanceFormatter = distanceFormatter)`.
  - Right: Route climb counter or ascent info:
    ```kotlin
    val counterText = if (liveClimb.routeIndex != null && liveClimb.totalRouteClimbs != null) {
        stringResource(R.string.climb_route_counter, liveClimb.routeIndex, liveClimb.totalRouteClimbs)
    } else {
        "${climb.totalElevationGain.roundToInt()} m"
    }
    Text(
        text = counterText,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    ```
* *Verification*: Status and counter row properly aligned matching `SegmentHeader`.

### Step 3: Standard Divider & Asymmetric Telemetry HUD (`LiveClimbSheet.kt`)
* Insert standardized `HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))` with 4dp vertical padding.
* Reconstruct telemetry HUD as an asymmetric 2-column `Row` matching `SegmentLiveDetails`:
  - Left `Column`:
    - Remaining distance to summit: `Text(text = "$remDistStr ${stringResource(R.string.climb_remaining_dist)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)`.
    - Remaining elevation: `Text(text = "$remElevStr ${stringResource(R.string.climb_remaining_elevation)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)`.
  - Right `Column` (`horizontalAlignment = Alignment.End`):
    - Current grade: `Text(text = gradeStr, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)`.
    - Grade label: `Text(text = stringResource(R.string.climb_grade), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)`.
* *Verification*: HUD layout mirrors `SegmentLiveDetails` typography and contrast.

### Step 4: Elevation Profile Canvas Presentation (`LiveClimbSheet.kt`)
* Place `ClimbProfileCanvas(liveClimb = liveClimb, modifier = Modifier.fillMaxWidth().height(64.dp))` beneath the telemetry HUD with consistent spacing.
* *Verification*: Profile canvas renders smoothly with gradient color bands and rider pin.

### Step 5: Unit & Contract Tests (`LiveClimbSheetLayoutTest.kt`)
* Update `LiveClimbSheetLayoutTest.kt` to verify:
  1. `Icons.Default.Terrain` and 32dp size token.
  2. `MaterialTheme.typography.titleLarge` for climb title.
  3. Right-aligned `ClimbCategoryChip`.
  4. `ClimbStatusBadge` and `labelLarge` route counter in subtitle row.
  5. Asymmetric telemetry HUD with `titleLarge` remaining distance, `bodyLarge` remaining elevation, and `headlineMedium` monospace current grade.
  6. `ClimbProfileCanvas` rendering.
* Run targeted tests via `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.climbs.LiveClimbSheetLayoutTest"`.

---

## 3. Invariants & Governance

1. **Chesterton's Fence Preservation**:
   - `ClimbProfileCanvas` gradient band mapping (Cat 4 to HC) and rider pin position math remain strictly unaltered.
   - Pacing metrics math (distance to summit, remaining elevation gain, current grade) remain 100% exact.
   - Tab gating in `SensorGridScreen.kt` (`REQ-UI-327` / `ATT-2945`) is completely untouched.
2. **Localization Parity**:
   - All string resources exist and are non-empty across all 9 supported locales.
3. **Clean-Room Test Pass Rate**:
   - Full test suite regression must execute with a 100% pass rate.

---

## 4. Gate 3 Readiness Checklist

- [x] All requirements mapped to atomic implementation steps.
- [x] UI component hierarchy matches `SegmentHeader` / `SegmentLiveDetails`.
- [x] Test specifications and assertions defined in `LiveClimbSheetLayoutTest`.
- [x] Invariants and backward-compatibility verified.
