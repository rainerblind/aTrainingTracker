# Stage 2: Requirement & Test Specification - ATT-6695: SensorSparklineView & LiveSparklineBuffer: Add X-axis time labels (-T, -T/2, Now)

**Ticket**: [ATT-6695](https://rainerblind.atlassian.net/browse/ATT-6695) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6695`  
**Requirement Mapping**: `REQ-TRK-059` (`REQ-TRK-059.9`) | **Test Spec ID**: `TST-TRK-059` (`TST-TRK-059.9`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (`REQ-TRK-059.9`)

### 1.1 Functional Scope & Technical Invariants
* **Buffer Temporal Span Tracking (`LiveSparklineBuffer.kt`)**: The buffer SHALL maintain private `totalElapsedSeconds: Int = 0` (incremented on each 1-Hz `addSample()` and reset to `0` in `clear()`). It SHALL expose `val currentSpanSeconds: Int = if (horizonSeconds > 0) minOf(maxOf(0, totalElapsedSeconds - 1), horizonSeconds) else maxOf(0, totalElapsedSeconds - 1)`, preserving authentic elapsed duration across multi-hour pairwise decimation.
* **State Model & ViewModel Exposure (`SensorFieldState.kt`, `BANALServiceRepository.kt`, `TrackingViewModel.kt`)**: `SensorFieldState` SHALL include `val sparklineSpanSeconds: Int = 0`. `BANALServiceRepository` SHALL expose `fun getSparklineSpanSeconds(sensorFieldId: Long): Int`. `TrackingViewModel.applySensorData()` SHALL assign `sparklineSpanSeconds` for graph-enabled fields.
* **Glanceable X-Axis Labels & Formatting (`SensorSparklineView.kt`)**: The composable SHALL wrap the `Canvas` in a `Column` followed by `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween)` rendering Left (`-T`), Middle (`-T/2`), and Right (`sparkline_axis_now`: `Jetzt` / `Now`) labels at compact `10.sp` / `labelSmall` in `onSurfaceVariant`.
* **Relative Time Offset Formatting**: Pure helper `formatSparklineTimeOffset(seconds: Int): String` SHALL format offsets as `-M:SS` for $T < 3600\text{ s}$ and `-H:MM:SS` for $T \ge 3600\text{ s}$.
* **Localization Parity**: String resource `sparkline_axis_now` SHALL maintain 100% 9-language translation parity (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

### 1.2 Given-When-Then Acceptance Criteria
* **AC-1 (Fixed Horizon Span & Capping)**: *Given* a Cockpit tile configured with `showGraph == true` and 5m (300s) horizon, *When* 80 seconds elapse, *Then* X-axis labels display `-1:20` (Left), `-0:40` (Middle), and localized `Jetzt`/`Now` (Right), capping at `-5:00` and `-2:30` once $\ge 300\text{ s}$ elapse.
* **AC-2 (Full Workout Decimation Span Preservation)**: *Given* a Cockpit tile configured with Full Workout (0s horizon) after 835 seconds of tracking, *When* rendered in `SensorSparklineView`, *Then* `LiveSparklineBuffer.currentSpanSeconds` preserves 834s despite decimation, rendering `-13:54` Left, `-6:57` Middle, and `Jetzt`/`Now` Right.
* **AC-3 (Localization & Visual Styling)**: *Given* any locale among the 9 supported languages, *When* sparkline X-axis renders, *Then* the Right label matches `sparkline_axis_now` in `onSurfaceVariant` at compact `10.sp` typography without layout clipping or jitter.

### 1.3 System Invariants
Zero Compose snapshot state writes in composition (`Rule 66`); bounded memory ($\le 300$ samples / $\le 1\text{ KB}$ per tile); 100% 9-language translation parity; backward compatibility of `SensorFieldState` default parameters.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-059`, extending `REQ-TRK-046` (`ShowValue`/`ShowBar`), `REQ-UI-436` (Bar-only mode), and `REQ-UI-159` (Root BackHandler). Refined by `ATT-6710` (singleton repository persistence) and `ATT-6695` (X-axis time labels `-T`, `-T/2`, `Now` and temporal span tracking in `LiveSparklineBuffer.kt` and `SensorSparklineView.kt`).
2. *Historical Origin & Commit Trace*: `ATT-4841` (DB v16 `SHOW_VALUE`/`SHOW_BAR`), `ATT-4927` (DB v18), `ATT-1294` (`LiveSparklineBuffer` in `TrackingViewModel`), `ATT-6710` (singleton persistence and task preservation), `ATT-6695` (X-axis time labels).
3. *Root Reason for Existing Formulation*: Prior sparkline implementations prioritized minimal canvas drawing without text layout overhead, leaving athletes unable to determine what temporal duration a filling buffer or decimated Full Workout sparkline represented.
4. *Preservation of Core Invariants*: Bounded memory ($\le 300$ samples / $\le 1\text{ KB}$ per tile), 1 Hz decimation math, `SensorSparklineView` canvas rendering, zero snapshot state mutations in composition (`Rule 66`), drawer navigation hierarchy, and 100% 9-language translation parity remain strictly preserved.

---

## 2. Test Specification & Traceability Matrix (`TST-TRK-059.9`)

### 2.1 Concrete Verification Procedures
* **`TST-TRK-059.9a` (Buffer Span & Decimation Persistence)**: In `LiveSparklineBufferSpanTest.kt`, assert `totalElapsedSeconds` increments on `addSample()`, resets on `clear()`, and `currentSpanSeconds` caps accurately on fixed horizons while surviving pairwise decimation in Full Workout mode.
* **`TST-TRK-059.9b` (Offset Formatting Unit Test)**: In `SensorSparklineViewTest.kt`, assert `formatSparklineTimeOffset` outputs `-M:SS` for $T < 3600\text{ s}$ (e.g. 80s -> `-1:20`, 40s -> `-0:40`, 300s -> `-5:00`) and `-H:MM:SS` for $T \ge 3600\text{ s}$ (e.g. 3600s -> `-1:00:00`, 5070s -> `-1:24:30`).
* **`TST-TRK-059.9c` (9-Locale Parity Test)**: Run `TranslationParityTest` verifying `sparkline_axis_now` is present in all 9 XML files.

### 2.2 Traceability Matrix

| Req ID | Test Case | Target Component | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-059.9` | `TST-TRK-059.9a` | `LiveSparklineBufferSpanTest` | Elapsed seconds accumulation, horizon capping, and decimation invariance | Specified |
| `REQ-TRK-059.9` | `TST-TRK-059.9b` | `SensorSparklineViewTest` | Sub-hour (`-M:SS`) and multi-hour (`-H:MM:SS`) relative time string formatting | Specified |
| `REQ-UI-106` | `TST-TRK-059.9c` | `TranslationParityTest` | 100% 9-locale parity for `sparkline_axis_now` in `strings_cockpit.xml` | Specified |
| `REQ-PRO-001` | `TST-TRK-059.5` | Targeted Regression Suite | `--tests "com.atrainingtracker.trainingtracker.ui.tracking.*"` | Specified |
