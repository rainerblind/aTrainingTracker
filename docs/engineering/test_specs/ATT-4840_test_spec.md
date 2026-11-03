# Stage 2: Requirement & Test Specification - ATT-4840: [Feature] Optionally start in auto paused

**Ticket**: [ATT-4840](https://rainerblind.atlassian.net/browse/ATT-4840)  
**Parent Epic**: [ATT-754](https://rainerblind.atlassian.net/browse/ATT-754) (*Filtering*)  
**Active Sprint**: `2026-44.1`  
**Author**: AI Agent 1 (Test Engineer)  
**Date**: 2026-11-01  

---

## 1. Requirement Specification

### `REQ-TRK-044`: Optional Automatic Stationary Auto-Pause at Workout Start
- `REQ-TRK-044.1`: `TuningConfig`, `TuningPreferencesDataStore`, and `AutoPauseConfig` SHALL persist a global boolean preference `startInAutoPauseEnabled` (default `false`).
- `REQ-TRK-044.2`: `AutoPauseTabsScreen.kt` SHALL render a global *Start in Auto-Paused* toggle (`TuningToggleItem`) at the top of the Auto-Pause settings screen with reactive updates via `AutoPauseViewModel`.
- `REQ-TRK-044.3`: `AutoPauseManager` SHALL expose a static pure function `shouldStartInAutoPause(config, sportType, currentSpeedMps)` evaluating whether startup conditions are satisfied: `config.startInAutoPauseEnabled == true`, auto-pause is enabled for `sportType`, and `currentSpeedMps` is below the configured sport speed threshold (or null/0.0/corrupt).
- `REQ-TRK-044.4`: When the user starts tracking with `startInAutoPauseEnabled == true` and `shouldStartInAutoPause` evaluates to `true`, tracking SHALL initialize directly in `TrackingMode.PAUSED` (`isAutoPaused = true`) and `AutoPauseState.AUTO_PAUSED` without accumulating debounce ticks.
- `REQ-TRK-044.5`: String resources for the toggle title and description SHALL maintain full parity across all 9 supported languages.

---

## 2. Acceptance Criteria (Given-When-Then)

- **AC-1 (Stationary Start)**: *Given* `startInAutoPauseEnabled` is `true`, bike auto-pause is enabled, and phone is stationary ($v = 0.0\text{ m/s}$), *When* workout tracking begins, *Then* state initializes directly in `TrackingMode.PAUSED` (`isAutoPaused = true`) and `AutoPauseState.AUTO_PAUSED`.
- **AC-2 (Sport Disabled)**: *Given* `startInAutoPauseEnabled` is `true`, but run auto-pause is disabled, *When* running workout begins while stationary, *Then* state initializes in `TrackingMode.TRACKING`.
- **AC-3 (In-Motion Start)**: *Given* `startInAutoPauseEnabled` is `true` and phone is moving at $5\text{ m/s}$ ($> v_{\text{threshold}}$), *When* workout begins, *Then* state initializes in `TrackingMode.TRACKING`.
- **AC-4 (Disabled Feature)**: *Given* `startInAutoPauseEnabled` is `false`, *When* workout begins while stationary, *Then* state initializes in `TrackingMode.TRACKING` and waits for standard debounce.

---

## 3. Test Case Specification & Traceability Matrix

| Test ID | Method / Test Class | Target Requirement | Description | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-044.1` | Unit (`AutoPauseManagerTest`) | `REQ-TRK-044.3` | Verify `shouldStartInAutoPause` predicate across sports, thresholds, and speeds | Planned |
| `TST-TRK-044.2` | Unit (`TrackerServiceTest`) | `REQ-TRK-044.4` | Verify workout start transition directly to `AUTO_PAUSED` and `TrackingMode.PAUSED` | Planned |
| `TST-TRK-044.3` | Unit (`AutoPauseViewModelTest`) | `REQ-TRK-044.1`, `REQ-TRK-044.2` | Verify ViewModel mutation of `startInAutoPauseEnabled` in DataStore | Planned |
| `TST-TRK-044.4` | Unit (`TranslationParityTest`) | `REQ-TRK-044.5` | Verify 9-language parity for start in auto-pause title & description strings | Planned |

---

## 4. System Invariants

1. **Manual Pause Sovereignty**: Explicit user pause lock (`mManualPauseLock`) strictly blocks auto-resume regardless of speed.
2. **Resume Confirmation**: Once started in `AUTO_PAUSED`, leaving auto-pause strictly requires `resumeDebounceSec` consecutive ticks $\ge v_{\text{threshold}}$ per `REQ-TRK-043`.
3. **FIFO Sample Integrity**: Initial sample insertion into SQLite table executes reliably whether starting in `TRACKING` or `AUTO_PAUSED`.
