# Stage 1 Analysis: ATT-1389 - Aftermath: Heart Rate 5-Zone Distribution Bar (Time-in-Zones)

**Ticket**: [ATT-1389](https://atrainingtracker.atlassian.net/browse/ATT-1389)  
**Sub-task**: [ATT-1709](https://atrainingtracker.atlassian.net/browse/ATT-1709) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1389`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Problem Statement & Motivation

Ambitious amateur athletes ("Halb-Profis") training by heart rate (Zone 1 Recovery through Zone 5 VO2max) need immediate confirmation of their training compliance upon concluding an activity (e.g. *"Was this a clean Zone 2 base run or did I overpace into Zone 3/4?"*).

Currently, in the Aftermath workout inspection view ([TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt) / [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt)):
- Athletes only see scalar extrema (Average HR, Max HR in [WorkoutExtrema.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutextrema/WorkoutExtrema.kt)).
- There is no visual breakdown of time spent in each physiological intensity zone.
- Athletes are forced to export their workouts to Strava or Garmin Connect to see their time-in-zones distribution.
- Indoor workouts (`trainer = true`) lacking GPS track/elevation currently have minimal graphical feedback.

---

## 2. Root Cause & Architectural Investigation (Forensic Analysis)

### 2.1 Heart Rate Zone Storage & Configuration
1. User-configured Heart Rate zones are persisted in `SettingsDataStore`:
   - `ZoneType.HR_RUN`: Default thresholds Z1: 130 bpm, Z2: 150 bpm, Z3: 165 bpm, Z4: 180 bpm.
   - `ZoneType.HR_BIKE`: Default thresholds Z1: 130 bpm, Z2: 150 bpm, Z3: 170 bpm, Z4: 180 bpm.
2. Zone thresholds are accessible synchronously via [SettingsDataStoreJavaHelper.getZoneMax](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStoreJavaHelper.kt) or asynchronously via `SettingsDataStore.getZoneMaxFlow`.
3. In [TrackingTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt#L211), the active zone type is chosen dynamically:
   ```kotlin
   val zoneType = if (isCycling) SettingsDataStore.ZoneType.HR_BIKE else SettingsDataStore.ZoneType.HR_RUN
   ```

### 2.2 Workout Sample Storage
1. Recorded workout samples are stored in SQLite via [WorkoutSamplesDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSamplesDatabaseManager.java) in a dedicated table named `WorkoutSamplesDatabaseManager.getTableName(baseFileName)`.
2. Each sample row contains:
   - `TIME_ACTIVE`: Monotonic active elapsed time in seconds.
   - `TIME_TOTAL`: Total elapsed time in seconds.
   - `HR`: Instantaneous heart rate in bpm.
3. Samples are recorded at 1Hz during active tracking. Paused durations do not advance `TIME_ACTIVE`.

### 2.3 Existing Aftermath Data Flow
1. [TrackOnMapAftermathViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TrackOnMapAftermathViewModel.kt) orchestrates data loading on `Dispatchers.IO`:
   - Decodes fast track polyline and elevation streams.
   - Fetches high-resolution `PathPoints` via [WorkoutRepository.getWorkoutTrackPoints](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt#L284).
   - Publishes `AftermathMapUIState` to the UI via `StateFlow`.
2. [TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt) embeds [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt), which renders:
   - `header`: [WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt)
   - `mapArea`: [ATrainingTrackerMap.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt)
   - `elevationProfile`: [ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt)

---

## 3. Chesterton's Fence & Invariant Audit

1. **Chesterton's Fence: `MapDetailLayout` Reusability**:
   - `MapDetailLayout` is shared by `TrackOnMapScreen` (Aftermath), `RouteOnMapScreen` (Routes), and `SegmentOnMapScreen` (Segments).
   - *Invariant*: Adding analytics support must use optional composable slots (`analyticsContent: @Composable ColumnScope.() -> Unit = {}`) with no-op defaults so Routes and Segments are completely unaffected.
2. **Chesterton's Fence: Missing Telemetry Handling**:
   - Workouts recorded without a heart rate monitor (or with heart rate sensor dropout throughout) must NOT show an empty, broken, or 0% zone bar.
   - *Invariant*: If valid heart rate sample count is 0 or total active HR time is 0, `hrZoneDistribution` must evaluate to `null` and the UI card must be completely omitted.
3. **Chesterton's Fence: Design System Consistency**:
   - Zone colors must strictly match `TTColor.Zone1` through `TTColor.Zone5` from [Color.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt#L121-L125).
   - Zone labels must reuse existing localized resources `R.string.zone_1_label` through `R.string.zone_5_label`.
4. **Chesterton's Fence: SQLite Single-Thread Concurrency**:
   - Sample database reading must run strictly on `Dispatchers.IO` using read-only queries with try-with-resources / `.use { }` to prevent cursor leaks.

---

## 4. Scope Bounding & Traceability

### In-Scope (ATT-1389)
1. **Domain Model**:
   - `ZoneTimeEntry`: Encapsulates `zoneIndex: Int` (1..5), `zoneLabelResId: Int`, `durationSec: Long`, `percentage: Float`, `color: Color`.
   - `ZoneDistributionData`: Encapsulates `totalActiveTimeSec: Long`, `entries: List<ZoneTimeEntry>`, `hasTelemetry: Boolean`.
   - `HeartRateZoneThresholds`: Encapsulates `z1Max`, `z2Max`, `z3Max`, `z4Max`.
2. **Computation Engine**:
   - `ZoneDistributionCalculator`: Pure mathematical engine computing time-in-zones from raw samples without Android dependencies.
   - `WorkoutRepository.getHeartRateZoneDistribution(workoutId, bSportType)`: Queries SQLite samples and applies zone calculation.
3. **ViewModel Integration**:
   - Extend `AftermathMapUIState` with `hrZoneDistribution: ZoneDistributionData? = null`.
   - In `TrackOnMapAftermathViewModel.loadAftermathData()`, compute and emit `hrZoneDistribution`.
4. **UI Presentation**:
   - `HeartRateZoneDistributionCard`: Sleek, rounded horizontal stacked bar with proportional segments using `TTColor.Zone1..Zone5`.
   - Compact legend showing Zone, time (formatted `m:ss` or `h:mm:ss`), and percentage (`X%`).
   - Slot `analyticsContent` into `MapDetailLayout` and pass card from `TrackOnMapScreen`.
5. **Localization**:
   - 9-language localization parity for section header (`aftermath_hr_zones_title`).
6. **Testing**:
   - Pure unit tests for `ZoneDistributionCalculator` (zone assignment, duration accumulation, edge cases, zero telemetry).
   - UI unit tests for `HeartRateZoneDistributionCard`.
   - 9-language localization test.

### Out-of-Scope (Deferred to Sibling Tickets)
- Cycling Power 5-Zone Distribution -> [ATT-1390](https://atrainingtracker.atlassian.net/browse/ATT-1390).
- Lap & Interval Split Chart -> [ATT-1392](https://atrainingtracker.atlassian.net/browse/ATT-1392).
- Enhanced Shareable Snapshot integration of zone graphics -> [ATT-1393](https://atrainingtracker.atlassian.net/browse/ATT-1393).

---

## 5. Risk Assessment & Mitigation

| Risk | Impact | Likelihood | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| **Large sample tables causing UI jank** | Medium | Low | Computation runs asynchronously on `Dispatchers.IO` with pre-fetched thresholds ($O(1)$ zone lookup per row). |
| **Missing HR column in legacy/imported tables** | High | Low | Query table schema dynamically; if `HR` column index is `-1`, immediately return `null`. |
| **Variable sampling intervals / pause drift** | Low | Medium | Time deltas $\Delta t$ are derived from `TIME_ACTIVE` and clamped to `0L..5L` to avoid pause spikes. |
| **Vertical screen space crowding on small devices** | Medium | Low | Compact stacked bar (10dp) + concise single-row or two-row legend (total height < 60dp). |

---

## 6. Gate 1 Self-Audit Checklist

- [x] Subtask ATT-1709 created and placed in sprint `2026-40.5`.
- [x] Root cause, user motivation, and problem statement thoroughly explored.
- [x] Chesterton's Fences and invariants explicitly cataloged.
- [x] Scope boundary cleanly isolates Heart Rate zones from Power (ATT-1390), Laps (ATT-1392), and Snapshots (ATT-1393).
- [x] Ready for Gate 1 Review Agent Audit.
