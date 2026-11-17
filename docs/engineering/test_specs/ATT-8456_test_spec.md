# Stage 2: Requirement & Test Specification - ATT-8456: Compact `RadarLightQuickSwitchSheet` & Ascending Mode Order

**Ticket**: [ATT-8456](https://rainerblind.atlassian.net/browse/ATT-8456) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) | **Branch**: `feature/ATT-8456`  
**Requirement Mapping**: `REQ-CON-047` (refining `REQ-CON-031.3`, `REQ-CON-031.8`) | **Test Spec ID**: `TST-CON-047`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-17

---

## 1. Requirement Specification (`REQ-CON-047`)

* **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
* **Implementation**: `RadarLightQuickSwitchSheet.kt`, `RadarLightButton.kt`, `RadarLightMode.kt`, `RadarLightController.kt`, `EditDeviceDialog.kt`

### 1.1 Functional & Architectural Requirements
1. **Compact Non-Scrolling Sheet (`REQ-CON-047.1`)**: `RadarLightQuickSwitchSheet` / `RadarLightQuickSwitchSheetContent` SHALL display `Automatik`, all available light modes, and all 4 audio snooze options (`SNOOZE_15M`, `SNOOZE_30M`, `FAST_ONLY`, `REST_OF_RIDE`, plus `RadarSnooze_Resume` when active) in a compact layout fitting standard phone viewports without vertical clipping, preserving all `RadarLightMode_*` / `RadarSnooze_*` testTags and Light/Dark/StVZO `@Preview`s.
2. **Button-Matching Semantic Icons (`REQ-CON-047.2`)**: `RadarLightQuickSwitchSheet` SHALL render the shared `resolveLightVisuals` vector icons and colors (`RadarLightButton.kt`: `FlashlightOff`, `Groups`, `FlashlightOn`, `NightlightRound`, `WbSunny`) instead of `10.dp` `CircleShape` dots.
3. **Unified Ascending-Intensity Mode Order (`REQ-CON-047.3`)**: `RadarLightMode.getAvailableModes()`, `RadarLightController.cycleNextMode()`, `RadarLightQuickSwitchSheet`, and `EditDeviceDialog` SHALL share the identical ascending order:
   - Non-StVZO: `Automatik -> OFF (0 lm) -> PELOTON (~8 lm) -> SOLID (~25 lm) -> NIGHT_FLASH (~40 lm) -> DAY_FLASH (100 lm) (-> Automatik)`.
   - StVZO (`isStvzo == true`): `Automatik -> OFF -> SOLID (-> Automatik)`.
4. **Zero Hardcoded Strings (`REQ-CON-047.4`)**: Reuse existing 9-locale `strings_radar.xml` resources exclusively (zero new or hardcoded UI strings).

### 1.2 Acceptance Criteria (Given-When-Then)
* **Given** `RadarLightQuickSwitchSheetContent` with `isLightControlSupported = true`,
  * **When** rendered on a smartphone viewport (`~360–412 dp` width) in Light and Dark themes,
  * **Then** `RadarLightMode_AUTO`, all `RadarLightMode_*` options, and all 4 `RadarSnooze_*` options fit cleanly without scroll clipping, and each light mode renders its `resolveLightVisuals` vector icon.
* **Given** non-StVZO or StVZO hardware,
  * **When** comparing `RadarLightMode.getAvailableModes()`, `RadarLightController.cycleNextMode()`, `RadarLightQuickSwitchSheet`, and `EditDeviceDialog`,
  * **Then** all 4 surfaces follow `Automatik -> OFF -> PELOTON -> SOLID -> NIGHT_FLASH -> DAY_FLASH` (StVZO: `Automatik -> OFF -> SOLID`).

### 1.3 System Invariants
`Automatik` always first; StVZO pruning strictly removes flashing modes; `RadarLightMode.commandByte` and `TuningPreferencesDataStore` `.name` persistence unchanged; ~400 ms `cycleNextMode()` debounce unchanged; zero SQLite schema changes.

---

## 2. Test Specification (`TST-CON-047`)

* **`TST-CON-047.1` (`RadarLightQuickSwitchSheetTest.kt`)**: Verify compact rendering (`RadarLightMode_AUTO`, all 5 `RadarLightMode_*`, all 4 `RadarSnooze_*` visible without clipping in bounded viewport) and `resolveLightVisuals` vector icon parity with `RadarLightButton`.
* **`TST-CON-047.2` (`RadarLightControllerTest.kt`, `RadarLightQuickSwitchSheetTest.kt`)**: Verify `RadarLightMode.getAvailableModes(false/true)` and `RadarLightController.cycleNextMode()` match ascending intensity order (`AUTO -> OFF -> PELOTON -> SOLID -> NIGHT_FLASH -> DAY_FLASH -> AUTO`; StVZO `AUTO -> OFF -> SOLID -> AUTO`), and verify sheet/dialog order parity.
* **`TST-CON-047.3` (`RadarLightQuickSwitchSheetTest.kt`, `TranslationParityTest.kt`)**: Verify zero hardcoded strings and 9-locale parity across `strings_radar.xml`.
* **`TST-CON-047.4` (Stage 5 Emulator / Preview Visual Verification)**: Capture Light & Dark screenshots of `RadarLightQuickSwitchSheet` on emulator (`Rule 8.5`) confirming zero clipping, crisp icon parity, and Light/Dark contrast.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-047.1` | UI (Robolectric) | `RadarLightQuickSwitchSheetTest`: compact non-clipping layout + `resolveLightVisuals` icons | `REQ-CON-047.1`, `REQ-CON-047.2` | Planned |
| `TST-CON-047.2` | Unit + UI | `RadarLightControllerTest`, `RadarLightQuickSwitchSheetTest`: ascending order parity | `REQ-CON-047.3`, `REQ-CON-031.8` | Planned |
| `TST-CON-047.3` | Contract | `RadarLightQuickSwitchSheetTest`, `TranslationParityTest`: existing 9-locale resources | `REQ-CON-047.4` | Planned |
| `TST-CON-047.4` | Emulator Visual | Light & Dark `RadarLightQuickSwitchSheet` screenshots (`Rule 8.5`) | `REQ-CON-047.1`, `REQ-CON-047.2` | Planned |
