# Stage 3: Implementation Plan - ATT-6710: Live sparkline buffers become empty when backgrounding app or recreating TrackingViewModel

**Ticket**: [ATT-6710](https://rainerblind.atlassian.net/browse/ATT-6710) | **Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) | **Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.2` | **Branch**: `feature/ATT-6710` | **Req/Test**: `REQ-TRK-059.6`..`REQ-TRK-059.8` / `TST-TRK-059.6`..`TST-TRK-059.8`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Architecture Rationale
Live sparkline trend graphs in the tracking cockpit reset to empty when backgrounding via system Back or recreating `TrackingViewModel`. We implement a 3-part architectural solution:
1. Preserve Activity and `ViewModelStore` on root Back by invoking `activity.moveTaskToBack(true)` instead of `activity.finish()` in `ATrainingTrackerApp.kt`.
2. Persist `LiveSparklineBuffer` instances in singleton `BANALServiceRepository`, continuously feeding samples at 1 Hz during `startObservingBANALService()` and clearing only on `clearBreadcrumbs()`.
3. Eliminate `cachedBaseFields` `emptyList()` fallback in `TrackingViewModel.applySensorData()`, unconditionally populating `sparklineSamples` from the repository buffer for all `showGraph == true` fields.

---

## 2. System Invariants & Preserved Behavior
1. **Bounded Buffer Memory**: `LiveSparklineBuffer` memory bounds ($\le 300$ samples / $\le 1\text{ KB}$ per tile) and decimation math remain unchanged.
2. **Back-Stack Integrity**: Non-root back navigation (child screens, bottom sheets, navigation drawer) remains 100% identical.
3. **Recomposition Hygiene**: Rule 66 respected; `SensorSparklineView` canvas rendering unchanged; no snapshot state writes during composition.
4. **Lifecycle Clearing**: Sparkline buffers reset only when a workout session starts or resets (`clearBreadcrumbs()`).

---

## 3. UI Consistency (Design Guidelines)
* **Reused Components**: `SensorSparklineView` (`ui/tracking/SensorSparklineView.kt`), `SensorFieldView` (`ui/tracking/SensorFieldView.kt`).
* **Theming**: Centralized `MaterialTheme.colorScheme.primary` and sport zone colors (`TTColor`); zero ad-hoc styling.

---

## 4. Step-by-Step Implementation Sequence

### Step 1: Singleton Sparkline Buffer Persistence in `BANALServiceRepository` (`REQ-TRK-059.6`)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt`
* **Changes**:
  - Add thread-safe registry: `ConcurrentHashMap<Long, SparklineBufferRegistration>`.
  - Expose `getOrCreateSparklineBuffer(sensorFieldId, horizonSeconds, sensorType, filterType, filterConstant, deviceName)`, `removeSparklineBuffer(sensorFieldId)`, `getSparklineSamples(sensorFieldId)`, and `clearSparklineBuffers()`.
  - In `startObservingBANALService()`: iterate registered buffers and append incoming numeric telemetry samples at 1 Hz matching by `(sensorType, filterType, filterConstant, deviceName)`.
  - In `clearBreadcrumbs()`: invoke `clearSparklineBuffers()`.

### Step 2: Activity Task Preservation on Root Back Navigation (`REQ-TRK-059.8`)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt`
* **Changes**: In Layer 3 root `BackHandler`, call `activity.moveTaskToBack(true)` instead of `activity.finish()` when `currentRoute == NavRoutes.START_TRACKING` and `previousBackStackEntry == null`.

### Step 3: Eliminate Stale `cachedBaseFields` Fallback in `TrackingViewModel` (`REQ-TRK-059.7`)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt`
* **Changes**: Delegate sparkline buffer management to `banalServiceRepository`. In `applySensorData()`, ensure every field with `showGraph == true` populates `sparklineSamples` from `banalServiceRepository.getSparklineSamples(sensorFieldId)` so non-emitting ticks retain buffered samples.

### Step 4: Unit & Contract Test Implementation (`TST-TRK-059.6` – `TST-TRK-059.8`)
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepositorySparklineTest.kt` (`TST-TRK-059.6`)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModelSparklineTest.kt` (`TST-TRK-059.7`)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerAppNavigationTest.kt` (`TST-TRK-059.8`)

### Step 5: Living Documentation Verification (`REQ-TRK-059.6`..`REQ-TRK-059.8`, `TST-TRK-059.6`..`TST-TRK-059.8`)
* **Files**: `docs/requirements/req_tracking_cockpit.md`, `docs/tests/tst_tracking_cockpit.md`
* **Changes**: Update requirement and test specification statuses from `[Planned]` to `[Verified]`.

### Step 6: Targeted Verification & Governance Checks
* **Commands**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.repositories.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  * `python3 tools/verify_requirement_governance.py`
