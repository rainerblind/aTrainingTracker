# Stage 2: Requirement & Test Specification - ATT-5846: ClusterItem & Filter Polish

**Ticket**: [ATT-5846](https://rainerblind.atlassian.net/browse/ATT-5846)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5846`  
**Requirement Mapping**: `REQ-UI-493` (`REQ-UI-493.1`–`REQ-UI-493.4`) | **Test Spec ID**: `TST-UI-493` (`TST-UI-493.1`–`TST-UI-493.5`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-08  

---

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Net-new requirement `REQ-UI-493`, refining `REQ-SET-067` (*Auto-Naming & Hit Counting*), `REQ-UI-186` (*Recognized Start Location Badges*), `REQ-UI-244` (*Cluster Map Thumbnails*), and `REQ-UI-149` (*Filter Bottom Sheets*).
* `Historical Origin & Commit Trace`: Introduced in `ATT-714`, `ATT-1402`, and `ATT-2276`.
* `Root Reason for Existing Formulation`: Initial cluster items only anticipated start locations; square 100dp maps were adapted before 3:2 aspect ratios; filter sheets reused `filter_clear_all` ("Alle zurücksetzen") without considering 50% split button widths.
* `Preservation of Core Invariants`: `GoogleMapOptions().liteMode(true)`, `DarkMapAntiFlashOverlay`, horizontal filter chips `filter_clear_all` contract, zero-touch auto-naming counter integrity, and 9-locale parity without emojis (`ATT-5400`) MUST NOT be compromised.

---

## 1. Requirement Specification (`REQ-UI-493`)

### 1.1 Problem Statement & Rationale
In `Lieblingsstrecken` cluster management (`WorkoutClusterComponents.kt`, `WorkoutClusterRepository.kt`, and `FilterBottomSheetScaffold.kt`), UI audits revealed four presentation defects: (1) cluster cards display `0 Aufzeichnungen` when `RouteClusters.hitCount` is desynchronized from `WorkoutSummaries`; (2) cluster cards only display start locations and fail to resolve destination coordinates (`endLat`, `endLng`); (3) square 100x100dp map thumbnails force awkward vertical whitespace beside metadata, and multi-gear equipment lists wrap unbounded; and (4) the filter bottom sheet reset button label ("Alle zurücksetzen") wraps onto two lines in the 50% split button row.

### 1.2 Functional & Architectural Requirements
1. **Hit Count Display Healing & Reconciliation (`REQ-UI-493.1`)**: In `WorkoutClusterComponents.kt` (`WorkoutClusterMetadataBlock`), displayed recordings count SHALL derive from `maxOf(cluster.hitCount, stats?.workoutCount ?: 0)`. In `WorkoutClusterRepository.kt` (`loadAllClustersLightweight`), clusters with `hitCount == 0` that have associated workouts in hydrated summary stats SHALL have their `hitCount` healed.
2. **Bidirectional Route Endpoint Location Context (`REQ-UI-493.2`)**: `WorkoutClustersViewModel` SHALL expose `getLocationName(lat: Double, lng: Double): String?`. In `WorkoutClusterMetadataBlock`, if start and destination resolve to the same location, display `Start & Ziel: [Location]` (`R.string.workout_start_and_destination`); if distinct, display `[Start] → [Ziel]`; if only one endpoint resolves, fall back to `Start: [Start]` or `Ziel: [Ziel]`.
3. **Landscape Map Thumbnail & Equipment Text Constraining (`REQ-UI-493.3`)**: In `ClusterItem`, route preview map thumbnail SHALL use a 3:2 landscape geometry of `width = 110.dp, height = 75.dp` with rounded corners (12dp), preserving `liteMode(true)` and `DarkMapAntiFlashOverlay`. Linked equipment text SHALL enforce `maxLines = 1` and `overflow = TextOverflow.Ellipsis`.
4. **Single-Line Filter Sheet Reset Button Action (`REQ-UI-493.4`)**: In `FilterBottomSheetScaffold.kt`, reset button SHALL bind concise localized resource `R.string.filter_reset` ("Zurücksetzen" / "Reset") with `maxLines = 1` across all 9 supported locales.

### 1.3 9-Locale Localization Table (`filter_reset`)
| Locale | File Path | Key | Value |
| :--- | :--- | :--- | :--- |
| EN (Base) | `res/values/strings.xml` | `filter_reset` | `Reset` |
| DE | `res/values-de/strings.xml` | `filter_reset` | `Zurücksetzen` |
| ES | `res/values-es/strings.xml` | `filter_reset` | `Restablecer` |
| FR | `res/values-fr/strings.xml` | `filter_reset` | `Réinitialiser` |
| IT | `res/values-it/strings.xml` | `filter_reset` | `Reimposta` |
| JA | `res/values-ja/strings.xml` | `filter_reset` | `リセット` |
| NL | `res/values-nl/strings.xml` | `filter_reset` | `Resetten` |
| PL | `res/values-pl/strings.xml` | `filter_reset` | `Resetuj` |
| PT | `res/values-pt/strings.xml` | `filter_reset` | `Redefinir` |

---

## 2. Test Specification (`TST-UI-493`)

### Test Cases
* **TST-UI-493.1 (Hit Count Healing & Repo Reconciliation)**: Assert `effectiveWorkoutCount` resolves `maxOf(cluster.hitCount, stats?.workoutCount ?: 0)` so clusters with `hitCount = 0` but `workoutCount = 3` display 3 recordings; assert `WorkoutClusterRepository.loadAllClustersLightweight()` heals `hitCount` in DB when `hitCount == 0`.
* **TST-UI-493.2 (Bidirectional Location Resolution)**: Assert identical start and destination displays `"Start & Ziel: [Location]"`; assert distinct endpoints display `"[Start] → [Ziel]"`; assert single endpoint falls back to `"Start: ..."` or `"Ziel: ..."`.
* **TST-UI-493.3 (Landscape Map Thumbnail & Equipment Ellipsis)**: Assert map thumbnail dimensions are 110.dp width by 75.dp height; assert equipment text enforces `maxLines = 1` and `TextOverflow.Ellipsis`.
* **TST-UI-493.4 (Filter Sheet Reset Button Single-Line & 9-Locale Parity)**: Assert `R.string.filter_reset` exists across all 9 locales with zero inline emojis; assert `FilterBottomSheetScaffold` binds `filter_reset` with `maxLines = 1`.
* **TST-UI-493.5 (Tier 1 Targeted Unit Test Regression)**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.database.*Cluster*" --tests "com.atrainingtracker.trainingtracker.ui.common.filters.*" --tests "com.atrainingtracker.trainingtracker.localization.*" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-493.1` | Unit & Contract | `WorkoutClusterComponents.kt`, `WorkoutClusterRepository.kt` | `REQ-UI-493.1` | Planned |
| `TST-UI-493.2` | Contract Test | `WorkoutClusterComponents.kt`, `WorkoutClustersViewModel.kt` | `REQ-UI-493.2` | Planned |
| `TST-UI-493.3` | Layout Contract | `WorkoutClusterComponents.kt` | `REQ-UI-493.3` | Planned |
| `TST-UI-493.4` | Localization & UI | `FilterBottomSheetScaffold.kt`, `strings.xml` (9 locales) | `REQ-UI-493.4` | Planned |
| `TST-UI-493.5` | Regression | Modified Package Test Suites | `REQ-UI-493.1`..`REQ-UI-493.4` | Planned |
