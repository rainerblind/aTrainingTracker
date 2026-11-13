# Stage 3 Implementation Plan - ATT-7522: Cockpit Inactive Sensor Placeholder Curve in ScreenMode.TRACKING

**Ticket**: [ATT-7522](https://rainerblind.atlassian.net/browse/ATT-7522)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Requirement Mapping**: `REQ-TRK-059.11` | **Test Spec ID**: `TST-TRK-059.11`  
**Branch**: `feature/ATT-7522` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Architecture & Component Decomposition (SWE.2)
- **Target Components**:
  - `app/src/main/java/.../ui/tracking/SensorSparklineView.kt`: Add parameter `isPlaceholder: Boolean = false` to `SensorSparklineView`. When `isPlaceholder == true`, render the curve with dimmed stroke (`alpha = 0.35f`), dashed polyline (`PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)`), omit gradient fill and terminal reading circle, and apply dimmed alpha (`0.5f`) to the bottom X-axis label row.
  - `app/src/main/java/.../ui/tracking/SensorFieldView.kt`: In Cockpit sensor tiles with `fieldState.showGraph == true`, when `fieldState.sparklineSamples.isEmpty()`, pass `DEFAULT_PREVIEW_SPARKLINE_SAMPLES`, effective horizon span (`fieldState.graphHorizonSeconds`), and `isPlaceholder = (screenMode == ScreenMode.TRACKING)`. When live samples arrive (`fieldState.sparklineSamples.isNotEmpty()`), immediately supply authentic samples with `isPlaceholder = false`.
  - `app/src/test/.../ui/tracking/SensorSparklineViewTest.kt`: Unit tests verifying stroke styling, dash effect, dot suppression, and dimmed label alpha when `isPlaceholder = true` vs `false`.
  - `app/src/test/.../ui/tracking/SensorFieldViewLayoutTest.kt`: Contract tests verifying placeholder resolution in `ScreenMode.TRACKING`, preview preservation in `ScreenMode.CONFIGURATION`/`PREVIEW`, and live telemetry supersedence.

---

## 2. Atomic Implementation Steps
1. **Step 1 - SensorSparklineView Placeholder Styling (`REQ-TRK-059.11`, `TST-TRK-059.11a`)**:
   - Add parameter `isPlaceholder: Boolean = false` with default argument to `SensorSparklineView`.
   - Update Canvas drawing: if `isPlaceholder`, stroke uses `lineColor.copy(alpha = 0.35f)` and `PathEffect.dashPathEffect`, gradient fill is omitted, and the terminal point circle is skipped.
   - Update label row: if `isPlaceholder`, apply `alpha = 0.5f` to `MaterialTheme.colorScheme.onSurfaceVariant`.
2. **Step 2 - SensorFieldView Fallback Resolution & Live Transition (`REQ-TRK-059.11`, `TST-TRK-059.11b/c`)**:
   - In `SensorFieldView.kt:466-485`, resolve:
     - `val hasLiveSamples = fieldState.sparklineSamples.isNotEmpty()`
     - `val effectiveSamples = if (hasLiveSamples) fieldState.sparklineSamples else DEFAULT_PREVIEW_SPARKLINE_SAMPLES`
     - `val effectiveSpan = if (hasLiveSamples) fieldState.sparklineSpanSeconds else (if (fieldState.graphHorizonSeconds > 0) fieldState.graphHorizonSeconds else 300)`
     - `val isPlaceholder = !hasLiveSamples && screenMode == ScreenMode.TRACKING`
   - Pass `isPlaceholder = isPlaceholder` to `SensorSparklineView`.
3. **Step 3 - Unit & Contract Test Suite Implementation (`TST-TRK-059.11a-d`)**:
   - Implement `testSensorSparklineView_placeholderStylingContracts` in `SensorSparklineViewTest.kt`.
   - Implement `testSensorFieldView_trackingModePlaceholderContracts` and `testSensorFieldView_liveTelemetrySupersedesPlaceholder` in `SensorFieldViewLayoutTest.kt`.
   - Assert `EditSensorFieldDialogSparklineTest` passes without regression.
4. **Step 4 - Automated Verification & Governance**:
   - Run targeted test suites: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"`.
   - Run audit tests: `ThemeColorCentralizationAuditTest` and `verify_requirement_governance.py`.

---

## 3. Invariant Protection & System Properties
- **Telemetry Truth Invariant**: Standby placeholder curves are visually distinct (dashed, dimmed, dot-less); authentic live samples immediately supersede placeholder curves upon arrival.
- **Composition Hygiene (`Rule 66`)**: Zero Compose snapshot state mutations during composition; pure Canvas drawing.
- **UI Consistency & Layout Stability**: `resolveSparklineHeight` maintains fixed intrinsic tile height across placeholder and live states, preventing layout shift when sensors connect.
- **Backward Compatibility**: `EditSensorFieldDialog.kt` retains existing preview rendering (`isPlaceholder = false`).

---

## 4. UI Consistency & Design Guidelines Compliance
- Complies with `docs/design_guidelines.md §2` (Dynamic Theme & Styling) using `MaterialTheme.colorScheme` alphas without hardcoded hex colors, and §2.3 (No Hardcoded Fixed Heights) preserving canonical dynamic height resolution.

---

## 5. Verification Strategy & Living Documentation
- Test Suite: `SensorSparklineViewTest`, `SensorFieldViewLayoutTest`, `EditSensorFieldDialogSparklineTest`, `ThemeColorCentralizationAuditTest`.
- Status Lifecycle: Advance `REQ-TRK-059.11` to `[Implemented]` in Stage 4, and `[Verified]` in Stage 5.
