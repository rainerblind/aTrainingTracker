# Stage 1 Analysis: Render Historical Duration and Speed/Pace Trend Chart for Workout Clusters (Lieblingsstrecken)

**Ticket**: [ATT-4067](https://rainerblind.atlassian.net/browse/ATT-4067)  
**Sub-task**: [ATT-4261](https://rainerblind.atlassian.net/browse/ATT-4261) (`[Analysis]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Executive Summary & Problem Domain

The Workout Clusters ("Lieblingsstrecken" / recurring spatial routes) feature automatically aggregates repeated training sessions matching a spatial fingerprint (Start, End, Apex, and Reference Distance) into persistent route clusters (`WorkoutCluster`). In `WorkoutClusterHeatmapScreen.kt`, summary statistics are currently computed and displayed via `WorkoutClusterStats.kt` (introduced in ATT-791), providing:
- **Tier 1 (Performance)**: Best Time (PR), Average Time, Average Speed/Pace.
- **Tier 2 (Cumulative Volume)**: Total Distance, Total Ascent, Total Active Time.
- **Tier 3 (Activity & Recency)**: Recordings Count Button, Last Activity relative timestamp.

### Identified Gap
While summary scalar metrics quantify overall volume and single-point records, athletes performing identical commutes, time trials, or regular home loops dozens of times lack visibility into **progression over time**. Athletes cannot answer fundamental performance questions:
1. *Is my pace improving, stagnating, or declining over consecutive attempts on this route?*
2. *How did weather, seasonal form, or fatigue affect specific historical efforts relative to my cluster average?*
3. *Where does my Personal Best (PR) sit on the chronological timeline, and how close have recent efforts come to matching it?*

In modern endurance training ecosystems (such as Strava's "Matched Runs" and "Your Efforts" segment trend graphs), athletes rely heavily on an interactive chronological trend visualization to trace performance trajectories across identical courses.

---

## 2. Historical Context & Chesterton's Fence

An archaeological review of the route clusters and visualization architecture reveals critical design decisions:

1. **`WorkoutClusterHeatmapScreen.kt` & `MapDetailLayout.kt` Architecture**:
   - `WorkoutClusterHeatmapScreen` delegates map presentation and bottom-sheet sliding layout to `MapDetailLayout`.
   - The header slot (`WorkoutClusterSummaryHeader`) houses identity editing, fingerprint marker editing, and the multi-tier `WorkoutClusterDetailDashboard`.
   - The bottom-sheet container in `MapDetailLayout` supports vertical scrolling and drag-based height adjustments, allowing additional visual content without obstructing map interactivity.

2. **Data Availability in `WorkoutClustersViewModel.kt`**:
   - Upon selecting a cluster (`selectCluster(cluster)`), `repository.getWorkoutsForCluster(cluster.id)` queries `WorkoutSummariesDatabaseManager` and populates `_clusterWorkouts.value = workouts`.
   - The workouts are exposed as `val clusterWorkouts: StateFlow<List<WorkoutData>>`.
   - Each `WorkoutData` record contains authentic chronological timestamps (`startTimeS`), formatted dates (`formattedDate`), active moving duration (`activeTimeSec`), average speed (`avgSpeedMps`), and sport discriminator (`bSportType`).
   - Thus, **zero database migrations, schema alterations, or heavy background backfills are required**. The exact chronological effort series is already loaded in memory when viewing a cluster detail screen.

3. **Performance & Jank Precaution**:
   - Prior refactoring (SCRUM-199 / ATT-359) offloaded map polyline decoding and peak marker calculations to background dispatchers to keep the UI thread buttery smooth at 60/120 fps.
   - Any new chart component must be lightweight, pure Compose `Canvas`-driven (avoiding heavy external libraries or memory allocations inside draw loops), and calculate geometry only when `clusterWorkouts` updates.

---

## 3. Root Cause & Architectural Gap Analysis

| Aspect | Current State | Target State | Architectural Solution |
| :--- | :--- | :--- | :--- |
| **Effort Progression** | Only scalar aggregates (`bestDurationSec`, `avgDurationSec`, `avgSpeedMps`) | Chronological time-series progression chart | Pure Compose `Canvas` trend chart plotting all member workouts ($N \ge 2$) |
| **Metric Flexibility** | Fixed display of Time and Speed/Pace | User-toggleable metric modes: Duration vs Speed/Pace | Two-segment toggle (`Duration` vs `Speed` / `Pace`) with sport-aware units (km/h vs min/km, mph vs min/mi) |
| **Contextual Benchmarks** | No visual comparison line | Explicit horizontal dashed reference line for Cluster Average | Render dashed line at $Y = \text{avg}$ with distinct styling and label |
| **Personal Best Highlight** | PR only displayed as scalar text in Tier 1 card | Gold PR badge / trophy marker on the fastest chronological effort | Distinctive gold-accented outer ring and trophy icon on the minimum duration point |
| **Interaction & Scoping** | No link between stats and individual efforts | Tap gesture selects/inspects effort and triggers workout peek | Tap detection resolving nearest point within touch radius ($< 24\text{dp}$), surfacing tooltips and triggering `selectWorkoutForPeek(id)` |

---

## 4. Target Design & Component Decomposition

### A. Mathematical Modeling (`WorkoutClusterTrendMath.kt`)
A decoupled, 100% unit-testable mathematical engine responsible for:
1. **Effort Normalization & Ordering**:
   - Filter out invalid or zero-duration workouts (`activeTimeSec > 0`).
   - Sort member workouts strictly ascending by `startTimeS`.
2. **Coordinate Transformation**:
   - Map chronological indices or timestamps to horizontal canvas space $[X_{\min}, X_{\max}]$.
   - Map metric values to vertical canvas space $[Y_{\min}, Y_{\max}]$ with standard visual padding ($15\%$) to avoid clipping extrema.
   - Invert $Y$-axis for Duration and Running Pace (where lower is faster) so upward trend always visually represents superior athletic performance.
3. **Cluster Reference Metrics**:
   - Fast lookup for Personal Best index: $\arg\min_{i} (\text{duration}_i)$ or $\arg\max_{i} (\text{speed}_i)$.
   - Cluster average reference line positioning: $Y_{\text{avg}}$.

### B. UI Component (`WorkoutClusterTrendChartCard.kt`)
A compact, Material 3-compliant card inserted into `WorkoutClusterDetailDashboard`:
1. **Header Row**:
   - Section Title ("Performance Trend" / localized).
   - Mode Toggle: "Duration" vs "Speed" (or "Pace" for running).
2. **Compose `Canvas` Area**:
   - Smooth curved or linear path connecting chronological efforts with subtle translucent gradient fill beneath.
   - Horizontal dashed reference line for the cluster average with subtle text label.
   - Data point circles with accent fill; Gold star/trophy marker for the PR point.
   - Highlight ring for the currently selected/peeked effort.
3. **Interactive Callout / Tooltip**:
   - Tapping an effort displays a popover or compact callout card with:
     - Formatted Date (e.g. "14.10.2026")
     - Effort Duration (`TimeFormatter`) & Speed/Pace (`SpeedFormatter` / `PaceFormatter`)
     - PR Indicator (if applicable)
     - Tap-to-peek action button / trigger.
4. **Conditional Visibility**:
   - If `cluster.hitCount < 2` or `clusterWorkouts.size < 2`, the card gracefully hides, preserving existing clean single-workout presentation.

---

## 5. Scope Bounding & Invariants

### In-Scope
- Pure Compose UI card and canvas plot in `ui/clusters/`.
- Decoupled pure Kotlin math helper for scale calculation, point projection, and touch hit testing.
- Two-state toggle between Duration and Speed/Pace.
- Sport-aware metric labeling and formatting (Cycling: km/h or mph; Running: min/km or min/mi; Others: km/h or m/s).
- Metric vs Imperial unit parity using `DistanceFormatter`, `SpeedFormatter`, `PaceFormatter`, `TimeFormatter`.
- 9-language localization parity for all newly introduced UI labels and strings (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-nl/`, `values-pl/`, `values-pt/`, `values-ru/`).
- Interactive tap selection dispatching to `WorkoutClustersViewModel.selectWorkoutForPeek(id)`.

### Out-of-Scope
- Database schema changes to `RouteClusters.db` or `WorkoutSummaries.db` (existing schema is 100% sufficient).
- Multi-finger zoom/pan gestures (a cluster rarely exceeds a few dozen efforts; a clean auto-scaled timeline fits comfortably within mobile screen bounds).
- External third-party chart libraries (MPAndroidChart, Vico, etc.) to keep APK footprint lean and avoid licensing/dependency bloat.

### Critical Invariants
1. **Upward Is Better**: Visual representation of "higher on chart" must consistently convey faster/better performance. For Speed, higher values are higher on $Y$. For Duration and Pace, lower values (faster times) are plotted higher on $Y$.
2. **Zero Allocation in Draw Loops**: Paths, Brushes, and Paint objects must be remembered and reused across recompositions.
3. **Single Workout Resilience**: If a cluster has only 0 or 1 recorded effort, the trend chart component must not crash or display an empty broken axis; it must hide completely.
4. **Clean-Room Verification**: Zero regression in the existing 3,421 test suite.

---

## 6. Risk Analysis & Mitigation Strategies

| Risk | Impact | Probability | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| **Recomposition Jank During Map Dragging** | Medium | Low | Hoist chart state, isolate `Canvas` into independent composable with `remember(clusterWorkouts, activeMetric)`, and use primitive coordinate structures. |
| **Pace Infinity / Zero Speed Outliers** | High | Low | Sanitize input points in `WorkoutClusterTrendMath`: discard workouts with `activeTimeSec <= 0` or `avgSpeedMps <= 0.0`. |
| **Touch Collision with Map / BottomSheet Drag** | Medium | Medium | Use bounded pointer input with tap gesture disambiguation, consuming taps within point hit radius ($24\text{dp}$) while allowing vertical scroll to pass through. |
| **Localization Missing in Non-English Locales** | High | Low | Exhaustively populate all 9 locale `strings.xml` files in Stage 4 with automated parity verification tests. |

---

## 7. Gate 1 Review Checklist

- [x] Problem domain thoroughly explored and user value proposition articulated.
- [x] Chesterton's Fence archaeology completed: data structures, `MapDetailLayout`, and `WorkoutClustersViewModel` analyzed.
- [x] Clear architectural boundaries defined (Math engine vs UI Card vs ViewModel).
- [x] In-scope and out-of-scope boundaries strictly defined.
- [x] Invariants established (Upward-is-better, zero draw allocations, single-workout resilience).
- [x] Deliverable authored at `docs/engineering/analysis/ATT-4067_analysis.md`.
