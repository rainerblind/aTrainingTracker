# Stage 3: Implementation Plan - ATT-6695: SensorSparklineView & LiveSparklineBuffer: Add X-axis time labels (-T, -T/2, Now)

**Ticket**: [ATT-6695](https://rainerblind.atlassian.net/browse/ATT-6695) | **Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) | **Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.2` | **Branch**: `feature/ATT-6695` | **Req/Test**: `REQ-TRK-059.9` / `TST-TRK-059.9`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Architecture Rationale
Live cockpit sparklines in `SensorSparklineView.kt` lack X-axis time labels. Athletes cannot discern the temporal scale while fixed horizon buffers fill up or during decimated multi-hour workouts. We introduce:
1. Exact elapsed span tracking in `LiveSparklineBuffer.kt` (`totalElapsedSeconds` and `currentSpanSeconds`).
2. Span exposure in `SensorFieldState.kt` and `BANALServiceRepository.kt`, updated via `TrackingViewModel.kt`.
3. Pure relative time offset formatting (`formatSparklineTimeOffset`) and a compact single-line `Row` in `SensorSparklineView.kt` displaying `-T`, `-T/2`, and localized `Jetzt`/`Now`.
4. 100% 9-language localization of `sparkline_axis_now` in `strings_cockpit.xml`.

---

## 2. System Invariants & Preserved Behavior
1. **Bounded Buffer Memory**: `LiveSparklineBuffer` memory bounds ($\le 300$ samples / $\le 1\text{ KB}$ per tile) and decimation math preserved.
2. **Recomposition Hygiene**: Rule 66 respected; zero snapshot state writes during composition; pure helper formatting.
3. **9-Locale Parity**: String resource `sparkline_axis_now` translated across all 9 supported locales with 100% parity.
4. **Backward Compatibility**: `SensorFieldState` constructor preserves default argument `sparklineSpanSeconds = 0`.

---

## 3. UI Consistency (Design Guidelines)
* **Reused Tokens**: `MaterialTheme.typography.labelSmall` (`10.sp`), `MaterialTheme.colorScheme.onSurfaceVariant`.
* **Layout**: Single-line `Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween)` positioned below the `Canvas` inside a `Column`.
* **Sizing**: `resolveSparklineHeight` updated slightly in Value+Graph mode (e.g. 26/34/44.dp) to provide clean vertical clearance for both canvas and label row without clipping.

---

## 4. Step-by-Step Implementation Sequence

### Step 1: Temporal Span Tracking in `LiveSparklineBuffer` & Repository (`REQ-TRK-059.9`)
* **Files**: `LiveSparklineBuffer.kt`, `BANALServiceRepository.kt`
* **Changes**:
  - In `LiveSparklineBuffer`: add private `var totalElapsedSeconds: Int = 0` (incremented on `addSample()`, cleared in `clear()`). Expose `val currentSpanSeconds: Int = if (horizonSeconds > 0) minOf(maxOf(0, totalElapsedSeconds - 1), horizonSeconds) else maxOf(0, totalElapsedSeconds - 1)`.
  - In `BANALServiceRepository`: expose `fun getSparklineSpanSeconds(sensorFieldId: Long): Int = sparklineBuffers[sensorFieldId]?.buffer?.currentSpanSeconds ?: 0`.

### Step 2: State Model & ViewModel Integration (`REQ-TRK-059.9`)
* **Files**: `SensorFieldState.kt`, `TrackingViewModel.kt`
* **Changes**:
  - In `SensorFieldState.kt`: add `val sparklineSpanSeconds: Int = 0`.
  - In `TrackingViewModel.kt`: populate `sparklineSpanSeconds = banalServiceRepository.getSparklineSpanSeconds(...)` in `loadSensorFieldStates()` and update in `applySensorData()`.

### Step 3: Pure Relative Offset Formatter & Glanceable X-Axis Label Row (`REQ-TRK-059.9`)
* **Files**: `SensorSparklineView.kt`, `SensorFieldView.kt`
* **Changes**:
  - Implement pure `formatSparklineTimeOffset(seconds: Int): String` (`-M:SS` for $<3600\text{ s}$, `-H:MM:SS` for $\ge 3600\text{ s}$).
  - In `SensorSparklineView`: wrap `Canvas` in `Column(modifier)`; add bottom `Row(fillMaxWidth, SpaceBetween)` rendering `-T`, `-T/2`, and `stringResource(R.string.sparkline_axis_now)` when `samples.size > 1`.
  - Update `SensorFieldView.kt` to pass `spanSeconds = fieldState.sparklineSpanSeconds`.

### Step 4: 9-Locale Translation Parity for `sparkline_axis_now` (`REQ-UI-106`)
* **Files**: `app/src/main/res/values*/strings_cockpit.xml` across all 9 locales:
  - `values` (EN: `Now`), `values-de` (DE: `Jetzt`), `values-es` (ES: `Ahora`), `values-fr` (FR: `Maintenant`), `values-it` (IT: `Adesso`), `values-ja` (JA: `今`), `values-nl` (NL: `Nu`), `values-pl` (PL: `Teraz`), `values-pt` (PT: `Agora`).

### Step 5: Unit & Contract Tests (`TST-TRK-059.9a` – `TST-TRK-059.9c`)
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/LiveSparklineBufferSpanTest.kt` (`TST-TRK-059.9a`)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorSparklineViewTest.kt` (`TST-TRK-059.9b`)
  - `TranslationParityTest.kt` (`TST-TRK-059.9c`)

### Step 6: Targeted Verification & Governance Checks
* **Commands**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.repositories.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
  * `python3 tools/verify_requirement_governance.py`
