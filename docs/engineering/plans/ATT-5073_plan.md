# Stage 3: Implementation Plan - ATT-5073: Specialized Altitude Devices Naming & Explanations

**Ticket**: [ATT-5073](https://rainerblind.atlassian.net/browse/ATT-5073)  
**Sub-task**: [[ATT-5166]](https://rainerblind.atlassian.net/browse/ATT-5166) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-SEN-026` (*Specialized Altitude Devices Naming Parity & Explanations*)  
**Test Mapping**: `TST-SEN-026`  
**Branch**: `feature/ATT-5073`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
In ATT-4859, `ALTITUDE_FROM_POSITION` was introduced as "Topografische Höhe (DEM)". However, `ALTITUDE_FROM_PRESSURE` remained labeled simply "Luftdruck" ("Air Pressure"), creating terminology asymmetry. In addition, `EditDeviceDialog.kt` provides no educational explanation for either specialized altitude device regarding their measurement principles, calibration needs, and trade-offs.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SEN-026` (*Specialized Altitude Devices Naming Parity & Explanations*), Sections 1–4
* **Test Mapping**: `TST-SEN-026` (`TST-SEN-026.1`, `TST-SEN-026.2`, `TST-SEN-026.3`, `TST-SEN-026.4`, `TST-SEN-026.5`)

---

## 3. System Invariants & Preserved Behavior
1. **Preservation of User Renames**: Custom device names in `Devices.db` MUST NOT be overwritten; only legacy default names are migrated.
2. **Sensor Math & Delivery Invariants**: Barometric formula, DEM queries, and calibration dispatch remain 100% intact.
3. **Strict 9-Language Parity**: All new and modified string resources maintain 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
4. **Theme & Component Harmony**: Explanatory card complies with M3 tokens (`RoundedCornerShape(12.dp)`, `outlineVariant`, `bodySmall`).

---

## 4. Proposed Architectural Changes

### Component 1: 9-Language String Resources (`strings_devices.xml`, `strings.xml`)
* Rename `devices_altitude_from_pressure` in all 9 locales ("Höhe aus Luftdruck" in DE, "Altitude from Pressure" in EN, etc.).
* Add 5 new localized string resources for altitude device explanations: title, section headers, and "How it works" + "Pros & Cons" texts for both pressure and position altitude sensors.

### Component 2: Idempotent Database Migration (`DevicesDatabaseManager.java`)
* Bump `DB_VERSION` to 9 in `DevicesDatabaseManager.java`.
* Implement `migrateAltitudeFromPressureLegacyNames(SQLiteDatabase db)`: queries `DEVICE_TYPE = 'ALTITUDE_FROM_PRESSURE'` and updates rows whose `NAME` equals any legacy default name ("Luftdruck", "Air Pressure", etc.) to the updated localized string resource.

### Component 3: Compose Educational Info Card (`EditDeviceDialog.kt`)
* Implement `AltitudeExplanationSection(deviceType: DeviceType)` in `EditDeviceDialog.kt`.
* Render structured `OutlinedCard` when `data.deviceType` is `ALTITUDE_FROM_PRESSURE` or `ALTITUDE_FROM_POSITION`.

### UI Consistency (Rule 23)
* **Reference screen**: `EditDeviceDialog.kt` / `ZoneHistoryDialog.kt` (`OutlinedCard` patterns).
* **Reused tokens**: `MaterialTheme.colorScheme.surfaceVariant`, `outlineVariant`, `bodySmall`, `titleSmall`, `Icons.Default.Info`.
* **New styles**: None; standard M3 card with token-based colors and typography.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Language Localization Updates
* Files: `app/src/main/res/values*/strings_devices.xml`, `app/src/main/res/values*/strings.xml` (all 9 locales)
* Rename `devices_altitude_from_pressure` and add explanation keys across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Step 2: Idempotent Devices.db Migration
* Files: `DevicesDatabaseManager.java`
* Bump `DB_VERSION = 9`. Add `migrateAltitudeFromPressureLegacyNames()` to update legacy default names.

### Step 3: Compose Altitude Explanation Card
* Files: `EditDeviceDialog.kt`
* Add `AltitudeExplanationSection` and conditionally render in `EditDeviceDialog`.

### Step 4: Unit & Localization Parity Tests
* Files: `SpecializedAltitudeDevicesLocalizationTest.kt`, `DevicesDatabaseManagerTest.kt`, `EditDeviceViewModelTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.database.DevicesDatabaseManagerTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted tests (`DevicesDatabaseManagerTest`, `TranslationParityTest`, `EditDeviceViewModelTest`) + `verify_requirement_governance.py`.
* **Rollback**: Clean git branch isolation (`feature/ATT-5073`); revertable via branch drop.
