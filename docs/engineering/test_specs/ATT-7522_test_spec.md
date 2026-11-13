# Stage 2: Requirement & Test Specification - ATT-7522: Cockpit Inactive Sensor Placeholder Curve in ScreenMode.TRACKING

**Ticket**: [ATT-7522](https://rainerblind.atlassian.net/browse/ATT-7522) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7522`  
**Requirement Mapping**: `REQ-TRK-059` (`REQ-TRK-059.11`) | **Test Spec ID**: `TST-TRK-059` (`TST-TRK-059.11`)  
**Priority**: Medium | **Type**: Functional | **Verification Method**: Unit  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (`REQ-TRK-059.11`)

### 1.1 Functional Scope & Technical Invariants
* **Preview & Placeholder Sparkline Fallback (`REQ-TRK-059.11`, ATT-7314, refined by ATT-7522)**:
  - In `SensorFieldView.kt`, when `fieldState.showGraph == true` and `fieldState.sparklineSamples.isEmpty()`:
    - If `screenMode != ScreenMode.TRACKING` (`CONFIGURATION` or `PREVIEW`), the composable SHALL provide `DEFAULT_PREVIEW_SPARKLINE_SAMPLES`, effective horizon span, and `isPlaceholder = false` to `SensorSparklineView`.
    - If `screenMode == ScreenMode.TRACKING`, the composable SHALL provide `DEFAULT_PREVIEW_SPARKLINE_SAMPLES`, effective horizon span, and `isPlaceholder = true` to `SensorSparklineView`.
    - As soon as genuine telemetry arrives (`fieldState.sparklineSamples.isNotEmpty()`), `SensorFieldView` SHALL supply live recorded samples, live span, and `isPlaceholder = false`.
  - In `SensorSparklineView.kt`, the composable SHALL support `isPlaceholder: Boolean = false`. When `isPlaceholder == true`:
    - Stroke polyline SHALL render with dimmed alpha (`alpha = 0.35f`).
    - Trend polyline SHALL draw using dashed styling (`PathEffect.dashPathEffect`).
    - Terminal circle dot at latest sample SHALL be suppressed.
    - X-axis label row below canvas SHALL render with dimmed alpha (`alpha = 0.5f`).
  - In `EditSensorFieldDialog.kt`, preview container SHALL preserve existing preview rendering with `isPlaceholder = false`.

### 1.2 Given-When-Then Acceptance Criteria
* **AC-1 (Live Tracking Inactive Sensor Placeholder)**: *Given* a sensor field with `showGraph = true` whose sensor is inactive (0 live samples) in `ScreenMode.TRACKING`, *When* rendered, *Then* `SensorFieldView` renders a dimmed, dashed placeholder curve with suppressed terminal dot and dimmed X-axis labels indicating the configured time horizon.
* **AC-2 (Seamless Transition to Live Telemetry)**: *Given* an inactive sensor tile displaying the placeholder curve during `ScreenMode.TRACKING`, *When* genuine telemetry samples arrive (`sparklineSamples.isNotEmpty()`), *Then* `SensorFieldView` immediately switches to authentic live samples with solid stroke, terminal dot, and `isPlaceholder = false`.
* **AC-3 (Configuration Mode Preview Preservation)**: *Given* a sensor field with `showGraph = true` in `ScreenMode.CONFIGURATION` or `ScreenMode.PREVIEW`, *When* `sparklineSamples` is empty, *Then* `SensorFieldView` renders the standard preview curve with `isPlaceholder = false`.
* **AC-4 (Edit Dialog Preview Preservation)**: *Given* an athlete in `EditSensorFieldDialog`, *When* graph is enabled, *Then* the dialog renders the preview sparkline curve with `isPlaceholder = false`.

### 1.3 System Invariants
Zero Compose snapshot state mutations inside composition (`Rule 66`); distinct visual presentation (dimmed, dashed, dot-less) preventing authentic telemetry spoofing; 100% 9-language localization parity in `strings_cockpit.xml`; bounded buffer memory ($\le 300$ samples / $\le 1\text{ KB}$ per tile).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-059` (`REQ-TRK-059.11`), refined by ATT-7522.
* **Historical Origin & Commit Trace**: `ATT-7314` introduced `DEFAULT_PREVIEW_SPARKLINE_SAMPLES` and configuration-mode fallback, but restricted tracking mode to blank voids.
* **Root Reason for Existing Formulation**: Developers assumed blank space was preferable to avoid synthetic confusion during tracking. The PO clarified that athletes perceive an empty void as broken and requested a dimmed, dashed placeholder curve.
* **Preservation of Core Invariants**: Authentic telemetry immediately supersedes placeholder; placeholder is visibly distinct (dashed, dimmed, dot-less); zero state mutations during composition.

---

## 2. Test Specification & Traceability Matrix (`TST-TRK-059.11`)

### 2.1 Concrete Verification Procedures
* **`TST-TRK-059.11a` (SensorSparklineView Placeholder Styling Unit Test)**: In `SensorSparklineViewTest.kt`, verify `resolveSparklineStrokeStyle`, stroke alpha, and terminal dot gating when `isPlaceholder = true` vs `false`.
* **`TST-TRK-059.11b` (SensorFieldView Fallback Resolution Unit Test)**: In `SensorFieldViewLayoutTest.kt` / `SensorSparklineViewTest.kt`, verify sample, span, and placeholder state resolution across `ScreenMode.TRACKING`, `ScreenMode.CONFIGURATION`, and active live samples.
* **`TST-TRK-059.11c` (Live Telemetry Transition Contract Test)**: Verify that when live samples are non-empty, `isPlaceholder` evaluates to `false` and live samples are passed directly.
* **`TST-TRK-059.11d` (Configuration Mode & Dialog Regression Test)**: In `EditSensorFieldDialogSparklineTest.kt`, assert dialog preview remains unaffected.

### 2.2 Traceability Matrix

| Req ID | Test Case | Target Component | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-059.11` | `TST-TRK-059.11a` | `SensorSparklineViewTest` | Placeholder dashed stroke, dimmed alpha, dot suppression | Specified |
| `REQ-TRK-059.11` | `TST-TRK-059.11b` | `SensorFieldViewLayoutTest` / `SensorSparklineViewTest` | Tracking mode placeholder vs Config mode preview resolution | Specified |
| `REQ-TRK-059.11` | `TST-TRK-059.11c` | `SensorSparklineViewTest` | Live telemetry supersedes placeholder with `isPlaceholder = false` | Specified |
| `REQ-TRK-059.11` | `TST-TRK-059.11d` | `EditSensorFieldDialogSparklineTest` | EditSensorFieldDialog preview regression | Specified |
| `REQ-PRO-001` | `TST-TRK-059.5` | Targeted Cockpit Suite | Clean-room compilation and test execution | Specified |
