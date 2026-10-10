# Stage 1 Analysis: ATT-3103 - Iterative visual design optimization for Live Climb cockpit sheet

**Ticket**: [ATT-3103](https://atrainingtracker.atlassian.net/browse/ATT-3103)  
**Sub-task**: [ATT-3158](https://atrainingtracker.atlassian.net/browse/ATT-3158) (`[Analysis]`)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.8`  
**Branch**: `feature/ATT-3103`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-41.7 Joint Review of ATT-2947, the human user requested an interactive visual design optimization session for the Live Climb cockpit bottom sheet (`LiveClimbSheet.kt`) to fine-tune spacing, typography balance, graphical presentation, and add comprehensive Jetpack Compose `@Preview` composables.

Currently:
1. `LiveClimbSheet.kt` lacks `@Preview` composables, preventing developers and UI designers from visually previewing the bottom sheet states (`APPROACHING`, `ON_CLIMB`, `FINISHED`) in Android Studio without running an active GPS workout simulation on hardware.
2. The telemetry HUD layout and profile canvas presentation benefit from fine-tuned vertical spacing, contrast, and crisp baseline rendering in both Light and Dark AMOLED modes.
3. System invariants defined in `LiveClimbSheetLayoutTest.kt` (32dp Terrain icon, titleLarge climb title, labelLarge route counter, headlineMedium monospace grade, bodyLarge remaining elevation, unified surface background) must be strictly maintained while elevating visual aesthetics.

---

## 2. Root Cause Analysis & Code Audit

Forensic inspection of `app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt` revealed:

### 2.1 Absence of Compose `@Preview` Composables
- The file contains 441 lines of production Compose code (`LiveClimbSheet`, `ClimbCategoryChip`, `ClimbStatusBadge`, `ClimbProfileCanvas`, `ClimbHudMetric`), but zero `@Preview` functions.
- To evaluate UI adjustments or inspect the component in dark AMOLED vs light mode, an engineer had to deploy the app to an Android emulator or device and trigger a live climb tracking state machine.

### 2.2 Elevation Profile Canvas Fine-Tuning
- In `ClimbProfileCanvas`:
  - Canvas height is fixed at `64.dp`. A modest expansion to `68.dp` or `72.dp` provides greater vertical resolution for steep gradient bands.
  - A subtle horizontal baseline line at the bottom of the profile curve enhances structural grounding against varying surface backgrounds.
  - The rider position pin (outer ring `6.dp`, inner dot `4.dp`, vertical line `1.5.dp`) provides great real-time feedback; ensuring high contrast against dark AMOLED backgrounds guarantees sunlight glanceability.

### 2.3 Spacing & Visual Hierarchy
- Vertical spacing between rows (`Arrangement.spacedBy(4.dp)`) and divider padding (`padding(vertical = 4.dp)`) maintains compact bottom sheet height so sensor fields above the sheet remain visible.
- Typography balance:
  - Header: 32dp Terrain icon + `titleLarge` Bold climb title + `ClimbCategoryChip`.
  - Subtitle: `ClimbStatusBadge` pill + `labelLarge` route counter.
  - Telemetry: `titleLarge` remaining distance + `bodyLarge` remaining elevation on the left; `headlineMedium` monospace grade on the right.

---

## 3. Chesterton's Fence Archaeology & Invariant Preservation

1. **`LiveClimbSheetLayoutTest.kt` Contracts**:
   - `Icons.Default.Terrain` with `32.dp`.
   - `MaterialTheme.typography.titleLarge` with `FontWeight.Bold` for climb name.
   - `climb_route_counter` with `MaterialTheme.typography.labelLarge`.
   - `MaterialTheme.typography.headlineMedium` with `FontFamily.Monospace` for instantaneous grade.
   - `MaterialTheme.typography.titleLarge` for remaining distance.
   - `MaterialTheme.typography.bodyLarge` for remaining elevation.
   - `MaterialTheme.colorScheme.surface` background on root container.
   - All 8 string tokens across all 9 locales.
   - *Invariant*: All token names, font styles, icon dimensions, and contract strings must remain strictly intact.

2. **Compact Viewport Invariant**:
   - `LiveClimbSheet` is hosted in `SensorGridScreen.kt` as a bottom sheet. It must remain compact and wrap its content without pushing tracking metrics off-screen.

3. **Multi-State Preview Coverage**:
   - Provide `@Preview` composables for all three primary states:
     - `APPROACHING` (showing distance to start)
     - `ON_CLIMB` (showing active rider pin, remaining distance/elevation, current grade)
     - `FINISHED` (showing summit state, zero remaining, full profile)
   - Support both Light Mode and Dark AMOLED Mode.

---

## 4. User Scope Grounding (ATT-1250)

In accordance with ATT-1250:
- Scope is strictly visual design optimization and preview tooling in `LiveClimbSheet.kt`.
- No changes to `LiveClimbsRepository`, detection algorithms, or database schemas.

---

## 5. Architectural Solution (SWE.2)

1. **Add Mock Data Provider & Preview Composables in `LiveClimbSheet.kt`**:
   - Construct reusable mock `Climb` and `LiveClimbData` fixtures for previewing.
   - Define `@Preview` functions for `LiveClimbSheet`:
     - `@Preview(name = "Light Mode - On Climb", showBackground = true)`
     - `@Preview(name = "Dark AMOLED Mode - On Climb", uiMode = Configuration.UI_MODE_NIGHT_YES)`
     - `@Preview(name = "Approaching Climb", showBackground = true)`
     - `@Preview(name = "Summit Finished", showBackground = true)`
2. **Refine Graphical Presentation in `ClimbProfileCanvas`**:
   - Add a subtle ground baseline line (`drawLine`) at `y = height - bottomPadding` for visual framing.
   - Maintain canvas height at `68.dp` for ideal aspect ratio and vertical resolution.
3. **Unit & Contract Verification**:
   - Add tests to `LiveClimbSheetLayoutTest.kt` verifying that `@Preview` composables exist and are properly declared.
   - Run clean-room test suite to verify 100% pass rate.

---

## 6. ASPICE Gate 1 Sign-Off Readiness

All forensic findings, invariant verifications, and architectural solutions are documented. Ready for Gate 1 transition and audit.
