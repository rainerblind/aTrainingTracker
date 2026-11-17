# Stage 1 Analysis: ATT-8450 - Offline Radar Tail Light Config in EditDeviceDialog

**Ticket**: [ATT-8450](https://rainerblind.atlassian.net/browse/ATT-8450) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-17_02_Spät` | **Branch**: `feature/ATT-8450`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-17 | **Recommended Estimate**: `2 SP`

---

## 1. Problem Statement & Motivation
PO testing of `ATT-7991` on a physical Pixel 10 with a paired, disconnected Garmin `RearVue8` (`radar_edit_device.png`: gray status dot, `80% (Gestern)`, `Sensordaten: nicht verbunden`) shows the *"Radar-Rücklicht"* section missing entirely from `EditDeviceDialog`. Because `ATT-7991` retired mode controls from Expert Settings (`RadarSettingsSection.kt`), users have zero UI to pre-configure `Automatik` or a default light mode when their radar is offline.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  - `EditDeviceDialog.kt:200-202, 1113-1121`: `RadarLightControlSection(isConnected)` returns early on `if (!isSimulating && (!isConnected || !isLightControlAvailable)) return`. When disconnected, `RadarLightController.isLightControlAvailable` is `false` (`RadarLightController.kt:69, 151`), hiding the entire section.
  - `RadarSettingsSection.kt:397-415` (`c422e1b6`): Retired the 3-way mode selector and dropdown (`REQ-CON-031.5`), leaving only `radarBrakeLightEnabled`.
  - **Side-Effect 1 — Offline Selection Highlight & Cold-Start Hydration (`RadarLightController.kt:61-67, 171-188, 259-326`)**: `EditDeviceDialog.kt:1176, 1254` derives selection from `!isAutoMode && mode == currentMode`. Offline, `setManualLightMode(mode)` persists `radarLightDefaultMode` via `TuningPreferencesDataStore`, but `setLightMode(targetMode)` returns `false` (`device == null`) and leaves `_currentMode` at `RadarLightMode.OFF`, mis-highlighting `OFF`. Moreover, `TuningPreferencesDataStore` (`radarLightAutoModeFlow` / `radarLightDefaultModeFlow`) is only read during workout hooks (`resolveActiveConfig` at `435-463`), not on cold app start before opening `EditDeviceDialog`.
  - **Side-Effect 2 — Radar-Only vs. StVZO Unknown Offline (`BTLERadarDevice.java:394, 725-746`, `DeviceData.kt:57-90`)**: `DeviceUiData` carries `deviceName` and `manufacturer` (no DB `modelNumber`). Connected radar-only units (`isConnected && !isLightControlAvailable`) and identifiable offline radar-only models (`RVR315`) must hide the section; `RearVue8` / StVZO status (`0x2A24`) is unknown until connection (`unregisterRadarDevice` does not reset `_isStvzo`), so offline UI must avoid misleading StVZO error messages while showing an apply-on-next-connect hint.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Keep `RadarLightControlSection` visible & interactive for `DeviceType.BIKE_RADAR` even when `!isConnected && !isSimulating`, excluding connected non-light devices (`isConnected && !isLightControlAvailable`) and known radar-only models (`RVR315`).
  - Hydrate and expose persisted `isAutoMode` + configured default mode (`preferredMode`/`radarLightDefaultMode`) so offline selection persists and highlights accurately before workout/connect.
  - Display a clear 9-locale offline hint explaining the selected mode applies on next connection (without misleading StVZO error text when unknown); support offline + StVZO states cleanly.
  - Add Robolectric Compose UI tests (`EditDeviceDialog` light section offline/online/StVZO/`RVR315`, zero `src/main` reads per `REQ-PRO-049.1`) and `RadarLightController` offline tests.
* **Out-of-Scope Non-Goals**:
  - Restoring mode controls to `RadarSettingsSection.kt` (`Design Guidelines §3.2`).
  - Changing SQLite `DevicesDatabaseManager` schema or `RadarLightQuickSwitchSheet.kt` (in-ride only).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-CON-031.5` (*Unified Preference Persistence & EditDeviceDialog UI Consistency*), `EditDeviceDialog.kt`, `RadarLightController.kt`, `strings_radar.xml`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-7540` (`c1d27049`/`6bfac996` era), amended in `ATT-7991` (`c422e1b6`, `6e9b3c06`) and `ATT-8456` (`REQ-CON-047.3` ascending mode order).
* **Root Reason for Existing Formulation**: `ATT-7540` gated `RadarLightControlSection` on `isConnected && isLightControlAvailable` to prevent dead live-dispatch buttons on `RVR315`/offline devices while `RadarSettingsSection.kt` still held offline mode settings. `ATT-7991` retired Expert Settings controls and added `isSimulating`, leaving disconnected devices without a configuration surface.
* **Preservation of Core Invariants**: Connected radar-only (`isConnected && !isLightControlAvailable`) and `RVR315` still hide light controls; `EditDeviceViewModel` draft state remains decoupled; StVZO pruning (`Automatik`, `OFF`, `SOLID`) and on-connect clamping stay intact; `RadarLightMode` ascending order (`REQ-CON-047.3`) and 9-locale parity (`REQ-CON-031.7`) preserved.

---

## 5. Change Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-CON-031.5`, `REQ-CON-031.7`, `TST-CON-031.5` in `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
* **Affected Architecture Elements**: `EditDeviceDialog.kt`, `RadarLightController.kt`, `app/src/main/res/values*/strings_radar.xml` (9 locales).
* **Verification Impact & Test Surface**: `RadarLightControllerTest.kt`, new Compose UI tests for `EditDeviceDialog` radar section (`app/src/test/.../editdevice/`), `TranslationParityTest.kt` (all complying with `REQ-PRO-049.1`).
* **Risk Assessment & Invariants**: **LOW-TO-MEDIUM**. Offline writes only persist `TuningPreferencesDataStore` (`isAutoMode`, `radarLightDefaultMode`) without touching `BTLERadarDevice` / `ANTBikeLightDevice` GATT/ANT channels or SQLite schema; live mode dispatch continues unchanged when connected/simulating.

---

## 6. Architectural Strategy & System Invariants
- **Visibility Guard**: Show `RadarLightControlSection` for `BIKE_RADAR` whenever `isSimulating || isLightControlAvailable || (!isConnected && !isKnownRadarWithoutLight(deviceName))`.
- **Offline State & Hydration**: Hydrate `RadarLightController` from `TuningPreferencesDataStore` when needed; separate live hardware `currentMode` from persisted/configured `preferredMode` (or expose effective UI selection) so offline clicks update radio/segmented selection and survive app restarts.
- **Offline UX & StVZO**: When `!isConnected && !isSimulating`, show a concise localized hint banner/caption (*applied on next radar connection*); render StVZO notice/pruning strictly when `isStvzo` is actually active.
