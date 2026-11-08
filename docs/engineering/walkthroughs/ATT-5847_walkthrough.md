# Stage 5: Walkthrough & Verification - ATT-5847: Equipment & Sensors UI: Deduplicate identical Strava gear names, localize ISO dates & frame types ('Road'), fix disconnected green dot & capitalize 'samsung'/'google'

**Ticket**: [ATT-5847](https://rainerblind.atlassian.net/browse/ATT-5847)  
**Sub-task**: `[Test]`  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-UI-492` (`REQ-UI-492.1`–`REQ-UI-492.4`)  
**Test Mapping**: `TST-UI-492` (`TST-UI-492.1`–`TST-UI-492.6`)  
**Branch**: `feature/ATT-5847`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Executive Summary & Verification Overview
Resolved 6 UI / UX defects across Equipment and Sensor dialogs:
1. **Strava Gear Deduplication**: In `EquipmentTabsScreen.kt`, when `stravaName == name`, an inline `StravaOriginalLogo` is displayed alongside the equipment title, omitting the redundant subtitle line.
2. **Date & Frame Type Localization**: Usage dates in `UsageItem` are localized via `DateTimeUtils.formatIsoDate()`. Bike frame types (MTB, Cross, Road, Time Trial) are resolved through string resources (`equipment_bike_type_*`) across all 9 supported locales.
3. **Spelling & Placeholder Parity**: Corrected German spelling `Rad Typ` $\rightarrow$ `Radtyp` and harmonized the sensor equipment placeholder to "Keine" / "None".
4. **Sensor Dialog Enhancements**: Disconnected sensor LED indicator is now gray (`ThemeNeutralModerate`), manufacturer names (e.g., "samsung", "google") are capitalized, and the equipment selector is styled as an `OutlinedTextField`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-492.1` | `TST-UI-492.1` | Contract Test (`testStravaGearNameDeduplicationContract`) | **PASSED** | `Verified` |
| `REQ-UI-492.2` | `TST-UI-492.2` | Contract Test (`testUsageDateFormattingAndFrameTypeLocalizationContract`) | **PASSED** | `Verified` |
| `REQ-UI-492.3` | `TST-UI-492.3` | Contract Test (`testGermanSpellingAndPlaceholderHarmonizationContract`) | **PASSED** | `Verified` |
| `REQ-UI-492.4` | `TST-UI-492.4` | Contract Test (`testSensorDetailStatusLedAndManufacturerCapitalizationContract`) | **PASSED** | `Verified` |
| `REQ-LOC-001` | `TST-UI-492.5` | Localization Parity (`TranslationParityTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-492.6` | Tier 1 Targeted Regression (`ui.equipment.*`, `devices.*`, `common.*`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
* **Command**: `./gradlew assembleDebug testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.equipment.*" --tests "com.atrainingtracker.trainingtracker.ui.devices.*" --tests "com.atrainingtracker.trainingtracker.ui.common.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "*TranslationParityTest*" --max-workers=2 && python3 tools/verify_requirement_governance.py`
* **Output**:
  ```text
  BUILD SUCCESSFUL in 58s (clean) / 2s (up-to-date)
  50 actionable tasks: 50 up-to-date
  PASS: Living documentation master tables match all 9 domain shards with 100% parity.
  Net-new requirement(s) detected: REQ-UI-492. Bypassing archaeology check cleanly.
  ```

---

## 4. Hardware / Physical Verification (Pixel 10)
* **On-Device Status**: No physical USB test device attached (`adb devices` reported no active targets). Visual on-device verification across Equipment Tabs, Edit Equipment Dialog, and Sensor Details is deferred to Ceremony 2 joint review.
* **UI Invariant Audit**: Verified Compose layout tree structure, color tokens (`ThemeNeutralModerate`), string resource lookups across EN/DE/ES/FR/IT/JA/NL/PL/PT, and capitalized manufacturer names via pure contract tests.

---

## 5. Invariant & Governance Verification
1. **Design System Colors**: Replaced hardcoded `Color.Green` in `EditDeviceDialog` with semantic `ThemeSuccessDefault` / `ThemeNeutralModerate` matching `DeviceStatusRow`.
2. **Localization Parity**: 100% translation key coverage across all 9 languages in `values*/strings.xml`.
3. **Living Documentation Parity**: `REQ-UI-492` and `TST-UI-492` marked `[Verified]` in `req_ui_design_system.md` and `tst_ui_design_system.md`; 100% parity verified in `docs/requirements.md` and `docs/tests.md`.
4. **Governance Verification**: `tools/verify_requirement_governance.py` confirmed 0 errors.
