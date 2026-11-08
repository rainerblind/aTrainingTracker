# Stage 2: Requirement & Test Specification - ATT-5887: Harmonize KnownLocationCard Layout with WorkoutClustersScreen

**Ticket**: [ATT-5887](https://atrainingtracker.atlassian.net/browse/ATT-5887)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5887`  
**Requirement Mapping**: `REQ-UI-469` (*Harmonized KnownLocationCard Layout & Unboxed Typography*)  
**Test Spec ID**: `TST-UI-469` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-UI-469)

### 1.1 Problem Statement & Rationale
`KnownLocationCard` renders Starts and Routes in boxed/bordered pill containers (`secondaryContainer` and `surfaceVariant` borders with chevrons). In `WorkoutClustersScreen` (`ClusterItem` / `WorkoutClusterComponents.kt`), metrics and cross-links use clean unboxed rows, with the primary hit count anchored at the bottom in bold primary blue typography. Removing discordant pill buttons and harmonizing with `ClusterItem` creates visual consistency across the app.

### 1.2 Functional & Architectural Requirements
1. **Harmonized Bottom-Anchored Column Architecture (`REQ-UI-469.1`)**: In `KnownLocationCard`, the content row below the title SHALL use `Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Bottom)`. The left column SHALL take `Modifier.weight(1f).fillMaxHeight()` with a vertical spring `Spacer(Modifier.weight(1f))` that pins the primary starts count to the bottom baseline aligned with the map thumbnail.
2. **Unboxed Primary Starts Metric (`REQ-UI-469.2`)**: The starts metric SHALL eliminate boxed `Surface` pill containers, border strokes, and chevron icons. It SHALL render as an unboxed clickable text row (`onShowWorkouts`, test tag `location_starts_badge_${item.id}`) anchored at the bottom displaying `Icons.AutoMirrored.Filled.DirectionsRun` (16.dp) and localized count (`R.plurals.known_locations_starts`) in bold primary typography (`MaterialTheme.typography.bodyMedium`, `FontWeight.Bold`, `MaterialTheme.colorScheme.primary`).
3. **Unboxed Routes Cross-Link (`REQ-UI-469.3`)**: When `linkedClusters.isNotEmpty()`, the routes count SHALL eliminate boxed `Surface` pill containers and chevron icons. It SHALL render as an unboxed cross-link row (`onShowRoutes`, test tag `location_routes_badge_${item.id}`) beneath the altitude metric, displaying `ic_favorite_route` (14.dp) and localized count (`R.plurals.known_locations_routes`) in primary link styling (`MaterialTheme.typography.bodySmall`, `FontWeight.Medium`, `MaterialTheme.colorScheme.primary`).
4. **Header, Map & Context Menu Invariants (`REQ-UI-469.4`)**: Title row with Home icon, altitude metric row in `onSurfaceVariant` with `Icons.Default.Terrain`, landscape map thumbnail (`116.dp × 80.dp`), and long-press context menu (`Home`, `Merge`, `Delete`) SHALL remain 100% strictly intact and functional.
5. **Contract & Layout Test Parity (`REQ-UI-469.5`)**: `KnownLocationCardLayoutTest.kt` SHALL be synchronized to assert `IntrinsicSize.Min` vertical alignment, unboxed typography hierarchy, and the elimination of bordered `Surface` pills in the metadata column.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Unboxed Starts Count)**:
  * *Given* `KnownLocationCard` on `KnownLocationsScreen`,
  * *When* the starts metric is rendered,
  * *Then* it displays as an unboxed row anchored at the bottom baseline with `DirectionsRun` and bold primary typography (`bodyMedium`, `FontWeight.Bold`), invoking `onShowWorkouts` on tap.
* **Criterion 2 (Unboxed Routes Cross-Link)**:
  * *Given* a known location with linked route clusters (`linkedClusters.isNotEmpty()`),
  * *When* the card is rendered,
  * *Then* the routes count renders as an unboxed cross-link below altitude in `primary` blue (`bodySmall`, `FontWeight.Medium`) with `ic_favorite_route`, invoking `onShowRoutes` on tap.
* **Criterion 3 (Pill Elimination)**:
  * *Given* `KnownLocationCard` metadata column,
  * *When* inspected for container styling,
  * *Then* zero bordered or tinted `Surface` pill containers wrap the starts or routes text.
* **Criterion 4 (Bottom-Anchored Alignment)**:
  * *Given* the bottom row of `KnownLocationCard`,
  * *When* measured by Compose layout,
  * *Then* it enforces `height(IntrinsicSize.Min)` with `verticalAlignment = Alignment.Bottom`, aligning metadata bottom with map thumbnail bottom.

### 1.4 System Invariants
* Zero regressions in 1-tap navigation to workouts or routes; test tags `location_starts_badge_*` and `location_routes_badge_*` preserved.
* Context menu options (Home base, Merge, Delete) and map preview dimensions (116x80dp) strictly preserved.
* 100% 9-language localization parity with zero missing strings or format mismatches.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-UI-469` refining `REQ-UI-195` and `REQ-UI-465.1`.
* **Historical Origin & Commit Trace**: `ATT-1643` (`REQ-UI-195`), `ATT-5825` (`REQ-UI-465.1`, commit `4c095473`).
* **Root Reason for Existing Formulation**: In ATT-1816 and ATT-5825, starts and routes were enclosed in explicit `Surface` pill containers to indicate clickability. In user review (Sprint 2026-45.11 Review of ATT-5825), the user requested harmonization with `ClusterItem`, which uses clean unboxed typography without enclosing pill boxes.
* **Preservation of Core Invariants**: Independent tap targets for `onShowWorkouts` and `onShowRoutes`, test tags, long-press menu (`Home`, `Merge`, `Delete`), and altitude formatting are 100% strictly preserved.

---

## 2. Test Specification (TST-UI-469)

### Test Case 1: Structural Contract & Layout Tests (`TST-UI-469.1`)
* **Scope**: Contract Test (`KnownLocationCardLayoutTest.kt`).
* **Assertions**:
  1. `KnownLocationCard` uses `Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Bottom)`.
  2. Left column takes `Modifier.weight(1f).fillMaxHeight()` and contains vertical spring `Spacer(Modifier.weight(1f))`.
  3. Starts metric renders with `Icons.AutoMirrored.Filled.DirectionsRun` and bold primary typography (`bodyMedium`, `FontWeight.Bold`, `primary`).
  4. Routes cross-link renders with `R.drawable.ic_favorite_route` and primary link styling (`bodySmall`, `FontWeight.Medium`, `primary`).
  5. Elimination of bordered `Surface` pill badges (`RoundedCornerShape(8.dp)`) in the metadata column.
  6. Preservation of test tags `location_starts_badge_${item.id}` and `location_routes_badge_${item.id}`.

### Test Case 2: Routes Badge Interaction & Filtering Invariant Tests (`TST-UI-469.2`)
* **Scope**: Unit Test (`KnownLocationCardRoutesBadgeTest.kt`).
* **Assertions**: Independent click callback invocations for `onShowWorkouts` and `onShowRoutes`; spatial cluster filter criteria construction from location item.

### Test Case 3: 9-Language Localization Audit (`TST-UI-469.3`)
* **Scope**: `TranslationParityTest.kt`.
* **Assertions**: `known_locations_starts` and `known_locations_routes` plurals exist across all 9 locales with zero format specifier mismatches.

### Test Case 4: Tier 1 Targeted Regression Suite (`TST-UI-469.4`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertions**: 100% pass rate across touched UI package, localization, theme audit, and build rules.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-469.1]` | Contract | `KnownLocationCardLayoutTest.kt`: `IntrinsicSize.Min`, unboxed typography, pill elimination, tags | `REQ-UI-469.1`, `REQ-UI-469.2`, `REQ-UI-469.3`, `REQ-UI-469.5` | Specified |
| `[TST-UI-469.2]` | Unit | `KnownLocationCardRoutesBadgeTest.kt`: Independent callbacks, spatial criteria construction | `REQ-UI-469.4`, `REQ-UI-188` | Specified |
| `[TST-UI-469.3]` | Localization | `TranslationParityTest.kt`: Parity for `known_locations_starts` and `known_locations_routes` across 9 locales | `REQ-LOC-001`, `REQ-UI-469.4` | Specified |
| `[TST-UI-469.4]` | Regression | Tier 1 targeted suite (`ui.knownlocations.*`, `localization.*`, `ui.theme.*`, `build.*`) | `REQ-PRO-001` | Specified |
