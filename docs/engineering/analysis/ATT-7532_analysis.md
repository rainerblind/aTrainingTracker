# Stage 1 Analysis: ATT-7532 - Configurable Morning HRV Check on Start Tracking Screen

**Ticket**: [ATT-7532](https://rainerblind.atlassian.net/browse/ATT-7532)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.5`  
**Branch**: `feature/ATT-7532`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
Following `REQ-UI-513`, standalone `drawer_morning_hrv` was removed from the drawer, requiring athletes to navigate into *Fitness & Physiology* $\to$ *Tab 2* to launch their daily Morning HRV check. Athletes performing waking check-ins need a contextual 1-tap entry point on the Start screen (`ControlTrackingScreen`) without cluttering the screen during active tracking, for users without paired HR sensors, after today's reading is already recorded, or past morning cutoff hours.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* `ControlTrackingScreen` renders in `TrackingMode.READY` before workout start. Its `bottomContent` currently hosts route selection and the generic HRV step test button.
* `DevicesDatabaseManager` exposes `hasPairedRemoteDevices()` but lacks a dedicated `hasPairedHeartRateDevices()` query for `DeviceType.HRM`.
* `HrvReadinessDatabaseManager` queries entries by date and posture, but lacks a fast boolean existence check (`hasEntryForDate(dateIso)`) to detect if today's check is completed.
* **Independent Peer Review & Verification**:
  * Verified hypothesis: `ControlTrackingViewModel` seamlessly derives reactive states (`allDevicesFromDb.map { ... }`), seeded synchronously via `hasPairedHeartRateDevices()` from SQLite.
  * Verified cutoff logic: `LocalTime.now() < LocalTime.of(cutoffHour, 0)` with reactive ticker or `ON_RESUME` lifecycle evaluation.
  * Confirmed navigation route: `onNavigateToMorningHrv` passes `navController.navigate(NavRoutes.MORNING_HRV)`.
  * Button placement: In `ControlTrackingScreen.bottomContent` only during `TrackingMode.READY`, above/alongside secondary actions.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Add `showMorningCheckOnStartScreen` (default `true`) and `morningCheckCutoffHour` (default `11`, range 6–15) to `TuningPreferencesDataStore`, `TuningConfig`, `AdvancedTuningScreen`, `AdvancedTuningDialog`, and `PhysiologyModelsSection.kt`.
  * Add `hasPairedHeartRateDevices(): Boolean` to `DevicesDatabaseManager` and `DeviceDataRepository`.
  * Add `hasCompletedMorningCheck(dateIso: String): Boolean` to `HrvReadinessDatabaseManager` and `HrvReadinessRepository`.
  * Add reactive visibility state to `ControlTrackingViewModel` / `TrackingTabsScreen` and render "Morgendlicher Check" button in `ControlTrackingScreen` when in `TrackingMode.READY` and all 4 conditions hold.
  * 9-language localization parity across all `strings_hrv.xml` files.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Altering morning HRV calculation algorithms, SWC baseline corridors, or `MorningHrvCheckScreen` internals.
  * Adding scheduled system alarms, push notifications, or wake locks for morning checks.
  * Modifying SQLite schemas in `devices.db` or `hrv_readiness.db`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* Net-new requirement only (`REQ-UI-525` / `TST-UI-525`). No existing requirements modified.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-UI-525` (*Configurable Morning HRV Check Launch Button on Start Tracking Screen*).
* **Affected Architecture Elements**: `DevicesDatabaseManager.java`, `DeviceDataRepository.kt`, `HrvReadinessDatabaseManager.kt`, `HrvReadinessRepository.kt`, `TuningPreferencesDataStore.kt`, `PhysiologyModelsSection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt`, `ControlTrackingScreen.kt`, `strings_hrv.xml` (all 9 locales).
* **Verification Impact & Test Surface**: `DevicesDatabaseManagerTest`, `HrvReadinessDatabaseManagerTest`, `ControlTrackingViewModelTest`, `TuningConfigTest`, `TranslationParityTest`, and `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Low risk. Isolated UI shortcut; all checks guarded against null/closed databases; zero SQLite migrations; zero side effects on workout tracking.
