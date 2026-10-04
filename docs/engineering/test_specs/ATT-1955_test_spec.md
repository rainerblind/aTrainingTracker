# Stage 2: Requirement & Test Specification - ATT-1955: In-Ride Fork-in-the-Road Route Selection & Decision Alerts

**Ticket**: [ATT-1955](https://rainerblind.atlassian.net/browse/ATT-1955)  
**Sub-task**: [ATT-2432](https://rainerblind.atlassian.net/browse/ATT-2432) (`[Req & Test Spec] In-Ride Fork-in-the-Road Route Selection & Decision Alerts`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-MAP-031` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts*)  
**Test Spec ID**: `TST-MAP-033` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts Verification*)  
**Branch**: `feature/ATT-1955`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Requirement Specification (REQ-MAP-031)

### 1.1 Problem Statement & Rationale
Endurance athletes frequently depart on training rides or long runs along familiar departure corridors (e.g., initial 2–5 km exiting their hometown) without having locked in a specific route beforehand. As the athlete progresses along the shared corridor, they approach critical forks in the road where saved routes diverge (e.g. Left fork $\to$ moderate 38 km rolling loop; Right fork $\to$ challenging 65 km hilly loop). Currently:
1. The athlete must either stop, remove gloves, and manually search the route list, or memorize the diverging paths in advance.
2. Comparative route context (distance, elevation gain, directional bearings) is missing at the decision point.
3. If the athlete continues down a branch without manual route activation, turn-by-turn prompts (`REQ-MAP-028`) and live climb profiles (`REQ-MAP-027`) remain inactive.

### 1.2 Functional & Architectural Requirements
The system SHALL detect upcoming route divergence points along shared departure corridors during active tracking, present directional decision cards in the Cockpit HUD, and provide 1-tap and autonomous route binding (`ATT-1955`):

1. **Dynamic Outbound Corridor Matching (`ForkRouteMatcher.kt`)**:
   - The system SHALL continuously evaluate the athlete's current workout trajectory against stored routes in `RoutesRepository.allRoutes` to track active candidates within spatial corridor tolerance ($\le 50\text{ m}$).
   - Candidate routes must share the athlete's current outbound path for $\ge 300\text{ m}$.
   - Routes diverging prior to the athlete's current position or traversed in opposite orientation SHALL be pruned.

2. **Divergence Vertex Detection & Alert Window (`RouteDivergenceDetector.kt`)**:
   - The system SHALL analyze candidate polylines ahead to locate the upcoming divergence vertex where candidate paths separate by $> 40\text{ m}$.
   - The system SHALL calculate remaining distance along the corridor to the fork: $D_{\text{fork}}$.
   - Relative bearing computation: Calculates angle difference $\Delta \theta$ between approach heading and branch departure heading:
     - $\Delta \theta < -20^\circ \implies \text{LEFT}$
     - $-20^\circ \le \Delta \theta \le 20^\circ \implies \text{STRAIGHT}$
     - $\Delta \theta > 20^\circ \implies \text{RIGHT}$
   - When $0 < D_{\text{fork}} \le 300\text{ m}$ and at least 2 distinct route branches diverge, the system SHALL activate the fork decision alert state.

3. **Cockpit Decision HUD Card (`ForkDecisionCard.kt` in `SensorGridScreen.kt`)**:
   - When the fork decision alert is active and no single route is locked, the Cockpit SHALL render a prominent decision card presenting each branching choice.
   - Each branch card SHALL display: relative directional arrow (Left, Straight, Right), route name, total/remaining distance, and elevation gain.
   - The athlete can manually tap any branch option to immediately bind that route to `RoutesRepository.activeNavigatedRouteId` and dismiss the prompt.
   - The athlete can dismiss the card via a close/dismiss action button.

4. **Autonomous Route Binding & Off-Corridor Deviation (`ForkNavigationRepository.kt`)**:
   - If the athlete proceeds past the divergence point and travels $\ge 50\text{ m}$ along one of the branches (cross-track error $< 25\text{ m}$ on chosen branch while cross-track error on alternative branches $> 50\text{ m}$), the system SHALL automatically bind the chosen route to `RoutesRepository.activeNavigatedRouteId`, dismiss the decision card, and initiate active turn prompts (`REQ-MAP-028`) and climb guidance (`REQ-MAP-027`).
   - If the athlete deviates $> 50\text{ m}$ from all candidate branches, the fork decision card SHALL be dismissed without binding any route.

5. **100% 9-Language Localization Parity**:
   - All decision card labels, directional cues, and dismiss actions SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Fork Proximity Alert)**:
  * *Given* an active workout tracking along a corridor shared by 2 or more stored routes,
  * *When* the athlete approaches within $300\text{ m}$ of the divergence point ($D_{\text{fork}} \le 300\text{ m}$),
  * *Then* the Cockpit SHALL display an ambient decision card presenting each branch's relative direction, route name, total distance, and elevation gain.
* **Criterion 2 (Autonomous Route Binding)**:
  * *Given* an active fork decision prompt displayed in the Cockpit,
  * *When* the rider turns onto one of the diverging branches and travels $\ge 50\text{ m}$ along it,
  * *Then* the system SHALL automatically bind the chosen route to `RoutesRepository.activeNavigatedRouteId`, dismiss the decision card, and initiate active turn and climb guidance.
* **Criterion 3 (1-Tap Manual Selection)**:
  * *Given* an active fork decision card in the Cockpit,
  * *When* the athlete taps one of the branch options,
  * *Then* the selected route SHALL immediately be bound as the active navigated route.
* **Criterion 4 (Clean Dismissal & Deviation)**:
  * *Given* an active fork prompt,
  * *When* the athlete taps dismiss or deviates from all candidate branches ($> 50\text{ m}$ from all routes),
  * *Then* the prompt SHALL be dismissed without binding a route.

### 1.4 System Invariants
1. **Zero Database Regressions**: SQLite schemas for `Routes.db` must not be altered or broken.
2. **Offline Sovereignty & JVM Testability**: All geometry calculations use pure geodesic spherical math (Haversine) without Android framework `android.location.Location` dependencies.
3. **Full Suite Pass Rate**: All existing 1,760+ unit tests must pass without failure.

---

## 2. Test Specification (TST-MAP-033)

### Test Case 1: Outbound Corridor Matching Unit Tests (`ForkRouteMatcherTest.kt` / `[TST-MAP-033.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/ForkRouteMatcherTest.kt`
* **Scenarios**:
  - `findCandidates_sharedDepartureCorridor_returnsMatchingRoutes`: Verifies 2 routes sharing departure within $50\text{ m}$ buffer are identified as active candidates.
  - `findCandidates_divergedOrReverseRoute_filtersOutRoute`: Verifies route heading in opposite direction or diverging before current location is excluded.
  - `findCandidates_insufficientSharedPrefix_returnsEmpty`: Verifies route with $< 300\text{ m}$ overlap is not considered a candidate.

### Test Case 2: Divergence Vertex Detection & Relative Bearings (`RouteDivergenceDetectorTest.kt` / `[TST-MAP-033.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteDivergenceDetectorTest.kt`
* **Scenarios**:
  - `detectDivergence_twoSplittingRoutes_findsAccurateCoordinateAndDistance`: Verifies divergence point identified where paths separate $> 40\text{ m}$.
  - `detectDivergence_computesRemainingDistance_approachingFork`: Verifies $D_{\text{fork}}$ decreases monotonically as position advances toward junction.
  - `computeRelativeBearing_mapsBranchAnglesToDirections`: Verifies left turn ($\Delta \theta < -20^\circ \implies \text{LEFT}$), straight ($\Delta \theta \approx 0^\circ \implies \text{STRAIGHT}$), right turn ($\Delta \theta > 20^\circ \implies \text{RIGHT}$).
  - `alertWindow_triggersWithin300Meters`: Verifies alert condition active when $0 < D_{\text{fork}} \le 300\text{ m}$.

### Test Case 3: Autonomous Route Binding & Deviation Handling (`ForkNavigationRepositoryTest.kt` / `[TST-MAP-033.3]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/ForkNavigationRepositoryTest.kt`
* **Scenarios**:
  - `onLocationChanged_pastForkOnBranchA_autoBindsRouteA`: Verifies traveling $\ge 50\text{ m}$ along branch A (cross-track $< 25\text{ m}$ vs branch B $> 50\text{ m}$) automatically sets `activeNavigatedRouteId = routeA.id`.
  - `onLocationChanged_deviatesFromBoth_dismissesPrompt`: Verifies cross-track error $> 50\text{ m}$ from both branches clears fork alert without binding.
  - `selectRouteManually_bindsRouteImmediately`: Verifies manual branch selection sets `activeNavigatedRouteId` and clears alert.
  - `dismissPrompt_clearsAlertWithoutBinding`: Verifies dismissal clears alert.

### Test Case 4: Cockpit Decision HUD Card UI Contract Tests (`ForkDecisionCardTest.kt` / `[TST-MAP-033.4]`)
* **Scope**: UI Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/ForkDecisionCardTest.kt`
* **Scenarios**:
  - `forkDecisionCard_rendersAllBranchOptionsWithDirections`: Verifies route names, distances, elevations, and direction icons render.
  - `forkDecisionCard_tapBranch_triggersOnRouteSelected`: Verifies tapping option invokes selection callback.
  - `forkDecisionCard_tapDismiss_triggersOnDismiss`: Verifies tapping close button invokes dismiss callback.

### Test Case 5: 9-Language Localization Audit (`TranslationParityTest.kt` / `[TST-MAP-033.5]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Goal**: Verify string presence and matching format specifiers across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT.
  * Keys: `fork_alert_title`, `fork_direction_left`, `fork_direction_straight`, `fork_direction_right`, `fork_approaching_in_m`, `fork_dismiss`.

### Test Case 6: Clean-Room Full Suite Regression Execution (`[TST-MAP-033.6]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-MAP-033.1]` | Unit | `ForkRouteMatcher.findCandidates` | `REQ-MAP-031.1` | Specified |
| `[TST-MAP-033.2]` | Unit | `RouteDivergenceDetector.detectDivergence` | `REQ-MAP-031.2` | Specified |
| `[TST-MAP-033.3]` | Unit | `ForkNavigationRepository.onLocationChanged` | `REQ-MAP-031.4` | Specified |
| `[TST-MAP-033.4]` | UI Contract | `ForkDecisionCard` composable | `REQ-MAP-031.3` | Specified |
| `[TST-MAP-033.5]` | Localization | `TranslationParityTest` (all 9 locales) | `REQ-MAP-031.5` | Specified |
| `[TST-MAP-033.6]` | Regression | `./gradlew testDebugUnitTest` | `REQ-MAP-031.1–5` | Specified |
