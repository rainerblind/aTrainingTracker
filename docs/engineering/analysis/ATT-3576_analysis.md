# Stage 1: Problem Domain & Root Cause Analysis (RCA) - ATT-3576

## Ticket Overview
- **Ticket ID**: ATT-3576
- **Subtask ID**: ATT-3750
- **Summary**: Unify Internal Sheet Layout of LiveClimbSheet and LiveSegmentSheet via Shared LiveTrackingEntitySheet
- **Type**: Improvement (Verbesserung)
- **Component**: UI Architecture / Bottom Sheet Design System (Rule 36)
- **Sprint**: 2026-42.5
- **Active Branch**: `feature/ATT-3576`

---

## 1. Problem Domain & Forensic Root Cause Analysis

### 1.1 Technical Background & Context
In Sprint 2026-42.3, ticket `ATT-3364` (*Harmonize live segment and live climb bottom sheets under unified sheet layout*, `REQ-UI-366`) set out to unify bottom sheet behavior and visual design across Strava Live Segments and ClimbPro climbs. Specifically, the specification required two distinct architectural tiers:
1. **Tier 1 (Outer Mounting Scaffolding)**: Unifying the container mounting wrapper in `SensorGridScreen.kt` via `LiveTrackingSheetContainer.kt`.
2. **Tier 2 (Internal Sheet Layout)**: Unifying the internal vertical layout hierarchy shared by `LiveSegmentSheet.kt` and `LiveClimbSheet.kt` (header slot, canonical 0.5dp divider with 16dp horizontal margins, live telemetry HUD slot, and elevation profile).

However, forensic review of git commit `31c4eb4f` reveals that only Tier 1 was implemented:
- `LiveTrackingSheetContainer.kt` was created and mounted in `SensorGridScreen.kt`.
- `LIveSegmentSheet.kt` was renamed to `LiveSegmentSheet.kt`.
- **`LiveClimbSheet.kt` was left completely untouched (0 lines changed in commit `31c4eb4f`)**.

### 1.2 Forensic Audit of Existing Sheet Implementations

1. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LiveSegmentSheet.kt`**:
   - `LiveSegmentSheet` delegates to `MapDetailLayout(showMap = false, ...)` with an internal `header` slot:
     ```kotlin
     Column(
         modifier = Modifier
             .fillMaxWidth()
             .background(MaterialTheme.colorScheme.surface)
     ) {
         SegmentHeader(
             summary = liveSegment.staticData.summary,
             liveSegmentStatus = liveSegment.liveData.segmentStatus,
             modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp)
         )
         HorizontalDivider(
             modifier = Modifier
                 .fillMaxWidth()
                 .padding(horizontal = 16.dp),
             thickness = 0.5.dp,
             color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
         )
         SegmentLiveDetails(
             liveSegmentData = liveSegment.liveData,
             modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
         )
     }
     ```
   - While `LiveSegmentSheet` exhibits the canonical 3-tier header/divider/HUD structure, it instantiates its own `HorizontalDivider` and hardcodes padding modifiers locally.

2. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt`**:
   - `LiveClimbSheet` builds a standalone ~130-line `Column` that diverges significantly from `LiveSegmentSheet`:
     ```kotlin
     Column(
         modifier = modifier
             .fillMaxWidth()
             .background(MaterialTheme.colorScheme.surface)
             .padding(horizontal = 16.dp, vertical = 8.dp),
         verticalArrangement = Arrangement.spacedBy(4.dp)
     ) {
         // Top header row + second row ...
         HorizontalDivider(
             modifier = Modifier
                 .fillMaxWidth()
                 .padding(vertical = 4.dp), // Divergent padding (vertical 4.dp vs horizontal 16.dp)
             thickness = 0.5.dp,
             color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
         )
         // Inline telemetry HUD row ...
         Spacer(modifier = Modifier.height(4.dp))
         ElevationProfile(...)
     }
     ```
   - **Architectural Divergences**:
     - Outer padding differs: `LiveClimbSheet` applies `padding(horizontal = 16.dp, vertical = 8.dp)` to its root column, whereas `LiveSegmentSheet` uses unpadded container with inner element padding (`top = 2.dp, bottom = 4.dp`).
     - Divider padding differs: `LiveClimbSheet` applies `padding(vertical = 4.dp)` without horizontal inset, while `LiveSegmentSheet` applies `padding(horizontal = 16.dp)` without vertical inset.
     - Code duplication: Both sheets manually declare `HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))`.
     - Component structure: `LiveClimbSheet` lacks decomposed `ClimbHeader` and `ClimbLiveDetails` composables, embedding inline formatting and layout in a single monolithic composable.

---

## 2. Chesterton's Fence Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Amends `REQ-UI-366` (*Harmonized Live Segment and Live Climb Tracking Bottom Sheet Architecture & Container (Rule 36)*). Formally tracked as `REQ-UI-383`.
   - Targets `ui/components/core/LiveTrackingEntitySheet.kt`, `ui/segments/LiveSegmentSheet.kt`, `ui/climbs/LiveClimbSheet.kt`, and associated contract test suites.
2. **Historical Origin & Commit Trace**:
   - Ticket `ATT-3364` (Sprint 2026-42.3, commit `31c4eb4f`).
   - `LiveClimbSheet.kt` was originally authored in Sprint 2026-40.14 (`ATT-1281`, commit `ceaf4070`) and updated in Sprint 2026-41.10 (`ATT-3316`, commit `69080e72`).
   - `LiveSegmentSheet.kt` evolved across `ATT-1644`, `ATT-1735`, `ATT-2231`, and `ATT-3108`.
3. **Root Reason for Existing Formulation**:
   - During `ATT-3364`, the primary focus was consolidating the outer sheet slot inside `SensorGridScreen.kt` to resolve layout overlapping and container token discrepancies. Unifying the internal layout of `LiveClimbSheet` was deferred and left incomplete, resulting in technical debt where the two sheets have divergent visual rhythm and redundant divider declarations.
4. **Preservation of Core Invariants**:
   - **Clamped Wrap-Content Height (`REQ-UI-270`)**: Both sheets must wrap content height to prevent viewport blowout.
   - **Visual Design System Tokens**: 0.5dp divider thickness with `outlineVariant.copy(alpha = 0.5f)` and `MaterialTheme.colorScheme.surface` container.
   - **Elevation Profile Integration (`REQ-UI-352`)**: Adaptive height calculation, gradient shading, and rider pin synchronization.
   - **Header & HUD Metrics**: 32dp terrain icon, climb category chip, climb status badge, route counter, and asymmetric telemetry metrics must remain pixel-perfect.

---

## 3. Scope Bounding & Impact Analysis (`ATT-1250`)

### 3.1 In-Scope Objectives
1. **Author `LiveTrackingEntitySheet.kt`**:
   - Create shared composable in `com.atrainingtracker.trainingtracker.ui.components.core`:
     ```kotlin
     @Composable
     fun LiveTrackingEntitySheet(
         headerContent: @Composable (Modifier) -> Unit,
         telemetryHudContent: @Composable (Modifier) -> Unit,
         elevationContent: @Composable () -> Unit,
         modifier: Modifier = Modifier
     )
     ```
   - Enforce canonical column hierarchy:
     - Outer `Column(modifier = modifier.fillMaxWidth().wrapContentHeight().background(MaterialTheme.colorScheme.surface))`
     - `headerContent(Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp))`
     - Canonical `HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))`
     - `telemetryHudContent(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))`
     - `elevationContent()`
2. **Refactor `LiveSegmentSheet.kt`**:
   - Delegate `MapDetailLayout`'s `header` slot to `LiveTrackingEntitySheet`:
     - `headerContent = { headerModifier -> SegmentHeader(..., modifier = headerModifier) }`
     - `telemetryHudContent = { hudModifier -> SegmentLiveDetails(..., modifier = hudModifier) }`
     - `elevationContent = {}`
   - Eliminate direct `HorizontalDivider` invocation.
3. **Refactor `LiveClimbSheet.kt`**:
   - Decompose into `ClimbHeader(climb, liveClimb, distanceFormatter, altitudeFormatter, modifier)` and `ClimbLiveDetails(liveClimb, distanceFormatter, altitudeFormatter, modifier)`.
   - Delegate root layout to `LiveTrackingEntitySheet`:
     - `headerContent = { headerModifier -> ClimbHeader(..., modifier = headerModifier) }`
     - `telemetryHudContent = { hudModifier -> ClimbLiveDetails(..., modifier = hudModifier) }`
     - `elevationContent = { ElevationProfile(...) }`
   - Eliminate direct `HorizontalDivider` invocation and remove divergent `padding(horizontal = 16.dp, vertical = 8.dp)` and `Spacer`.
4. **Mandatory Automated Contract Tests**:
   - Update `LiveTrackingSheetContainerTest.kt`, `LiveSegmentSheetContractTest.kt`, and `LiveClimbSheetLayoutTest.kt` to assert via source code analysis that:
     - Both `LiveSegmentSheet.kt` and `LiveClimbSheet.kt` invoke `LiveTrackingEntitySheet`.
     - Neither `LiveSegmentSheet.kt` nor `LiveClimbSheet.kt` contains a raw `HorizontalDivider(` call.
   - Verify all unit and preview tests pass without regression.

### 3.2 Out-of-Scope Objectives
- Modifying `MapDetailLayout` map rendering, gesture handling, or status bar padding.
- Modifying `ElevationProfile` curve drawing or gradient calculation.
- Changing `ClimbDetector` score thresholds or climb database models.
- Altering `SensorGridScreen.kt` outer sheet mounting logic.

---

## 4. Risk Assessment & Mitigation

- **Risk**: Extracting sub-composables in `LiveClimbSheet.kt` breaks existing tests that inspect source code for string tokens (e.g. `Icons.Default.Terrain`, `climb_route_counter`, `climb_grade`).
  - **Mitigation**: The extracted composables (`ClimbHeader`, `ClimbLiveDetails`) will remain in `LiveClimbSheet.kt`. All tokens, typography styles, and string resources will be fully preserved in the file.
- **Risk**: ElevationProfile padding discrepancy when rendered inside `LiveTrackingEntitySheet`.
  - **Mitigation**: In `LiveTrackingEntitySheet`, `elevationContent()` is invoked at full width without extra horizontal padding, matching `ElevationProfile(modifier = Modifier.fillMaxWidth())` requirements.
- **Risk**: Visual regression in header or HUD spacing.
  - **Mitigation**: The padding values passed to `headerContent` (`start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp`) and `telemetryHudContent` (`horizontal = 16.dp, vertical = 4.dp`) exactly replicate the canonical metrics proven in `LiveSegmentSheet`.
