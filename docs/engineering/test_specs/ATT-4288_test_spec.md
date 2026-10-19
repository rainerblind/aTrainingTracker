# Stage 2: Requirement & Test Specification - ATT-4288: Remove duplicate '+' prefix from onboarding_pair_sensor_action and devices_action_search_sensors strings

**Ticket**: [[ATT-4288]](https://rainerblind.atlassian.net/browse/ATT-4288)  
**Sub-task**: [[ATT-4326]](https://rainerblind.atlassian.net/browse/ATT-4326) (`[Req & Test Spec]`)  
**Parent Epic**: [[ATT-2617]](https://rainerblind.atlassian.net/browse/ATT-2617) (*Optimize first impression*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-SEN-020` (*Streamlined Sensor Onboarding, Empty-State Action & Discovery Auto-Pairing*)  
**Test Spec ID**: `TST-SEN-020`  
**Branch**: `feature/ATT-4288`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification ([REQ-SEN-020])

### 1.1 Problem Statement & Rationale
During physical on-device testing of `ATT-4051` on a Google Pixel 10 (documented in Jira attachment `sensor_onboarding_double_plus.png`), a visual defect was identified on two core sensor onboarding and discovery surfaces:
1. **Start Cockpit Sensor Onboarding Button (`ControlTrackingScreen.kt`)**:
   When no remote sensors are paired (`hasPairedRemoteDevices == false` and `devices.isEmpty()`), the Start screen displays an `OutlinedButton` inside the sensor slot. The composable renders a leading Material icon `Icon(imageVector = Icons.Default.Add, ...)` followed by `Text(text = stringResource(R.string.onboarding_pair_sensor_action))`. Because `onboarding_pair_sensor_action` was drafted with a literal `"+ "` prefix across all 9 locales, the rendered button displays `+  + Sensor koppeln` (German), `+  + Pair Sensor` (English), etc.
2. **"My Sensors" Empty-State Action Button (`DeviceListScreen.kt`)**:
   When navigating to *"My Sensors"* with an empty device list and no active scan, `EmptyStatePlaceholder` renders an action `OutlinedButton` with a leading `Icon(imageVector = Icons.Default.Add, ...)` followed by `Text(text = stringResource(R.string.devices_action_search_sensors))`. Because this string resource also starts with a literal `"+ "` prefix, the button displays `+  + Sensoren suchen` (German), `+  + Search for Sensors` (English), etc.

This duplicate plus symbol produces an unpolished, glitchy first impression for new users. Under **Governance Rule 49** and **Design Guidelines Section 5.5**, action iconography must remain strictly in Compose `Icon` slots, and localized string resources must remain purely textual without embedded ASCII glyph prefixes.

### 1.2 Functional & Architectural Requirements
1. **Text-Only Sensor Onboarding String Resource (`REQ-SEN-020.1`, `REQ-SEN-020.4`)**:
   * The system SHALL remove the literal `"+ "` prefix from `onboarding_pair_sensor_action` across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
   * The localized string resource SHALL contain only purely textual action wording (e.g. EN: `Pair Sensor`, DE: `Sensor koppeln`).
2. **Text-Only Empty-State Sensor Search String Resource (`REQ-SEN-020.2`, `REQ-SEN-020.4`)**:
   * The system SHALL remove the literal `"+ "` prefix from `devices_action_search_sensors` across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
   * The localized string resource SHALL contain only purely textual action wording (e.g. EN: `Search for Sensors`, DE: `Sensoren suchen`).
3. **Preservation of Compose Action Iconography (`REQ-SEN-020.1`, `REQ-SEN-020.2`)**:
   * In `ControlTrackingScreen.kt`, the onboarding button SHALL retain its leading Material icon `Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))` adjacent to `Text(text = stringResource(R.string.onboarding_pair_sensor_action))`.
   * In `DeviceListScreen.kt`, the empty-state action button SHALL retain its leading Material icon `Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))` adjacent to `Text(text = stringResource(R.string.devices_action_search_sensors))`.
   * The rendered buttons SHALL display exactly one plus icon glyph followed by the clean localized text (`[+] Pair Sensor`, `[+] Sensor koppeln`), eliminating duplicate plus characters.
4. **Automated Localization Contract Hardening (`REQ-SEN-020.4`)**:
   * Automated test suite `SensorOnboardingLocalizationTest.kt` SHALL be extended with a dedicated test `testActionStringsDoNotContainPlusPrefixAcrossAllLocales()` asserting that neither `onboarding_pair_sensor_action` nor `devices_action_search_sensors` starts with `'+'` in any of the 9 locales.

### 1.3 9-Locale Canonical String Mapping Table
The following complete table defines the canonical string transformations across all 9 supported application locales:

| Locale | Directory | Resource Key | Current Value (With Duplicate `+`) | Refined Value (Purely Textual) |
| :--- | :--- | :--- | :--- | :--- |
| **Default (EN)** | `values/strings.xml` | `onboarding_pair_sensor_action` | `+ Pair Sensor` | `Pair Sensor` |
| | | `devices_action_search_sensors` | `+ Search for Sensors` | `Search for Sensors` |
| **German (DE)** | `values-de/strings.xml` | `onboarding_pair_sensor_action` | `+ Sensor koppeln` | `Sensor koppeln` |
| | | `devices_action_search_sensors` | `+ Sensoren suchen` | `Sensoren suchen` |
| **Spanish (ES)** | `values-es/strings.xml` | `onboarding_pair_sensor_action` | `+ Vincular sensor` | `Vincular sensor` |
| | | `devices_action_search_sensors` | `+ Buscar sensores` | `Buscar sensores` |
| **French (FR)** | `values-fr/strings.xml` | `onboarding_pair_sensor_action` | `+ Associer un capteur` | `Associer un capteur` |
| | | `devices_action_search_sensors` | `+ Rechercher des capteurs` | `Rechercher des capteurs` |
| **Italian (IT)** | `values-it/strings.xml` | `onboarding_pair_sensor_action` | `+ Associa sensore` | `Associa sensore` |
| | | `devices_action_search_sensors` | `+ Cerca sensori` | `Cerca sensori` |
| **Japanese (JA)** | `values-ja/strings.xml` | `onboarding_pair_sensor_action` | `+ センサーをペアリング` | `センサーをペアリング` |
| | | `devices_action_search_sensors` | `+ センサーを検索` | `センサーを検索` |
| **Dutch (NL)** | `values-nl/strings.xml` | `onboarding_pair_sensor_action` | `+ Sensor koppelen` | `Sensor koppelen` |
| | | `devices_action_search_sensors` | `+ Sensoren suchen` | `Sensoren zoeken` |
| **Polish (PL)** | `values-pl/strings.xml` | `onboarding_pair_sensor_action` | `+ Sparuj czujnik` | `Sparuj czujnik` |
| | | `devices_action_search_sensors` | `+ Szukaj czujników` | `Szukaj czujników` |
| **Portuguese (PT)**| `values-pt/strings.xml` | `onboarding_pair_sensor_action` | `+ Emparelhar sensor` | `Emparelhar sensor` |
| | | `devices_action_search_sensors` | `+ Procurar sensores` | `Procurar sensores` |

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Start Cockpit Onboarding Button Visual Presentation)**:
  * *Given* a user on `ControlTrackingScreen` with zero paired sensors (`hasPairedRemoteDevices == false` and `devices.isEmpty()`),
  * *When* the onboarding button is rendered in the top sensor slot across any of the 9 supported locales,
  * *Then* the button displays a single `Icons.Default.Add` vector icon and the clean localized text (`Pair Sensor` / `Sensor koppeln` / etc.) without a duplicate `+` prefix.
* **Criterion 2 (My Sensors Empty-State Search Button Visual Presentation)**:
  * *Given* a user viewing `DeviceListScreen` with an empty sensor list and no active scan (`devices.isEmpty()`, `!isSearching`, `filterCriteria.isEmpty`),
  * *When* `EmptyStatePlaceholder` renders its action button across any of the 9 supported locales,
  * *Then* the button displays a single `Icons.Default.Add` vector icon and the clean localized text (`Search for Sensors` / `Sensoren suchen` / etc.) without a duplicate `+` prefix.
* **Criterion 3 (Automated Localization Contract Enforcement)**:
  * *Given* all 9 XML string resource files (`values*/strings.xml`),
  * *When* audited by `SensorOnboardingLocalizationTest.kt`,
  * *Then* `onboarding_pair_sensor_action` and `devices_action_search_sensors` exist, are non-blank, and do NOT begin with `'+'`.
* **Criterion 4 (Clean-Room Test Pass Rate)**:
  * *Given* the complete project test suite,
  * *When* executing `./gradlew testDebugUnitTest`,
  * *Then* 100% of tests pass without regression.

### 1.5 System Invariants
* **Invariant 1 (Behavioral & Navigation Invariance)**: Tapping `onboarding_pair_sensor_action` in `ControlTrackingScreen.kt` navigates to `drawer_my_sensors` via `onPairSensor()`; tapping `devices_action_search_sensors` in `DeviceListScreen.kt` triggers `onScanForSensors()` (opening `PairingProtocolBottomSheet`). All click handling, ViewModel dispatch, and navigation logic remain 100% unchanged.
* **Invariant 2 (Strict 9-Locale Parity)**: All 9 supported locales maintain identical semantic meaning, 100% key completeness, and zero missing definitions.
* **Invariant 3 (UI Baseline Conformance — Rule 49 & Section 5.5)**: Graphic icons belong strictly in Compose `Icon` slots; XML string resources remain purely textual. Category filter pill wildcards (e.g. `+ all +`) are untouched as they use plus signs as deliberate delimiters and do not render adjacent vector icons.
* **Invariant 4 (Human Decision Gate — Rule 3)**: Parent ticket `ATT-4288` transitions to `Final Review (Human)` at Stage 5; transitioning to `Erledigt` is reserved exclusively for the human user.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**:
  Net-new requirement `REQ-SEN-020` (*Streamlined Sensor Onboarding, Empty-State Action & Discovery Auto-Pairing*), extending `REQ-UI-301` / `REQ-UI-393`, refined by `ATT-4288`. Targets `ControlTrackingScreen.kt`, `TrackingTabsScreen.kt`, `DeviceListScreen.kt`, `DevicesTabbedScreen.kt`, `DeviceManager.java`, `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`, `app/src/main/res/values*/strings.xml`, and `SensorOnboardingLocalizationTest.kt`.
* **Historical Origin & Commit Trace**:
  - `ATT-4051` (commit `1835aaac`, Sprint `2026-42.10`, authored by Rainer Blind): Introduced `REQ-SEN-020`, adding `onboarding_pair_sensor_action`, `devices_empty_state_search_hint`, and `devices_action_search_sensors`.
  - Sprint Review for `2026-42.10`: Human reviewer and Agent 1 observed the duplicate `+` on Google Pixel 10, logged attachment `sensor_onboarding_double_plus.png`, and created bug ticket `ATT-4288` at the top of the backlog.
* **Root Reason for Existing Formulation**:
  When specifying and implementing `ATT-4051`, user stories and acceptance criteria colloquially described the buttons as *"+ Pair Sensor"* and *"+ Search for Sensors"*. During development, standard Material iconography (`Icons.Default.Add`) was placed in the Compose layout, while the literal `"+ "` character was simultaneously added to the string resources in `strings.xml`. Compose rendered both the icon and the text glyph side-by-side, creating the unintended `+  +` double plus artifact.
* **Preservation of Core Invariants**:
  Removing the leading `"+ "` prefix from the XML string resources eliminates the redundant glyph while preserving 100% of user orientation, translations across all 9 locales, navigation to *"My Sensors"*, protocol bottom sheets, auto-pairing, manual unpairing, and test suite integrity.

---

## 2. Test Specification ([TST-SEN-020])

### Test Case 1: `testActionStringsDoNotContainPlusPrefixAcrossAllLocales` (`[TST-SEN-020.12]`)
* **Scope**: Automated Localization Unit & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/SensorOnboardingLocalizationTest.kt`
* **Preconditions**:
  * XML resource files located in `app/src/main/res/values*/strings.xml` across all 9 locales (`""`, `"-de"`, `"-es"`, `"-fr"`, `"-it"`, `"-ja"`, `"-nl"`, `"-pl"`, `"-pt"`).
* **Action**:
  ```kotlin
  @Test
  fun testActionStringsDoNotContainPlusPrefixAcrossAllLocales() {
      val resDir = findResDir()
      val actionKeys = listOf(
          "onboarding_pair_sensor_action",
          "devices_action_search_sensors"
      )

      for (locale in locales) {
          val valuesDir = File(resDir, "values$locale")
          val stringsFile = File(valuesDir, "strings.xml")

          for (key in actionKeys) {
              val value = parseStringResource(stringsFile, key)
              assertNotNull("Missing key '$key' in values$locale/strings.xml", value)
              assertFalse(
                  "Key '$key' in values$locale/strings.xml should not start with '+' (Rule 49 / Sec 5.5 violation): '$value'",
                  value!!.trim().startsWith("+")
              )
          }
      }
  }
  ```
* **Expected Result**: Assertions pass across all 9 locales with zero failures.

### Test Case 2: 9-Language Localization & Specifier Audit (`[TST-SEN-020.5]`)
* **Scope**: Localization Parity & Non-Blank Audit
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/SensorOnboardingLocalizationTest.kt`
* **Preconditions**: All 9 locale string files present.
* **Action**:
  Execute `testAllRequiredKeysExistAndAreNonEmptyAcrossAllLocales()`.
* **Expected Result**:
  * `onboarding_pair_sensor_action`, `devices_empty_state_search_hint`, and `devices_action_search_sensors` exist and are non-blank across all 9 locales.

### Test Case 3: Clean-Room Regression Suite (`[TST-SEN-020.6]`)
* **Scope**: Full Clean-Room Regression Suite
* **Command**: `./gradlew testDebugUnitTest`
* **Preconditions**: Clean working copy with Stage 4 changes applied.
* **Action**: Execute full Gradle unit test suite.
* **Expected Result**: 100% test pass rate across all project modules with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test / Verification Procedure | Requirement Mapping | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-SEN-020.12]` | Localization Contract | `SensorOnboardingLocalizationTest.testActionStringsDoNotContainPlusPrefixAcrossAllLocales` | `REQ-SEN-020.1`, `REQ-SEN-020.2`, `REQ-SEN-020.4` | Specified |
| `[TST-SEN-020.5]` | Localization Parity | `SensorOnboardingLocalizationTest.testAllRequiredKeysExistAndAreNonEmptyAcrossAllLocales` | `REQ-SEN-020.4`, `REQ-UI-106` | Specified |
| `[TST-SEN-020.3]` | UI Contract | `ControlTrackingScreenTest` / `ControlTrackingSensorHeaderContractTest` | `REQ-SEN-020.1` | Verified |
| `[TST-SEN-020.4]` | UI Contract | `DeviceListScreenTest` / `DeviceListScreenContractTest` | `REQ-SEN-020.2` | Verified |
| `[TST-SEN-020.6]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
