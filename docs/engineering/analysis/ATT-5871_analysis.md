# Stage 1 Analysis: ATT-5871 - Disambiguate 'Einheiten' ('Maßeinheiten'), Localize 'Imperial (mile)' & Distinct Power Icon

**Ticket**: [ATT-5871](https://rainerblind.atlassian.net/browse/ATT-5871)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-5909](https://rainerblind.atlassian.net/browse/ATT-5909) (*Localization & Formatting Parity (i18n)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5871`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
During UI inspection of the Navigation Drawer, Units dialog, and Activity Type selector, three defects were identified:
1. **Ambiguous German Drawer Label (`Einheiten`)**: In German, both `tab_workouts` (*Training -> Einheiten*) and `prefsUnitsTitle` (*Einstellungen -> Einheiten*) use `"Einheiten"`, causing drawer navigation ambiguity.
2. **Untranslated `'Imperial (mile)'` in German Units Dialog**: `values-de/strings.xml:105` renders `"Imperial (mile)"` with untranslated English unit `"mile"` instead of localized `"Imperial (Meilen)"`.
3. **Identical Cycling Icon in `Aktivitätstyp wählen`**: In `ActivityTypeSelectionDialog.kt` and `CopyTrackingTabDialog.kt`, `ActivityType.BIKE_SPEED_AND_CADENCE` and `ActivityType.BIKE_POWER` both reference `R.drawable.bsport_bike`, rendering them visually indistinguishable.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**:
  * `prefsUnitsTitle` was translated literally as `"Einheiten"` without considering collision with workout sessions (`tab_workouts`).
  * `units_type_imperial` retained the English string literal `"(mile)"` during German localization pass.
  * `ActivityType.java:39` initialized `BIKE_POWER` with `R.drawable.bsport_bike` as a legacy shortcut before power-specific vector assets (`bt_bike_pwr.xml`, `ic_power.xml`) were introduced.
* **Independent Peer Review & Verification**:
  * Live audit confirms `AppNavigationDrawer.kt:238` binds `R.string.prefsUnitsTitle` and L195 binds `R.string.tab_workouts`. In `values-de/strings.xml`, both resolve to `"Einheiten"`.
  * `UnitsSettingsDialog.kt:46` binds `R.string.prefsUnitsTitle` as bottom sheet header and renders `MyUnits.values()` mapped to `units_type_metric` and `units_type_imperial`.
  * Visual inspection of attachments `round4_01_einheiten_units.png` and `settings_01_tracking_tabs.png` via `read_file` confirms the untranslated `"Imperial (mile)"` radio option and identical bike icons for `Radeln` and `Rad Leistung`.
  * Code audit confirms `ActivityType.BIKE_POWER` enum name is stored as SQLite key in `TrackingViewsDatabaseManager.java`, but `logoId` is purely presentation-tier and safe to update.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail
* **In-Scope Goals**:
  1. Update `prefsUnitsTitle` in `values-de/strings.xml` to `"Maßeinheiten"` (disambiguating drawer and dialog header).
  2. Localize `units_type_imperial` in `values-de/strings.xml` to `"Imperial (Meilen)"`.
  3. Author `bsport_bike_power.xml` vector drawable combining authentic `bsport_bike` cyclist geometry with canonical `ic_power` lightning bolt badge in the top quadrant, and update `ActivityType.BIKE_POWER` `logoId` to `R.drawable.bsport_bike_power`.
  4. Update contract/parity tests (`TranslationParityTest`, `VectorAssetResolutionContractTest`, `ComposeSafePainterContractTest`).
* **Out-of-Scope Non-Goals**:
  * Changing `ActivityType.BIKE_POWER.name()` or database schema (`TrackingViewsDbHelper`).
  * Modifying `BSportType.BIKE` definition or sport categories.
* **Complexity Rating**: **1 SP (Trivial / Standard)**. Pure resource localization and vector asset disambiguation.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Amends `REQ-UI-122` (*9-Language Localization & Translation Parity*) and `REQ-UI-149` (*Activity Type Selection Dialog*); establishes `REQ-UI-487` (*Navigation Drawer Units Disambiguation & Distinct Power Cockpit Iconography*).
* **Historical Origin & Commit Trace**: Commit `2ef1bbdf` (navigation drawer migration) and commit `0e17d2a5` (Compose settings sheets).
* **Root Reason for Existing Formulation**: Literal 1:1 dictionary translation of "Units" -> "Einheiten" created an accidental homonym with training workout sessions; `BIKE_POWER` reused `bsport_bike` because no dedicated multi-color sport vector existed at creation time.
* **Preservation of Core Invariants**: `SP_UNITS` preference keys (`METRIC`/`IMPERIAL`) and `ActivityType` enum names in SQLite tracking views remain 100% byte-for-byte identical.

---

## 5. Architectural Strategy & High-Level Solution
* **Localization Updates (`values-de/strings.xml`)**:
  - `prefsUnitsTitle`: Change `"Einheiten"` -> `"Maßeinheiten"`.
  - `units_type_imperial`: Change `"Imperial (mile)"` -> `"Imperial (Meilen)"`.
* **Distinct Power Vector Asset (`app/src/main/res/drawable/bsport_bike_power.xml`)**:
  - Analytical vector asset matching `bsport_bike.xml` viewport (`192x192`, `48dp`), embedding canonical `ic_power` lightning bolt in the top-left quadrant (using `#FFA000` / `#FFC107` amber fill with dark outline or `#FF9800`, mirroring `bt_bike_pwr.xml` REQ-UI-442 pattern).
* **Model Wiring (`ActivityType.java`)**:
  - Set `BIKE_POWER(BSportType.BIKE, R.string.activity_type_bike_power, R.drawable.bsport_bike_power)`.
* **Testing & Verification**:
  - Update `VectorAssetResolutionContractTest.kt` to assert `bsport_bike_power` resolution and layer integrity.
  - Run `TranslationParityTest` and `ThemeColorCentralizationAuditTest`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. SQLite table contracts (`TrackingViewsDatabaseManager`) and preferences (`SP_UNITS`) remain unaltered.
  2. `BSportType.BIKE` sport mapping and sensor priorities remain strictly intact.
  3. Drawer routing (`NavRoutes.fromDrawerItemId(R.id.drawer_units)`) is 100% preserved.
* **Risk Rating**: **LOW** (Presentation-tier resource adjustments with zero data migration or concurrency impact).
