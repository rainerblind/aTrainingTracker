# Stage 5: Walkthrough & Verification - ATT-5073: Specialized Altitude Devices Naming & Explanations

**Ticket**: [ATT-5073](https://rainerblind.atlassian.net/browse/ATT-5073)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-SEN-026` (`REQ-SEN-026.1`, `REQ-SEN-026.2`, `REQ-SEN-026.3`, `REQ-SEN-026.4`)  
**Test Mapping**: `TST-SEN-026` (`TST-SEN-026.1`, `TST-SEN-026.2`, `TST-SEN-026.3`, `TST-SEN-026.4`, `TST-SEN-026.5`)  
**Branch**: `feature/ATT-5073`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Executive Summary & Verification Overview
ATT-5073 establishes terminology harmony by renaming the internal barometric sensor (`devices_altitude_from_pressure`) across all 9 supported locales ("Höhe aus Luftdruck" in DE, "Altitude from Pressure" in EN, and equivalents in ES, FR, IT, JA, NL, PL, PT), matching `ALTITUDE_FROM_POSITION` ("Topografische Höhe (DEM)"). `DevicesDatabaseManager` upgrades `Devices.db` to `DB_VERSION = 9` with an idempotent migration (`migrateAltitudeFromPressureLegacyNames`) that updates legacy default names while strictly preserving custom user renames. In `EditDeviceDialog.kt`, an educational `AltitudeExplanationSection` renders a styled Material 3 `OutlinedCard` detailing "How it works" and operational "Pros & Cons" for both `ALTITUDE_FROM_PRESSURE` and `ALTITUDE_FROM_POSITION`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SEN-026.1` | `TST-SEN-026.1` | Unit Test (`SpecializedAltitudeDevicesLocalizationTest.testDevicesAltitudeFromPressure_renamedAcrossAll9Locales`) | **PASSED** | `Verified` |
| `REQ-SEN-026.2` | `TST-SEN-026.2` | Contract Test (`DevicesDatabaseManagerTest.testMigrateAltitudeFromPressureLegacyNames_updatesLegacyDefaultNames`, `testDevicesDbHelper_dbVersionIsNine`) | **PASSED** | `Verified` |
| `REQ-SEN-026.3` | `TST-SEN-026.3` | Compose UI Layout Inspection & Compilation (`EditDeviceDialog.kt`) | **PASSED** | `Verified` |
| `REQ-SEN-026.4` | `TST-SEN-026.4` | Localization Tests (`TranslationParityTest`, `SpecializedAltitudeDevicesLocalizationTest.testAltitudeExplanationKeys_existAndNonEmptyAcrossAll9Locales`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | Tier 1 Targeted | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.database.*" --tests "com.atrainingtracker.trainingtracker.backup.*" --tests "com.atrainingtracker.trainingtracker.localization.*" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"` | **PASSED** (100% Tier 1) | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Suite (Rule 60)
* **Scope**: `Tier 1 targeted: com.atrainingtracker.banalservice.database.*`, `com.atrainingtracker.trainingtracker.backup.*`, `com.atrainingtracker.trainingtracker.localization.*`, `com.atrainingtracker.trainingtracker.build.*`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`
```text
BUILD SUCCESSFUL in 10s
32 actionable tasks: 1 executed, 31 up-to-date
```

### Requirement Governance
```text
Net-new requirement(s) detected: REQ-SEN-026. Bypassing archaeology check cleanly.
```

---

## 4. Hardware / Physical Verification (Pixel 10)
* **ADB Verification**: Deferred to Sprint Review (device offline: `66020DLCR002FL offline`).

---

## 5. Invariant & Governance Verification
1. **Preservation of User Renames**: Stored user-edited device names in `Devices.db` are strictly preserved without modification.
2. **Sensor Delivery & Math**: Barometric formula, Kalman filtering, and DEM querying logic remain 100% intact.
3. **UI Consistency**: `OutlinedCard` follows M3 tokens (`surfaceVariant`, `outlineVariant`, `bodySmall`, `labelMedium`, `primary`) with accessible contrast.
4. **Living Documentation**: `REQ-SEN-026` and `TST-SEN-026` confirmed `[Verified]` in `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
