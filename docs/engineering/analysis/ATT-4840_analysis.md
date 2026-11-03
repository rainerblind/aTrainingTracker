# Stage 1: Analysis - ATT-4840: [Feature] Optionally start in auto paused

**Ticket**: [ATT-4840](https://rainerblind.atlassian.net/browse/ATT-4840)  
**Parent Epic**: [ATT-754](https://rainerblind.atlassian.net/browse/ATT-754) (*Filtering*)  
**Active Sprint**: `2026-44.1`  
**Author**: AI Agent 1 (Software Architect)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & User Motivation

When starting a workout while stationary (e.g., preparing bike mounts, securing phones in pockets, or waiting at trailheads), `TrackerService` currently initializes in `TRACKING` mode. It then accumulates `sportDebounceSec` (e.g. 2s, 5s, or 10s) before transitioning to `AUTO_PAUSED`. Consequently, stationary startup time artificially contaminates moving time and active duration statistics.

---

## 2. Independent Peer Review & Verification (4-Augen-Prinzip)

Audited `TrainingApplication.java`, `TrackerService.java`, and `AutoPauseManager.kt` on `sprint/2026-44.1`:
1. **Startup Call Sites**: `TrainingApplication.startTracking()` unconditionally sets `cTrackingMode = TrackingMode.TRACKING`. When `TrackerService.onStartCommand(START_NORMAL)` runs, `mAutoPauseManager` begins in `TRACKING` and waits for 1-Hz tick debounce.
2. **Sensor Gating**: `BANALService` is already active prior to workout start, maintaining selected `BSportType` and active `SPEED_mps` telemetry.
3. **Architecture Proposal Verification**: Evaluated the feasibility of evaluating startup auto-pause directly at the workout initiation boundary. Adding `shouldStartInAutoPause(config, sportType, currentSpeedMps)` to `AutoPauseManager` enables synchronous transition to `AUTO_PAUSED` (`TrackingMode.PAUSED` with `isAutoPaused = true`) without entering `PAUSE_DEBOUNCE_PENDING`.

---

## 3. Forensic Gap Analysis

- **Configuration Gap**: `TuningPreferencesDataStore` and `AutoPauseConfig` lack a global `startInAutoPauseEnabled: Boolean` preference (default `false`).
- **UI Gap**: `AutoPauseTabsScreen.kt` has no global toggle card for `startInAutoPauseEnabled` before sport-specific settings.
- **Engine Gap**: `AutoPauseManager` lacks a pure domain predicate `shouldStartInAutoPause` to evaluate startup conditions without waiting for the debounce loop.
- **Service Integration Gap**: `TrainingApplication.startTracking()` and `TrackerService` need to inspect `shouldStartInAutoPause` during initial workout startup.

---

## 4. Scope Bounding (ATT-1250)

- **In-Scope**:
  - Global `startInAutoPauseEnabled` in `TuningPreferencesDataStore`, `TuningConfig`, `AutoPauseConfig`, and `AutoPauseViewModel`.
  - Global toggle card in `AutoPauseTabsScreen.kt`.
  - Static evaluation method `AutoPauseManager.shouldStartInAutoPause(...)`.
  - Initial startup auto-pause transition in `TrainingApplication.startTracking()` and `TrackerService.onStartCommand(START_NORMAL)`.
  - 9-language localization parity and unit test suite.
- **Out-of-Scope**:
  - Modifying manual pause lock precedence (`mManualPauseLock`).
  - Modifying resume debounce behavior (`REQ-TRK-043`).

---

## 5. Architectural Impact & SWE.1 Invariants

1. **Manual Pause Sovereignty**: Explicit user pause lock retains absolute precedence over auto-resume.
2. **Speed Threshold Invariant**: Evaluated against the exact sport speed threshold from `AutoPauseConfig`.
3. **Database FIFO Ordering**: Initial sample insertion in SQLite table occurs cleanly whether tracking starts in `TRACKING` or `AUTO_PAUSED`.
