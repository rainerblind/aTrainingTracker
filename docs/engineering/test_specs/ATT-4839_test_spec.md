# Stage 2: Requirement & Test Specification - ATT-4839: [Verbesserung] Different times for detection of auto pause and leaving auto pause

**Ticket**: [ATT-4839](https://rainerblind.atlassian.net/browse/ATT-4839)  
**Parent Epic**: [ATT-754](https://rainerblind.atlassian.net/browse/ATT-754) (*Filtering*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.1`  
**Author**: AI Agent 1 (Test Engineer)  
**Date**: 2026-11-01  

---

## 1. Requirement Specification

### `REQ-TRK-043`: Sport-Specific Auto-Pause Resume Debounce & Symmetric Single-Threshold Evaluation
- `REQ-TRK-043.1`: `TuningConfig`, `TuningPreferencesDataStore`, and `AutoPauseConfig` SHALL persist independent resume debounce times: Bike ($1..20\text{ s}$, default $2\text{ s}$), Run ($1..20\text{ s}$, default $5\text{ s}$), Other ($1..60\text{ s}$, default $10\text{ s}$).
- `REQ-TRK-043.2`: `AutoPauseManager` SHALL evaluate both pausing ($v < v_{\text{threshold}}$) and resuming ($v \ge v_{\text{threshold}}$) against the exact configured sport speed threshold, eliminating the hidden `+0.5` speed hysteresis offset deadband.
- `REQ-TRK-043.3`: In `AUTO_PAUSED`, transitioning to `TRACKING` and emitting `SoundCue.AUTO_RESUME` SHALL require `resumeDebounceSec` consecutive 1-Hz ticks of fresh, valid speed $\ge v_{\text{threshold}}$. Any tick with speed $< v_{\text{threshold}}$ or stale/corrupt reading SHALL reset the resume counter to 0.
- `REQ-TRK-043.4`: `AutoPauseTabsScreen.kt` SHALL provide a *Resume Detection Time* slider for each sport tab with immediate persistence and reset support via `AutoPauseViewModel`.
- `REQ-TRK-043.5`: String resources SHALL maintain 9-language localization parity across all supported locales.

---

## 2. Acceptance Criteria (Given-When-Then)

- **AC-1 (Multi-Tick Confirmation)**: *Given* `AutoPauseManager` in `AUTO_PAUSED` (threshold 3 km/h, resume debounce 2s), *When* speed $\ge 3\text{ km/h}$ arrives for 2 consecutive seconds, *Then* state transitions to `TRACKING` and `SoundCue.AUTO_RESUME` is emitted.
- **AC-2 (Jitter Rejection)**: *Given* `AutoPauseManager` in `AUTO_PAUSED` (resume debounce 2s), *When* 1 tick at 5 km/h is followed by a tick at 0 km/h, *Then* resume counter resets to 0 and state remains `AUTO_PAUSED`.
- **AC-3 (Single Threshold)**: *Given* `AutoPauseManager` in `AUTO_PAUSED` with threshold 3 km/h, *When* speed is 3.0 km/h, *Then* tick counts toward resume debounce without requiring $3.5\text{ km/h}$.
- **AC-4 (UI & Reset)**: *Given* `AutoPauseTabsScreen`, *When* slider is adjusted or reset is tapped, *Then* values persist reactively in `TuningPreferencesDataStore`.

---

## 3. Test Case Specification & Traceability Matrix

| Test ID | Method / Test Class | Target Requirement | Description | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-043.1` | Unit (`AutoPauseManagerTest`) | `REQ-TRK-043.1`, `REQ-TRK-043.3` | Multi-tick resume debounce on Bike (2s), Run (5s), Other (10s) | Planned |
| `TST-TRK-043.2` | Unit (`AutoPauseManagerTest`) | `REQ-TRK-043.3` | Transient GPS spike rejection and counter reset on stationary/stale tick | Planned |
| `TST-TRK-043.3` | Unit (`AutoPauseManagerTest`) | `REQ-TRK-043.2` | Single threshold evaluation without `+0.5` hysteresis offset | Planned |
| `TST-TRK-043.4` | Unit (`AutoPauseViewModelTest`) | `REQ-TRK-043.1`, `REQ-TRK-043.4` | ViewModel state mutation and sport-specific reset persistence | Planned |
| `TST-TRK-043.5` | Unit (`TranslationParityTest`) | `REQ-TRK-043.5` | 9-language translation parity for resume detection time strings | Planned |

---

## 4. System Invariants

1. **Manual Pause Sovereignty**: Explicit user pause lock (`mManualPauseLock`) strictly blocks auto-resume regardless of speed.
2. **Sensor Lost Recovery**: 2 consecutive fresh valid ticks remain required to recover from `SENSOR_LOST`.
3. **Continuous Metric Integrity**: Moving average speed calculation and total time persistence remain untouched.
