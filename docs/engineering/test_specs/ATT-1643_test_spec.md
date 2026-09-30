# Stage 2: Requirement & Test Specification - ATT-1643: Dedicated Row and Refined Compact Sizing for Starts and Strecken Badges on KnownLocationCard

**Ticket**: [ATT-1643](https://atrainingtracker.atlassian.net/browse/ATT-1643)  
**Sub-task**: [ATT-1660](https://atrainingtracker.atlassian.net/browse/ATT-1660) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Requirement Mapping**: `REQ-UI-195` (*Lieblingsorte: Dedicated Row and Compact Sizing for Starts and Strecken Badges on KnownLocationCard*)  
**Test Spec ID**: `TST-UI-149`  
**Branch**: `feature/ATT-1643`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Requirement Specification (REQ-UI-195)

### 1.1 Problem Statement & Rationale
On [KnownLocationCard](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt), the altitude metric and interactive badges (Starts-Badge and Strecken-Badge) share an adaptive `FlowRow`. When a location has high hit counts or multiple routes, the routes badge wraps to a second line while the altitude stays on the first line. This produces uneven card heights and staggered visual rhythm across the list. Additionally, the badges currently enforce a `defaultMinSize(minHeight = 48.dp)` container size that bloats the visual card dimensions.

### 1.2 Functional & Architectural Requirements
1. **Vertical Layout Progression**:
   - The root content `Column` of `KnownLocationCard` SHALL present elements in a strict top-to-bottom sequence:
     1. Location name header (`Text`, `titleMedium`, `FontWeight.Bold`).
     2. Dedicated altitude metric row (`ic_ascent` icon, formatted elevation string).
     3. Dedicated interactive badges row (`FlowRow` or `Row` with `horizontalArrangement = Arrangement.spacedBy(8.dp)`).
   - Decoupling: The altitude metric SHALL NOT share a row or `FlowRow` with interactive badges.
2. **Refined Compact Badge Styling**:
   - Both Starts-Badge and Strecken-Badge (`Surface`) SHALL feature compact proportions:
     - Shape: `RoundedCornerShape(8.dp)`.
     - Internal content padding: `horizontal = 8.dp, vertical = 4.dp`.
     - Typography: `MaterialTheme.typography.labelLarge` with `FontWeight.Bold`.
     - Leading icons (`Place`, `ic_favorite_route`): `14.dp`.
     - Trailing chevron (`Icons.AutoMirrored.Filled.ArrowForward`): `12.dp`.
     - Omit explicit `defaultMinSize(minHeight = 48.dp)` so visual height stays compact (~28–32dp) while preserving accessible tap semantics.
3. **Preservation of Test Tags & Navigation**:
   - `location_starts_badge_${item.id}` and `location_routes_badge_${item.id}` SHALL be preserved on the respective `Surface` components.
   - `onShowWorkouts` and `onShowRoutes` click handlers SHALL remain fully wired and accessible.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Altitude Decoupling & Dedicated Badges Row)**:
  * *Given* an athlete viewing favorite locations in `KnownLocationsScreen`,
  * *When* inspecting any `KnownLocationCard`,
  * *Then* the altitude metric SHALL render on its own line below the location name, and interactive badges SHALL render on a separate dedicated line below the altitude metric.
* **Criterion 2 (Compact Badge Proportions)**:
  * *Given* a location card with linked routes,
  * *When* viewing the Starts-Badge and Strecken-Badge,
  * *Then* both badges SHALL display compact styling with `RoundedCornerShape(8.dp)`, 14dp leading icons, and 12dp trailing chevrons, without stretching to 48dp visual height.
* **Criterion 3 (Interactive Navigation)**:
  * *Given* an athlete tapping the compact Starts-Badge or Strecken-Badge,
  * *Then* the app SHALL navigate to the filtered workouts or routes list respectively.

### 1.4 System Invariants
- Card single-tap edit, long-press delete context menu (`REQ-UI-061`), navigation callbacks, and 9-language localization parity MUST NOT be broken.

---

## 2. Test Specification (TST-UI-149)

### Test Case 1: Layout & Badge Contract Verification (`TST-UI-149.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationCardLayoutTest.kt`
* **Preconditions**: `KnownLocationsScreen.kt` source code.
* **Action**: Verify structural AST / Regex assertions:
  - Altitude metric row is isolated from badges container.
  - Interactive badges reside in their own dedicated row beneath altitude.
  - Badges use `RoundedCornerShape(8.dp)` and compact padding `horizontal = 8.dp, vertical = 4.dp`.
  - Badges omit `defaultMinSize(minHeight = 48.dp)`.
  - Test tags `location_starts_badge_` and `location_routes_badge_` are preserved.
* **Expected Result**: All structural assertions pass.

### Test Case 2: Navigation Drill-Down Preservation (`TST-UI-149.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreenDrillDownTest.kt`
* **Preconditions**: `KnownLocationItem` with starts count.
* **Action**: Verify `onShowWorkouts` and `onShowRoutes` callback dispatch.
* **Expected Result**: Assert callbacks invoke cleanly with correct criteria.

### Test Case 3: Context Menu Invariant Audit (`TST-UI-149.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/GlobalDeleteContextMenuAuditTest.kt`
* **Preconditions**: `KnownLocationsScreen.kt`.
* **Action**: Run `GlobalDeleteContextMenuAuditTest`.
* **Expected Result**: Context menu remains strictly delete-only per `REQ-UI-061`.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-149.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-149.1` | Unit | `KnownLocationCardLayoutTest` | `REQ-UI-195` | Specified |
| `TST-UI-149.2` | Unit | `KnownLocationsScreenDrillDownTest` | `REQ-UI-195`, `REQ-UI-185` | Specified |
| `TST-UI-149.3` | Unit | `GlobalDeleteContextMenuAuditTest` | `REQ-UI-061` | Specified |
| `TST-UI-149.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
