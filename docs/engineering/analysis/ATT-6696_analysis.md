# Stage 1 Analysis: ATT-6696 - EquipmentViewModel & Repositories: Replace Leaky DatabaseManager DTOs & Retire Deprecated Params

**Ticket**: [ATT-6696](https://rainerblind.atlassian.net/browse/ATT-6696)  
**Sub-task**: `[Analysis]` (Created on submission)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-46.2`  
**Branch**: `feature/ATT-6696` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
In Phase A (`ATT-432`), presentation layers were decoupled from direct manager singletons, but repositories retained leaky `*DatabaseManager` inner DTO return types (`DevicesDatabaseManager.SimpleSensorInfo`, `DevicesDatabaseManager.NameAndBatteryPercentage`, `SportTypeDatabaseManager.SimpleSportTypeInfo`, `WorkoutSummariesDatabaseManager.Stats`). Furthermore, `EquipmentViewModel.kt` retained 12 constructor parameters (including 3 `@Deprecated` database managers) and 7 `resolve*()` shims to preserve legacy test harnesses, while directly instantiating `EquipmentDbHelper` and `SportTypeEquipmentLinkManager` (`docs/coding_standards.md` §2.5).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
Codebase inspection independently verified all ticket hypotheses:
1. **Leaky DTO Returns**:
   - `DeviceDataRepository.getCriticalBatteryDevices` leaks `DevicesDatabaseManager.NameAndBatteryPercentage` into `MainActivityWithNavigation` and `SensorBatteryNotificationManager`.
   - `DeviceDataRepository.allRemoteSensors` / `getSensorsForSportType` leaks `DevicesDatabaseManager.SimpleSensorInfo` into `EquipmentViewModel`, `EditEquipmentDialog`, `EquipmentSensorMatrixScreen`, and `EquipmentSensorOrdering`.
   - `SportTypesRepository.sportTypesList` / `getSportTypes` leaks `SportTypeDatabaseManager.SimpleSportTypeInfo` into `EquipmentViewModel`, `EditEquipmentDialog`, and `EditWorkoutViewModel`.
   - `WorkoutRepository.getEquipmentStats` / `getEquipmentStatsForPeriod` leaks `WorkoutSummariesDatabaseManager.Stats` into `StatsData.kt` and `EquipmentViewModel`.
2. **`EquipmentViewModel` Shim Clutter**:
   - Constructor carries 3 `@Deprecated` parameters (`dbSportHelper`, `dbDevicesHelper`, `dbSummariesManager`) and 7 `resolve*()` delegation fallbacks.
   - Equipment and link CRUD queries bypass `EquipmentRepository` via direct `dbEquipmentHelper` and `dbLinksHelper` fields.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Introduce strongly-typed domain DTOs (`CriticalBatteryDevice`, `SimpleSensorInfo`, `SimpleSportTypeInfo`, `WorkoutStatsSummary`) in the domain/repository layers.
  * Encapsulate equipment CRUD and sport-to-equipment links inside `EquipmentRepository`.
  * Clean up `EquipmentViewModel.kt` constructor to depend strictly on domain repositories (`EquipmentRepository`, `SportTypesRepository`, `DeviceDataRepository`, `WorkoutRepository`), removing all 3 deprecated parameters and 7 shims.
  * Update `EquipmentViewModel*Test` harnesses to mock domain repositories directly.
* **Out-of-Scope Non-Goals**:
  * Altering SQLite database schemas, indices, or underlying SQL queries.
  * Modifying UI layouts, Compose components, or presentation logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-507` (*Repository-Only UI Database Access Layer*).
* **Historical Origin & Commit Trace**: Commit `d650ce5a` (`ATT-432: Phase A`).
* **Root Reason for Existing Formulation**: Shims and deprecated parameters were introduced as a temporary bridge to prevent breaking 4 existing unit test harnesses during the initial Phase A sweep.
* **Preservation of Core Invariants**: The transitional shims are now obsolete. Replacing them with true repository injection preserves all reactive StateFlows, calculations, and UI contracts with zero behavior drift.

---

## 5. Architectural Strategy & High-Level Solution
* **Domain Layer Purity**: UI and ViewModel components depend exclusively on domain models without importing `*DatabaseManager`.
* **Consolidated Equipment Repository**: `EquipmentRepository` serves as the single source of truth for equipment CRUD and sport-to-equipment associations.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero functional or numerical regression across equipment management and stats screens.
  2. Thread safety on `Dispatchers.IO` preserved for all repository queries.
  3. Clean architecture boundary: UI $\to$ Repository $\to$ DatabaseManager.
* **Risk Rating**: **MEDIUM** (Refactor touches multiple domain DTOs and 4 ViewModel test harnesses; 2 SP).
