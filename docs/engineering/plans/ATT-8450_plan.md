# Stage 3: Implementation Plan - ATT-8450: Offline EditDeviceDialog Radar Tail Light Configuration

**Ticket**: [ATT-8450](https://rainerblind.atlassian.net/browse/ATT-8450) | **Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8450` | **Risk**: `LOW-MEDIUM` (UI + State Hydration)  
**Requirement Mapping**: `REQ-CON-048` (`REQ-CON-048.1`..`REQ-CON-048.4`), `REQ-CON-031.5`, `REQ-CON-031.7`, `REQ-CON-047.3`  
**Test Mapping**: `TST-CON-048` (`TST-CON-048.1`..`TST-CON-048.4`), `TST-CON-031.5`

---

## 1. Architecture, UI Consistency & Traceability
* **Boundaries**: `RadarLightController.kt` owns reactive `isAutoMode`, live `currentMode`, persisted `preferredMode`, cold-start `TuningPreferencesDataStore` hydration (`hydrateFromPreferences`), and `_isStvzo` reset on disconnect. `EditDeviceDialog.kt` (`RadarLightControlSection`) remains decoupled from `EditDeviceViewModel` draft state (`saveChanges()` untouched) and delegates selection directly to `RadarLightController`. Zero SQLite migrations; `TrackerService`, `BANALService`, `MyBTLEDevice`, and `ANT-Android-SDKs` untouched.
* **UI Consistency (`docs/design_guidelines.md` §1.1, §2.8, §4, §5.2–5.5, §5.13)**: Offline Multi-Mode retains the existing `Automatik` + `RadioButton` rows in ascending `REQ-CON-047.3` order (`OFF`, `PELOTON`, `SOLID`, `NIGHT_FLASH`, `DAY_FLASH`). StVZO retains `SingleChoiceSegmentedButtonRow` with `SegmentedButton(..., icon = {})`, `maxLines = 1`, `TextOverflow.Ellipsis` (`§1.1`, `§2.8`). Offline hint uses `RoundedCornerShape(8.dp)` neutral `MaterialTheme.colorScheme.surfaceVariant` + `onSurfaceVariant` (no warning/error red, no inline emojis, `§5.3–5.5`). Light & Dark `@Preview`s wrapped in `ATrainingTrackerTheme` (`§4`); 9-locale parity in `strings_radar.xml`.

| Step | Requirement | Test Case | Target Files |
| :--- | :--- | :--- | :--- |
| 1 | `REQ-CON-048.2` | `TST-CON-048.2` | `RadarLightController.kt`, `RadarLightControllerTest.kt` |
| 2 | `REQ-CON-048.4`, `REQ-CON-031.7` | `TST-CON-048.4` | `app/src/main/res/values*/strings_radar.xml` (9 locales) |
| 3 | `REQ-CON-048.1`, `REQ-CON-048.3`, `REQ-CON-048.4` | `TST-CON-048.1`, `TST-CON-048.3` | `EditDeviceDialog.kt`, `EditDeviceDialogRadarLightTest.kt` |
| 4 | `REQ-CON-048.1`..`.4` | `TST-CON-048.1`..`.4` | Targeted unit + Compose UI tests, `TranslationParityTest`, `Rule 8.5` PNG |

---

## 2. Atomic Implementation Steps (Stage 4 Construction)
1. **Step 1 — Cold-Start Hydration, Offline `preferredMode` & StVZO Reset (`RadarLightController.kt`, `RadarLightControllerTest.kt`)**:
   - Expose `_preferredMode` / `val preferredMode: StateFlow<RadarLightMode>` initialized from `TuningPreferencesDefaults.RADAR_LIGHT_DEFAULT_MODE` and kept synced in `updateCachedPreferences()` and `reset()`.
   - Add `@JvmStatic fun hydrateFromPreferences(context: Context? = null)` reading `TuningPreferencesDataStore.getInstance(ctx).getTuningConfig()` when `TuningPreferencesDataStore.hasInitializedInstance()` (or initializing via `ctx` safely) if `!hasLoadedInitialPreferences`, populating `isAutoMode` and `preferredMode`.
   - Reset `_isStvzo.value = false` inside `unregisterRadarDevice(device)` when `activeRadarDevice === device`.
   - Add unit tests in `RadarLightControllerTest.kt` covering offline `setManualLightMode()`/`setAutoMode()`, `hydrateFromPreferences()` before workout start, and `_isStvzo` reset on disconnect.
2. **Step 2 — 9-Locale Offline Hint Resource (`strings_radar.xml`)**:
   - Add `radar_light_offline_hint` across all 9 locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) explaining the selected mode applies on next radar connection (zero inline emojis, no unindexed format errors).
3. **Step 3 — Offline `RadarLightControlSection` & `@Preview`s (`EditDeviceDialog.kt`, `EditDeviceDialogRadarLightTest.kt`)**:
   - Add `internal fun isKnownRadarWithoutLight(deviceName: String?): Boolean` matching `RVR315` / `RVR 315` (case-insensitive).
   - Update `RadarLightControlSection(isConnected, deviceName)` to hydrate via `LaunchedEffect(Unit) { RadarLightController.hydrateFromPreferences(context) }`, and render whenever `isSimulating || (isConnected && isLightControlAvailable) || (!isConnected && !isKnownRadarWithoutLight(deviceName))`.
   - Bind mode selection highlight to `val effectiveMode = if (isConnected || isSimulating) currentMode else preferredMode`, show `R.string.radar_light_active_mode_format` when connected/simulating in `Automatik`, and when `!isConnected && !isSimulating` display the localized offline hint (`testTag("RadarLightOfflineHint")`) without spurious StVZO notices (`isStvzo == false`).
   - Extract testable internal `RadarLightControlSectionContent(...)` (with Light/Dark offline Multi-Mode and StVZO `@Preview`s) and add `EditDeviceDialogRadarLightTest.kt` using `createComposeRule()` + `@GraphicsMode(NATIVE)` with zero `src/main` reads (`REQ-PRO-049.1`).
4. **Step 4 — Verification & `Rule 8.5` Screenshot (`TST-CON-048.1`..`.4`)**:
   - Run targeted Gradle unit + Compose UI + `TranslationParityTest`; capture and inspect `docs/engineering/walkthroughs/img/ATT-8450_radar_offline_edit_device.png` (Light/Dark) via `read_file`.

---

## 3. System Invariants & Rollback
* **Invariants**: Connected `!isLightControlAvailable` and `RVR315` remain hidden; `EditDeviceViewModel.saveChanges()` draft state decoupled; `REQ-CON-047.3` ascending mode order & StVZO pruning/clamping preserved; zero SQLite schema changes; 9-locale parity; `ANT-Android-SDKs` untouched.
* **Rollback**: Single git revert on `feature/ATT-8450` prior to sprint squash-merge.
