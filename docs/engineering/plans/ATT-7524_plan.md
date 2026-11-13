# Stage 3: Implementation Plan - ATT-7524: Default ZoneDisplayOptions to Background + Left & Right Bars and Persist HRV Display Options

**Ticket**: [ATT-7524](https://rainerblind.atlassian.net/browse/ATT-7524) | **Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7524`  
**Requirement Mapping**: `REQ-UI-172`, `REQ-TRK-061.3` | **Test Mapping**: `TST-UI-124.1`–`TST-UI-124.4`, `TST-TRK-061.3`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Description & Background
`ZoneDisplayOptions` currently defaults to `showRightBar = false`, causing asymmetrical border framing on live Cockpit sensor tiles. Enabling both left and right bars (`showRightBar = true`) alongside tinted background improves glanceability without reducing numeric text legibility. In addition, the 4th `HRV (DFA a1)` tab in `ZonesSettingsActivity` stores toggles only in ephemeral `remember` state, and `TrackingViewModel` maps HRV to HR run/bike zones rather than dedicated HRV display options across all sport types.

---

## 2. Traceability & Requirements Mapping
* **REQ-UI-172**: Symmetrical defaults (`showRightBar = true`), DataStore missing-key fallback to `true`, explicit `false` preservation, dedicated HRV DataStore persistence (`hrv_dfa_a1_zone_show_*`) $\rightarrow$ `TST-UI-124.1`, `TST-UI-124.2`.
* **REQ-TRK-061.3**: Decouple `HRV_DFA_A1` from HR zone mapping in `TrackingViewModel.kt` to resolve dedicated HRV display options across all sports (`RUN`, `BIKE`, `UNKNOWN`) $\rightarrow$ `TST-TRK-061.3`.
* **REQ-UI-106 / REQ-PRO-001**: 9-language translation parity and unit test suite regression safety $\rightarrow$ `TST-UI-124.3`, `TST-UI-124.4`.

---

## 3. System Invariants & Preserved Behavior
1. **Explicit Preference Preservation**: Users with explicitly saved `showRightBar = false` in DataStore are preserved.
2. **Threshold Invariant**: HRV threshold gap ($T_1 \ge T_2 + 0.05$) and semantic color tokens (`TTColor.Zone2`..`Zone4`) remain intact.
3. **Model & DB Schema Invariance**: SQLite tables and `SettingsDataStore.ZoneType` enum remain unchanged (`DB_VERSION = 30`).
4. **Human Decision Gate**: Parent ticket advances to `Test` and eventually `Final Review (Human)`.

---

## 4. Proposed Architectural Changes
* **Component 1 (Domain Model - `ZoneDisplayOptions.kt`)**: Update constructor default `val showRightBar: Boolean = true`.
* **Component 2 (Persistence & Cache - `SettingsDataStore.kt`)**: Add `HRV_DFA_A1_ZONE_SHOW_*` keys, `hrvZoneDisplayOptionsFlow`, `saveHrvZoneDisplayOptions`, include `hrvDisplayOptions` in `SettingsSnapshot`, and update missing `rightBarKey` fallback to `true`.
* **Component 3 (UI - `ZonesSettingsActivity.kt`)**: In `HrvDfaA1SettingsContent`, collect `hrvZoneDisplayOptionsFlow` and persist on toggle.
* **Component 4 (Cockpit - `TrackingViewModel.kt`)**: Observe `snapshot.hrvDisplayOptions` and direct `HRV_DFA_A1` to dedicated HRV display options across all sports. Remove `HRV_DFA_A1` from `getZoneType()`.
* **UI Consistency (`docs/design_guidelines.md` §1 & §5)**: Reuses `ZoneDisplayToggleRow`, M3 `Switch`, `TTColor.Zone*` tokens, and 12% alpha container background.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1: Domain Model Defaults (`ZoneDisplayOptions.kt`)**: Update constructor default `showRightBar = true` (`TST-UI-124.1`).
* **Step 2: DataStore Persistence & Snapshot Caching (`SettingsDataStore.kt`)**: Add keys, flow, save function, snapshot property, and `prefs[keys.rightBarKey] ?: true` fallback (`TST-UI-124.2`).
* **Step 3: Wire HRV Tab Persistence (`ZonesSettingsActivity.kt`)**: Replace local ephemeral state in `HrvDfaA1SettingsContent` with DataStore flow and persistence on toggle (`TST-UI-124.2`).
* **Step 4: Wire Cockpit Resolution (`TrackingViewModel.kt`)**: Direct `HRV_DFA_A1` to dedicated HRV display options across all sport profiles (`TST-TRK-061.3`).
* **Step 5: Unit Tests & Verification**: Update `ZoneDisplayOptionsTest.kt`, `SensorFieldZoneDisplayTest.kt`, `SettingsDataStoreZoneAtomicTest.kt`, and add `TrackingViewModelHrvTest.kt` cases (`TST-UI-124.1`..`TST-UI-124.4`).
  * Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests pass 100%, `verify_requirement_governance.py` passes with zero violations.
* **Rollback**: Branch isolation allows full revert via `git reset --hard feature/ATT-7524` before merge.
