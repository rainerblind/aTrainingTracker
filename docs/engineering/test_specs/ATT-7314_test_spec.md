# Stage 2: Requirement & Test Specification - ATT-7314: Render preview sparkline and preserve buffer history when graph is enabled for inactive sensors

**Ticket**: [ATT-7314](https://rainerblind.atlassian.net/browse/ATT-7314) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.4` | **Branch**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-TRK-059` (`REQ-TRK-059.11`, `REQ-TRK-059.12`) | **Test Spec ID**: `TST-TRK-059` (`TST-TRK-059.11`, `TST-TRK-059.12`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (`REQ-TRK-059.11`, `REQ-TRK-059.12`)

### 1.1 Functional Scope & Technical Invariants
* **Configuration Mode Preview Fallback (`REQ-TRK-059.11`, ATT-7314)**:
  - In `SensorFieldView.kt`, when `fieldState.showGraph == true` and `fieldState.sparklineSamples.isEmpty()`, if `screenMode != ScreenMode.TRACKING` (i.e. in `ScreenMode.CONFIGURATION` or `ScreenMode.PREVIEW`), the composable SHALL provide `DEFAULT_PREVIEW_SPARKLINE_SAMPLES` and effective time horizon span `if (fieldState.graphHorizonSeconds > 0) fieldState.graphHorizonSeconds else 300` to `SensorSparklineView`.
  - In `ScreenMode.TRACKING`, when `sparklineSamples` is empty, `SensorSparklineView` SHALL remain empty/blank, guaranteeing zero fake or synthetic telemetry data during live recording.
  - In `EditSensorFieldDialog.kt`, when `uiState.isGraphSupported && uiState.showGraph == true`, a preview container SHALL render `SensorSparklineView` with `DEFAULT_PREVIEW_SPARKLINE_SAMPLES` and the active horizon seconds directly below the stepped time horizon slider.
* **Buffer History Preservation Across Horizon Modifications (`REQ-TRK-059.12`, ATT-7314)**:
  - In `LiveSparklineBuffer.kt`, the buffer SHALL provide thread-safe `transferFrom(previous: LiveSparklineBuffer)` to migrate accumulated samples and elapsed duration across capacity modifications.
  - For fixed horizons (`horizonSeconds > 0`), the new buffer SHALL preserve the trailing `min(prevSamples.size, horizonSeconds)` samples and clamp `totalElapsedSeconds` to `minOf(previous.totalElapsedSeconds, horizonSeconds)`.
  - For full workout mode (`horizonSeconds <= 0`), the new buffer SHALL preserve all previous samples into `fullWorkoutBuffer` and apply pairwise decimation if sample count exceeds `MAX_FULL_WORKOUT_SAMPLES`.
  - In `BANALServiceRepository.kt:registerSparklineBuffer()`, when re-registering an existing tile with an updated `horizonSeconds` but matching `configHash`, the repository SHALL invoke `buffer.transferFrom(existing.buffer)` to preserve historical samples rather than dropping to 0 samples.

### 1.2 Given-When-Then Acceptance Criteria
* **AC-1 (Configuration Mode Inactive Sensor Preview)**: *Given* a sensor field configured with `showGraph = true` whose sensor is inactive (0 live samples), *When* viewed in `ScreenMode.CONFIGURATION` (Edit Tracking Tabs) or `ScreenMode.PREVIEW`, *Then* `SensorFieldView` renders a representative preview trend curve and active X-axis horizon bounds (`-T`, `-T/2`, `Now`) without blank space.
* **AC-2 (Live Tracking Inactive Sensor Authenticity Guard)**: *Given* a sensor field configured with `showGraph = true` whose sensor is disconnected during workout recording (`ScreenMode.TRACKING`), *When* rendered, *Then* `SensorFieldView` renders no sparkline path, guaranteeing zero synthetic data pollution during live sessions.
* **AC-3 (Edit Dialog Instant Sparkline Preview)**: *Given* an athlete in `EditSensorFieldDialog`, *When* toggling `Show Graph` on or adjusting the time horizon slider, *Then* a preview card immediately displays the sparkline curve with the chosen temporal bounds.
* **AC-4 (Buffer History Retention on Horizon Expansion)**: *Given* an active sensor buffer holding 60 samples (1m horizon), *When* the athlete increases the horizon to 5m (300s) for the same sensor configuration, *Then* all 60 existing samples are retained and subsequent samples append to the existing series.
* **AC-5 (Buffer History Retention on Horizon Contraction)**: *Given* an active sensor buffer holding 300 samples (5m horizon), *When* the athlete decreases the horizon to 1m (60s), *Then* the most recent 60 trailing samples are preserved in the new buffer.

### 1.3 System Invariants
Zero Compose snapshot state writes during composition (`Rule 66`); strictly zero fake/synthetic data injection during `ScreenMode.TRACKING`; bounded buffer memory ($\le 300$ samples / $\le 1\text{ KB}$ per tile); 100% 9-language translation parity.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-059`, extending `REQ-TRK-046` (`ShowValue`/`ShowBar` display elements), `REQ-UI-436` (Bar-only mode), and `REQ-UI-159` (Root BackHandler navigation). Refined by `ATT-6710` (singleton repository persistence in `BANALServiceRepository.kt`), `ATT-6695` (X-axis time labels `-T`, `-T/2`, `Now`), `ATT-6690` (stepped `Slider` in `EditSensorFieldDialog.kt`), `ATT-6689` (Graph-only tile intrinsic height constraint and workout reset buffer clearance), and `ATT-7314` (Preview sparkline curve in configuration mode and buffer history preservation across horizon changes).
2. *Historical Origin & Commit Trace*: `ATT-4841` introduced `SHOW_VALUE` and `SHOW_BAR` in DB v16; `ATT-4927` added sport-dependent bar gating in DB v18; `ATT-1294` introduced `LiveSparklineBuffer` and `FilterChip` in `EditSensorFieldDialog`; `ATT-6710` added singleton repository persistence and task preservation; `ATT-6695` added X-axis time labels; `ATT-6690` introduced the stepped slider; `ATT-6689` fixed Graph-only `0px` collapse in `IntrinsicSize.Min` rows and session reset bleed; `ATT-7314` added configuration-mode preview fallback and buffer history transfer.
3. *Root Reason for Existing Formulation*: Fresh `LiveSparklineBuffer` instantiation was a simplifying initial implementation under the assumption that config changes always cleared buffers. In `ScreenMode.CONFIGURATION`, mock samples were omitted because the system prioritized real telemetry, causing blank tiles when configuring inactive sensors.
4. *Preservation of Core Invariants*: Exactly the same 7 discrete duration horizons (`60s`, `120s`, `300s`, `600s`, `1200s`, `3600s`, `0s`) remain selectable; persistence to `GraphHorizonS` in SQLite remains 100% identical; bounded buffer memory ($\le 300$ samples / $\le 1\text{ KB}$ per tile) and 100% 9-language translation parity remain strictly preserved; live tracking mode continues to display strictly authentic telemetry without synthetic injection.

---

## 2. Test Specification & Traceability Matrix (`TST-TRK-059.11`, `TST-TRK-059.12`)

### 2.1 Concrete Verification Procedures
* **`TST-TRK-059.11a` (SensorFieldView Configuration Mode Preview Fallback)**: In `SensorFieldViewLayoutTest.kt`, assert that when `showGraph = true` and `sparklineSamples = emptyList()`, rendering in `ScreenMode.CONFIGURATION` and `ScreenMode.PREVIEW` passes `DEFAULT_PREVIEW_SPARKLINE_SAMPLES` to the sparkline composable.
* **`TST-TRK-059.11b` (SensorFieldView Live Tracking Empty Sparkline Authenticity)**: In `SensorFieldViewLayoutTest.kt`, assert that when `showGraph = true` and `sparklineSamples = emptyList()`, rendering in `ScreenMode.TRACKING` leaves `SensorSparklineView` empty with zero samples drawn.
* **`TST-TRK-059.11c` (EditSensorFieldDialog Preview Card Presentation)**: In `EditSensorFieldDialogSparklineTest.kt`, verify that when `isGraphSupported` and `showGraph` are true, the preview sparkline container is displayed with the selected duration horizon.
* **`TST-TRK-059.12a` (LiveSparklineBuffer State Transfer Unit Test)**: In `LiveSparklineBufferSpanTest.kt`, verify `transferFrom` across:
  - Expanding horizon (60s -> 300s): all 60 samples and elapsed duration preserved.
  - Contracting horizon (300s -> 60s): last 60 trailing samples preserved, span clamped to 60s.
  - Fixed to Full Workout: all samples retained in `fullWorkoutBuffer`.
  - Full Workout to Fixed: trailing samples retained up to fixed capacity.
* **`TST-TRK-059.12b` (BANALServiceRepository Horizon Change Retention)**: In `BANALServiceRepositorySparklineTest.kt`, verify `registerSparklineBuffer` with a changed `horizonSeconds` retains accumulated samples for matching `configHash`.

### 2.2 Traceability Matrix

| Req ID | Test Case | Target Component | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-059.11` | `TST-TRK-059.11a` | `SensorFieldViewLayoutTest` | Configuration mode preview fallback for inactive sensors | Specified |
| `REQ-TRK-059.11` | `TST-TRK-059.11b` | `SensorFieldViewLayoutTest` | Live tracking authenticity (zero synthetic data in TRACKING) | Specified |
| `REQ-TRK-059.11` | `TST-TRK-059.11c` | `EditSensorFieldDialogSparklineTest` | Preview card in EditSensorFieldDialog | Specified |
| `REQ-TRK-059.12` | `TST-TRK-059.12a` | `LiveSparklineBufferSpanTest` | `transferFrom` expansion, contraction, and mode shifts | Specified |
| `REQ-TRK-059.12` | `TST-TRK-059.12b` | `BANALServiceRepositorySparklineTest` | Repository buffer sample retention across horizon changes | Specified |
| `REQ-PRO-001` | `TST-TRK-059.5` | Targeted Cockpit Suite | Clean-room verification across tracking & repository suites | Specified |
