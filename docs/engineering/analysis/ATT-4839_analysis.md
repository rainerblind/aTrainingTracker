# Stage 1: Analysis - ATT-4839: [Verbesserung] Different times for detection of auto pause and leaving auto pause

**Ticket**: [ATT-4839](https://rainerblind.atlassian.net/browse/ATT-4839)  
**Parent Epic**: [ATT-754](https://rainerblind.atlassian.net/browse/ATT-754) (*Filtering*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.1`  
**Author**: AI Agent 1 (Software Architect)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & User Impact

Currently, entering Auto-Pause evaluates a configurable per-sport debounce timer (`debounceSec`), whereas leaving Auto-Pause (resuming to `TRACKING`) triggers on a single 1-Hz tick where speed exceeds a hidden `+0.5` hysteresis offset above the pause speed threshold (`AutoPauseManager.kt:478`).
A single GPS jitter spike (common while stationary at traffic lights, under trees, or indoors) immediately and falsely triggers auto-resume. Furthermore, the hidden `+0.5` offset is opaque to athletes who expect symmetric, sport-tailored debounce filtering.

---

## 2. Forensic Investigation & Root Cause Analysis

1. **Immediate Resume on Single Tick**: In `AutoPauseManager.evaluateTick(...)`, the `AutoPauseState.AUTO_PAUSED` branch transitions to `TRACKING` immediately upon the first tick where `speedMps >= resumeThresholdMps`. There is no confirmation debounce counter for resumption.
2. **Asymmetric Hysteresis Offset**: `calculateResumeSpeedThresholdUserUnit(...)` artificially shifts the threshold by `+0.5` user units (`HYSTERESIS_OFFSET_USER_UNIT`), attempting to compensate for lack of time-based debounce through speed-based deadbanding.
3. **Missing DataStore Keys & UI Controls**: `TuningConfig` and `TuningPreferencesDataStore` only store pause debounce (`autoPause*DebounceSec`). No persistent preferences exist for resume debounce times.

---

## 3. Chesterton's Fence Archaeology (`REQ-TRK-018` / `REQ-TRK-028` / `REQ-TRK-037`)

1. *Original Target*: `REQ-TRK-018` (*Sport-Specific Auto-Pause Engine*) and `REQ-TRK-037` (*Speed Sensor Standstill vs. In-Motion Signal Loss*).
2. *Historical Origin*: Commits `53c20db4` (ATT-45, adding `+0.5` hysteresis offset) and `69e984f1` (ATT-3984).
3. *Root Reason for Existing Formulation*: The `+0.5` speed deadband was introduced to prevent immediate chatter at standstill when GPS velocity fluctuated around the threshold. However, speed deadband alone fails against transient GPS multi-path jumps (e.g. 5–10 km/h spikes). Time-based debounce sustained over $N$ consecutive seconds is the robust physical solution.
4. *Preservation of Core Invariants*: Manual pause priority (`mManualPauseLock`), 2-tick `SENSOR_LOST` recovery, synchronous memory gating, and continuous `TIME_TOTAL_s` accumulation must remain strictly preserved.

---

## 4. Scope Boundaries & Blast Radius

- **In Scope**:
  - Add per-sport `resumeDebounceSec` to `TuningConfig`, `TuningPreferencesDataStore`, and `AutoPauseConfig` (Bike: default 2s, 1..20s; Run: default 5s, 1..20s; Other: default 10s, 1..60s).
  - Update `AutoPauseManager`: remove `+0.5` speed hysteresis offset, evaluate both pause and resume against the exact configured sport speed threshold, and require `resumeDebounceSec` consecutive 1-Hz ticks of fresh valid speed $\ge$ threshold before resuming to `TRACKING`.
  - Add Resume Detection Time slider in `AutoPauseTabsScreen.kt` for each sport tab.
  - 9-language localization parity and unit tests in `AutoPauseManagerTest.kt` and `AutoPauseViewModelTest.kt`.
- **Out of Scope**:
  - Altering manual pause lock semantics or sensor lost state machine.
  - Changes to workout sample recording or FIT/GPX export formats.

---

## 5. Architectural Solution (SWE.2) & Verification Plan

- **Engine State Machine**: Add `resumeDebounceCounter: Int`. In `AUTO_PAUSED`, increment counter on fresh valid ticks $\ge$ sport threshold; reset counter to 0 on any tick $<$ threshold or stale/corrupt reading. Transition to `TRACKING` and emit `SoundCue.AUTO_RESUME` only when `resumeDebounceCounter >= sportResumeDebounceSec`.
- **Verification**: Expand `AutoPauseManagerTest.kt` with multi-tick resume debounce test cases and jitter rejection; verify `AutoPauseTabsScreen` and `AutoPauseViewModel`.
