# Stage 5: Walkthrough & Verification - ATT-6710: Live sparkline buffers become empty when backgrounding app or recreating TrackingViewModel

**Ticket**: [ATT-6710](https://rainerblind.atlassian.net/browse/ATT-6710) | **Sub-task**: `[Test]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.2` | **Branch**: `feature/ATT-6710`  
**Requirement Mapping**: `REQ-TRK-059` (`REQ-TRK-059.6`..`REQ-TRK-059.8`) | **Test Mapping**: `TST-TRK-059` (`TST-TRK-059.6`..`TST-TRK-059.8`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Executive Summary & Verification Overview
ATT-6710 fixes three compounding root causes that caused live sparkline trend graphs (`SensorSparklineView` / `LiveSparklineBuffer`) in the tracking cockpit to reset to empty:
1. **Activity Task Preservation**: In `ATrainingTrackerApp.kt:260`, updated Layer 3 root `BackHandler` to invoke `activity.moveTaskToBack(true)` instead of `activity.finish()`, preserving `MainActivityWithNavigation` and its `ViewModelStore` on root Back.
2. **Singleton Repository Persistence**: In `BANALServiceRepository.kt`, implemented thread-safe `sparklineBuffers` registry (`registerSparklineBuffer`, `getSparklineSamples`, `clearSparklineBuffers`, `updateSparklineBuffers`) accumulating 1 Hz incoming numeric telemetry across drawer navigation and resetting on `clearBreadcrumbs()`.
3. **Non-Emitting Tick Sample Retention**: In `TrackingViewModel.applySensorData()`, all `showGraph == true` fields unconditionally pull latest sample snapshots from `BANALServiceRepository`, eliminating the stale `cachedBaseFields` `emptyList()` fallback.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-059.6` | `TST-TRK-059.6` | Unit (`BANALServiceRepositorySparklineTest`) | **PASSED** | `Verified` |
| `REQ-TRK-059.7` | `TST-TRK-059.7` | Unit (`TrackingViewModelSparklineTest`) | **PASSED** | `Verified` |
| `REQ-TRK-059.8` | `TST-TRK-059.8` | Contract & Logic (`ATrainingTrackerAppNavigationTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-TRK-059.5` | Tier 1 Targeted Package Suite | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.repositories.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.*" --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*"`
```text
BUILD SUCCESSFUL in 1m 28s
33 actionable tasks: 1 executed, 32 up-to-date
```
Execution Scope: 407 tests executed across modified packages (`repositories`, `ui.tracking.tracking`, `ui.navigation`, `build`, `theme`, `components.core`), 100% pass rate.

### Living Documentation & Governance Check
Command: `python3 tools/verify_requirement_governance.py`
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Zero unmounted Composable islands detected in git diff.
PASS: Repository encapsulation verified (zero leaky DatabaseManager/Cursor members).
PASS: Zero architecture or system specification modifications detected.
Modified/Altered existing requirement(s) detected: REQ-TRK-059.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Invariant & Governance Verification
* **Bounded Buffer Memory**: `LiveSparklineBuffer` bounds ($\le 300$ samples / $\le 1\text{ KB}$ per tile) and decimation math preserved without modification.
* **Child Navigation Hierarchy**: Non-root navigation (backstack popping, drawer navigation, bottom sheets) is 100% preserved.
* **Recomposition Hygiene**: Rule 66 respected; `SensorSparklineView` canvas rendering unchanged with zero snapshot state writes during composition.
