# Stage 2: Requirement & Test Specification - ATT-1953: "Take Me Home" Return Navigation, Remaining Distance & ETA HUD

**Ticket**: [ATT-1953](https://rainerblind.atlassian.net/browse/ATT-1953)  
**Sub-task**: [ATT-2422](https://rainerblind.atlassian.net/browse/ATT-2422) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-MAP-029` (*"Take Me Home" Return Navigation, Remaining Distance & Elevation-Aware ETA HUD*)  
**Test Spec ID**: `TST-MAP-031` (*Return Navigation & Elevation-Aware Dynamic ETA Verification*)  
**Branch**: `feature/ATT-1953`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Requirement Specification (REQ-MAP-029)

### 1.1 Problem Statement & Rationale
Endurance cyclists and runners often ride long distances and become fatigued or encounter deteriorating weather mid-workout. When exhausted or pressed for time, athletes need instantaneous, reliable answers to two vital questions: *"How much further to get home along my familiar route?"* and *"What is my estimated time of arrival (ETA)?"* Crucially, a flat distance-only calculation is wildly inaccurate in hilly terrain (e.g. climbing $300\,\text{m}$ over the final $5\,\text{km}$ takes twice as long as descending or traversing flat terrain at the same nominal effort). Athletes need an elevation-aware ETA that factors in both remaining distance and remaining climb.

### 1.2 Functional & Architectural Requirements
The system SHALL provide a one-tap "Take Me Home" return guidance mode, calculate remaining distance and elevation-aware ETA, and display the metrics in the Cockpit HUD (ATT-1953):

1. **Home Base Resolution (`HomeLocationResolver.kt`)**:
   - The system SHALL resolve the athlete's primary Home destination by querying `KnownLocationsDatabaseManager`.
   - *Priority 1 (Name Match)*: Any location whose name contains `"haus"`, `"home"`, or `"zuhause"` (case-insensitive substring match).
   - *Priority 2 (Start Frequency)*: In the absence of an explicitly named location, the location with `ExtremaType.START` having the highest `hitCount` (most frequent departure/arrival location).
   - If resolved, returns `HomeDestination(id, name, latLng, altitude)`.

2. **Return Corridor Snapping (`ReturnCorridorSnapper.kt`)**:
   - *Active Route Mode*: When `RoutesRepository.activeNavigatedRouteId` is non-null:
     - For outbound/forward travel, destination is the route's endpoint (or start if loop). Remaining path spans from current projection point to route end.
     - When "Return Home along route" is active, guidance follows the route polyline in reverse from current location back to route start ($D_{\text{remaining}} = D_{\text{current}}$, and climb $H_{\text{climb}}$ computed by summing positive elevations in reverse).
   - *Unrouted Saved Route Snapping*: When no route is actively navigated:
     - The system SHALL evaluate saved routes in `RoutesRepository.allRoutes` to identify candidate routes that terminate near Home ($\le 500\,\text{m}$) and where the athlete is within corridor proximity ($\le 100\,\text{m}$).
   - *Direct Geodesic Fallback*: If no matching route is available, the system projects a direct return vector to the Home destination.

3. **Elevation-Aware Dynamic ETA Engine (`ElevationAwareEtaCalculator.kt`)**:
   - The system SHALL calculate remaining climb $H_{\text{climb}}$ by summing all positive vertical elevation gains along the remaining path segments:
     $$H_{\text{climb}} = \sum_{k} \max(0.0, \text{altitude}_{k+1} - \text{altitude}_{k})$$
   - The system SHALL calculate predicted travel duration $T_{\text{remaining}}$ using the athletic climbing model:
     $$T_{\text{remaining}} = \frac{D_{\text{remaining}}}{V_{\text{flat}}} + \frac{H_{\text{climb}}}{\text{VAM}_{\text{climb}}}$$
     Where:
     - $V_{\text{flat}}$: Smoothed rolling speed when moving ($> 1.5\,\text{m/s}$), clamped to reasonable athletic limits; defaults to activity profile baseline ($20.0\,\text{km/h} \approx 5.56\,\text{m/s}$ for cycling; $10.0\,\text{km/h} \approx 2.78\,\text{m/s}$ for running; $5.0\,\text{km/h} \approx 1.39\,\text{m/s}$ for walking).
     - $\text{VAM}_{\text{climb}}$: Effective vertical ascent rate (default $500\,\text{m/h} \approx 0.1389\,\text{m/s}$ for cycling; $400\,\text{m/h} \approx 0.1111\,\text{m/s}$ for running/walking, aligning with Naismith's classic 1h per 600m rule).
   - Clock ETA: $\text{ETA} = \text{System.currentTimeMillis()} + (T_{\text{remaining}} \times 1000)$.
   - Formatting: Exposes formatted clock arrival time (e.g. `"18:42"`) and remaining duration (e.g. `"~34 min"`).

4. **Cockpit HUD Integration (`ReturnNavigationHud.kt` in `SensorGridScreen.kt`)**:
   - In `SensorGridScreen.kt`, when Return Navigation is active or an active route has remaining progress, render a high-visibility HUD banner displaying:
     - Destination name & icon (e.g. "🏠 Zu Hause" or route name)
     - Remaining distance formatted in kilometers (e.g. `"12.4 km"`)
     - Remaining vertical gain in meters (e.g. `"+180 m"`)
     - Elevation-adjusted dynamic ETA (e.g. `"18:42 (~34 min)"`)
     - Clear/dismiss action button.

5. **1-Tap Entry Points**:
   - In `RouteActionChipRow` (`SensorGridScreen.kt`), display remaining distance and ETA directly in the chip row when navigation is active.
   - In `RouteSelectorSheet.kt`, provide a prominent "Take Me Home" ("Heimweg") card/button at the top to initiate return navigation immediately.

6. **100% 9-Language Localization Parity**:
   - All user-facing strings SHALL maintain 100% parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (HUD Activation & Display)**:
  * *Given* an active tracking session with a loaded route or established home location,
  * *When* the user activates "Take Me Home" or rides along the return corridor,
  * *Then* `ReturnNavigationHud` SHALL display the destination name, remaining distance, remaining elevation gain, and predicted ETA.
* **Criterion 2 (Elevation-Aware Dynamic ETA Calculation)**:
  * *Given* an active "Take Me Home" navigation session,
  * *When* the remaining route segment contains significant uphill climbing ($H_{\text{climb}} > 0$),
  * *Then* the predicted ETA SHALL explicitly incorporate the climbing ascent time penalty ($H_{\text{climb}} / \text{VAM}$) rather than naive flat-line speed division.
* **Criterion 3 (Safe Familiar Routing)**:
  * *Given* an athlete navigating home,
  * *When* snapping to the return route,
  * *Then* guidance SHALL strictly follow the recorded route path or established return corridor rather than raw unverified motor routing.
* **Criterion 4 (Reverse Return on Active Route)**:
  * *Given* an athlete navigating an active out-and-back route,
  * *When* initiating return home along the route,
  * *Then* remaining distance and remaining elevation gain SHALL be calculated in reverse from current location back to route start.

### 1.4 System Invariants
1. **Zero Database Regressions**: SQLite schemas for `Routes.db` and `StartLocation2Altitude.db` must not be broken or altered.
2. **Offline Sovereignty**: Calculations must be 100% local without network requests.
3. **Full Suite Pass Rate**: All existing 1,760+ unit tests must pass without failure.

---

## 2. Test Specification (TST-MAP-031)

### Test Case 1: Home Base Resolution Unit Tests (`HomeLocationResolverTest.kt` / `[TST-MAP-031.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/HomeLocationResolverTest.kt`
* **Scenarios**:
  - `resolveHome_withExplicitNameHausOrHome_returnsNamedLocation`: Verifies location named "Zu Hause" or "Home Base" is selected over others.
  - `resolveHome_withoutExplicitName_returnsStartLocationWithHighestHitCount`: Verifies location with `ExtremaType.START` and max `hitCount` is selected.
  - `resolveHome_withEmptyDatabase_returnsNullGracefully`: Verifies no crash when database is empty.

### Test Case 2: Elevation-Aware ETA Calculation Unit Tests (`ElevationAwareEtaCalculatorTest.kt` / `[TST-MAP-031.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/ElevationAwareEtaCalculatorTest.kt`
* **Scenarios**:
  - `calcEta_flatTerrain_durationEqualsDistanceOverSpeed`: Verifies 10km at 20km/h (5.556 m/s) with 0m climb yields exactly 1800s (30 min).
  - `calcEta_climbingTerrain_addsClimbPenaltyAccurately`: Verifies 5km at 20km/h + 300m climb at 500m/h VAM adds 2160s (36 min) climbing penalty, yielding 51 min total.
  - `calcEta_downhillOnly_doesNotReduceDurationBelowFlatTime`: Verifies negative descent does not subtract from flat time.
  - `formatEta_validInputs_producesClockAndDurationStrings`: Verifies output format matching `"18:42"` and `"~34 min"`.

### Test Case 3: Return Corridor Snapper Unit Tests (`ReturnCorridorSnapperTest.kt` / `[TST-MAP-031.3]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/ReturnCorridorSnapperTest.kt`
* **Scenarios**:
  - `snapActiveRoute_forwardProgress_computesRemainingDistanceAndClimb`: Verifies remaining distance and climb from midway point to end.
  - `snapActiveRoute_reverseReturn_computesReverseDistanceAndClimb`: Verifies backtracking along route computes correct reverse distance and inverted climb deltas.
  - `snapSavedRoutes_findsRouteTerminatingAtHome`: Verifies route ending at home within 100m corridor is selected.

### Test Case 4: Cockpit HUD Contract & State Tests (`ReturnNavigationHudContractTest.kt` / `[TST-MAP-031.4]`)
* **Scope**: Unit & UI Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/ReturnNavigationHudContractTest.kt`
* **Scenarios**:
  - `hudState_formatting_displaysCorrectTokens`: Verifies kilometers formatted to 1 decimal place, elevation with `+` sign and `m`, ETA clock time.

### Test Case 5: 9-Language Localization & Specifier Audit (`[TST-MAP-031.5]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/translations/TranslationParityTest.kt`
* **Goal**: Verify string presence and matching `%s`/`%d` tokens across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 6: Clean-Room Full Suite Regression Execution (`[TST-MAP-031.6]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-MAP-031.1]` | Unit | `HomeLocationResolver.resolveHomeLocation` | `REQ-MAP-029` (1) | Specified |
| `[TST-MAP-031.2]` | Unit | `ElevationAwareEtaCalculator.calculateEta` | `REQ-MAP-029` (3) | Specified |
| `[TST-MAP-031.3]` | Unit | `ReturnCorridorSnapper.snapToCorridor` | `REQ-MAP-029` (2) | Specified |
| `[TST-MAP-031.4]` | UI Contract | `ReturnNavigationHud`, `RouteActionChipRow` | `REQ-MAP-029` (4, 5) | Specified |
| `[TST-MAP-031.5]` | Localization | `TranslationParityTest` | `REQ-MAP-029` (6) | Specified |
| `[TST-MAP-031.6]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
