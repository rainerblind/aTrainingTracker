# Stage 1 Analysis: ATT-8456 - Compact `RadarLightQuickSwitchSheet` with Mode Icons & Ascending Mode Order

**Ticket**: [ATT-8456](https://rainerblind.atlassian.net/browse/ATT-8456) | **Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) | **Branch**: `feature/ATT-8456` | **SP**: 2 (Standard, confirmed)

---

## 1. Problem Statement & Independent Peer Review & Verification
PO review (`ATT-7972`, Pixel 10, verified via `radar_quick_switch_pixel10.png`) identified two UX/consistency defects:
1. **Oversized Quick-Switch Sheet & Dot-Only Indicators (`RadarLightQuickSwitchSheet.kt:129-309`)**:
   - Renders `Automatik` + 5 modes + `Audio-Schlummern` header + 4 snooze options as 10 full-width `RadioButton` rows (`vertical = 10.dp`, `spacedBy(8.dp)`). On Pixel 10, the 4th snooze option (`Für restliche Fahrt`) overflows off-screen.
   - Uses `10.dp` `CircleShape` dots instead of the semantic vector icons in `RadarLightButton.kt:202-208` (`resolveLightVisuals`: `FlashlightOff`, `Groups`, `FlashlightOn`, `NightlightRound`, `WbSunny`).
2. **Mismatched Mode Order (`RadarLightMode.kt:36-70` vs `RadarLightController.kt:340-373`)**:
   - `RadarLightMode.entries` (`getAvailableModes(false)`) lists `OFF -> DAY_FLASH -> NIGHT_FLASH -> SOLID -> PELOTON`.
   - `cycleNextMode()` cycles `AUTO -> DAY_FLASH -> SOLID -> PELOTON -> NIGHT_FLASH -> OFF -> AUTO` (StVZO: `AUTO -> SOLID -> OFF -> AUTO`).
   - Target ascending intensity order: **Non-StVZO**: `Automatik -> OFF (0 lm) -> PELOTON (~8 lm) -> SOLID (~25 lm) -> NIGHT_FLASH (~40 lm) -> DAY_FLASH (100 lm) -> Automatik`; **StVZO**: `Automatik -> OFF -> SOLID -> Automatik`.

---

## 2. User Scope Grounding (`ATT-1250`) & Architecture Notes
* **In-Scope**: Compact non-scrolling layout + button-matching mode icons in `RadarLightQuickSwitchSheet.kt` (retaining `RadarLightMode_*` and `RadarSnooze_*` testTags and Light/Dark/StVZO `@Preview`s); unify `RadarLightMode.getAvailableModes()` and `RadarLightController.cycleNextMode()` to ascending intensity order; update unit/Compose tests + Stage 5 emulator screenshots.
* **Out-of-Scope**: Altering `TuningPreferencesDataStore` keys, `commandByte` mappings, `SmartRadarLightEngine` escalation thresholds, or 9-locale strings (reuse existing strings in `strings_radar.xml`).
* **Shared Visual Helper**: Extract/expose `resolveLightVisuals(RadarLightMode)` from `RadarLightButton.kt:202-208` so `RadarLightQuickSwitchSheet` and `RadarLightButton` share one icon/color source of truth (zero duplication). `cycleNextMode()` can step directly through `getAvailableModes()` to prevent future order drift.

---

## 3. Chesterton's Fence Audit (`REQ-PRO-022`) & Side-Effect Analysis (`Rule 68`)
* **Original Requirement ID & Target**: `REQ-CON-031.3` (Quick-Switch Sheet) and `REQ-CON-031.8` (Debounced Mode Cycling) in `docs/requirements/req_tracking_cockpit.md`, plus `TST-CON-031.3` / `TST-CON-031.9` in `docs/tests/tst_tracking_cockpit.md`.
* **Historical Origin & Commit Trace**: `ATT-5102` (`c1d27049`) defined `RadarLightMode` enum order (`OFF, DAY_FLASH, NIGHT_FLASH, SOLID, PELOTON`); `ATT-7991` (`c422e1b6`) introduced `cycleNextMode()` with `DAY_FLASH -> SOLID -> PELOTON -> NIGHT_FLASH -> OFF` and `Automatik` row in `RadarLightQuickSwitchSheet`.
* **Root Reason for Existing Formulation**: Two features evolved separately without a shared ordering contract; `RadioButton` list rows predated adding `Automatik` + 4 snooze modes on one sheet.
* **Preservation of Core Invariants**: `Automatik` remains first; StVZO pruning strictly removes flashing modes (`OFF, SOLID` only); `TuningPreferencesDataStore` stores `.name` (never `.ordinal`); ~400 ms hardware debounce in `cycleNextMode()` and `MaterialTheme.colorScheme.outline` styling stay intact.
* **Cross-Caller Impact (`grep` verified)**:
  - `EditDeviceDialog.kt:1126`: Calls `RadarLightMode.getAvailableModes(isStvzo)` — StVZO segmented row remains `[Automatik, Aus, Dauerlicht]`; non-StVZO list automatically adopts ascending intensity order (achieving parity!).
  - `RadarLightControllerTest.kt:657-707` & `L844-847` (added in `ATT-8455`): `SOLID` now cycles to `NIGHT_FLASH` (not `PELOTON`) — update both tests to the new ascending sequence.
  - `RadarLightButtonContractTest.kt:98` & `RadarLightQuickSwitchSheetTest.kt`: Enum membership and `testTag` contracts remain valid.

---

## 4. Change-Request Impact Analysis (`REQ-PRO-064` / ASPICE SUP.10)
* **Affected Requirements**: `REQ-CON-031.3`, `REQ-CON-031.8` (plus `TST-CON-031.3`, `TST-CON-031.9`) in `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
* **Affected Architecture Elements**: `RadarLightMode.kt`, `RadarLightController.kt`, `RadarLightButton.kt`, `RadarLightQuickSwitchSheet.kt` (passive ordering parity in `EditDeviceDialog.kt`).
* **Verification Impact & Test Surface**: `RadarLightControllerTest.kt`, `RadarLightQuickSwitchSheetTest.kt`, `RadarLightButtonContractTest.kt` + Stage 5 `emulator-5554` screenshot verification.
* **Risk Assessment & Invariants**: **LOW-MEDIUM (Visual/UI)**. Zero SQLite schema changes; zero `.ordinal` serialization dependencies; `ANT-Android-SDKs` untouched.
