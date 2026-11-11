# Stage 3: Implementation Plan - ATT-5104: Automate radar tail light modes based on ambient light sensor (tunnels/dusk), GPS sunrise/sunset, and approaching traffic

**Ticket**: [ATT-5104](https://rainerblind.atlassian.net/browse/ATT-5104)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `v2026.46`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-CON-034` (`REQ-CON-034.1`..`REQ-CON-034.6`)  
**Test Mapping**: `TST-CON-034` (`TST-CON-034.1`..`TST-CON-034.6`)  
**Branch**: `feature/ATT-5104`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background
Cycling rearview radar units (Garmin Varia RTL515/820, Magene L508, Bryton Gardia) support multi-tier lighting modes, but cyclists currently must manually adjust settings or rely on static defaults. 

This plan implements `SmartRadarLightEngine`:
1. Environmental adaptation: fast tunnel detection (< 2s, < 50 lx) with 10s exit hysteresis (> 300 lx).
2. Sensor gating: screen-off (`PowerManager.isInteractive == false`) and proximity (`Sensor.TYPE_PROXIMITY == NEAR`) checks prevent jersey pocket darkness from triggering false tunnel modes.
3. Astronomical solar elevation: GPS-driven solar angle calculation ($\alpha < -3.0^\circ$ = dusk/night) provides primary day/night classification immune to pocket occlusion.
4. 3-Stage threat escalation matrix with strict anti-glare protection at night (max 40 lm Night Flash, never 100 lm Day Flash at night), command rate-limiting ($\ge 1.5\text{ s}$), and 3–5s cooldown hysteresis.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-CON-034` (*Environmental & Traffic-Adaptive Smart Tail Light Mode Automation*)
* **Test Mapping**: `TST-CON-034` (*Environmental & Traffic-Adaptive Smart Tail Light Automation Test Suite*)
  - `REQ-CON-034.1` / `TST-CON-034.1`: Screen-off & pocket proximity gating.
  - `REQ-CON-034.2` / `TST-CON-034.2`: Astronomical solar elevation angle calculation.
  - `REQ-CON-034.3` / `TST-CON-034.3`: Tunnel fast-detection and exit hysteresis.
  - `REQ-CON-034.4` / `TST-CON-034.4`: 3-stage traffic escalation matrix with night anti-glare cap.
  - `REQ-CON-034.5` / `TST-CON-034.5`: Minimum 1.5s command rate-limiting and 3–5s post-pass cooldown.
  - `REQ-CON-034.6` / `TST-CON-034.6`: Preference persistence & 9-language UI localization parity.

---

## 3. System Invariants & Preserved Behavior
1. **Strict Night Anti-Glare Protection**: Day Flash (100 lm) is strictly forbidden under dusk/night conditions.
2. **Pocket Occlusion Immunity**: 0 lx dark readings while stowed in pockets/bags never trigger false tunnel modes; falls back to GPS solar elevation.
3. **Queue Rate-Limiting**: Dispatches rate-limited to at most one per $\ge 1.5\text{ s}$, protecting BLE GATT queues and ANT+ 8 Hz channels.
4. **StVZO Compliance**: Preserves `RadarLightController` StVZO clamps (solid only).
5. **Zero SQLite Migration**: Stored in `TuningPreferencesDataStore`; `DB_VERSION` unchanged.

---

## 4. Proposed Architectural Changes
### Component 1: SolarElevationCalculator (`SolarElevationCalculator.kt`)
- Calculates solar elevation angle from `(latitude, longitude, timestampMs)`.
- Classifies DAY vs. DUSK_NIGHT using civil twilight threshold ($-3.0^\circ$).

### Component 2: SmartRadarLightEngine (`SmartRadarLightEngine.kt`)
- Gated ambient sensor listener (`Sensor.TYPE_LIGHT`, screen interactive, `TYPE_PROXIMITY`).
- Tunnel state machine: entry (< 50 lx for $\ge 1.5\text{ s}$), exit (> 300 lx for $\ge 10\text{ s}$).
- 3-stage threat escalation driven by `RadarStateRepository.state`:
  - Night: Stage 0 = `PELOTON` (8 lm), Stage 1 = `SOLID` (25 lm), Stage 2 = `NIGHT_FLASH` (40 lm).
  - Day: Stage 0 = `OFF`, Stage 1 = `SOLID`/`DAY_FLASH`, Stage 2 = `DAY_FLASH` (100 lm).
- Rate limiter: $\ge 1.5\text{ s}$ command interval; $3.5\text{ s}$ cooldown hysteresis.
- Manual override tracking: suspends automation when rider manually selects a mode in Cockpit sheet.

### Component 3: Preferences & UI (`TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`)
- Add `radarSmartLightAutomation` boolean preference.
- Add toggle switch card in `RadarSettingsSection.kt` under Tail Light settings.
- Add 9-language string resources in `strings_radar.xml`.

### UI Consistency (Rule 23)
- **Reference screen**: `RadarSettingsSection.kt` Tail Light Settings Card.
- **Reused components**: Standard Compose `Card`, `Row`, `Text`, `Switch`.
- **Theme tokens**: `MaterialTheme.typography.bodyLarge`/`bodySmall`, `MaterialTheme.colorScheme.onSurface`/`onSurfaceVariant`.
- **One-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence
### Step 1: SolarElevationCalculator (`REQ-CON-034.2`, `TST-CON-034.2`)
* Files: `SolarElevationCalculator.kt`, `SolarElevationCalculatorTest.kt`
* Implement astronomical formula and unit tests for known noon/sunset/midnight coordinates.

### Step 2: SmartRadarLightEngine (`REQ-CON-034.1`, `REQ-CON-034.3`, `REQ-CON-034.4`, `REQ-CON-034.5`)
* Files: `SmartRadarLightEngine.kt`, `SmartRadarLightEngineTest.kt`
* Implement pocket gating, tunnel detection, 3-stage escalation, and rate-limiting.

### Step 3: Preferences, Controller Integration & UI (`REQ-CON-034.6`, `TST-CON-034.6`)
* Files: `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `values*/strings_radar.xml`, `RadarLightController.kt`
* Add preference key, UI switch, and 9-language translations.

### Step 4: Verification Suite (`TST-CON-034.1`..`TST-CON-034.6`)
* Run targeted tests via `parallel_sprint_lock.py` and verify `TranslationParityTest`.

---

## 6. Verification & Rollback Plan
* **Verification**: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.radar.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
* **Rollback**: `git checkout sprint/2026-46.2` safely discards working branch.
