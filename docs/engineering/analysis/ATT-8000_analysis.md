# Stage 1 Analysis: ATT-8000 - Polar Verity Sense Swim Mode Integration

**Ticket**: [ATT-8000](https://rainerblind.atlassian.net/browse/ATT-8000)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.8`  
**Branch**: `feature/ATT-8000`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-15  

---

## 1. Problem Statement & Motivation
In *White Swimming Mode*, Polar Verity Sense records optical 1-Hz HR and gyroscope wall turns as structured Polar Training Sessions (`PolarTrainingSessionApi`) rather than raw streams (`PolarOfflineRecordingApi`). Furthermore, during active Swim Mode, firmware rejects BLE requests with `SYSTEM_BUSY`. Currently, `PolarVeritySenseOfflineController` only reads raw offline recordings (`ATT-7923`/`ATT-7924`) and lacks training session parsing, gyroscope turn extraction, FTU initialization, and localized button-exit `SYSTEM_BUSY` guidance.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* `polar-ble-sdk:8.6.0` implements `PolarTrainingSessionApi` and exposes `FEATURE_POLAR_TRAINING_DATA`, but `PolarVeritySenseOfflineController` only queries `listOfflineRecordings()`.
* **Independent Peer Review & Verification**: Independently verified `PolarBleApi` and protobuf structures in `polar-ble-sdk:8.6.0`. White Swim Mode sessions produce `PbSportIdentifier == 103` (`Pool Swimming`), 1-Hz HR in `PbExerciseSamples.heartRateSamples`, and wall turns in `distanceSamples`. Verified `isFtuDone()` / `doFirstTimeUse()` exists for unprovisioned sensors. Confirmed `SYSTEM_BUSY` occurs when queries run before the user presses the button to cycle back to Blue Sensor Mode.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Extend `PolarVeritySenseOfflineController` with `PolarTrainingSessionApi` (`getTrainingSessionReferences`, `getTrainingSession`, `deleteTrainingSession`).
  * Identify Swim Mode sessions (`PbSportIdentifier == 103`), extract 1-Hz HR and raw turn timestamps decoupled from sensor pool length.
  * Support factory-fresh `doFirstTimeUse()` if `isFtuDone` is false.
  * Catch `SYSTEM_BUSY`, persist pending sync with swim-mode tag, and surface localized button-exit guidance in Aftermath across 9 languages.
* **Out-of-Scope Non-Goals**: No live BLE swim streaming (RF blocked in water); no `WorkoutRr.db` HRV modification; no H10 controller modifications; no pool length EEPROM override.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement only (`REQ-SET-110`). No existing requirements modified.
* **Historical Origin & Commit Trace**: Extends `REQ-SET-108` (`ATT-7923`) and `REQ-SET-109` (`ATT-7924`).
* **Root Reason for Existing Formulation**: `REQ-SET-108`/`109` addressed standard offline HR streams. Swim Mode sessions require structured training session handling.
* **Preservation of Core Invariants**: Retains single-shot arming, GATT arbitration via `PolarOfflineGattCoordinator`, and post-commit flash cleanup.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-SET-110` in `docs/requirements/req_settings_profiles.md`.
* **Affected Architecture Elements**: `PolarVeritySenseOfflineController.kt`, `PolarOfflineGattCoordinator.kt`, `PolarH10RetrySyncBanner.kt`, `strings_devices.xml` (9 locales).
* **Verification Impact & Test Surface**: Net-new `TST-SET-112`; unit tests in `PolarVeritySenseOfflineControllerTest.kt`, UI test in `PolarRetrySyncBannerAndroidTest.kt`.
* **Risk Assessment & Invariants**: Low-to-Medium risk. Isolated to Polar Bluetooth LE offline package and Aftermath banner; zero database migration.

---

## 6. Architectural Strategy & High-Level Solution
* Add `FEATURE_POLAR_TRAINING_DATA` to offline session feature set.
* In `stopAndBackfillWorkout` / `syncPendingWorkout`, scan both raw recordings and training session references. Parse swim session metadata (`PbSportIdentifier == 103`), extracting 1-Hz HR and turn transition timestamps.
* When `SYSTEM_BUSY` is caught, tag pending sync with `verity:swim_busy:` and render localized button guidance: *"Please press the button on your Polar Verity Sense to exit Swim Mode (return to blue LED) and tap Sync Now."*
* Delete matched training session via `deleteTrainingSession` strictly post-commit.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `Devices.db` stays at `DB_VERSION = 13` (zero schema bumps).
  2. Zero synthetic RR/HRV data written to `WorkoutRr.db`.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **MEDIUM** (BLE SDK `PolarTrainingSessionApi`, protobuf parsing, and 9-language UI localization).
