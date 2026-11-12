# Stage 5: Walkthrough & Verification - ATT-7314: Render preview sparkline and preserve buffer history when graph is enabled for inactive sensors

**Ticket**: [ATT-7314](https://rainerblind.atlassian.net/browse/ATT-7314) | **Sub-task**: `[Test]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.4` | **Branch**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-TRK-059` (`REQ-TRK-059.11`, `REQ-TRK-059.12`) | **Test Mapping**: `TST-TRK-059` (`TST-TRK-059.11`, `TST-TRK-059.12`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview
ATT-7314 resolves two usability and data continuity deficiencies when configuring live sparkline graph tiles:
1. **Preview Sparkline in Configuration Mode & Edit Dialog**:
   - In `SensorSparklineView.kt`, exported `DEFAULT_PREVIEW_SPARKLINE_SAMPLES` (24 smooth representative points forming an athletic workout progression).
   - In `SensorFieldView.kt`, when `fieldState.showGraph == true` and `fieldState.sparklineSamples.isEmpty()`, if `screenMode != ScreenMode.TRACKING` (i.e. Edit Tracking Tabs in `ScreenMode.CONFIGURATION` or `ScreenMode.PREVIEW`), the tile provides the preview samples and effective horizon duration to `SensorSparklineView`, guaranteeing a clear visual layout representation rather than blank space.
   - When `screenMode == ScreenMode.TRACKING`, inactive sensors strictly render blank, preventing any synthetic data pollution during live recording.
   - In `EditSensorFieldDialog.kt`, added a live preview `Surface` card beneath the stepped slider rendering `SensorSparklineView` with `DEFAULT_PREVIEW_SPARKLINE_SAMPLES` and the active horizon seconds.
2. **Buffer History Preservation Across Horizon Changes**:
   - In `LiveSparklineBuffer.kt`, implemented thread-safe `transferFrom(previous: LiveSparklineBuffer)` with deadlock-free lock ordering (`System.identityHashCode`), retaining samples across fixed-to-fixed, fixed-to-full, and full-to-fixed transitions and correctly maintaining elapsed time.
   - In `BANALServiceRepository.registerSparklineBuffer()`, when re-registering an identical sensor configuration with a modified time horizon (`configHash` match), invokes `buffer.transferFrom(existing.buffer)` to preserve historical samples rather than dropping to 0 samples.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-059.11` | `TST-TRK-059.11a` | Unit (`SensorSparklineViewTest`, `SensorFieldViewLayoutTest`) | **PASSED** | `Verified` |
| `REQ-TRK-059.11` | `TST-TRK-059.11b` | Unit (`SensorSparklineViewTest`) | **PASSED** | `Verified` |
| `REQ-TRK-059.11` | `TST-TRK-059.11c` | Unit & Contract (`EditSensorFieldDialogSparklineTest`) | **PASSED** | `Verified` |
| `REQ-TRK-059.12` | `TST-TRK-059.12a` | Unit (`LiveSparklineBufferSpanTest`) | **PASSED** | `Verified` |
| `REQ-TRK-059.12` | `TST-TRK-059.12b` | Unit & Repository (`BANALServiceRepositorySparklineTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-TRK-059.5` | Targeted Cockpit Suite (516 tests) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit Test Suite Execution
Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.repositories.BANALServiceRepositorySparklineTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
```text
BUILD SUCCESSFUL in 1m 24s
33 actionable tasks: 5 executed, 28 up-to-date
```
Execution Scope: 516 tests completed with 100% pass rate across `LiveSparklineBufferSpanTest`, `BANALServiceRepositorySparklineTest`, `SensorSparklineViewTest`, `EditSensorFieldDialogSparklineTest`, `SensorFieldViewLayoutTest`, and `TranslationParityTest`.

### Living Documentation & Governance Check
Command: `python3 tools/verify_requirement_governance.py --text-file docs/engineering/test_specs/ATT-7314_test_spec.md`
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
* **Live Tracking Authenticity**: Zero fake data bleeds into `ScreenMode.TRACKING` when sensors are disconnected.
* **Bounded Buffer Memory**: $\le 300$ samples ($\le 1\text{ KB}$ per tile) preserved across all decimation and migration operations.
* **Thread Safety**: Lock ordering based on `System.identityHashCode` prevents lock inversion deadlocks.
* **Zero Compose Snapshot State writes during composition** (Rule 66).
* **100% 9-Language Localization Parity**: Reuses existing strings `Preview` and `sensor_field_graph_horizon` with full coverage.
