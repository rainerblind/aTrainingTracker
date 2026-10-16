# Stage 2: Requirement & Test Specification - ATT-3576

## Ticket Overview
- **Ticket ID**: ATT-3576
- **Subtask ID**: ATT-3751
- **Summary**: Unify Internal Sheet Layout of LiveClimbSheet and LiveSegmentSheet via Shared LiveTrackingEntitySheet
- **Requirement ID**: `REQ-UI-383`
- **Test Specification ID**: `TST-UI-346`
- **Active Branch**: `feature/ATT-3576`

---

## 1. Formal Requirement Specification (`REQ-UI-383`)

### 1.1 Requirement Title
**Unified Internal Tracking Sheet Layout & Shared LiveTrackingEntitySheet Architecture**

### 1.2 Description
The system SHALL eliminate duplicated internal layout structures, divergent outer/inner paddings, and redundant divider declarations across live tracking sheets by introducing a shared internal layout composable `LiveTrackingEntitySheet` in `com.atrainingtracker.trainingtracker.ui.components.core` and refactoring both `LiveSegmentSheet.kt` and `LiveClimbSheet.kt` to strictly delegate to it, enforcing Rule 36 (Single Source of Truth & DRY) and guaranteeing visual and structural parity (ATT-3576, completing Tier 2 of `REQ-UI-366`):

1. **Shared Internal Layout Composable (`LiveTrackingEntitySheet.kt`)**:
   - The system SHALL define `LiveTrackingEntitySheet` in package `com.atrainingtracker.trainingtracker.ui.components.core`:
     ```kotlin
     @Composable
     fun LiveTrackingEntitySheet(
         headerContent: @Composable (Modifier) -> Unit,
         telemetryHudContent: @Composable (Modifier) -> Unit,
         elevationContent: @Composable () -> Unit,
         modifier: Modifier = Modifier
     )
     ```
   - Inside `LiveTrackingEntitySheet`, the system SHALL enforce the canonical vertical layout structure:
     - Outer `Column(modifier = modifier.fillMaxWidth().wrapContentHeight().background(MaterialTheme.colorScheme.surface))`.
     - Invokes `headerContent(Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp))`.
     - Renders the canonical `HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))`.
     - Invokes `telemetryHudContent(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))`.
     - Invokes `elevationContent()`.

2. **Refactor `LiveSegmentSheet.kt`**:
   - In `LiveSegmentSheet.kt`, the system SHALL replace the local `Column` + `HorizontalDivider` in `MapDetailLayout`'s `header` slot with an invocation of `LiveTrackingEntitySheet`:
     - `headerContent = { headerModifier -> SegmentHeader(summary = liveSegment.staticData.summary, liveSegmentStatus = liveSegment.liveData.segmentStatus, modifier = headerModifier) }`
     - `telemetryHudContent = { hudModifier -> SegmentLiveDetails(liveSegmentData = liveSegment.liveData, modifier = hudModifier) }`
     - `elevationContent = {}`
   - Direct calls to `HorizontalDivider` in `LiveSegmentSheet.kt` SHALL be completely eliminated.

3. **Refactor `LiveClimbSheet.kt`**:
   - In `LiveClimbSheet.kt`, the system SHALL decompose the monolithic layout into:
     - `ClimbHeader(climb: Climb, liveClimb: LiveClimbData, distanceFormatter: DistanceFormatter, altitudeFormatter: AltitudeFormatter, modifier: Modifier)`
     - `ClimbLiveDetails(liveClimb: LiveClimbData, distanceFormatter: DistanceFormatter, altitudeFormatter: AltitudeFormatter, modifier: Modifier)`
   - `LiveClimbSheet` SHALL delegate its entire root layout to `LiveTrackingEntitySheet`:
     - `headerContent = { headerModifier -> ClimbHeader(climb = climb, liveClimb = liveClimb, distanceFormatter = distanceFormatter, altitudeFormatter = altitudeFormatter, modifier = headerModifier) }`
     - `telemetryHudContent = { hudModifier -> ClimbLiveDetails(liveClimb = liveClimb, distanceFormatter = distanceFormatter, altitudeFormatter = altitudeFormatter, modifier = hudModifier) }`
     - `elevationContent = { ElevationProfile(...) }`
   - Direct calls to `HorizontalDivider` and divergent `padding(horizontal = 16.dp, vertical = 8.dp)` in `LiveClimbSheet.kt` SHALL be completely eliminated.

4. **Preservation of System Invariants**:
   - Clamped wrap-content height (`REQ-UI-270`), dynamic peek heights (`BottomSheetDesign.PeekHeightLiveSegment`), elevation profile gradient shading and rider pin tracking (`REQ-UI-352`), and 100% clean-room unit test suite pass rate MUST NOT be broken.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Amends `REQ-UI-366` (*Harmonized Live Segment and Live Climb Tracking Bottom Sheet Architecture & Container (Rule 36)*). Formally tracked as `REQ-UI-383`.
   - Targets `LiveTrackingEntitySheet.kt`, `LiveSegmentSheet.kt`, `LiveClimbSheet.kt`, `LiveTrackingSheetContainerTest.kt`, `LiveSegmentSheetContractTest.kt`, and `LiveClimbSheetLayoutTest.kt`.
2. **Historical Origin & Commit Trace**:
   - Ticket `ATT-3364` (Sprint 2026-42.3, commit `31c4eb4f`) implemented Tier 1 (`LiveTrackingSheetContainer` in `SensorGridScreen.kt`), but left `LiveClimbSheet.kt` untouched (0 lines changed).
3. **Root Reason for Existing Formulation**:
   - The outer container consolidation in `SensorGridScreen.kt` took priority in ATT-3364 to prevent overlapping sheets, leaving internal sheet refactoring incomplete.
4. **Preservation of Core Invariants**:
   - Exact divider styling (0.5dp thickness, `outlineVariant` with alpha 0.5f).
   - Uniform 16dp horizontal divider margin and HUD padding (horizontal = 16dp, vertical = 4dp).
   - Terrain icon, category badges, status badges, and route counters in `LiveClimbSheet`.
   - 100% full clean-room unit test suite pass rate.

---

## 3. Formal Test Specification (`TST-UI-346`)

### 3.1 Architectural Contract Tests
1. **`LiveTrackingSheetContainerTest.kt`**:
   - Verify `LiveTrackingEntitySheet` composable function exists and is public via reflection.
   - Verify `LiveTrackingEntitySheet.kt` source structure:
     - Applies `.fillMaxWidth()` and `.wrapContentHeight()`.
     - Applies `MaterialTheme.colorScheme.surface`.
     - Contains canonical `HorizontalDivider` with `thickness = 0.5.dp` and `padding(horizontal = 16.dp)`.
2. **`LiveSegmentSheetContractTest.kt`**:
   - Assert `LiveSegmentSheet.kt` invokes `LiveTrackingEntitySheet(`.
   - Assert `LiveSegmentSheet.kt` contains zero direct calls to `HorizontalDivider(`.
3. **`LiveClimbSheetLayoutTest.kt`**:
   - Assert `LiveClimbSheet.kt` invokes `LiveTrackingEntitySheet(`.
   - Assert `LiveClimbSheet.kt` contains zero direct calls to `HorizontalDivider(`.
   - Assert `ClimbHeader` and `ClimbLiveDetails` composable declarations exist in `LiveClimbSheet.kt`.
   - Verify all existing visual token assertions (Terrain icon 32dp, `climb_route_counter`, `climb_grade`, `climb_remaining_dist`, etc.) remain satisfied.

### 3.2 Regression & Preview Tests
1. Verify `LiveClimbSheet_OnClimb_Preview`, `LiveClimbSheet_OnClimb_Dark_Preview`, `LiveClimbSheet_Approaching_Preview`, and `LiveClimbSheet_Finished_Preview` compile cleanly.
2. Execute full clean-room suite: `./gradlew testDebugUnitTest`.

---

## 4. Acceptance Criteria (Given-When-Then)

- **Criterion 1: Both Sheets Delegate to LiveTrackingEntitySheet**:
  - *Given* `LiveSegmentSheet.kt` and `LiveClimbSheet.kt`,
  - *When* inspecting their source code in `LiveSegmentSheetContractTest` and `LiveClimbSheetLayoutTest`,
  - *Then* both files invoke `LiveTrackingEntitySheet(` and neither file contains a direct `HorizontalDivider(` call.

- **Criterion 2: Identical Header, Divider, and HUD Padding**:
  - *Given* an active live segment sheet and an active live climb sheet,
  - *When* rendered in the cockpit,
  - *Then* header margins (`start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp`), 0.5dp divider margins (`horizontal = 16.dp`), and HUD margins (`horizontal = 16.dp, vertical = 4.dp`) are governed by the single shared `LiveTrackingEntitySheet` composable.

- **Criterion 3: Regression Immunity**:
  - *Given* the complete unit test suite,
  - *When* executing `./gradlew testDebugUnitTest`,
  - *Then* 100% of tests pass without errors or regressions.
