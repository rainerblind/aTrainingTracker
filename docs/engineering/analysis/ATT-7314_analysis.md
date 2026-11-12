# Stage 1 Analysis: ATT-7314 - Render preview sparkline and preserve buffer history when graph is enabled for inactive sensors

**Ticket**: [ATT-7314](https://rainerblind.atlassian.net/browse/ATT-7314)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `sprint/2026-46.4`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
During Sprint 2026-46.3 Joint Review of ATT-6690, the Product Owner identified two usability deficiencies with live sparkline tile configuration:
1. When an athlete enables "Show Graph" or changes the time horizon in `EditSensorFieldDialog` for an inactive or disconnected sensor (e.g. HR chest strap or Power meter before pedaling, or workout not yet recording), the buffer contains 0 samples. Consequently, `SensorSparklineView` renders nothing, leaving the tile completely blank in Edit Tracking Tabs (`ScreenMode.CONFIGURATION` / `ScreenMode.PREVIEW`). The athlete receives zero visual confirmation of how the graph will look or fit within the tile layout.
2. In `BANALServiceRepository.registerSparklineBuffer()`, changing the time horizon on a tile whose sensor is streaming or has buffered history completely instantiates a new empty buffer, dropping all accumulated historical samples and resetting the temporal span to 0 instead of preserving and sliding or resampling existing data.

---

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
* **Forensic Root Cause Analysis (RCA)**:
  1. *Empty Sample Blank Render in Configuration Mode*:
     - `SensorSparklineView.kt:103-105` explicitly specifies `if (samples.isEmpty()) return`, aborting canvas draw and label row composition.
     - `SensorFieldView.kt:466-485` supplies `samples = fieldState.sparklineSamples` and `spanSeconds = fieldState.sparklineSpanSeconds`. In `ScreenMode.CONFIGURATION` (and `PREVIEW`), when a sensor is disconnected or workout inactive, `sparklineSamples` is `emptyList()`. In graph-only mode (`showValue == false && showGraph == true`), this causes the card content area to appear completely empty without giving layout context.
  2. *Lack of Sparkline Preview inside EditSensorFieldDialog*:
     - `EditSensorFieldDialog.kt` provides a stepped slider for the 7 duration horizons (ATT-6690) but no visual preview card illustrating the resulting graph curve and X-axis temporal notation (`-T`, `-T/2`, `Now`).
  3. *Unconditional Buffer Clearance on Horizon Change*:
     - `BANALServiceRepository.kt:616-631`:
       ```kotlin
       val configHash = Objects.hash(sensorType, filterType, filterConstant, sourceDeviceName)
       val existing = sparklineBuffers[sensorFieldId]
       if (existing != null && existing.horizonSeconds == horizonSeconds && existing.configHash == configHash) {
           return existing.buffer
       }
       val buffer = LiveSparklineBuffer(horizonSeconds)
       ...
       ```
       When `horizonSeconds` changes for an identical sensor configuration (`existing.configHash == configHash`), the existing buffer is replaced by a fresh, empty `LiveSparklineBuffer(horizonSeconds)`. All accumulated history is dropped.
  4. *Missing Buffer Transfer / Migration Logic*:
     - `LiveSparklineBuffer.kt` lacks a state transfer / copying method (`transferFrom`) to carry over existing samples and elapsed seconds to the new buffer capacity.

* **Independent Peer Review & Verification**:
  - *Call-Site Audit*:
    - `SensorFieldView.kt`: Lines 458–486 render `SensorSparklineView`. Gating synthetic preview data strictly behind `screenMode != ScreenMode.TRACKING && fieldState.sparklineSamples.isEmpty()` ensures that real telemetry during `ScreenMode.TRACKING` is never masked or polluted with mock curves.
    - `EditSensorFieldDialog.kt`: Lines 237–288 house the time-horizon slider. An embedded preview card below the slider provides immediate visual feedback.
    - `LiveSparklineBuffer.kt` & `BANALServiceRepository.kt`: Adding thread-safe `transferFrom(previous: LiveSparklineBuffer)` allows seamless preservation of samples across fixed horizons and full workout mode.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail (REQ-PRO-029)
* **In-Scope Goals**:
  - Define `DEFAULT_PREVIEW_SPARKLINE_SAMPLES` (24 representative telemetry points forming a realistic athletic effort trend).
  - In `SensorFieldView.kt`, render preview sparkline and effective horizon span when `fieldState.showGraph == true && fieldState.sparklineSamples.isEmpty()` in `screenMode != ScreenMode.TRACKING`.
  - In `EditSensorFieldDialog.kt`, render a sparkline preview container under the time horizon slider showing the selected duration and representative curve.
  - In `LiveSparklineBuffer.kt`, implement thread-safe `transferFrom(previous: LiveSparklineBuffer)` handling fixed-to-fixed, fixed-to-full, and full-to-fixed transitions without sample loss.
  - In `BANALServiceRepository.kt`, invoke `buffer.transferFrom(existing.buffer)` when `existing.configHash == configHash`.
  - Add comprehensive unit tests for preview fallback and buffer sample preservation.
* **Out-of-Scope Non-Goals**: Modifying SQLite schemas, altering live sensor ingestion in `BANALService`, or synthesizing live data in `ScreenMode.TRACKING`.
* **Complexity Estimation**: 2 SP (Standard ASPICE lifecycle, bounded UI fallback and buffer transfer logic).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-TRK-059` (`REQ-TRK-059.4`, `REQ-TRK-059.5`, `REQ-TRK-059.6`, `REQ-TRK-059.9`).
* **Historical Origin & Commit Trace**: `ATT-1294` introduced `LiveSparklineBuffer`; `ATT-6710` added singleton repository persistence; `ATT-6695` added X-axis labels; `ATT-6690` added stepped slider; `ATT-6689` resolved graph-only tile height collapse.
* **Root Reason for Existing Formulation**: Fresh buffer allocation was a simplifying initial implementation. In `ScreenMode.CONFIGURATION`, mock samples were omitted because the system prioritized real telemetry.
* **Preservation of Core Invariants**:
  - Live tracking (`ScreenMode.TRACKING`) SHALL strictly display authentic telemetry data or empty state when no data exists (zero fake data during active recording).
  - Bounded memory invariants ($\le 300$ samples / $\le 1\text{ KB}$ per tile) remain strictly enforced.
  - Zero Compose snapshot state writes in composition (Rule 66).
  - 100% 9-language translation parity maintained.

---

## 5. Architectural Strategy & High-Level Solution
* **Representative Preview Samples**: Export `DEFAULT_PREVIEW_SPARKLINE_SAMPLES: List<Float>` in `SensorSparklineView.kt`.
* **SensorFieldView Preview Fallback**:
  ```kotlin
  val effectiveSamples = if (fieldState.sparklineSamples.isNotEmpty()) {
      fieldState.sparklineSamples
  } else if (screenMode != ScreenMode.TRACKING) {
      DEFAULT_PREVIEW_SPARKLINE_SAMPLES
  } else {
      emptyList()
  }
  val effectiveSpan = if (fieldState.sparklineSamples.isNotEmpty()) {
      fieldState.sparklineSpanSeconds
  } else if (screenMode != ScreenMode.TRACKING) {
      if (fieldState.graphHorizonSeconds > 0) fieldState.graphHorizonSeconds else 300
  } else {
      0
  }
  ```
* **Edit Dialog Preview Card**: In `EditSensorFieldDialog.kt`, render a preview `Surface` displaying `SensorSparklineView` with `DEFAULT_PREVIEW_SPARKLINE_SAMPLES` and the active horizon seconds.
* **Buffer State Migration**: In `LiveSparklineBuffer.kt`:
  ```kotlin
  fun transferFrom(previous: LiveSparklineBuffer) {
      synchronized(previous.lock) {
          synchronized(this.lock) {
              val prevSamples = previous.getSamples()
              if (this.horizonSeconds > 0) {
                  val toKeep = if (prevSamples.size > this.horizonSeconds) {
                      prevSamples.subList(prevSamples.size - this.horizonSeconds, prevSamples.size)
                  } else {
                      prevSamples
                  }
                  this.fixedBuffer.clear()
                  this.fixedBuffer.addAll(toKeep)
                  this.totalElapsedSeconds = minOf(previous.totalElapsedSeconds, this.horizonSeconds)
              } else {
                  this.fullWorkoutBuffer.clear()
                  this.fullWorkoutBuffer.addAll(prevSamples)
                  this.totalElapsedSeconds = previous.totalElapsedSeconds
                  if (this.fullWorkoutBuffer.size > MAX_FULL_WORKOUT_SAMPLES) {
                      decimateFullWorkout()
                  }
              }
          }
      }
  }
  ```

---

## 6. Change Impact Analysis (SUP.10 / REQ-PRO-064)
* **Affected Requirements**: `REQ-TRK-059` (`REQ-TRK-059.4`, `REQ-TRK-059.5`, `REQ-TRK-059.6`, `REQ-TRK-059.9`).
* **Affected Architecture Elements**:
  - `LiveSparklineBuffer.kt`
  - `BANALServiceRepository.kt`
  - `SensorSparklineView.kt`
  - `SensorFieldView.kt`
  - `EditSensorFieldDialog.kt`
* **Verification Impact & Test Surface**:
  - `LiveSparklineBufferSpanTest.kt`: Verify sample retention and span calculation across horizon expansions and contractions.
  - `BANALServiceRepositorySparklineTest.kt`: Verify buffer history preservation upon time horizon modifications.
  - `SensorFieldViewLayoutTest.kt` & `SensorSparklineViewTest.kt`: Verify preview curve rendering in `ScreenMode.CONFIGURATION` and empty rendering in `ScreenMode.TRACKING`.
  - `EditSensorFieldDialogSparklineTest.kt`: Verify dialog preview presentation.
* **Risk Assessment & Invariants**: **LOW**. Invariants preserved; thread-safety maintained via nested locking; live tracking behavior remains strictly authentic.
