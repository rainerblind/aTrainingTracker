# Stage 1 Analysis: ATT-5887 - Harmonize KnownLocationCard Layout with WorkoutClustersScreen

**Ticket**: [ATT-5887](https://atrainingtracker.atlassian.net/browse/ATT-5887)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5887`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
* In `KnownLocationsScreen.kt` (`KnownLocationCard`), the `Starts` and `Strecken` indicators are rendered as boxed, tinted/bordered pill badges (`811 Starts ->` with `secondaryContainer` background and `39 Strecken ->` with `surfaceVariant` border) placed immediately below the altitude metric.
* In contrast, `WorkoutClustersScreen.kt` (`ClusterItem` / `WorkoutClusterComponents.kt`) implements a clean, unboxed card architecture:
  * Cross-links (e.g. `Start: Zu Hause`) render as clean text rows with an icon (`14.dp`) in primary blue (`MaterialTheme.colorScheme.primary`) without bordered pill containers.
  * The primary count (`444 Aufzeichnungen`) anchors at the bottom of the metadata column in bold primary blue typography (`MaterialTheme.colorScheme.primary`, `FontWeight.Bold`).
* The discordant pill buttons break visual harmony between Lieblingsorte and Lieblingsstrecken. Aligning `KnownLocationCard` with `ClusterItem` creates visual consistency across the app.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: Incremental additions in ATT-1816 and ATT-5825 wrapped interactive counts in explicit `Surface` pill shapes to make tap targets obvious, which diverged from the unboxed typography pattern of `ClusterItem`.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Inspected `WorkoutClusterComponents.kt:260-272` & `110-224` (`WorkoutClusterMetadataBlock`): Uses `Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.Bottom)` with `Modifier.weight(1f).fillMaxHeight()`, placing metrics and cross-links above a vertical spring `Spacer(Modifier.weight(1f))` that pins `hitCount` (`N Aufzeichnungen`) to the bottom in bold primary blue.
  * Inspected `KnownLocationsScreen.kt:802-908` (`KnownLocationCard`): Left column nests `FlowRow` with two `Surface` pills (`RoundedCornerShape(8.dp)`), an arrow chevron, and a redundant `Place` icon for starts.
  * Cross-ticket synergy audit:
    * `ATT-5889`: Preserved `onMerge` ("Zusammenführen…", `location_merge_action_${item.id}`) in the long-press context menu.
    * `ATT-5884` Item 2: Replaced the redundant location pin on `Starts` with a workout/activity icon (`Icons.AutoMirrored.Filled.DirectionsRun`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Redesign `KnownLocationCard` body layout with `height(IntrinsicSize.Min)` and `Spacer(Modifier.weight(1f))` to mirror `ClusterItem`.
  * Replace bordered pill badges: render routes cross-link (`ic_favorite_route` + `N Strecken`) as an unboxed row in `MaterialTheme.colorScheme.primary`; anchor starts count (`Icons.AutoMirrored.Filled.DirectionsRun` + `N Starts`) at the bottom in bold primary typography.
  * Preserve `onShowWorkouts`, `onShowRoutes`, and `onMerge` actions and test tags (`location_starts_badge_${item.id}`, `location_routes_badge_${item.id}`).
  * Update `KnownLocationCardLayoutTest.kt` to enforce the harmonized unboxed structure.
* **Out-of-Scope**: Altering thumbnail map dimensions (`116x80dp`), zoom algorithms (`KnownLocationZoomMath`), database schema, or ViewModels.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Net-new refinement `REQ-UI-469` (*Harmonized KnownLocationCard Layout & Typography*), refining `REQ-UI-195` and `REQ-UI-465.1`.
* **Historical Origin & Commit Trace**: `ac58d8b7` (ATT-1733), `4c095473` (ATT-5825).
* **Root Reason for Existing Formulation**: Pill containers were used to emphasize interactivity before the unboxed primary-link pattern was standardized across cluster cards.
* **Preservation of Core Invariants**: Independent tap targets for `onShowWorkouts` and `onShowRoutes` preserved; long-press menu (`Home`, `Merge`, `Delete`) intact; 100% 9-language localization parity preserved.

---

## 5. Architectural Strategy & High-Level Solution
* In `KnownLocationCard`:
  1. Enclose details and map in `Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Bottom)`.
  2. In the left column (`weight(1f).fillMaxHeight()`):
     * Altitude metric: `Icons.Default.Terrain` + altitude text.
     * Routes cross-link: if `linkedClusters.isNotEmpty()`, clean row with `ic_favorite_route` and `known_locations_routes` in `MaterialTheme.colorScheme.primary`.
     * `Spacer(Modifier.weight(1f))` vertical spring.
     * Starts primary count: anchored at bottom, clickable `onShowWorkouts`, bold primary text with `DirectionsRun` icon and `known_locations_starts`.
* Update structural contract tests to verify the removal of bordered pill containers and adherence to unboxed primary hierarchy.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: 1-tap navigation to filtered workouts and routes preserved; context menu actions (including `onMerge`) intact; zero regressions in list filtering or map thumbnails.
* **Risk Rating**: **LOW** — Pure declarative UI refactoring within `KnownLocationCard` and its companion unit tests with zero data layer impact.
