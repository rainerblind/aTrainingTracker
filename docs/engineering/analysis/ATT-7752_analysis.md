# Stage 1 Analysis: ATT-7752 - Equipment-sensor matrix & DB: Track Front/Rear wheel sensor mounting position with selection dialog & dual-sensor user guidance

**Ticket**: [ATT-7752](https://rainerblind.atlassian.net/browse/ATT-7752)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Branch**: `feat/ATT-7752`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
Currently, `EquipmentDbHelper.LINKS` (`EquipmentId`, `ANTDeviceId`) and `EquipmentSensorMatrixScreen.kt` only support a binary checkbox link between bikes and sensors. For multi-speed odometry fusion (ATT-131 / ATT-7751), per-wheel circumference tracking, and GPS-free dual-wheel slow-puncture detection (iTPMS, ATT-7753), the system must track which sensor is mounted on the front wheel (`FRONT`) and which on the rear wheel (`REAR`). Furthermore, athletes lack clear contextual explanation of why 1 or 2 wheel sensors provide significant physical accuracy advantages over standalone smartphone GPS.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Database & Repository Layer**: `EquipmentDbHelper.LINKS` table currently has only two columns (`EquipmentId`, `ANTDeviceId`) at `DB_VERSION = 3`. An upgrade to `DB_VERSION = 4` adding `wheel_position TEXT DEFAULT 'UNSPECIFIED'` is non-destructive, preserving 100% of existing bike-sensor pairings while enabling typed `WheelPosition` (`FRONT`, `REAR`, `UNSPECIFIED`) persistence.
* **Matrix & Dialog UI Layer**: `EquipmentSensorMatrixScreen.kt` renders raw `Checkbox` for all sensor types. Wheel speed sensors (`BIKE_SPEED`, `BIKE_SPEED_AND_CADENCE`) require an interactive Material 3 mounting dialog (Front, Rear, Unspecified, Unlink) and a compact visual indicator (`V`/`H` or `F`/`R`) within `SENSOR_COLUMN_WIDTH` (88 dp, `Rule 84`).
* **Bike Guardrail**: If bike $B$ already links a `FRONT` sensor and the user marks a second sensor as `FRONT`, the system must guard against duplicates by offering a clean swap or reassigning the existing sensor to `REAR`/`UNSPECIFIED`.
* **In-App Guidance & Documentation**: Contextual cards in `EquipmentSensorMatrixScreen.kt` and `EditDeviceDialog.kt` plus `docs/guide/{en,de}/03-equipment-sensor-matrix.md` explain the physical benefits (lag-free speed, canopy/tunnel distance, sharp gradient, and dual-wheel puncture warning) across all 9 supported locales.
* **Independent Hypothesis Verification**: Verified that `DatabaseSchemaRegistry.kt` binds directly to `EquipmentDbHelper.DB_VERSION`, ensuring automatic backup/restore compatibility without manual registry edits.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Extend `EquipmentDbHelper.LINKS` with `wheel_position` via v3 $\rightarrow$ v4 SQLite migration; introduce `WheelPosition` enum.
  2. Implement `EquipmentRepository` and `EquipmentViewModel` APIs to read/write/swap wheel positions and guard against duplicate `FRONT` or `REAR` assignments per bike.
  3. Interactive wheel mounting selection dialog and compact cell indicator (`V`/`H` / `F`/`R`) in `EquipmentSensorMatrixScreen.kt`.
  4. Contextual in-app guidance card/dialog and documentation in `docs/guide/{en,de}/03-equipment-sensor-matrix.md` across 9 languages.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Implementing the iTPMS puncture detection algorithm or longitudinal circumference time series (deferred to `ATT-7753`).
  2. Multi-sensor unicycle EKF speed fusion (implemented in `ATT-131` / `ATT-7751`).
  3. Altering non-wheel sensor linking (cadence, HRM, power, radar remain standard 1-tap checkboxes).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-SEN-029`, extending `REQ-UI-256` (*Equipment-Sensor Matrix*, `ATT-2126`) and `REQ-DAT-031` (*EquipmentDbHelper*, `ATT-4011`).
* **Historical Origin & Commit Trace**: `ATT-2126` (initial matrix implementation), `ATT-2307` (bi-directional sync), and `ATT-7751` (ATT Fused sensor).
* **Root Reason for Existing Formulation**: Original matrix assumed binary association was sufficient when bikes only had one speed sensor.
* **Preservation of Core Invariants**: 100% backward compatibility of existing links (`wheel_position = UNSPECIFIED`), zero impact on non-wheel sensors, SQLite schema integrity, and 100% test pass rate.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-SEN-029` (`docs/requirements/req_tracking_cockpit.md`), `TST-SEN-029` (`docs/tests/tst_tracking_cockpit.md`).
* **Affected Architecture Elements**: `EquipmentDbHelper.java`, `WheelPosition.java`, `EquipmentRepository.kt`, `EquipmentViewModel.kt`, `EquipmentSensorMatrixScreen.kt`, `EditDeviceDialog.kt`, `strings_equipment.xml` (9 locales), `docs/guide/{en,de}/03-equipment-sensor-matrix.md`.
* **Verification Impact & Test Surface**: Real SQLite v3 $\rightarrow$ v4 migration tests, wheel position persistence/swap tests, Compose matrix interaction tests, 9-language translation parity (`TranslationParityTest`).
* **Risk Assessment & Invariants**: Low risk. Non-destructive `ALTER TABLE Links ADD COLUMN`, isolated dialogs, zero impact on active workout tracking pipeline.

---

## 6. Architectural Strategy & High-Level Solution
1. **Schema & Model**: Define `WheelPosition` enum (`FRONT`, `REAR`, `UNSPECIFIED`). In `EquipmentDbHelper.java`, bump `DB_VERSION = 4` and execute `ALTER TABLE Links ADD COLUMN wheel_position TEXT DEFAULT 'UNSPECIFIED'` in `onUpgrade(3, 4)`.
2. **Repository & ViewModel**: Extend `EquipmentRepository` with `getDeviceWheelPositionsForEquipment(equipmentId)` and `setDeviceLink(equipmentId, deviceId, isLinked, wheelPosition)`. Update `EquipmentItem` to carry `linkedDeviceWheelPositions: Map<Long, WheelPosition>`.
3. **UI & Guardrail**: In `EquipmentSensorMatrixScreen.kt`, tapping a wheel speed sensor cell triggers `WheelPositionSelectionDialog`. If the selected position is already occupied on the same bike, prompt to swap or reassign the existing sensor. Render a localized short badge alongside the checkbox.
4. **Guidance**: Add collapsible guidance card in `EquipmentSensorMatrixScreen.kt` and `EditDeviceDialog.kt`, explaining 1 vs. 2 wheel sensors with 100% 9-language parity.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Existing database links remain completely functional with `UNSPECIFIED` position.
  2. Non-wheel sensors (HRM, Cadence, Radar, Power) continue to operate with standard 1-tap checkbox toggles.
  3. Zero text clipping or truncation under German and English locales (`Rule 84`).
* **Risk Rating**: **LOW** — Additive SQLite column, isolated Compose dialogs, zero impact on workout recording engine.
