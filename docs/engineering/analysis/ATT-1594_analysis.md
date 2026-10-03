# Stage 1: Problem Domain & Root Cause Analysis - ATT-1594: Kompaktes Strecken-Badge auf Lieblingsort-Karten analog zum Starts-Badge

**Ticket**: [ATT-1594](https://atrainingtracker.atlassian.net/browse/ATT-1594)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.4`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Statement & User Impact

In `ATT-1402` (`REQ-UI-186`), an architectural and visual bridge was established between Known Start Locations (*Lieblingsorte*) and Route Clusters (*Lieblingsstrecken*). To surface this connection, `KnownLocationCard` was updated to display a section header (`"Lieblingsstrecken ab hier (N):"`) followed by a `FlowRow` of individual `SuggestionChip` items for every route cluster starting within the location's geofence.

### Current Limitations:
1. **Vertical Card Bloat**: If a favorite location serves as the starting point for multiple route clusters (e.g. 4 to 8 distinct routes), each chip wraps onto new lines, causing the card to expand significantly in height. This disrupts the visual rhythm of the list and pushes subsequent locations far below the fold.
2. **Visual Inconsistency**: While historical workout sessions starting at a location are elegantly summarized in a single compact, interactive drill-down badge (`"N Starts >"`), route clusters are rendered as an expansive chip cloud.
3. **Redundant Granularity**: Clicking an individual route chip navigates to that specific cluster details, whereas athletes who want to explore their routes departing from that location prefer a centralized view in the *Lieblingsstrecken* tab with the spatial filter pre-applied.

---

## 2. Chesterton's Fence & Requirement Archaeology

### 1. Requirement & Ticket Lineage
* **Originating Ticket**: `ATT-1402` (*Lieblingsorte & Lieblingsstrecken Bridge: Starting Location Association, Invariant & UI Integration*).
* **Requirement**: `REQ-UI-186`.
* **Historical Reason for FlowRow Chips**: In `ATT-1402`, the immediate priority was verifying the algorithmic association between locations and clusters and providing direct 1-tap navigation to individual clusters. Presenting chips was the fastest mechanism to prove the link visually.

### 2. Core Intent & Invariant Preservation
* **Intent**: To make athletes aware of which and how many route clusters depart from each favorite location, and to provide interactive drill-down navigation.
* **Evolution**: Replacing the sprawling `FlowRow` chips with a compact interactive badge (`"N Strecken >"`) styled analogously to the Starts-Badge (`"N Starts >"`) preserves the core intent while drastically improving layout density, visual balance, and navigation ergonomics.
* **Invariants Preserved**:
  - Universal delete-only long-press context menu (`REQ-UI-061`).
  - Single-tap card body editing (`onEdit`).
  - Starts count badge (`onShowWorkouts`) drill-down navigation to workouts.
  - Single-thread SQLite database concurrency (`KnownLocationsDB-Thread`, `WorkoutClusterDB-Thread`).

---

## 3. Forensic Scope & Affected Components

1. **`KnownLocationsScreen.kt`**:
   - In `KnownLocationCard`: Replace the `Text` header and `FlowRow` of `SuggestionChip`s with a compact interactive `Surface` badge placed alongside the Starts-Badge.
   - Badge styling:
     - Shape: `RoundedCornerShape(12.dp)`.
     - Background: `MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)` or `primaryContainer.copy(alpha = 0.5f)`.
     - Icon: `Icons.Default.Route` (or `Icons.AutoMirrored.Filled.AltRoute` / `Icons.Default.DirectionsRun`).
     - Pluralized Text: `pluralStringResource(R.plurals.known_locations_routes, linkedClusters.size, linkedClusters.size)` (e.g. `"1 Strecke"`, `"3 Strecken"`).
     - Trailing Chevron: `Icons.AutoMirrored.Filled.ArrowForward`.
     - Touch Target: Enforce minimum accessible height of 48dp.
     - Click Callback: `onShowRoutes: (KnownLocationItem) -> Unit`.
   - Card Row layout: Place Altitude metric, Starts-Badge, and Routes-Badge in a horizontal arrangement (`FlowRow` or `Row` with `Arrangement.spacedBy(8.dp)`) to accommodate various screen widths and font scalings gracefully.
   - Zero-Routes State: When `linkedClusters.isEmpty()`, the Routes-Badge is omitted completely.

2. **`ATrainingTrackerApp.kt`**:
   - In `composable(NavRoutes.START_LOCATIONS)`:
     - Pass `onShowRoutes = { locationItem -> ... }` to `KnownLocationsScreen`.
     - Action: Construct `ClusterFilterCriteria` preset with `startLocationName = locationItem.name`, `startLocationLat = locationItem.latLng.latitude`, `startLocationLng = locationItem.latLng.longitude`, and `startLocationRadiusM = locationItem.radius.toDouble().takeIf { it > 0.0 } ?: 200.0`.
     - Update `clustersViewModel.setFilterCriteria(criteria)`.
     - Navigate to `NavRoutes.LOCATIONS`.

3. **String Resources & Localization**:
   - Define plural resource `known_locations_routes` across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT):
     - EN: `1 Route`, `%1$d Routes`
     - DE: `1 Strecke`, `%1$d Strecken`
     - ES: `1 ruta`, `%1$d rutas`
     - FR: `1 itinéraire`, `%1$d itinéraires`
     - IT: `1 percorso`, `%1$d percorsi`
     - JA: `%1$d件のルート`
     - NL: `1 route`, `%1$d routes`
     - PL: `1 trasa`, `%1$d trasy`, `%1$d tras`
     - PT: `1 percurso`, `%1$d percursos`
   - Define accessible content description string `known_locations_view_routes`:
     - EN: `"View favorite tracks starting here"`
     - DE: `"Lieblingsstrecken ab hier anzeigen"`
     - (and all remaining 7 locales with 100% parity).

---

## 4. Risk Assessment & Mitigation

| Risk | Impact | Mitigation Strategy |
| :--- | :--- | :--- |
| Metric row width overflow on compact screens | Medium | Use `FlowRow` for the metrics and badges row with `Arrangement.spacedBy(8.dp)` and `verticalArrangement = Arrangement.spacedBy(4.dp)`, ensuring badges wrap cleanly on small screens without clipping. |
| Inaccessible touch targets | Low | Set `.defaultMinSize(minHeight = 48.dp)` on the Routes-Badge Surface, matching the Starts-Badge. |
| Missing translations | High | Formulate new strings and plurals across all 9 locales simultaneously and verify via `TranslationParityTest.kt`. |
| Breaking existing test suites | Low | Update existing tests in `KnownLocationsScreenDrillDownTest.kt` or `KnownLocationsViewModelRoutesTest.kt` and create dedicated component unit tests. |

---

## 5. Conclusion & Stage Gate 1 Readiness

The scope is bounded, invariants are clearly understood, and the architecture builds seamlessly on existing patterns established in `ATT-1401` and `ATT-1402`. Ready to proceed to Stage 2 (Requirement & Test Specification).
