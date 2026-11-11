# Stage 1 Analysis: ATT-6695 - SensorSparklineView & LiveSparklineBuffer: Add X-axis time labels (-T, -T/2, Now)

**Ticket**: [ATT-6695](https://rainerblind.atlassian.net/browse/ATT-6695)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6695`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
Live sparkline trend graphs in the tracking cockpit (`SensorSparklineView`, `ATT-1294`) display a continuous polyline and gradient fill across the entire tile width without any X-axis time labels. Because tiles can be configured with discrete horizons (1m, 2m, 5m, 10m, 20m, 1h, Full Workout) and buffers dynamically stretch across 100% width while filling up during initial workout minutes, athletes cannot perceive what temporal window the trend currently represents. Adding compact Left (`-T`), Middle (`-T/2`), and Right (`Jetzt` / `Now`) labels below the sparkline provides glanceable temporal scale across both warming buffers and decimated multi-hour workouts.

---

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
* **Gap Analysis**:
  1. *Buffer Temporal Ignorance*: `LiveSparklineBuffer` maintains sample values in `ArrayDeque` or `ArrayList`, but does not track cumulative elapsed sample time ($t_{\text{elapsed}}$). When decimation halves samples in Full Workout mode, sample count no longer corresponds to elapsed seconds.
  2. *State Model Gap*: `SensorFieldState` carries `sparklineSamples: List<Float>` but lacks `sparklineSpanSeconds: Int`, preventing UI components from determining the current time span.
  3. *Missing X-Axis UI*: `SensorSparklineView` contains only a raw `Canvas`. It lacks an X-axis label row and relative time offset formatters.
* **Independent Peer Review & Verification**:
  - *Inspection of `current_screen.png`*: Live cockpit screenshot confirms full-width tiles (Row 3 `Geschw.` and Row 4 `HF`) have ample vertical space for a compact single-line X-axis label row below the polyline canvas.
  - *Decimation Resilience*: Decoupling `totalElapsedSeconds` (incremented on each 1 Hz `addSample()` and cleared in `clear()`) from buffer array size ensures `currentSpanSeconds` preserves authentic elapsed time even after multi-hour pairwise decimation.
  - *Sub-hour vs. Multi-hour Formatting*: Offsets format as `-M:SS` for $T < 3600\text{ s}$ and `-H:MM:SS` for $T \ge 3600\text{ s}$. Right label is localized via `sparkline_axis_now` across all 9 locales.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail (REQ-PRO-029)
* **In-Scope Goals**:
  - Track `totalElapsedSeconds` and expose `currentSpanSeconds` in `LiveSparklineBuffer.kt`.
  - Expose `getSparklineSpanSeconds` in `BANALServiceRepository.kt`.
  - Add `sparklineSpanSeconds: Int = 0` to `SensorFieldState.kt` and populate in `TrackingViewModel.kt`.
  - Implement pure `formatSparklineTimeOffset(seconds: Int): String` and compact X-axis label row in `SensorSparklineView.kt`.
  - Add `sparkline_axis_now` across all 9 `strings_cockpit.xml` files with 100% parity.
  - Unit tests for span math, offset formatting, and `TranslationParityTest`.
* **Out-of-Scope Non-Goals**: Modifying decimation algorithms, changing DB schema, or adding Y-axis metric value scales.
* **Complexity Estimation**: 2 SP (Standard feature across buffer, state model, Compose view, and 9 locales).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Extends `REQ-TRK-059` (*Live Sparkline Graph Cockpit Tiles*) with `REQ-TRK-059.9` (*Glanceable X-Axis Time Labels & Temporal Span Tracking*).
* **Historical Origin & Commit Trace**: `ATT-1294` introduced `LiveSparklineBuffer` and `SensorSparklineView`; `ATT-6710` added singleton repository persistence.
* **Root Reason for Existing Formulation**: Initial sparkline implementation prioritized lightweight canvas rendering without label text overhead.
* **Preservation of Core Invariants**: Bounded buffer memory ($\le 300$ samples / $\le 1\text{ KB}$ per tile), zero Compose snapshot state writes in composition (Rule 66), and canvas polyline visual rendering remain 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution
* **Buffer Layer**: `LiveSparklineBuffer` tracks `totalElapsedSeconds: Int` and exposes `currentSpanSeconds: Int = if (horizonSeconds > 0) minOf(maxOf(0, totalElapsedSeconds - 1), horizonSeconds) else maxOf(0, totalElapsedSeconds - 1)`.
* **Repository & ViewModel**: `BANALServiceRepository` exposes `getSparklineSpanSeconds(sensorFieldId)`. `TrackingViewModel` assigns `sparklineSpanSeconds` on `SensorFieldState`.
* **UI Presentation**: In `SensorSparklineView`, wrap `Canvas` in a `Column` followed by `Row(fillMaxWidth, SpaceBetween)` displaying `-T`, `-T/2`, and localized `R.string.sparkline_axis_now` with `onSurfaceVariant` and `10.sp` typography.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: 100% 9-language translation parity; zero recomposition churn; zero memory expansion; backward compatibility with existing previews and call sites.
* **Risk Rating**: **LOW** (Presentation and buffer accounting refinement; zero threading or database risks).
