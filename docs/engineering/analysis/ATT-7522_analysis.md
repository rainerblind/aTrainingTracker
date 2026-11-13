# Stage 1 Analysis: ATT-7522 - Cockpit Inactive Sensor Placeholder Curve in ScreenMode.TRACKING

**Ticket**: [ATT-7522](https://rainerblind.atlassian.net/browse/ATT-7522)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7522` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
During the Sprint Review of ATT-7314, the Product Owner clarified the intended behavior for inactive sensors when sparkline is enabled (`showGraph = true`):
* While the preview curve added in `EditSensorFieldDialog` and `ScreenMode.CONFIGURATION` is appreciated and must be kept, the athlete's primary request was for the Cockpit display during live tracking / standby (`ScreenMode.TRACKING`).
* When a sensor tile has `showGraph = true` but the sensor is inactive, disconnected, or awaiting initial telemetry, `SensorFieldView` passes `emptyList()`, causing `SensorSparklineView` to return immediately and leaving an empty blank void. The athlete requested a subtle, dimmed, or dashed placeholder curve to visualize the graph area and time horizon while clearly distinguishing it from genuine recorded data.

---

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
* **Root Cause Analysis (RCA)**:
  * In `SensorFieldView.kt:468-479`, fallback samples and span are gated behind `screenMode != ScreenMode.TRACKING`. When in `ScreenMode.TRACKING` with `fieldState.sparklineSamples.isEmpty()`, `effectiveSamples` resolves to `emptyList()` and `effectiveSpanSeconds` resolves to `0`.
  * In `SensorSparklineView.kt:116`, `if (samples.isEmpty()) return` exits early without drawing, causing complete blankness in both Value+Graph and Graph-Only modes.
  * `SensorSparklineView` lacks a parameter or styling mechanism to render a distinct placeholder state.
* **Independent Peer Review & Verification**:
  * Inspected `SensorFieldView.kt:458-499`: confirmed `screenMode != ScreenMode.TRACKING` leaves tracking mode blank when samples are empty.
  * Inspected `SensorSparklineView.kt:108-245`: confirmed `samples.isEmpty()` exits early. Adding `isPlaceholder: Boolean = false` allows rendering dimmed (`alpha = 0.35f`), dashed (`dashPathEffect`), dot-less placeholder polylines with dimmed X-axis labels (`alpha = 0.5f`).
  * Verified that when genuine telemetry arrives (`fieldState.sparklineSamples.isNotEmpty()`), `SensorFieldView` immediately switches to authentic live samples with `isPlaceholder = false`, guaranteeing seamless transition.
  * Verified `ScreenMode.CONFIGURATION` / `PREVIEW` and `EditSensorFieldDialog` remain fully intact.

---

## 3. User Scope Grounding & Complexity Split Guardrail (ATT-1250 / REQ-PRO-029)
* **In-Scope Goals**:
  1. In `SensorSparklineView.kt`, add `isPlaceholder: Boolean = false` rendering subtle dashed styling (`PathEffect.dashPathEffect`), dimmed stroke color (`alpha = 0.35f`), suppressed terminal dot, and dimmed X-axis label row (`alpha = 0.5f`).
  2. In `SensorFieldView.kt`, when `fieldState.showGraph == true` and `fieldState.sparklineSamples.isEmpty()`, supply `DEFAULT_PREVIEW_SPARKLINE_SAMPLES`, effective horizon span, and `isPlaceholder = (screenMode == ScreenMode.TRACKING)`.
  3. Ensure seamless transition: when `fieldState.sparklineSamples.isNotEmpty()`, render authentic telemetry with `isPlaceholder = false`.
  4. Unit and contract test coverage in `SensorSparklineViewTest.kt` and `SensorFieldViewLayoutTest.kt`.
  5. Living documentation synchronization (`REQ-TRK-059.11`, `TST-TRK-059.11`).
* **Out-of-Scope Non-Goals**: No changes to `LiveSparklineBuffer.kt`, `BANALServiceRepository.kt`, SQLite schemas, or telemetry ingestion.
* **Complexity Rating**: 1 SP (Trivial / Standard UI styling & contract test). Splitting not required.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Refines `REQ-TRK-059.11` (*Preview Sparkline Fallback for Inactive Sensors*).
* **Historical Origin & Commit Trace**: `ATT-1294` introduced `SensorSparklineView`; `ATT-7314` introduced `DEFAULT_PREVIEW_SPARKLINE_SAMPLES` and configuration-mode preview fallback, intentionally suppressing preview in `ScreenMode.TRACKING` to prevent synthetic data confusion.
* **Root Reason for Existing Formulation**: Developers assumed zero data was preferable to synthetic data during tracking. The PO clarified that blank voids degrade cockpit ergonomics and requested a distinct placeholder curve.
* **Preservation of Core Invariants**: Zero authentic telemetry spoofing (placeholder is visually distinct: dimmed, dashed, dot-less); authentic samples immediately supersede placeholder; 100% 9-language localization parity preserved; zero Compose snapshot state writes in composition (Rule 66).

---

## 5. Side-Effect & Cross-Caller Impact Analysis (Rule 68)
* `SensorSparklineView`: `isPlaceholder: Boolean = false` maintains 100% binary/source compatibility for `EditSensorFieldDialog.kt` and previews.
* `SensorFieldView`: Layout height stability is guaranteed before and after sensor connects (`resolveSparklineHeight`), preventing UI jumps.
* Zero runtime performance impact: purely hardware-accelerated Canvas operations.

---

## 6. Change Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-TRK-059.11` in `docs/requirements/req_tracking_cockpit.md`.
* **Affected Architecture Elements**: `SensorFieldView.kt`, `SensorSparklineView.kt`, `SensorSparklineViewTest.kt`, `SensorFieldViewLayoutTest.kt`.
* **Verification Impact & Test Surface**: `SensorSparklineViewTest`, `SensorFieldViewLayoutTest`, `TranslationParityTest`, `ThemeColorCentralizationAuditTest`, `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Low risk. Confined entirely to UI rendering fallback; no data ingestion or persistence touched.

---

## 7. Living Documentation Plan
* Update `docs/requirements/req_tracking_cockpit.md` (`REQ-TRK-059.11`) and `docs/tests/tst_tracking_cockpit.md` (`TST-TRK-059.11`).
