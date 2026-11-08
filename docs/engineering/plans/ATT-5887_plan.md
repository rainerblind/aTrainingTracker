# Stage 3: Implementation Plan - ATT-5887: Harmonize KnownLocationCard Layout with WorkoutClustersScreen

**Ticket**: [ATT-5887](https://atrainingtracker.atlassian.net/browse/ATT-5887)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5887`  
**Requirement Mapping**: `REQ-UI-469` | **Test Mapping**: `TST-UI-469`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-08  

---

## 1. Problem Description & Background
`KnownLocationCard` currently renders Starts and Routes inside bordered/tinted `Surface` pill badges with chevrons. This clashes with `WorkoutClustersScreen.kt` (`ClusterItem` / `WorkoutClusterMetadataBlock`), which establishes a clean unboxed card architecture with primary-blue cross-links and a bottom-anchored recording count in bold primary blue typography.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-469` (*Harmonized KnownLocationCard Layout & Unboxed Typography*)
  * `REQ-UI-469.1`: Bottom-anchored column architecture (`IntrinsicSize.Min` + vertical spring `Spacer`).
  * `REQ-UI-469.2`: Unboxed starts metric with `DirectionsRun` and bold primary typography.
  * `REQ-UI-469.3`: Unboxed routes cross-link with `ic_favorite_route` in primary styling.
  * `REQ-UI-469.4`: Header, map thumbnail (116x80dp), context menu (`Merge`, `Home`, `Delete`), and callbacks preserved.
  * `REQ-UI-469.5`: Contract and layout test synchronization.
* **Test Mapping**: `TST-UI-469` (`TST-UI-469.1` through `TST-UI-469.4`).

---

## 3. System Invariants & Preserved Behavior
1. **Interactive Callbacks**: Independent tap targets for `onShowWorkouts` and `onShowRoutes` preserved via test tags `location_starts_badge_${item.id}` and `location_routes_badge_${item.id}`.
2. **Context Menu Purity**: Universal long-press context menu retaining `onMerge` ("Zusammenführen…", ATT-5889), `Home` toggle, and `Delete`.
3. **UI Consistency (docs/design_guidelines.md)**: Reference screen `WorkoutClustersScreen.kt` (`ClusterItem`); theme tokens `MaterialTheme.colorScheme.primary`, `onSurfaceVariant`, `bodyMedium`, `bodySmall`, `FontWeight.Bold`, and `FontWeight.Medium`.
4. **9-Language Parity**: Plurals `known_locations_starts` and `known_locations_routes` 100% preserved.

---

## 4. Proposed Architectural Changes

### Component 1: KnownLocationCard (`KnownLocationsScreen.kt`)
* Refactor body row to `Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Bottom)`.
* In left column (`Modifier.weight(1f).fillMaxHeight()`):
  * Keep altitude metric row (`Icons.Default.Terrain` + formatted altitude).
  * Render routes cross-link (`linkedClusters.isNotEmpty()`) as unboxed clickable row with `ic_favorite_route` (14.dp) and `bodySmall` in `colorScheme.primary`.
  * Add vertical spring `Spacer(modifier = Modifier.weight(1f))`.
  * Render primary starts metric as unboxed clickable row with `Icons.AutoMirrored.Filled.DirectionsRun` (16.dp) and `bodyMedium` in `colorScheme.primary` (`FontWeight.Bold`).
* Eliminate obsolete `Surface` pill wrappers, borders, and `ArrowForward` chevrons.

### Component 2: Layout Contract Tests (`KnownLocationCardLayoutTest.kt`)
* Update structural assertions to verify `IntrinsicSize.Min` row container, unboxed primary typography for starts/routes, `DirectionsRun` icon, and absence of bordered `Surface` pills in the metadata column.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Refactor KnownLocationCard Layout
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt`
* Implement unboxed bottom-anchored hierarchy matching `ClusterItem`.

### Step 2: Synchronize Layout Contract Tests
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationCardLayoutTest.kt`
* Update contract test cases to assert `REQ-UI-469` rules.

### Step 3: Targeted Verification & Governance Suite
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* Command: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit/contract suite and living docs governance script.
* **Rollback**: Clean git commit boundary on `feature/ATT-5887` enables immediate revert if regression occurs.
