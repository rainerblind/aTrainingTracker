# Stage 2 Requirement & Test Specification: ATT-1646

**Ticket**: [ATT-1646](https://atrainingtracker.atlassian.net/browse/ATT-1646)  
**Sub-task**: [ATT-1670](https://atrainingtracker.atlassian.net/browse/ATT-1670) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1646`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Requirement Specification (REQ-SET-074)

### REQ-SET-074: Expert Settings Drawer Positioning, Localization & 50m Default GPS Tolerance
The system SHALL sequester expert-level tuning preferences into a dedicated section at the bottom of the navigation drawer, align terminology to "Expert Settings" across 9 languages, and tighten default GPS accuracy tolerance to 50 meters (ATT-1646):

1. **Dedicated Bottom Drawer Section (`AppNavigationDrawer.kt`)**:
   - In `createDrawerGroups`, the navigation item `R.id.drawer_advanced_tuning` SHALL be removed from `drawer__settings` and placed into a dedicated, final `DrawerGroup` situated at the bottom of the drawer.
   - The dedicated section SHALL use header string resource `R.string.drawer__expert_settings`.
   - The automatic horizontal divider preceding the final group SHALL visually separate expert settings from standard user preferences.
2. **Naming & 100% 9-Language Localization Parity**:
   - The terminology across all dialogs and drawer items SHALL be unified to "Expert Settings" (*Experten-Einstellungen*):
     - `drawer__expert_settings`:
       - DE: *"Experten-Einstellungen"*
       - EN: *"Expert Settings"*
       - ES: *"Ajustes de experto"*
       - FR: *"Paramètres d'expert"*
       - IT: *"Impostazioni per esperti"*
       - JA: *"エキスパート設定"*
       - NL: *"Expertinstellingen"*
       - PL: *"Ustawienia eksperckie"*
       - PT: *"Configurações de especialista"*
     - `advanced_tuning_title`: Updated to match the above in all 9 supported locales (replacing "Erweiterte Abstimmung", "Advanced Tuning", etc.).
     - `advanced_tuning_open`: Updated with matching ellipsis (`…`) across all 9 supported locales.
3. **Default GPS Accuracy Tolerance (`TuningPreferencesDataStore.kt`)**:
   - `TuningPreferencesDefaults.GPS_ACCURACY_THRESHOLD_M` SHALL be standardized to `50.0f` (reduced from `200.0f`).
   - Fresh application installations and factory resets via `resetToDefaults()` SHALL initialize `gpsAccuracyThresholdMeters` to `50.0f`.
4. **Preservation of Core Invariants**:
   - Navigation item ID `R.id.drawer_advanced_tuning` and bottom sheet route mapping `SettingsBottomSheetType.ADVANCED_TUNING` remain identical.
   - DataStore preference keys (`tuning_gps_accuracy_threshold`) and slider clamping bounds ($10.0\text{m} .. 500.0\text{m}$) remain 100% intact.
   - Saved custom athlete preferences are strictly preserved across upgrades.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

1. **Original Requirement ID & Target**: `REQ-SET-073` (*Dedicated Advanced Tuning Preferences Screen with Parameter Clamping and Reset-to-Defaults*).
2. **Historical Origin & Commit Trace**: Ticket `ATT-1304` (Sprint `2026-40.4`).
3. **Root Reason for Existing Formulation**: In `ATT-1304`, the tuning item was grouped inside general settings for speed of implementation, and the GPS default was inherited from legacy constants (`SpeedAndLocationDevice.ACCURACY_THRESHOLD = 200`).
4. **Refinement Reason**: Post-sprint user review determined that German "Erweiterte Abstimmung" was unnatural, mixing expert controls with standard settings created user confusion, and 50m provides significantly cleaner GPS tracks out-of-the-box.
5. **Preservation of Core Invariants**: Safety clamping, factory reset mechanics, non-blocking DataStore persistence, and 9-language translation parity are fully maintained.

---

## 3. Acceptance Criteria (Given-When-Then)

* **AC-1 (Drawer Placement & Grouping)**:
  - *Given* an athlete opening the navigation drawer,
  - *When* inspecting the categories,
  - *Then* "Experten-Einstellungen" (or "Expert Settings") SHALL appear as a dedicated category at the very bottom of the drawer, separated by a horizontal divider below general settings, containing the `drawer_advanced_tuning` item.
* **AC-2 (Localization Uniformity)**:
  - *Given* the application running in any of the 9 supported locales,
  - *When* viewing the drawer section title, dialog header, or trigger button,
  - *Then* the label SHALL display the localized "Expert Settings" string without missing translations.
* **AC-3 (Default GPS Tolerance)**:
  - *Given* a new installation or an athlete tapping "Standard wiederherstellen",
  - *When* inspecting `gpsAccuracyThresholdMeters`,
  - *Then* the threshold SHALL evaluate to `50.0m` (`50.0f`).

---

## 4. Test Case Specification (TST-SET-063)

### TST-SET-063: Expert Settings Navigation Drawer Grouping, Localization and 50m GPS Default Verification
- **Target Components**: [AppNavigationDrawer.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt), [TuningPreferencesDataStore.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt), `strings.xml`
- **Test Files**:
  - [AppNavigationDrawerTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerTest.kt)
  - [TuningConfigTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningConfigTest.kt)
- **Scenarios**:
  1. `testDrawerGroups_expertSettings_placedInDedicatedBottomGroup`: Verify `groups.last()` has `titleRes == R.string.drawer__expert_settings` and contains `R.id.drawer_advanced_tuning`.
  2. `testDrawerGroups_generalSettings_doesNotContainAdvancedTuning`: Verify `drawer__settings` group no longer contains `R.id.drawer_advanced_tuning`.
  3. `defaultValues_conformToEstablishedBehavior`: In `TuningConfigTest`, assert `config.gpsAccuracyThresholdMeters == 50.0f` and `TuningPreferencesDefaults.GPS_ACCURACY_THRESHOLD_M == 50.0f`.
