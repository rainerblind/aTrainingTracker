# Stage 3: Implementation Plan - ATT-5846: ClusterItem & Filter Polish

**Ticket**: [ATT-5846](https://rainerblind.atlassian.net/browse/ATT-5846)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5846`  
**Requirement Mapping**: `REQ-UI-493` (`REQ-UI-493.1`–`REQ-UI-493.4`) | **Test Mapping**: `TST-UI-493` (`TST-UI-493.1`–`TST-UI-493.5`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-08  

---

## 1. Problem Description & Background
Live UI walkthroughs identified four defects in `Lieblingsstrecken`: (1) cluster cards show `0 Aufzeichnungen` when `hitCount` is desynchronized; (2) missing destination endpoint context for point-to-point routes; (3) square 100dp map thumbnail leaves vertical whitespace while equipment lists wrap unbounded; and (4) filter sheet reset button ("Alle zurücksetzen") wraps onto two lines in a 50% split row.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-493` (*ClusterItem & Filter (Lieblingsstrecken) Polish*)
* **Test Mapping**: `TST-UI-493` (*ClusterItem & Filter Polish Verification*)

---

## 3. System Invariants & Preserved Behavior
1. **Map Invariants**: Preserve `GoogleMapOptions().liteMode(true)` and `DarkMapAntiFlashOverlay` on all cluster thumbnails.
2. **Horizontal Filter Chip Contract**: `filter_clear_all` remains strictly enforced on `ActiveFilterChipsRow.kt`.
3. **Auto-Naming Counter Integrity (`REQ-SET-067`)**: Healed `hitCount` matches actual `WorkoutSummaries` counts.
4. **Localization Parity**: 9-locale parity without emojis (`ATT-5400`).

---

## 4. Proposed Architectural Changes
* **Repository (`WorkoutClusterRepository.kt`)**: In `loadAllClustersLightweight()`, reconcile clusters where `hitCount == 0` against hydrated `stats.workoutCount`.
* **ViewModel (`WorkoutClustersViewModel.kt`)**: Expose `getLocationName(lat: Double, lng: Double): String?`.
* **UI (`WorkoutClusterComponents.kt`)**: In `WorkoutClusterMetadataBlock`, calculate `maxOf(cluster.hitCount, stats?.workoutCount ?: 0)` for hit badge; render bidirectional location (`Start & Ziel` or `Start → Ziel`); truncate equipment (`maxLines = 1, Ellipsis`). In `ClusterItem`, set thumbnail to `110.dp x 75.dp`.
* **Filter Scaffold (`FilterBottomSheetScaffold.kt`)**: Bind `R.string.filter_reset` with `maxLines = 1`.
* **UI Consistency**: Reference screen `WorkoutClustersScreen.kt`; reused `FilterBottomSheetScaffold`, `DarkMapAntiFlashOverlay`; tokens `TTColor`, `RoundedCornerShape(12.dp)`, `MaterialTheme.colorScheme.surface`. No one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Localization Resource Definition (`REQ-UI-493.4`)
* Files: `app/src/main/res/values*/strings.xml` (all 9 locales)
* Action: Add `filter_reset` string entries across all 9 locales without emojis.

### Step 2: Filter Bottom Sheet Reset Button Polish (`REQ-UI-493.4`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/common/filters/FilterBottomSheetScaffold.kt`
* Action: Bind `R.string.filter_reset` with `maxLines = 1` and `overflow = TextOverflow.Ellipsis`.

### Step 3: ViewModel Bidirectional Location Lookup (`REQ-UI-493.2`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt`
* Action: Implement `getLocationName(lat: Double, lng: Double): String?`.

### Step 4: Repository Lightweight Load Reconciliation (`REQ-UI-493.1`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterRepository.kt`
* Action: Self-heal clusters with `hitCount == 0` when hydrated summary stats contain `workoutCount > 0`.

### Step 5: ClusterItem Layout, Badge Healing & Ellipsis (`REQ-UI-493.1`–`REQ-UI-493.3`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt`
* Action: Update `ClusterItem` map thumbnail to `110.dp x 75.dp`; update `WorkoutClusterMetadataBlock` with effective hit count, bidirectional endpoints (`Start & Ziel` or `Start → Ziel`), and equipment `maxLines = 1, Ellipsis`.

### Step 6: Automated Test Verification (`TST-UI-493.1`–`TST-UI-493.5`)
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterItemAndFilterPolishTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.ClusterItemAndFilterPolishTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests (`TST-UI-493`), visual/layout contract tests, and full Tier 1 cluster suite.
* **Rollback**: Atomic git commit isolation on `feature/ATT-5846`.
