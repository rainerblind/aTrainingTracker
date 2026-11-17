# Stage 2: Requirement & Test Specification - ATT-8450: Offline EditDeviceDialog Radar Tail Light Configuration

**Ticket**: [ATT-8450](https://rainerblind.atlassian.net/browse/ATT-8450) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) | **Branch**: `feature/ATT-8450`  
**Requirement Mapping**: `REQ-CON-048` (refining `REQ-CON-031.5`, `REQ-CON-031.7`, `REQ-CON-047.3`) | **Test Spec ID**: `TST-CON-048`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-17

---

## 1. Requirement Specification (`REQ-CON-048` `[Planned]`)
* **Priority**: High | **Type**: Functional | **Verification Method**: Unit
* **Implementation**: `EditDeviceDialog.kt`, `RadarLightController.kt`, `strings_radar.xml`

### 1.1 Functional & Architectural Requirements
1. **Offline Visibility & Radar-Only Exclusion (`REQ-CON-048.1`)**: `EditDeviceDialog.kt` (`RadarLightControlSection`) SHALL remain visible and interactive for `DeviceType.BIKE_RADAR` even when `!isConnected && !isSimulating`, unless live-connected without light support (`isConnected && !isLightControlAvailable && !isSimulating`) or identifiable as a radar-only model (`RVR315` / `RVR 315`).
2. **Cold-Start Hydration, Offline Selection & StVZO Reset (`REQ-CON-048.2`)**: `RadarLightController` SHALL hydrate persisted `isAutoMode` and `preferredMode` (`radarLightDefaultMode`) from `TuningPreferencesDataStore` prior to workout start, expose the effective selected mode so offline manual selection persists and highlights accurately (never reverting highlight to `OFF`), and reset `_isStvzo` to `false` in `unregisterRadarDevice()`.
3. **Offline Apply-on-Connect Hint & Unknown StVZO (`REQ-CON-048.3`)**: When `!isConnected && !isSimulating`, `RadarLightControlSection` SHALL show an unambiguous localized hint (*applied on next connection*) and MUST NOT show a spurious StVZO notice/error when `isStvzo == false`, while still pruning flashing modes when `isStvzo == true` and clamping on connection.
4. **9-Locale Parity, Previews & Behavioral Tests (`REQ-CON-048.4`)**: New `strings_radar.xml` keys SHALL maintain 100% parity across all 9 locales, with Light/Dark `@Preview`s and Robolectric `createComposeRule()` UI tests (zero `src/main` reads per `REQ-PRO-049.1`).

### 1.2 Acceptance Criteria (Given-When-Then) & Invariants
* **Given** a disconnected `RearVue8` (`BIKE_RADAR`, `isConnected = false`, `isStvzo = false`), **When** `EditDeviceDialog` opens and `SOLID` or `Automatik` is selected, **Then** the section renders with the offline hint (zero StVZO notice), highlights `SOLID` / `Automatik` immediately, and preserves the choice after cold restart.
* **Given** `RVR315` offline or connected `!isLightControlAvailable`, **When** `EditDeviceDialog` renders, **Then** `RadarLightControlSection` is omitted; **Given** `isStvzo = true` offline/online, **Then** only `Automatik`, `OFF`, `SOLID` + StVZO notice render, and `unregisterRadarDevice()` clears `_isStvzo`.
* **Invariants**: `EditDeviceViewModel` draft state decoupled; `REQ-CON-047.3` ascending order preserved; StVZO clamping on connect preserved; zero SQLite schema changes; 9-locale `strings_radar.xml` parity.

### 1.3 Chesterton's Fence Audit (`REQ-PRO-022`)
* **Original Requirement ID & Target**: `REQ-CON-048` refining `REQ-CON-031.5`, `REQ-CON-031.7` (`EditDeviceDialog.kt`, `RadarLightController.kt`, `strings_radar.xml`).
* **Historical Origin & Commit Trace**: `ATT-7540` gated on `isConnected && isLightControlAvailable`; `ATT-7991` (`c422e1b6`, `6e9b3c06`, `533d88b1`) retired Expert Settings mode controls & added `isSimulating`; `ATT-8456` unified ascending mode order.
* **Root Reason for Existing Formulation**: `ATT-7540` relied on `RadarSettingsSection.kt` for offline configuration before `ATT-7991` retired it.
* **Preservation of Core Invariants**: Keeps connected `!isLightControlAvailable` & `RVR315` hidden, draft isolation, StVZO pruning/clamping, and 9-locale parity intact.

---

## 2. Test Specification (`TST-CON-048` `[Planned]`)
* **`TST-CON-048.1` (`EditDeviceDialogRadarLightTest.kt`)**: Robolectric `createComposeRule()` asserting offline `RearVue8` visibility, ascending options (`Automatik`, `OFF`, `PELOTON`, `SOLID`, `NIGHT_FLASH`, `DAY_FLASH`), offline hint, zero StVZO notice when `isStvzo == false`, and omission for `RVR315` and connected `!isLightControlAvailable`.
* **`TST-CON-048.2` (`RadarLightControllerTest.kt`)**: Unit tests for offline `setManualLightMode()` / `setAutoMode()` UI selection state + DataStore persistence (never highlighting `OFF`), cold-start hydration before workout start, and `_isStvzo = false` reset in `unregisterRadarDevice()`.
* **`TST-CON-048.3` (`EditDeviceDialogRadarLightTest.kt`)**: Offline StVZO (`Automatik`, `OFF`, `SOLID` + notice), offline Multi-Mode, Light/Dark `@Preview`s, and `Rule 8.5` PNG capture inspection (`REQ-PRO-049.1` zero `src/main` reads).
* **`TST-CON-048.4` (`TranslationParityTest.kt`)**: 9-locale `strings_radar.xml` parity (`radar_light_offline_hint`) + targeted Gradle test execution.

| Locale | `radar_light_offline_hint` (Draft) |
| :--- | :--- |
| `values` (EN) | `Not connected — selected mode applies on next connection.` |
| `values-de` | `Nicht verbunden – gewählter Modus wird beim nächsten Verbinden angewendet.` |
| `values-es`..`pt` | `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt` full parity in Stage 4 |

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-048.1` | UI (Compose) | Offline `RearVue8` visible + hint; `RVR315`/unsupported hidden | `REQ-CON-048.1`, `REQ-CON-048.3` | Planned |
| `TST-CON-048.2` | Unit | Cold-start hydration, offline selection state, `_isStvzo` reset | `REQ-CON-048.2` | Planned |
| `TST-CON-048.3` | UI + PNG | Offline StVZO & Multi-Mode, Light/Dark `@Preview`s, `Rule 8.5` PNG | `REQ-CON-048.3`, `REQ-CON-048.4` | Planned |
| `TST-CON-048.4` | Contract | 9-locale `strings_radar.xml` parity + targeted suite pass | `REQ-CON-048.4`, `REQ-CON-031.7` | Planned |
