# Stage 2: Requirement & Test Specification - ATT-5104: Automate radar tail light modes based on ambient light sensor (tunnels/dusk), GPS sunrise/sunset, and approaching traffic

**Ticket**: [ATT-5104](https://rainerblind.atlassian.net/browse/ATT-5104)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `v2026.46`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-CON-034`  
**Test Mapping**: `TST-CON-034`  
**Branch**: `feature/ATT-5104`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-11  

---

## 1. Traceability & Requirements Mapping

* **Requirement**: `REQ-CON-034` (*Environmental & Traffic-Adaptive Smart Tail Light Mode Automation*)
* **Test Specification**: `TST-CON-034` (*Environmental & Traffic-Adaptive Smart Tail Light Automation Test Suite*)

---

## 2. Requirements & Verification Matrix

| Requirement ID | Description | Test Case | Target Test Class |
| :--- | :--- | :--- | :--- |
| `REQ-CON-034.1` | Pocket & screen-off false trigger gating (`isInteractive` & `TYPE_PROXIMITY == FAR`) | `TST-CON-034.1` | `SmartRadarLightEngineTest.kt` |
| `REQ-CON-034.2` | Astronomical solar elevation fallback ($\alpha \ge -3.0^\circ$ = DAY, $< -3.0^\circ$ = DUSK_NIGHT) | `TST-CON-034.2` | `SolarElevationCalculatorTest.kt` |
| `REQ-CON-034.3` | Tunnel detection (< 50 lx, < 2s activation) and 10s exit hysteresis (> 300 lx) | `TST-CON-034.3` | `SmartRadarLightEngineTest.kt` |
| `REQ-CON-034.4` | 3-stage threat escalation matrix with strict night anti-glare cap (never Day Flash at night) | `TST-CON-034.4` | `SmartRadarLightEngineTest.kt` |
| `REQ-CON-034.5` | Command rate-limiting ($\ge 1.5\text{ s}$) & post-threat 3–5s cooldown hysteresis | `TST-CON-034.5` | `SmartRadarLightEngineTest.kt` |
| `REQ-CON-034.6` | Preference persistence & 9-language UI localization parity in `strings_radar.xml` | `TST-CON-034.6` | `TranslationParityTest.kt` |

---

## 3. Detailed Acceptance Criteria & Verification Procedures

### TST-CON-034.1: Sensor Gating & Pocket/Screen-Off Protection
* **Given**: Phone in dark jersey pocket ($0\text{ lx}$) with screen OFF or proximity NEAR during daytime.
* **When**: Ambient sensor reports $0\text{ lx}$.
* **Then**: The reading is ignored and the system remains in the daytime profile driven by solar elevation.

### TST-CON-034.2: Solar Elevation Angle Calculation
* **Given**: Known GPS coordinates and timestamps for noon, dusk/sunset, and midnight.
* **When**: Evaluated via `SolarElevationCalculator.calculateSolarElevation()`.
* **Then**: The solar angle $\alpha$ matches astronomical ground truth within $\pm 1^\circ$, correctly classifying DAY vs. DUSK_NIGHT.

### TST-CON-034.3: Fast Tunnel Detection & Exit Hysteresis
* **Given**: Active ride in sunlight ($> 1000\text{ lx}$) with screen ON and proximity FAR.
* **When**: Ambient light drops to $< 50\text{ lx}$ for $\ge 1.5\text{ s}$.
* **Then**: Light switches to tunnel mode within $2\text{ s}$, and recovers to daytime mode $10\text{ s}$ after returning to $> 300\text{ lx}$.

### TST-CON-034.4: 3-Stage Escalation & Night Anti-Glare Protection
* **Given**: Night profile active (Stage 0 = `PELOTON` 8 lm).
* **When**: Approaching and fast-approaching vehicles are detected.
* **Then**: Mode escalates to `SOLID` (Stage 1) and `NIGHT_FLASH` (Stage 2); `DAY_FLASH` 100 lm is strictly prohibited at night.

### TST-CON-034.5: Command Rate-Limiting & Cooldown
* **Given**: Rapid 4–8 Hz radar frame updates with fluctuating threats.
* **When**: Mode changes are requested.
* **Then**: Commands are rate-limited to at most one per $\ge 1.5\text{ s}$ and enforce a $3\text{--}5\text{ s}$ cooldown before de-escalating.

---

## 4. Invariants & Governance

1. **Strict Night Anti-Glare Cap**: `DAY_FLASH` (100 lm) is strictly forbidden under dusk/night conditions to avoid blinding motorists.
2. **Pocket Occlusion Immunity**: $0\text{ lx}$ sensor readings when stowed in pockets never trigger false tunnel modes.
3. **Queue Protection**: Enforces $\ge 1.5\text{ s}$ command rate-limiting across both BLE GATT and ANT+ channels.
4. **StVZO Compliance**: Respects StVZO regulatory clamps on flashing modes.
5. **Zero SQLite Migrations**: Settings persisted in `TuningPreferencesDataStore` without database schema changes.
