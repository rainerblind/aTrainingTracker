# Stage 3: Implementation Plan - ATT-3576

## Ticket Overview
- **Ticket ID**: ATT-3576
- **Subtask ID**: ATT-3752
- **Summary**: Unify Internal Sheet Layout of LiveClimbSheet and LiveSegmentSheet via Shared LiveTrackingEntitySheet
- **Requirement ID**: `REQ-UI-383`
- **Test Specification ID**: `TST-UI-346`
- **Active Branch**: `feature/ATT-3576`

---

## 1. SWE.2 Architecture & Design Decisions

### 1.1 Architectural Context & Strategy
This plan implements Tier 2 of `REQ-UI-366` as formalized in `REQ-UI-383`: establishing a unified internal bottom sheet layout composable `LiveTrackingEntitySheet` across Strava Live Segments and ClimbPro climbs in the tracking cockpit.

### 1.2 Component Design: `LiveTrackingEntitySheet.kt`
- **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/LiveTrackingEntitySheet.kt`
- **Package**: `com.atrainingtracker.trainingtracker.ui.components.core`
- **Signature**:
  ```kotlin
  @Composable
  fun LiveTrackingEntitySheet(
      headerContent: @Composable (Modifier) -> Unit,
      telemetryHudContent: @Composable (Modifier) -> Unit,
      elevationContent: @Composable () -> Unit,
      modifier: Modifier = Modifier
  )
  ```
- **Internal Column Hierarchy**:
  - `Column(modifier = modifier.fillMaxWidth().wrapContentHeight().background(MaterialTheme.colorScheme.surface))`
  - Slot 1: `headerContent(Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp))`
  - Canonical Divider:
    ```kotlin
    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
    ```
  - Slot 2: `telemetryHudContent(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))`
  - Slot 3: `elevationContent()`

### 1.3 Refactoring `LiveSegmentSheet.kt`
- **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LiveSegmentSheet.kt`
- Replace internal `Column` and `HorizontalDivider` in `MapDetailLayout`'s `header` parameter with:
  ```kotlin
  header = {
      LiveTrackingEntitySheet(
          headerContent = { headerModifier ->
              SegmentHeader(
                  summary = liveSegment.staticData.summary,
                  liveSegmentStatus = liveSegment.liveData.segmentStatus,
                  modifier = headerModifier
              )
          },
          telemetryHudContent = { hudModifier ->
              SegmentLiveDetails(
                  liveSegmentData = liveSegment.liveData,
                  modifier = hudModifier
              )
          },
          elevationContent = {}
      )
  }
  ```
- Eliminate direct `HorizontalDivider` instantiation from `LiveSegmentSheet.kt`.

### 1.4 Refactoring `LiveClimbSheet.kt`
- **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt`
- Decompose monolithic layout into two dedicated composables:
  1. `ClimbHeader`:
     ```kotlin
     @Composable
     fun ClimbHeader(
         climb: Climb,
         liveClimb: LiveClimbData,
         distanceFormatter: DistanceFormatter,
         altitudeFormatter: AltitudeFormatter,
         modifier: Modifier = Modifier
     )
     ```
     Encloses the top header row (32dp Terrain icon, climb name, `ClimbCategoryChip`) and second row (`ClimbStatusBadge`, climb route counter / elevation gain).
  2. `ClimbLiveDetails`:
     ```kotlin
     @Composable
     fun ClimbLiveDetails(
         liveClimb: LiveClimbData,
         distanceFormatter: DistanceFormatter,
         altitudeFormatter: AltitudeFormatter,
         modifier: Modifier = Modifier
     )
     ```
     Encloses the live telemetry HUD row (remaining distance & elevation progress on left, instantaneous grade in headlineMedium monospace font on right).
- Root `LiveClimbSheet` delegates directly to `LiveTrackingEntitySheet`:
  ```kotlin
  @Composable
  fun LiveClimbSheet(
      liveClimb: LiveClimbData,
      modifier: Modifier = Modifier
  ) {
      val climb = liveClimb.climb
      val distanceFormatter = remember { DistanceFormatter() }
      val altitudeFormatter = remember { AltitudeFormatter() }

      LiveTrackingEntitySheet(
          headerContent = { headerModifier ->
              ClimbHeader(
                  climb = climb,
                  liveClimb = liveClimb,
                  distanceFormatter = distanceFormatter,
                  altitudeFormatter = altitudeFormatter,
                  modifier = headerModifier
              )
          },
          telemetryHudContent = { hudModifier ->
              ClimbLiveDetails(
                  liveClimb = liveClimb,
                  distanceFormatter = distanceFormatter,
                  altitudeFormatter = altitudeFormatter,
                  modifier = hudModifier
              )
          },
          elevationContent = {
              val normalizedPoints = climb.normalizedPathPoints.ifEmpty { climb.pathPoints }
              val currentDistance = if (liveClimb.status == LiveClimbStatus.ON_CLIMB || liveClimb.status == LiveClimbStatus.FINISHED) {
                  (liveClimb.currentProgressFraction.coerceIn(0f, 1f) * climb.distanceMeters)
              } else null

              ElevationProfile(
                  pathPoints = normalizedPoints,
                  currentDistance = currentDistance,
                  showZoomControls = false,
                  climbGradientShading = true,
                  modifier = Modifier.fillMaxWidth()
              )
          },
          modifier = modifier
      )
  }
  ```
- Eliminate direct `HorizontalDivider` call and remove divergent `padding(horizontal = 16.dp, vertical = 8.dp)` and `Spacer(Modifier.height(4.dp))`.

---

## 2. UI Consistency Guidelines (Rule 23)

- **Closest Existing Reference Screen**: Live Tracking Cockpit bottom sheets (`LiveSegmentSheet` and `LiveClimbSheet`).
- **Reused Components**:
  - `LiveTrackingEntitySheet` (`ui/components/core/`)
  - `LiveTrackingSheetContainer` (`ui/components/core/`)
  - `ElevationProfile` (`ui/map/`)
  - `SegmentHeader` & `SegmentLiveDetails` (`ui/segments/`)
  - `ClimbCategoryChip`, `ClimbStatusBadge`, `MetricBadge` (`ui/climbs/`, `ui/components/`)
- **Theme Tokens & Design System**:
  - Background: `MaterialTheme.colorScheme.surface`
  - Divider: `HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))` with `padding(horizontal = 16.dp)`.
  - Header Margins: `padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp)`.
  - HUD Margins: `padding(horizontal = 16.dp, vertical = 4.dp)`.
- **Justification for New Styles**: Zero new one-off styles. Harmonizes `LiveClimbSheet` with `LiveSegmentSheet`'s battle-tested tokens.

---

## 3. Atomic Step-by-Step Implementation Sequence

### Step 1: Create `LiveTrackingEntitySheet.kt`
- Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/LiveTrackingEntitySheet.kt`.
- Implement `LiveTrackingEntitySheet` composable enforcing outer `Column`, canonical `HorizontalDivider`, and exact slot modifiers.

### Step 2: Update `LiveTrackingSheetContainerTest.kt`
- Add tests in `LiveTrackingSheetContainerTest.kt`:
  - `testLiveTrackingEntitySheet_composableExistsAndIsPublic`: verify function exists via reflection.
  - `testLiveTrackingEntitySheet_sourceStructureAndTokens`: verify `.fillMaxWidth()`, `.wrapContentHeight()`, unbroken `MaterialTheme.colorScheme.surface`, and canonical `HorizontalDivider` (thickness 0.5.dp, horizontal padding 16.dp).

### Step 3: Refactor `LiveSegmentSheet.kt`
- In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LiveSegmentSheet.kt`, delegate `MapDetailLayout`'s `header` slot to `LiveTrackingEntitySheet`.
- Remove raw `HorizontalDivider` instantiation.

### Step 4: Update `LiveSegmentSheetContractTest.kt`
- In `LiveSegmentSheetContractTest.kt`, add:
  - `testLiveSegmentSheet_delegatesToLiveTrackingEntitySheet`: assert `LiveSegmentSheet.kt` contains `LiveTrackingEntitySheet(` and contains zero occurrences of `HorizontalDivider(`.

### Step 5: Refactor `LiveClimbSheet.kt`
- In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt`:
  - Extract `ClimbHeader` composable.
  - Extract `ClimbLiveDetails` composable.
  - Delegate `LiveClimbSheet` root to `LiveTrackingEntitySheet`.
  - Remove raw `HorizontalDivider` call, outer `padding(horizontal = 16.dp, vertical = 8.dp)`, and `Spacer(Modifier.height(4.dp))`.

### Step 6: Update `LiveClimbSheetLayoutTest.kt`
- In `LiveClimbSheetLayoutTest.kt`, add:
  - `testLiveClimbSheet_delegatesToLiveTrackingEntitySheet`: assert `LiveClimbSheet.kt` contains `LiveTrackingEntitySheet(` and zero occurrences of `HorizontalDivider(`.
  - `testLiveClimbSheet_decomposedComposablesExist`: assert `fun ClimbHeader` and `fun ClimbLiveDetails` exist and are public.
  - Verify all existing layout and preview assertions continue to pass.

### Step 7: Run Targeted Unit Tests
- Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.LiveTrackingSheetContainerTest" --tests "com.atrainingtracker.trainingtracker.ui.segments.LiveSegmentSheet*" --tests "com.atrainingtracker.trainingtracker.ui.climbs.LiveClimbSheetLayoutTest"`.

---

## 4. Invariant Protection & Rollback Safety

1. **Zero Raw Horizontal Dividers**:
   - Neither `LiveSegmentSheet.kt` nor `LiveClimbSheet.kt` may instantiate `HorizontalDivider(` directly.
2. **Visual Rhythm & Tokens**:
   - Header padding (`start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp`), 0.5dp divider with `horizontal = 16.dp`, and HUD padding (`horizontal = 16.dp, vertical = 4.dp`) are strictly enforced by `LiveTrackingEntitySheet`.
3. **ClimbPro Telemetry & Preview Compatibility**:
   - 32dp Terrain icon, category chips, status badges, route counters, monospace grade typography, and all 4 preview composables (`LiveClimbSheet_OnClimb_Preview`, `LiveClimbSheet_OnClimb_Dark_Preview`, `LiveClimbSheet_Approaching_Preview`, `LiveClimbSheet_Finished_Preview`) are fully preserved.
4. **Regression Safety**:
   - 100% full clean-room unit test suite pass rate strictly preserved.
