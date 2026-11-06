# Stage 1 Analysis: ATT-5490 - Supporter to Free Downgrade: Preselect Google Fused & Active Remote Sensor Instead of Barometer

**Ticket**: [ATT-5490](https://rainerblind.atlassian.net/browse/ATT-5490)  
**Parent Epic**: None | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5490`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
When an athlete returns to Free tier with $> 2$ paired sensors, `KeepTwoSensorsBottomSheet` (via `SensorQuotaReconciliationManager.determineDefaultKeepDeviceIds`) currently preselects raw `SPEED_AND_LOCATION_GPS` and `ALTITUDE_FROM_PRESSURE`. This has two significant UX shortcomings:
1. Preselecting internal Barometer over the athlete's external BLE/ANT+ sensors (HRM, Power, Speed) discards their primary training telemetry and contradicts `determineRecommendedUnpairDeviceId` (which recommends unpairing Barometer first).
2. Hardcoding GPS ignores the athlete's preferred `SPEED_AND_LOCATION_GOOGLE_FUSED` sensor (which `ATT-5491` established as the primary location provider).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: `determineDefaultKeepDeviceIds` in `SensorQuotaReconciliationManager.kt` (L114–127) hardcodes a search for `SPEED_AND_LOCATION_GPS` followed by `ALTITUDE_FROM_PRESSURE`. External sensors (`protocol != Protocol.SMARTPHONE`) are only considered in a blind fallback loop if GPS or Barometer is missing.
* **Independent Peer Review & Verification**:
  * Confirmed in `SensorQuotaReconciliationManager.kt`: L116 searches only for `SPEED_AND_LOCATION_GPS`; L118 unconditionally adds `ALTITUDE_FROM_PRESSURE` before evaluating any remote sensors.
  * Confirmed in `DeviceListViewModel.kt`: `determineDefaultKeepDeviceIds` delegates to `reconciliationManager`, while `determineRecommendedUnpairDeviceId` (L251–257) only checks `ALTITUDE_FROM_PRESSURE` and `SPEED_AND_LOCATION_GPS` without recognizing `SPEED_AND_LOCATION_GOOGLE_FUSED`.
  * Verified `lastSeen` format: formatted via `DateFormat.getDateTimeInstance()` in `DevicesDatabaseManager.getLastActiveString()`. A multi-pattern parser with fallback to original list order ensures robust sorting.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * Update `determineDefaultKeepDeviceIds` in `SensorQuotaReconciliationManager.kt`:
    * Slot 1: If both Google Fused and GPS are paired, keep Google Fused. If only one is paired, keep that single location sensor.
    * Slot 2: Preselect the most recently active remote sensor (`protocol != Protocol.SMARTPHONE`), prioritized by `isConnected == true` first, most recent `lastSeen` timestamp second, and original order third.
    * Fallback: If $< 2$ sensors selected, fall back to `ALTITUDE_FROM_PRESSURE` and remaining paired devices.
  * Update `determineRecommendedUnpairDeviceId`: Recommend `ALTITUDE_FROM_PRESSURE` first; if both Google Fused and GPS are paired, recommend `SPEED_AND_LOCATION_GPS` next.
  * Update living requirements (`REQ-SET-084.2`, `REQ-SET-084.5`) and test suites (`SensorQuotaReconciliationManagerTest`, `DeviceListViewModelQuotaTest`).
* **Out-of-Scope Non-Goals**:
  * Modifying `KeepTwoSensorsBottomSheet.kt` Compose UI or dialog presentation logic.
  * Altering database schema or `reconcilePairedDevices` persistence logic.
* **Complexity Rating**: **1 SP (Trivial)** (Refined selection heuristic and unit test updates).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-SET-084` (`REQ-SET-084.2`, `REQ-SET-084.5`) targeting `SensorQuotaReconciliationManager.kt` and `DeviceListViewModel.kt`.
* **Historical Origin & Commit Trace**: `ATT-51` (Initial Free-tier quota), `ATT-5469` (`29251f2d`, global reconciliation manager).
* **Root Reason for Existing Formulation**: GPS and Barometer were historically assumed to be the safe baseline smartphone sensors before external remote sensor activity tracking and Google Fused prioritization (`ATT-5491`) were integrated.
* **Preservation of Core Invariants**:
  1. Quota invariant: strictly at most 2 sensors kept on Free tier (`MAX_FREE_PAIRED_DEVICES = 2`).
  2. Non-destructive unpairing: unselected sensors remain in SQLite with custom names and calibration factors preserved.
  3. Safe fallback: Barometer remains preselected when no remote sensors are paired.

---

## 5. Architectural Strategy & High-Level Solution
* **SensorQuotaReconciliationManager.kt**:
  * Refactor `determineDefaultKeepDeviceIds` to evaluate location sensor preference (Google Fused over GPS when both paired) and select the top remote sensor using a stable comparator (`isConnected` desc, parsed `lastSeen` timestamp desc).
  * Centralize `determineRecommendedUnpairDeviceId` inside `SensorQuotaReconciliationManager` with updated dual-location logic, delegating from `DeviceListViewModel.kt`.
* **Timestamp Utility**: Implement safe timestamp parser handling standard `DateFormat.getDateTimeInstance()` and ISO patterns without crashing.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression in Free quota enforcement; active workout tracking suppression (`!isTracking()`) strictly preserved; non-destructive unpairing intact.
* **Risk Rating**: **LOW** (Pure algorithmic selection improvement; zero DB schema or threading changes).
