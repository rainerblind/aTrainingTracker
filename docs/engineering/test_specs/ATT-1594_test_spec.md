# Stage 2: Requirement & Test Specification - ATT-1594: Kompaktes Strecken-Badge auf Lieblingsort-Karten analog zum Starts-Badge

**Ticket**: [ATT-1594](https://atrainingtracker.atlassian.net/browse/ATT-1594)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.4`  
**Requirement Mapping**: `REQ-UI-188` (*Lieblingsorte: Compact Interactive Routes Badge on KnownLocationCard*)  
**Test Mapping**: `TST-UI-142` (`TST-UI-142.1`, `TST-UI-142.2`, `TST-UI-142.3`, `TST-UI-142.4`)  
**Branch**: `feature/ATT-1594`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Traceability Matrix

| Requirement | Test Identifier | Verification Level | Scope & Target Components |
| :--- | :--- | :--- | :--- |
| **`REQ-UI-188`** | `TST-UI-142.1` | Unit / Component Test | `KnownLocationCardRoutesBadgeTest.kt`: Verify routes badge rendering, pluralized count, omission on zero clusters, click callbacks, card edit and delete menu invariance. |
| **`REQ-UI-188`** | `TST-UI-142.2` | Integration Test | `ATrainingTrackerAppRoutesDrillDownTest.kt`: Verify `onShowRoutes` callback constructs `ClusterFilterCriteria` with matching location coordinates and radius and triggers navigation. |
| **`REQ-UI-188`** | `TST-UI-142.3` | Localization Audit | `TranslationParityTest.kt`: Verify `known_locations_routes` plural and `known_locations_view_routes` string across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT). |
| **`REQ-UI-188`** | `TST-UI-142.4` | System Regression | Full clean-room test suite run (`./gradlew testDebugUnitTest`). |

---

## 2. Requirement Definition: `REQ-UI-188`

The system SHALL provide a compact interactive routes badge on `KnownLocationCard` summarizing linked route clusters (*Lieblingsstrecken*) departing from that favorite location, replacing the multi-line individual cluster chips list (ATT-1594):
1. *Compact Routes Badge Visual Presentation (`KnownLocationCard` in `KnownLocationsScreen.kt`)*:
• When a favorite location has one or more linked route clusters (`linkedClusters.isNotEmpty()`), the card SHALL render a single compact interactive `Surface` badge alongside the Starts-Badge.
• The badge SHALL be styled consistently with the Starts-Badge (`RoundedCornerShape(12.dp)`, `defaultMinSize(minHeight = 48.dp)` for accessible touch ergonomics).
• The badge content SHALL include:
  - Route icon (`Icons.Default.Route`),
  - Pluralized count string using `@plurals/known_locations_routes` (e.g. `"1 Strecke"`, `"3 Strecken"`),
  - Trailing navigation chevron (`Icons.AutoMirrored.Filled.ArrowForward`).
• *Metrics & Badges Layout*: The altitude metric, Starts-Badge, and Routes-Badge SHALL be arranged in a horizontal `FlowRow` or `Row` ensuring clean wrapping without clipping on narrow screens.
• *Zero Routes State*: When `linkedClusters.isEmpty()`, the routes badge SHALL be omitted.
2. *Interactive Drill-Down Navigation (`ATrainingTrackerApp.kt`)*:
• `KnownLocationCard` and `KnownLocationsScreen` SHALL expose navigation callback `onShowRoutes: (KnownLocationItem) -> Unit`.
• Tapping the Routes-Badge SHALL invoke `onShowRoutes(item)`.
• In `ATrainingTrackerApp.kt`, `onShowRoutes` SHALL construct `ClusterFilterCriteria` preset with the location's name (`startLocationName`), coordinates (`startLocationLat`, `startLocationLng`), and radius (`startLocationRadiusM`), update `WorkoutClustersViewModel.setFilterCriteria(criteria)`, and navigate to `NavRoutes.LOCATIONS`.
3. *100% 9-Language Localization Parity*:
• Plural resource `known_locations_routes` and accessible content description string `known_locations_view_routes` SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.

---

## 3. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement (REQ-UI-188), refining and replacing the `FlowRow` chip presentation aspect of `REQ-UI-186` (`ATT-1402`).
2. **Historical Origin & Commit Trace**: Ticket `ATT-1402` (commit `91acde33`) under Epic `ATT-1396` (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*).
3. **Root Reason for Existing Formulation**: In `ATT-1402`, individual chips were rendered in a `FlowRow` to prove the algorithmic bridge between locations and clusters. As routes proliferated, this caused visual bloat on location cards.
4. **Preservation of Core Invariants**: The algorithmic association via `clustersByLocationId` remains unchanged; card body edit (`onEdit`) and universal delete context menu (`REQ-UI-061`) remain intact; Starts-Badge (`onShowWorkouts`) remains intact; 9-language localization parity strictly maintained.

---

## 4. Test Specifications: `TST-UI-142`

### Test Case `TST-UI-142.1`: Routes Badge Component Tests (`KnownLocationCardRoutesBadgeTest.kt`)
* **Objective**: Verify that `KnownLocationCard` displays the compact badge when linked clusters exist, hides it when zero clusters exist, invokes `onShowRoutes` on click, and preserves edit and long-press delete behaviors.
* **Given**: A `KnownLocationItem` with varying numbers of linked `WorkoutCluster`s.
* **When**: Composed inside a test environment.
* **Then**:
  1. With `linkedClusters.size == 3`, badge displays `"3 Strecken"` (or `"3 Routes"`) with route icon and chevron.
  2. With `linkedClusters.size == 1`, badge displays `"1 Strecke"` (or `"1 Route"`).
  3. With `linkedClusters.isEmpty()`, routes badge is completely absent.
  4. Clicking routes badge invokes `onShowRoutes(item)`.
  5. Clicking card body outside badges invokes `onEdit(item)`.
  6. Long-pressing card body triggers universal delete-only menu (`REQ-UI-061`).

### Test Case `TST-UI-142.2`: Navigation & Preset Spatial Filter Integration
* **Objective**: Verify that `onShowRoutes` properly populates `ClusterFilterCriteria` and triggers navigation to `NavRoutes.LOCATIONS`.
* **Given**: A location item "Zuhause" at `(48.137, 11.575)` with radius `250.0`.
* **When**: `onShowRoutes` callback executes.
* **Then**: `ClusterFilterCriteria` is created with `startLocationName = "Zuhause"`, `startLocationLat = 48.137`, `startLocationLng = 11.575`, and `startLocationRadiusM = 250.0`, and applied to `WorkoutClustersViewModel`.

### Test Case `TST-UI-142.3`: 9-Language Localization Audit (`TranslationParityTest.kt`)
* **Objective**: Enforce 100% translation completeness and format specifier safety for `known_locations_routes` and `known_locations_view_routes`.
* **Locales**: `values` (EN), `values-de` (DE), `values-es` (ES), `values-fr` (FR), `values-it` (IT), `values-ja` (JA), `values-nl` (NL), `values-pl` (PL), `values-pt` (PT).
* **Command**: `./gradlew testDebugUnitTest --tests "*TranslationParity*"`
* **Expected Result**: 0 missing resources, 0 format mismatches.

### Test Case `TST-UI-142.4`: Clean-Room Full Suite Regression
* **Objective**: Confirm 100% build and test pass rate with 0 regressions.
* **Command**: `./gradlew testDebugUnitTest`

---

## 5. Acceptance Criteria Checklist (Given-When-Then)

- [x] Given a favorite location has 1+ linked clusters, when viewing card, then a single compact badge is displayed alongside Starts-Badge.
- [x] Given routes badge is tapped, when clicked, then app navigates to Lieblingsstrecken tab with preset spatial filter.
- [x] Given a favorite location has 0 linked clusters, when viewing card, then routes badge is omitted.
- [x] Given card body is tapped outside badges, then edit dialog launches; when long-pressed, delete-only menu appears.
- [x] Given 9 application locales, when parsed by TranslationParityTest, then zero missing entries exist.
