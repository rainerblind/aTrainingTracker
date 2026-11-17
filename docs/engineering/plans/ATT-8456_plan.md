# Stage 3: Implementation Plan - ATT-8456: Compact `RadarLightQuickSwitchSheet` & Ascending Mode Order

**Ticket**: [ATT-8456](https://rainerblind.atlassian.net/browse/ATT-8456) | **Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8456` | **Risk**: `LOW-MEDIUM` (Visual/UI)  
**Requirement Mapping**: `REQ-CON-047` (`REQ-CON-047.1`..`REQ-CON-047.4`), `REQ-CON-031.3`, `REQ-CON-031.8`  
**Test Mapping**: `TST-CON-047` (`TST-CON-047.1`..`TST-CON-047.4`), `TST-CON-031.9`

---

## 1. Architecture, UI Consistency & Traceability
* **Boundaries**: `RadarLightMode.kt` defines canonical ascending intensity order (`OFF, PELOTON, SOLID, NIGHT_FLASH, DAY_FLASH`); `RadarLightController.cycleNextMode()` derives steps from `RadarLightMode.getAvailableModes(_isStvzo.value)`; `RadarLightButton.kt` exposes `internal fun resolveLightVisuals(RadarLightMode)` shared with `RadarLightQuickSwitchSheet.kt` (zero duplicated icon maps); `EditDeviceDialog.kt:1126` consumes `getAvailableModes(isStvzo)` directly (StVZO `[Automatik, Aus, Dauerlicht]` unchanged; non-StVZO adopts ascending order automatically, preserving `isAutoMode` vs explicit mode separation).
* **UI Consistency (`docs/design_guidelines.md` §1.1, §5.4, §5.5, §5.8, §5.14, §5.20)**: Avoids `FilterChip` for exclusive mode selection and avoids uneven `FlowRow`. Uses deterministic 2-column selectable `Surface` tiles (`Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min))`, `Modifier.weight(1f).fillMaxHeight()`) inside `AppModalBottomSheet` (no nested `verticalScroll`), `MaterialTheme.colorScheme.outline` inactive borders, `TTColor` / `resolveLightVisuals` vector icons, and existing 9-locale `strings_radar.xml` labels verified for German width budgets (`360–412 dp`). Zero SQLite or string XML changes.

| Step | Requirement | Test Case | Target Files |
| :--- | :--- | :--- | :--- |
| 1 | `REQ-CON-047.3`, `REQ-CON-031.8` | `TST-CON-047.2`, `TST-CON-031.9` | `RadarLightMode.kt`, `RadarLightController.kt`, `RadarLightControllerTest.kt` |
| 2 | `REQ-CON-047.2` | `TST-CON-047.1` | `RadarLightButton.kt`, `RadarLightButtonContractTest.kt` |
| 3 | `REQ-CON-047.1`, `REQ-CON-047.2`, `REQ-CON-047.4` | `TST-CON-047.1`..`TST-CON-047.3` | `RadarLightQuickSwitchSheet.kt`, `RadarLightQuickSwitchSheetTest.kt` |
| 4 | `REQ-CON-047.1`..`TST-CON-047.4` | `TST-CON-047.4` | Targeted unit + Compose UI tests + Stage 5 Light/Dark `emulator-5554` screenshots |

## 2. Atomic Implementation Steps (Stage 4 Construction)
1. **Step 1 — Ascending Mode Order in `RadarLightMode.kt` & `RadarLightController.kt`**:
   - Reorder `RadarLightMode` enum entries to ascending intensity: `OFF` (0 lm), `PELOTON` (~8 lm), `SOLID` (~25 lm), `NIGHT_FLASH` (~40 lm), `DAY_FLASH` (100 lm), preserving `commandByte`, `titleResId`, `isStvzoCompliant`, and `fromCommandByte()`.
   - Refactor `RadarLightController.cycleNextMode()` to iterate `RadarLightMode.getAvailableModes(_isStvzo.value)` (`AUTO -> OFF -> PELOTON -> SOLID -> NIGHT_FLASH -> DAY_FLASH -> AUTO` non-StVZO; `AUTO -> OFF -> SOLID -> AUTO` StVZO), preserving ~400 ms debounce.
   - Update `RadarLightControllerTest.kt` (`testRadarLightMode_stvzoCapabilityPruning`, `testRadarLightController_debouncedCycleNextMode_andStvzo`, `testRadarLightController_manualOverrideSurvivesIncomingFramesUntilSetAutoMode`).
2. **Step 2 — Share `resolveLightVisuals` in `RadarLightButton.kt`**:
   - Change `resolveLightVisuals(lightMode: RadarLightMode): Pair<ImageVector, Color>` from `private` to `internal` so `RadarLightQuickSwitchSheet.kt` reuses the exact button icons and colors without duplication.
3. **Step 3 — Compact 2-Column Icon Sheet in `RadarLightQuickSwitchSheet.kt` & `RadarLightQuickSwitchSheetTest.kt`**:
   - Replace the 10 tall `RadioButton` rows with compact 2-column selectable `Surface` tiles (`height(IntrinsicSize.Min)` rows):
     - Light tiles: `Automatik` (icon + live `currentMode` subtitle when active) + `availableModes` in ascending order, each showing `resolveLightVisuals(mode)` icon (`20.dp`) instead of `10.dp` `CircleShape` dot, keeping `RadarLightMode_AUTO` / `RadarLightMode_${mode.name}` tags and `MaterialTheme.colorScheme.outline` inactive styling.
     - Audio Snooze tiles: 2×2 grid for `SNOOZE_15M`, `SNOOZE_30M`, `FAST_ONLY`, `REST_OF_RIDE` (plus `RadarSnooze_Resume` button when snoozed), keeping `RadarSnooze_${mode.name}` tags and existing `strings_radar.xml` labels.
   - Keep Light, Dark, and StVZO `@Preview`s updated; expand `RadarLightQuickSwitchSheetTest.kt` to verify bounded-height visibility (`<= 520.dp`), mode order, icon rendering, and `EditDeviceDialog` order parity via `getAvailableModes`.
4. **Step 4 — Verification (`Rule 8.5`)**:
   - Run targeted tests: `./gradlew :app:testDebugUnitTest --tests "*RadarLight*" --tests "*EditDeviceViewModelTest*"` (and `TranslationParityTest`). In Stage 5, capture Light & Dark screenshots on `emulator-5554` and inspect via `read_file`.

## 3. System Invariants & Rollback
* **Invariants**: `Automatik` first; StVZO pruning excludes flashing modes; `.name` DataStore persistence & `commandByte` wire payloads unchanged; `EditDeviceDialog` StVZO segmented row unchanged; ~400 ms debounce intact; `ANT-Android-SDKs` untouched.
* **Rollback**: Single git revert on `feature/ATT-8456` prior to squash-merge.
