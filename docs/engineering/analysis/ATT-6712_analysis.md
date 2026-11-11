# Stage 1 Analysis: ATT-6712 - Wire HealthConnectHrvImporter Opt-In Toggle & Sync, Localize OVERNIGHT_WEARABLE & Paginate HRV Reads

**Ticket**: [ATT-6712](https://rainerblind.atlassian.net/browse/ATT-6712)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6712` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
Sprint `2026-46.1` joint review audit of `ATT-4981` identified three critical defects in Android Health Connect HRV ingestion:
1. **Orphaned Backend & Missing Settings Opt-In Toggle**: `HealthConnectHrvImporter.importRecentHrv()`, `TrainingApplication.setHealthConnectHrvImportEnabled()`, and `HealthConnectManager.HRV_READ_PERMISSIONS` have zero callers in `app/src/main/`. Neither `HealthConnectSettingsDialog` exposes the opt-in toggle/permission flow, nor `PeriodsViewModel` / `MorningHrvCheckViewModel` triggers background sync.
2. **Unlocalized Enum Fallback & Missing Source Provenance Badge**: `HrvReadinessTrendCard.kt:133, 199` falls back to `else -> posture.name`, rendering raw `"OVERNIGHT_WEARABLE"` across all 9 non-English locales (`Design Guidelines §4`), and lacks a visual source provenance badge (*"Health Connect (Oura / Fitbit / ...)"*) from `entry.notes`.
3. **Hard RHR Gate, Missing PageToken Pagination & Mid-Sleep Lockout**: `HealthConnectManager.hasHrvReadPermissions()` requires both `READ_HEART_RATE_VARIABILITY` and `READ_RESTING_HEART_RATE`, blocking HRV import entirely if the user only grants HRV permissions. Furthermore, `client.readRecords` lacks a `pageToken` pagination loop (truncating imports >1,000 records), and deduplication skips any date as soon as a single row exists, locking out later full-night samples when an initial mid-sleep sync occurred and leaving `restingHrBpm = 0`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **HealthConnectSettingsDialog.kt**: Implements only workout export (`syncToHealthConnect()`) with `REQUIRED_PERMISSIONS`, lacking a dedicated toggle and permission launcher for `HRV_READ_PERMISSIONS` and `isHealthConnectHrvImportEnabled()`.
* **HealthConnectManager.kt:133**: `hasHrvReadPermissions()` checks `HRV_READ_PERMISSIONS.all { it in granted }`. `RestingHeartRateRecord` read permission must be optional; only `HeartRateVariabilityRmssdRecord` is mandatory.
* **HealthConnectHrvImporter.kt:137, 151**: Invokes `client.readRecords(request)` once without iterating `pageToken != null`, truncating multi-week backfills with 5-minute nocturnal epochs.
* **HealthConnectHrvImporter.kt:178-181**: Skips existing `dateIso` entries unconditionally; neither `HrvReadinessDatabaseManager` nor `HrvReadinessRepository` provides an `updateEntry()` API to update unpinned nocturnal entries in-place when newer timestamps or non-zero RHR arrive.
* **HrvReadinessTrendCard.kt:130, 195**: Missing `HrvPosture.OVERNIGHT_WEARABLE` branch in `when (posture)`, falling back to raw enum name without localization or source provenance badge.
* **Independent Hypothesis Verification (4-Augen-Prinzip)**: Code and schema forensics confirm that `HrvReadiness.db` (v1) already stores `notes` (e.g. `HealthConnect:<pkg>:<id>`). Updating unpinned rows in-place requires no SQLite schema migrations, strictly preserving Rule 64.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals**:
  * Wire "Import Resting HRV from Health Connect" toggle + `HRV_READ_PERMISSIONS` launcher in `HealthConnectSettingsDialog.kt`, triggering a 60-day initial backfill on enable.
  * Wire 14-day refresh in `PeriodsViewModel.refreshHrvTrend()` and `MorningHrvCheckViewModel.init`.
  * Relax `hasHrvReadPermissions()` to gate strictly on `HeartRateVariabilityRmssdRecord`, treating `RestingHeartRateRecord` as optional.
  * Implement `pageToken` pagination loop in `HealthConnectHrvImporter.kt` for both HRV and RHR queries.
  * Add `updateEntry()` in `HrvReadinessDatabaseManager` and `HrvReadinessRepository`, updating unpinned `OVERNIGHT_WEARABLE` entries in-place when newer nocturnal samples arrive.
  * Localize `morning_hrv_posture_overnight_wearable` across all 9 locales in `strings_hrv.xml`, handle `OVERNIGHT_WEARABLE` in `HrvReadinessTrendCard.kt`, and display the Health Connect provenance badge from `notes`.
  * Keep manual chest-strap posture selection in `MorningHrvCheckScreen.kt` restricted to waking postures (`SUPINE`, `SEATED`, `STANDING`).
* **Out-of-Scope Non-Goals**:
  * Changing `HrvReadiness.db` SQLite schema (v1).
  * Adding Android emulator tests (Emulator is permanently retired; JVM unit/Robolectric tests only).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID**: `REQ-PER-031` (`REQ-PER-031.1`..`031.5`), `REQ-PER-033`, `REQ-SET-103`.
* **Historical Origin**: Sprint `2026-46.1` (`ATT-4981`, commit `04c05e52`).
* **Root Reason for Existing Formulation**: ATT-4981 established initial ingestion math (log-normal aggregation) and posture isolation. Settings UI wiring and pagination were deferred to sprint follow-up; RHR was coupled to HRV read permissions out of convenience.
* **Preservation of Core Invariants**: Waking spot-check baselines (`SUPINE`, `SEATED`, `STANDING`) and nocturnal baselines (`OVERNIGHT_WEARABLE`) remain strictly isolated; manual check remains restricted to waking postures; zero schema changes to `HrvReadiness.db`.

---

## 5. Architectural Strategy & High-Level Solution
1. **HealthConnectManager**: Check `HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class) in granted` in `hasHrvReadPermissions()`.
2. **HealthConnectHrvImporter**: Loop `pageToken` until null. Support in-place updates of unpinned nocturnal records when `maxTs > existing.timestampMs` or `existing.restingHrBpm == 0 && rhr > 0`.
3. **Database & Repository**: Add `updateEntry(entry: HrvReadinessEntry): Boolean` to `HrvReadinessDatabaseManager` and `HrvReadinessRepository`.
4. **Settings Dialog**: Add "Import Resting HRV from Health Connect" switch with `HRV_READ_PERMISSIONS` permission request and 60-day backfill trigger on enable.
5. **ViewModels**: Trigger 14-day refresh in `PeriodsViewModel.refreshHrvTrend()` and `MorningHrvCheckViewModel.init` when enabled.
6. **UI & Localization**: Add `morning_hrv_posture_overnight_wearable` across 9 locales; render localized label and source provenance badge (`"Health Connect (Oura / Fitbit / ...)"`) in `HrvReadinessTrendCard.kt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Strict posture baseline isolation; waking check restricted to waking postures; zero SQLite schema migrations; 100% clean-room test pass rate under `/tmp/att_build_merge.lock`.
* **Risk Rating**: **LOW-MEDIUM**. Additive UI toggle, pagination, and localized strings; isolated repository update logic verified via comprehensive Robolectric/JVM tests.
