# Stage 3: Implementation Plan - ATT-7314: Render preview sparkline and preserve buffer history when graph is enabled for inactive sensors

**Ticket**: [ATT-7314](https://rainerblind.atlassian.net/browse/ATT-7314) | **Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) | **Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.4` | **Branch**: `sprint/2026-46.4` | **Req/Test**: `REQ-TRK-059.11`, `REQ-TRK-059.12` / `TST-TRK-059.11`, `TST-TRK-059.12`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Architecture Rationale
When an athlete configures a live sparkline graph tile for a sensor that is currently disconnected or inactive:
1. In `EditSensorFieldDialog` and Edit Tracking Tabs (`ScreenMode.CONFIGURATION` / `ScreenMode.PREVIEW`), the buffer contains 0 samples, causing `SensorSparklineView` to return early without rendering. The tile appears completely blank, giving zero layout preview.
2. In `BANALServiceRepository.registerSparklineBuffer()`, changing the time horizon on an existing sensor configuration drops the old buffer and creates a fresh empty buffer, discarding all accumulated historical telemetry.

We address both issues across clean architectural boundaries:
- In `LiveSparklineBuffer.kt`, add thread-safe `transferFrom(previous: LiveSparklineBuffer)` migrating samples and elapsed duration across capacity changes.
- In `BANALServiceRepository.kt`, invoke `buffer.transferFrom(existing.buffer)` when re-registering an identical sensor configuration with a modified time horizon.
- In `SensorSparklineView.kt`, define `DEFAULT_PREVIEW_SPARKLINE_SAMPLES` (24 smooth representative telemetry points).
- In `SensorFieldView.kt`, provide preview samples and horizon span fallback when `fieldState.showGraph == true && fieldState.sparklineSamples.isEmpty()` in `screenMode != ScreenMode.TRACKING`, while strictly keeping live tracking (`ScreenMode.TRACKING`) empty when no data exists.
- In `EditSensorFieldDialog.kt`, render a preview container below the stepped horizon slider displaying `SensorSparklineView` with `DEFAULT_PREVIEW_SPARKLINE_SAMPLES` and the active horizon seconds.

---

## 2. System Invariants & Preserved Behavior
1. **Live Tracking Authenticity**: In `ScreenMode.TRACKING`, only authentic sensor data is rendered; zero synthetic/mock data is injected during active tracking.
2. **Buffer Memory Bounds**: The $\le 300$ samples ($\le 1\text{ KB}$ per tile) memory budget is strictly preserved during `transferFrom`.
3. **Thread Safety**: `transferFrom` locks both the source and target buffers to prevent concurrent modification during sample ingestion.
4. **Composition Hygiene**: Zero Compose snapshot state writes during composition (Rule 66).
5. **Localization Parity**: 100% 9-language translation parity maintained.

---

## 3. UI Consistency (Design Guidelines)
* **Guideline Compliance**: Strictly conforms to `docs/design_guidelines.md` §1.4 and Section 5.
* **Reused Tokens**: `MaterialTheme.typography.labelSmall`, `MaterialTheme.colorScheme.surfaceVariant`, `MaterialTheme.colorScheme.primary`, `MaterialTheme.colorScheme.onSurfaceVariant`.
* **Layout Sizing**: Sparkline preview container uses 48.dp height matching cockpit card aesthetics.

---

## 4. Step-by-Step Implementation Sequence

### Step 1: Thread-Safe `transferFrom` in `LiveSparklineBuffer.kt` (`REQ-TRK-059.12`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/LiveSparklineBuffer.kt`
* **Changes**:
  - Implement `fun transferFrom(previous: LiveSparklineBuffer)` synchronizing on `previous.lock` then `this.lock`.
  - For fixed horizons, copy trailing `min(samples.size, horizonSeconds)` samples and clamp `totalElapsedSeconds`.
  - For full workout, copy all samples into `fullWorkoutBuffer`, set `totalElapsedSeconds`, and decimate if `> 300`.

### Step 2: Buffer Preservation on Horizon Change in `BANALServiceRepository.kt` (`REQ-TRK-059.12`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt`
* **Changes**:
  - In `registerSparklineBuffer()`, if `existing != null && existing.configHash == configHash`, call `buffer.transferFrom(existing.buffer)`.

### Step 3: Representative Preview Samples in `SensorSparklineView.kt` (`REQ-TRK-059.11`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorSparklineView.kt`
* **Changes**:
  - Expose `val DEFAULT_PREVIEW_SPARKLINE_SAMPLES: List<Float>` with 24 representative data points.

### Step 4: Configuration Mode Sparkline Fallback in `SensorFieldView.kt` (`REQ-TRK-059.11`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt`
* **Changes**:
  - In `SensorFieldView`, when `fieldState.showGraph == true`:
    * `val effectiveSamples = if (fieldState.sparklineSamples.isNotEmpty()) fieldState.sparklineSamples else if (screenMode != ScreenMode.TRACKING) DEFAULT_PREVIEW_SPARKLINE_SAMPLES else emptyList()`
    * `val effectiveSpanSeconds = if (fieldState.sparklineSamples.isNotEmpty()) fieldState.sparklineSpanSeconds else if (screenMode != ScreenMode.TRACKING) (if (fieldState.graphHorizonSeconds > 0) fieldState.graphHorizonSeconds else 300) else 0`
  - Pass `effectiveSamples` and `effectiveSpanSeconds` to `SensorSparklineView`.

### Step 5: Instant Sparkline Preview in `EditSensorFieldDialog.kt` (`REQ-TRK-059.11`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt`
* **Changes**:
  - Directly under the stepped horizon slider, render a `Surface` preview card containing a title and `SensorSparklineView(DEFAULT_PREVIEW_SPARKLINE_SAMPLES, spanSeconds = uiState.graphHorizonSeconds)`.

### Step 6: Unit and Contract Tests (`TST-TRK-059.11`, `TST-TRK-059.12`)
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/LiveSparklineBufferSpanTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepositorySparklineTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldViewLayoutTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialogSparklineTest.kt`

### Step 7: Clean-Room Test Suite & Governance Verification
* **Commands**:
  * `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.repositories.BANALServiceRepositorySparklineTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
  * `python3 tools/verify_requirement_governance.py`
