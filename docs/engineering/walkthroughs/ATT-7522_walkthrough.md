# Stage 5 Walkthrough - ATT-7522: Cockpit Inactive Sensor Placeholder Curve in ScreenMode.TRACKING

**Ticket**: [ATT-7522](https://rainerblind.atlassian.net/browse/ATT-7522) | **Sub-task**: `[Test]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7522`  
**Requirement Mapping**: `REQ-TRK-059` (`REQ-TRK-059.11`) | **Test Spec ID**: `TST-TRK-059` (`TST-TRK-059.11`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-13  

---

## 1. Executive Summary & Implementation Verification
ATT-7522 resolves the athlete-facing empty void in Cockpit tiles during live workout recording/standby (`ScreenMode.TRACKING`) when sparkline trend graphs are enabled (`showGraph = true`) but the sensor is disconnected, inactive, or awaiting telemetry:
1. **Subtle Placeholder Curve in ScreenMode.TRACKING (`REQ-TRK-059.11`, ATT-7522)**:
   - In `SensorSparklineView.kt`, added `isPlaceholder: Boolean = false`. When activated, renders a subtle, dimmed polyline (`alpha = 0.35f`), dashed stroke styling (`PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)`), suppresses the gradient fill and terminal point circle to prevent synthetic confusion, and dims X-axis labels (`alpha = 0.5f`).
   - In `SensorFieldView.kt`, when `fieldState.showGraph == true` and `fieldState.sparklineSamples.isEmpty()`, passes `DEFAULT_PREVIEW_SPARKLINE_SAMPLES`, effective horizon span, and `isPlaceholder = (screenMode == ScreenMode.TRACKING)` to `SensorSparklineView`.
2. **Immediate & Seamless Transition to Live Telemetry**:
   - As soon as genuine telemetry arrives (`fieldState.sparklineSamples.isNotEmpty()`), `SensorFieldView` immediately switches to live recorded samples with `isPlaceholder = false` (solid trend stroke, gradient fill, and current-reading terminal dot).
3. **Configuration Mode & Dialog Preview Preservation**:
   - `ScreenMode.CONFIGURATION`, `ScreenMode.PREVIEW`, and `EditSensorFieldDialog` retain standard full-contrast preview rendering with `isPlaceholder = false`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-059.11` | `TST-TRK-059.11a` | Unit (`SensorSparklineViewTest`) | **PASSED** | `Verified` |
| `REQ-TRK-059.11` | `TST-TRK-059.11b` | Contract (`SensorFieldViewLayoutTest`) | **PASSED** | `Verified` |
| `REQ-TRK-059.11` | `TST-TRK-059.11c` | Unit & Contract (`SensorFieldViewLayoutTest`, `SensorSparklineViewTest`) | **PASSED** | `Verified` |
| `REQ-TRK-059.11` | `TST-TRK-059.11d` | Contract (`EditSensorFieldDialogSparklineTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-TRK-059.5` | Targeted Cockpit Suite & Theme Centralization Audit | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit Test Suite Execution
Commands:
- `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.SensorSparklineViewTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldViewLayoutTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldDialogSparklineTest"`
- `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
- `./gradlew :app:compileDebugAndroidTestKotlin`
- `./gradlew :app:lintDebug`
```text
BUILD SUCCESSFUL (100% pass rate across targeted UI tests, theme audit, and AndroidTest compilation)
```

### Requirement Governance Verification
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

## 4. Invariant Protection & System Properties
* **Telemetry Truth Invariant**: Standby placeholder curves are visually distinct (dashed, dimmed, dot-less); authentic live samples immediately supersede placeholder curves upon arrival.
* **Composition Hygiene (Rule 66)**: Zero Compose snapshot state mutations during composition; pure Canvas drawing.
* **Layout Stability**: `resolveSparklineHeight` preserves fixed intrinsic height across placeholder and live states, preventing UI jumps when sensors connect.
* **Zero Hex Color Hardcoding**: Theme colors strictly derive from `MaterialTheme.colorScheme` alphas.
