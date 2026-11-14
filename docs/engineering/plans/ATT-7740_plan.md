# Stage 3: Implementation Plan - ATT-7740: Morning HRV Cutoff Hour Default to 08:00 and Upper Bound Extension

**Ticket**: [ATT-7740](https://rainerblind.atlassian.net/browse/ATT-7740)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6`  
**Requirement Mapping**: `REQ-UI-525` / `REQ-UI-525.1`  
**Test Mapping**: `TST-UI-525` / `TST-UI-525.1`  
**Branch**: `feat/ATT-7740` | **Author**: Pro Agent (`agent1` / `agent2`) | **Date**: 2026-11-14  

---

## 1. Problem Description & Background
Physical testing on Pixel 10 in Sprint 2026-46.5 highlighted that the default morning check cutoff of 11:00 AM caused the CTA button on the Start screen to stay visible well into midday. Furthermore, the upper bound was hard-capped at 15:00, preventing afternoon/evening check-in configuration for shift workers. This plan details the atomic steps to set default cutoff hour to `8` (08:00 AM), extend max bound to `23` (11:00 PM), and format slider text with zero padding.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-525.1` (Tuning Preferences Configuration)
* **Test Mapping**: `TST-UI-525.1` (`TuningConfigTest.kt`, `ControlTrackingViewModelTest.kt`)

---

## 3. System Invariants & Preserved Behavior
1. **Time Gating Logic**: `timeProvider().hour < tuning.morningCheckCutoffHour` logic is unchanged.
2. **Auto-Hide & Sensor Requirements**: Paired HRM and single daily completion auto-hide invariants preserved.
3. **Zero DB Impact**: No database migrations or schema adjustments.

---

## 4. Proposed Architectural Changes

### Component 1: `TuningPreferencesDataStore.kt` (`TuningPreferencesDefaults`)
* Update `DEFAULT_MORNING_CHECK_CUTOFF_HOUR = 8`.
* Update `MAX_MORNING_CHECK_CUTOFF_HOUR = 23`.

### Component 2: `PhysiologyModelsSection.kt`
* Bind `valueRange` to `6.0f..23.0f` using `TuningPreferencesDefaults` constants.
* Bind `steps` to `16` ($23 - 6 - 1$).
* Format `valueText` and `defaultText` as `String.format(Locale.US, "%02d:00", hour)`.
* Coerce `onValueChange` in `MIN_MORNING_CHECK_CUTOFF_HOUR..MAX_MORNING_CHECK_CUTOFF_HOUR`.

### Component 3: Unit Tests (`TuningConfigTest.kt`, `ControlTrackingViewModelTest.kt`)
* Update default assertions to `8` and max bound to `23`.
* Add test verifying default cutoff behavior at 07:30 vs 08:00.

### UI Consistency (Rule 23)
* **Reference component**: Existing `TuningSliderItem` inside `PhysiologyModelsSection.kt`.
* **Zero new one-off styles**: Fully standard M3 tuning slider wrapper.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Constants in `TuningPreferencesDataStore.kt`
* Set `DEFAULT_MORNING_CHECK_CUTOFF_HOUR = 8`.
* Set `MAX_MORNING_CHECK_CUTOFF_HOUR = 23`.

### Step 2: Update UI Slider in `PhysiologyModelsSection.kt`
* Format `valueText` and `defaultText` with `%02d:00`.
* Update `valueRange = MIN_MORNING_CHECK_CUTOFF_HOUR.toFloat()..MAX_MORNING_CHECK_CUTOFF_HOUR.toFloat()`.
* Update `steps = MAX_MORNING_CHECK_CUTOFF_HOUR - MIN_MORNING_CHECK_CUTOFF_HOUR - 1`.
* Update `onValueChange` coercion.

### Step 3: Update Unit Tests
* Update `TuningConfigTest.kt` assertions for `8` and `23`.
* Add `ControlTrackingViewModelTest.kt` coverage for default cutoff at 08:00 AM.

### Step 4: Verification Commands
* Run: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.TuningConfigTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingViewModelTest"`
* Run: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests and living doc governance check.
* **Rollback**: Clean git branch `feat/ATT-7740` isolation.
