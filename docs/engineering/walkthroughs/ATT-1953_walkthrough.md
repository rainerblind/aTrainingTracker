# Stage 5: Verification Walkthrough - ATT-1953: "Take Me Home" Return Navigation, Remaining Distance & ETA HUD

**Ticket**: [ATT-1953](https://rainerblind.atlassian.net/browse/ATT-1953)  
**Sub-task**: [ATT-2425](https://rainerblind.atlassian.net/browse/ATT-2425) (`[Test] "Take Me Home" Return Navigation, Remaining Distance & ETA HUD`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-MAP-029`  
**Test Mapping**: `TST-MAP-031`  
**Branch**: `feature/ATT-1953`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Executive Summary

This walkthrough document verifies the complete software construction, mathematical modeling, elevation-aware dynamic ETA computation, safe corridor return snapping, Cockpit HUD banner integration, and 9-language localization for [ATT-1953](https://rainerblind.atlassian.net/browse/ATT-1953), fulfilling requirement `REQ-MAP-029` and test specification `TST-MAP-031`.

aTrainingTracker now delivers a dedicated "Take Me Home" return navigation and arrival ETA experience for cyclists and runners:
- **Home Destination Resolution (`HomeLocationResolver.kt`)**: Automatically identifies the athlete's primary home base from `KnownLocationsDatabaseManager`. Priority 1 searches for case-insensitive keyword matches (`"haus"`, `"home"`, `"zuhause"`). Priority 2 falls back to the departure/arrival location with `ExtremaType.START` having the highest `hitCount`.
- **Dynamic Elevation-Aware Dynamic ETA Engine (`ElevationAwareEtaCalculator.kt`)**: Implements the athletic climbing duration model:
  $$T_{\text{remaining}} = \frac{D_{\text{remaining}}}{V_{\text{flat}}} + \frac{H_{\text{climb}}}{\text{VAM}_{\text{climb}}}$$
  Positive vertical elevation gain ($H_{\text{climb}} = \sum \max(0, \Delta h)$) along remaining path segments is explicitly penalized using sport-specific VAM ($500\,\text{m/h}$ for cycling, $400\,\text{m/h}$ for running). Downhill descents are clipped so they never reduce travel time below the flat time. Outputs formatted arrival clock time (`"18:42"`) and remaining duration (`"~34 min"`).
- **Return Corridor Snapper (`ReturnCorridorSnapper.kt`)**: Projects current GPS fixes orthogonally onto route polylines:
  1. *Active Route Mode*: Computes forward remaining distance and climb to destination, or reverses along the route back to start ($D_{\text{remaining}} = D_{\text{current}}$, with inverted ascent calculations).
  2. *Saved Route Candidate Snapping*: Matches candidate saved routes in `RoutesRepository.allRoutes` that terminate or start near Home ($\le 500\,\text{m}$) when the athlete is within corridor proximity ($\le 200\,\text{m}$).
  3. *Direct Geodesic Fallback*: Evaluates direct vector distance and elevation delta to Home when unrouted.
- **Return Navigation Repository (`ReturnNavigationRepository.kt`, `ReturnNavigationState.kt`)**: Exposes a reactive `StateFlow<ReturnNavigationState>` coordinating active routes, athlete coordinates, speed, and home state. Provides 1-tap controls: `startTakeMeHome()`, `stopTakeMeHome()`, `setReverseReturn()`, and `dismissHud()`.
- **Cockpit HUD Banner & Action Integration (`ReturnNavigationHud.kt`, `SensorGridScreen.kt`, `RouteSelectorSheet.kt`)**:
  - `ReturnNavigationHud`: High-contrast, glanceable banner displayed below the turn cue banner showing destination icon (`ic_nav_home`), name, remaining km, remaining vertical gain, dynamic ETA clock time, and dismiss action.
  - `RouteActionChipRow`: Updated in `SensorGridScreen.kt` to display remaining km and arrival time directly in the chip row when navigation is active.
  - `RouteSelectorSheet`: Prominent 1-tap "Take Me Home" ("Heimweg") card at the top of the route selection sheet.
- **100% 9-Language Localization Parity**: All 8 user-facing strings maintained across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 2. Verification Matrix

| Acceptance Criterion | Verification Method | Status | Evidence |
| :--- | :--- | :--- | :--- |
| **AC-1: Home Base Resolution** | [HomeLocationResolverTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/routes/HomeLocationResolverTest.kt) | **PASSED** | Verifies keyword name matching ("Zu Hause", "Home Base"), start frequency hitCount fallback, and empty database safety. |
| **AC-2: Dynamic Elevation-Aware ETA Engine** | [ElevationAwareEtaCalculatorTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/routes/ElevationAwareEtaCalculatorTest.kt) | **PASSED** | Verifies flat terrain baseline ($T = D / V$), climb penalty addition ($H_{\text{climb}} / \text{VAM}$), smoothed current speed handling, path climbing sums, and duration formatting. |
| **AC-3: Return Corridor Snapping** | [ReturnCorridorSnapperTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/routes/ReturnCorridorSnapperTest.kt) | **PASSED** | Verifies forward remaining progress along active route, reverse return back to start, candidate saved route snapping ending near home ($\le 500\,\text{m}$), and direct geodesic fallback. |
| **AC-4: Cockpit HUD Banner & Contract** | [ReturnNavigationHudContractTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/ReturnNavigationHudContractTest.kt) | **PASSED** | Verifies formatted tokens (km, +m climb, clock time, duration) and visibility logic (`hasRemainingMetrics`, dismissal, zero distance). |
| **AC-5: 100% 9-Language Localization Audit** | [TranslationParityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt) | **PASSED** | 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT with matching format specifiers and zero missing entries. |
| **AC-6: Requirement & Test Governance** | `python3 tools/verify_requirement_governance.py` | **PASSED** | `REQ-MAP-029` and `TST-MAP-031` marked `Verified` with zero governance violations. |
| **AC-7: Full Clean-Room Test Suite Regression** | `./gradlew testDebugUnitTest` | **PASSED** | Clean-room test suite executed with 100% pass rate. |

---

## 3. Key Implementation Highlights

### 1. Dynamic Elevation-Aware ETA Calculation (`ElevationAwareEtaCalculator.kt`)
```kotlin
val tFlatSeconds = dist / effectiveFlatSpeed
val tClimbSeconds = climb / defaultVam
val totalDurationSeconds = tFlatSeconds + tClimbSeconds

val arrivalClockMillis = currentTimeMillis + (totalDurationSeconds * 1000.0).roundToLong()
val formattedClock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(arrivalClockMillis))
```

### 2. Return Corridor Snapping (`ReturnCorridorSnapper.kt`)
```kotlin
if (isReverseReturn) {
    remainingDist = currentDistAlongRoute
    remainingClimb = ElevationAwareEtaCalculator.calculateReverseClimbAlongPath(path, bestSegmentIndex, bestT)
} else {
    remainingDist = (totalDist - currentDistAlongRoute).coerceAtLeast(0.0)
    remainingClimb = ElevationAwareEtaCalculator.calculateClimbAlongPath(path, bestSegmentIndex, bestT)
}
```

### 3. Cockpit HUD Banner (`ReturnNavigationHud.kt`)
```kotlin
AnimatedVisibility(
    visible = navigationState.hasRemainingMetrics,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically()
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        // Destination name, remaining km, +m climb, and arrival clock time (ETA)
    }
}
```

---

## 4. Verification & Clean-Room Regression Evidence
Targeted unit tests and full suite regression executed with 100% pass rate:
- `com.atrainingtracker.trainingtracker.routes.HomeLocationResolverTest`: PASSED
- `com.atrainingtracker.trainingtracker.routes.ElevationAwareEtaCalculatorTest`: PASSED
- `com.atrainingtracker.trainingtracker.routes.ReturnCorridorSnapperTest`: PASSED
- `com.atrainingtracker.trainingtracker.ui.routes.ReturnNavigationHudContractTest`: PASSED
- `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`: PASSED
- `com.atrainingtracker.trainingtracker.ui.routes.RouteSelectorSheetTest`: PASSED
- `com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenRouteIntegrationTest`: PASSED
