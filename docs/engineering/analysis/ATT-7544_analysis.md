# Stage 1 Analysis: ATT-7544 - Add Bike Radar Sensor Status Icon and Source Dialog to Cockpit Header

**Ticket**: [ATT-7544](https://rainerblind.atlassian.net/browse/ATT-7544)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7544` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
The Cockpit header (`SensorStatus`) renders availability icons for 8 core sensors (`TIME_ACTIVE`, `ACCURACY`, `ALTITUDE`, `DISTANCE_m`, `SPEED_mps`, `CADENCE`, `HR`, `POWER`), displaying active (`1.0f`) vs. inactive (`0.38f`) alpha and opening `SensorSourceDialog` on click. With Cycling Rearview Radar (`DeviceType.BIKE_RADAR`) now integrated, Cockpit lacks a dedicated radar status indicator and source inspection dialog.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Missing Sensor Mapping & Vector Asset)**: `SensorType.getIconResId()` defaults to `R.drawable.ic_cross` for radar metrics. No standalone `24dp` `R.drawable.ic_radar` vector exists. In `bt_bike_radar.xml:152` (ATT-7518), the top-left mini-icon suffers from double-circle ("snowman") chord math and pinched crescent arcs; `ic_radar` must be authored with clean, correct geometry.
* **RCA 2 (360 dp Header Geometry & Icon Budget)**: In `SensorStatus.kt` (Portrait), the central container has width `352.dp - 32.dp - 32.dp - 16.dp = 272.dp` (REQ-UI-451). At current `padding(horizontal = 6.dp).size(22.dp)` (`34.dp` per icon), 8 icons consume `272.dp`. Adding a 9th icon without adjustment requires `306.dp` (> 272.dp), causing clipping. Reducing horizontal padding to `4.dp` yields `9 * 30.dp = 270.dp <= 272.dp`, fitting cleanly with 2 dp margin.
* **RCA 3 (Dialog Presentation & Scalar Suppression)**: `SensorSourceDialog` currently displays `sensorType.getFullName(context)` ("Radar Threat Count") and scalar `valueWithUnit`. For radar, the title must be localized "Bike Radar" (`R.string.device_type_bike_radar`), and scalar values must be omitted since radar threat traffic is visual.
* **Independent Peer Review & Verification**:
  * Verified `SensorStatus.kt#L84-95`: `sensorDefinitions` contains 8 entries; lacks `RADAR_THREAT_COUNT`.
  * Verified `SensorStatus.kt#L193-210`: 6.dp padding + 22.dp size = 34.dp. Verified 360.dp container arithmetic (272.dp available).
  * Verified `BANALService.java#L495-510`: `activeSensors` includes `RADAR_THREAT_COUNT` when hardware connects or `RadarTrafficSimulator` runs.
  * Verified `DeviceType.java#L225-232`: `getDeviceTypeList(RADAR_THREAT_COUNT)` already maps to `BIKE_RADAR`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Author clean `24dp` vector drawable `res/drawable/ic_radar.xml` (960x960 viewport, white fill with tint).
  2. Map `RADAR_THREAT_COUNT` (and radar scalar metrics) to `R.drawable.ic_radar` in `SensorType.getIconResId()`.
  3. Add `SensorType.RADAR_THREAT_COUNT` as 9th icon in `SensorStatus.kt` `sensorDefinitions`.
  4. Adjust portrait padding to `4.dp` horizontal (`270.dp` width), guaranteeing responsive fit on 360.dp displays.
  5. In `SensorSourceDialog.kt`, render "Bike Radar" title, list `BIKE_RADAR` devices, and suppress scalar `valueWithUnit`.
  6. Maintain 9-language translation parity for content descriptions and dialog titles.
* **Out-of-Scope Non-Goals**:
  * Modifying `bt_bike_radar.xml` card background silhouette (ATT-7518).
  * Modifying radar telemetry decoders, GATT queue, or `RadarStateRepository`.
  * Overlaying radar threats onto Cockpit header (handled by `RadarEdgeOverlay`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-UI-048` (*Clean Sensor Status Header*), `REQ-UI-049` (*Sensor Source Dialog*), `REQ-UI-451` (*Cockpit Top Bar Centering*).
* **Historical Origin & Commit Trace**: Commit `8baf42c8` (`ATT-5397`, Sprint 2026-45.4) and commit `fb6797a7` (`ATT-5422`, Sprint 2026-45.4).
* **Root Reason for Existing Formulation**: Budgeted 272.dp central box for 8 legacy sensors before radar integration.
* **Preservation of Core Invariants**: Symmetrical 32.dp touch targets and 16.dp edge margins are preserved; central alignment maintained.

---

## 5. Change Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-UI-048`, `REQ-UI-049`, `REQ-UI-451`.
* **Affected Architecture Elements**: `ic_radar.xml`, `SensorType.java`, `SensorStatus.kt`, `SensorSourceDialog.kt`, `strings_radar.xml` (9 locales).
* **Verification Impact & Test Surface**: `SensorStatusTopBarSpacingTest.kt`, `SensorStatusLegibilityTest.kt`, `SensorSourceDialogContractTest.kt`, `TranslationParityTest.kt`.
* **Risk Assessment & Invariants**: LOW risk. UI layout and dialog presentation changes; zero schema or service modifications.

---

## 6. Architectural Strategy & High-Level Solution
1. **Asset**: Create `ic_radar.xml` with mathematically clean concentric arcs and center dot.
2. **SensorType**: Return `R.drawable.ic_radar` in `SensorType.getIconResId()` for radar types.
3. **SensorStatus**: Add `RADAR_THREAT_COUNT` to `sensorDefinitions`; set horizontal padding to `4.dp` in Portrait.
4. **SensorSourceDialog**: Format title as "Bike Radar" and suppress scalar value for `RADAR_THREAT_COUNT`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: 100% regression-free 8 legacy sensor indicators; 360.dp display compatibility; 9-language localization parity.
* **Risk Rating**: **LOW** (Targeted Compose UI addition, localized string reuse, and layout padding adjustment).
