# Stage 1 Analysis: ATT-4288 - Remove duplicate '+' prefix from onboarding_pair_sensor_action and devices_action_search_sensors strings

**Ticket**: [[ATT-4288]](https://rainerblind.atlassian.net/browse/ATT-4288)  
**Sub-task**: [[ATT-4325]](https://rainerblind.atlassian.net/browse/ATT-4325) (`[Analysis]`)  
**Parent Epic**: [[ATT-2617]](https://rainerblind.atlassian.net/browse/ATT-2617) (*Optimize first impression*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Branch**: `feature/ATT-4288`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

During on-device physical testing and Sprint Review of `ATT-4051` on a Google Pixel 10 (documented in Jira attachment `sensor_onboarding_double_plus.png`), a visual defect was identified on two core sensor onboarding and discovery surfaces:
1. **Start Screen Sensor Onboarding Action (`ControlTrackingScreen.kt`)**:
   When no remote sensors are paired (`hasPairedRemoteDevices == false` and `devices.isEmpty()`), the Start screen displays an `OutlinedButton` in the sensor header. The composable renders a leading Material icon `Icon(imageVector = Icons.Default.Add, ...)` followed by `Text(text = stringResource(R.string.onboarding_pair_sensor_action))`. Because the string resource begins with a literal `"+ "` prefix across all 9 locales, the rendered button displays `+  + Sensor koppeln` (German), `+  + Pair Sensor` (English), etc.
2. **"My Sensors" Empty-State Action (`DeviceListScreen.kt`)**:
   When navigating to *"My Sensors"* with an empty device list and no active scan, `EmptyStatePlaceholder` renders an action `OutlinedButton` with a leading `Icon(imageVector = Icons.Default.Add, ...)` followed by `Text(text = stringResource(R.string.devices_action_search_sensors))`. Because this string resource also starts with a literal `"+ "` prefix, the button displays `+  + Sensoren suchen` (German), `+  + Search for Sensors` (English), etc.

This duplicate plus symbol creates an unpolished, glitchy appearance on the user's very first interaction with the application. Under **Governance Rule 49** and **Design Guidelines Section 5.5**, action iconography must remain strictly in Compose `Icon` slots, and localized string resources must remain purely textual without embedded ASCII glyph prefixes.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Origin in Sprint 2026-42.10 (`ATT-4051`, Commit `1835aaac`)
In Sprint `2026-42.10`, ticket `ATT-4051` addressed usability on fresh installations by introducing:
- A prominent onboarding button on the Start screen when no sensors are paired (`ControlTrackingScreen.kt`).
- An actionable empty-state search button in *"My Sensors"* (`DeviceListScreen.kt`).

During the specification and implementation of `ATT-4051`:
- The acceptance criteria and design notes described the actions colloquially as *"+ Pair Sensor"* and *"+ Search for Sensors"*, emphasizing that tapping them adds or searches for sensors.
- When writing the Jetpack Compose UI code, the developer properly included standard Material iconography:
  ```kotlin
  // ControlTrackingScreen.kt:353-359
  OutlinedButton(onClick = onPairSensor, ...) {
      Icon(
          imageVector = Icons.Default.Add,
          contentDescription = null,
          modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(text = stringResource(R.string.onboarding_pair_sensor_action))
  }
  ```
  and:
  ```kotlin
  // DeviceListScreen.kt:135-145
  OutlinedButton(onClick = onScanForSensors) {
      Icon(
          imageVector = Icons.Default.Add,
          contentDescription = null,
          modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(text = stringResource(R.string.devices_action_search_sensors))
  }
  ```
- Simultaneously, when defining the localized strings in `app/src/main/res/values*/strings.xml`, the developer included a literal `"+ "` character inside the text elements across all 9 locales:
  ```xml
  <!-- values/strings.xml -->
  <string name="onboarding_pair_sensor_action">+ Pair Sensor</string>
  <string name="devices_action_search_sensors">+ Search for Sensors</string>
  ```
  ```xml
  <!-- values-de/strings.xml -->
  <string name="onboarding_pair_sensor_action">+ Sensor koppeln</string>
  <string name="devices_action_search_sensors">+ Sensoren suchen</string>
  ```

Because Compose places the vector `Icon(Icons.Default.Add)` immediately adjacent to the text, the literal `+` in the string produces the visible duplication `+  +`.

### 2.2 Call Site & Repository Audit
A complete repository audit confirmed that `onboarding_pair_sensor_action` and `devices_action_search_sensors` are used in exactly two production locations:
1. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt` (line 359)
2. `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListScreen.kt` (line 144)

No other production views, dialogs, or notification services reference these keys.

### 2.3 9-Locale String Resource Inventory
Across all 9 supported application locales, exactly 18 string definitions require modification:

| Locale | File Path | `onboarding_pair_sensor_action` (Current $\rightarrow$ Fixed) | `devices_action_search_sensors` (Current $\rightarrow$ Fixed) |
| :--- | :--- | :--- | :--- |
| **Default (EN)** | `values/strings.xml` | `+ Pair Sensor` $\rightarrow$ `Pair Sensor` | `+ Search for Sensors` $\rightarrow$ `Search for Sensors` |
| **German (DE)** | `values-de/strings.xml` | `+ Sensor koppeln` $\rightarrow$ `Sensor koppeln` | `+ Sensoren suchen` $\rightarrow$ `Sensoren suchen` |
| **Spanish (ES)** | `values-es/strings.xml` | `+ Vincular sensor` $\rightarrow$ `Vincular sensor` | `+ Buscar sensores` $\rightarrow$ `Buscar sensores` |
| **French (FR)** | `values-fr/strings.xml` | `+ Associer un capteur` $\rightarrow$ `Associer un capteur` | `+ Rechercher des capteurs` $\rightarrow$ `Rechercher des capteurs` |
| **Italian (IT)** | `values-it/strings.xml` | `+ Associa sensore` $\rightarrow$ `Associa sensore` | `+ Cerca sensori` $\rightarrow$ `Cerca sensori` |
| **Japanese (JA)** | `values-ja/strings.xml` | `+ センサーをペアリング` $\rightarrow$ `センサーをペアリング` | `+ センサーを検索` $\rightarrow$ `センサーを検索` |
| **Dutch (NL)** | `values-nl/strings.xml` | `+ Sensor koppelen` $\rightarrow$ `Sensor koppelen` | `+ Sensoren zoeken` $\rightarrow$ `Sensoren zoeken` |
| **Polish (PL)** | `values-pl/strings.xml` | `+ Sparuj czujnik` $\rightarrow$ `Sparuj czujnik` | `+ Szukaj czujników` $\rightarrow$ `Szukaj czujników` |
| **Portuguese (PT)**| `values-pt/strings.xml` | `+ Emparelhar sensor` $\rightarrow$ `Emparelhar sensor` | `+ Procurar sensores` $\rightarrow$ `Procurar sensores` |

### 2.4 Audit of Other String Resources with `+` Prefixes
A search across all `<string>` entries in `values/strings.xml` for leading `+ ` characters revealed:
- `equipment_all`: `+ all +`
- `equipment_all_bikes`: `+ all bikes +`
- `equipment_all_shoes`: `+ all shoes +`
- `show_all_sport_types`: `+ all sports +`
- `all_sports`: `+ all sports +`
- `all_equipment`: `+ all equipment +`
- `all_shoes`: `+ all shoes +`
- `all_bikes`: `+ all bikes +`

These entries are category wildcard filter pills (e.g. `+ all +`) where the surrounding plus symbols are deliberate stylistic delimiters and are not accompanied by `Icons.Default.Add` vector icons. `onboarding_pair_sensor_action` and `devices_action_search_sensors` are the **only** action button strings in the application that suffer from the duplicate `+` issue.

### 2.5 Analysis of Existing Unit and Contract Tests
1. **`SensorOnboardingLocalizationTest.kt`**:
   Currently verifies that `onboarding_pair_sensor_action`, `devices_empty_state_search_hint`, and `devices_action_search_sensors` exist and are non-blank in all 9 locales. It does not check for or enforce absence of literal `+` prefixes. Removing the `+ ` prefix preserves non-blank validity, so this test will continue to pass.
2. **`ControlTrackingSensorHeaderContractTest.kt`**:
   Asserts that `ControlTrackingScreen.kt` contains the string reference `R.string.onboarding_pair_sensor_action` and `Icons.Default.Add`. It does not assert raw string contents.
3. **`DeviceListScreenContractTest.kt`**:
   Asserts that `DeviceListScreen.kt` contains `R.string.devices_action_search_sensors` and `Icons.Default.Add`. It does not assert raw string contents.

Conclusion: No existing unit or contract tests will be broken by removing `"+ "`. To lock in the fix and prevent future regression, `SensorOnboardingLocalizationTest.kt` should be extended with an explicit assertion that action button strings do not begin with `+`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Remove the leading `"+ "` prefix from `onboarding_pair_sensor_action` and `devices_action_search_sensors` across all 9 application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) (18 string entries modified).
  2. Maintain the leading `Icon(imageVector = Icons.Default.Add, ...)` in `ControlTrackingScreen.kt` and `DeviceListScreen.kt` to provide clean, unambiguous add action iconography.
  3. Extend `SensorOnboardingLocalizationTest.kt` to verify that `onboarding_pair_sensor_action` and `devices_action_search_sensors` do NOT begin with `+` in any of the 9 locales.
  4. Ensure 100% clean-room test pass rate (`./gradlew testDebugUnitTest`).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Modifying category filter wildcard strings (e.g., `+ all +`, `+ all bikes +`), which intentionally use plus delimiters and do not render adjacent vector icons.
  2. Modifying layout hierarchy, padding, callbacks, or conditional visibility logic in `ControlTrackingScreen.kt` or `DeviceListScreen.kt`.
  3. Modifying `DeviceManager.java`, database schemas, or Bluetooth LE/ANT+ auto-pairing behavior.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  Modifies/refines `REQ-SEN-020` (*Streamlined Sensor Onboarding, Empty-State Action & Discovery Auto-Pairing*), specifically sub-clauses `REQ-SEN-020.1`, `REQ-SEN-020.2`, `REQ-SEN-020.4`, and their associated acceptance criteria.
  Target files:
  - `app/src/main/res/values*/strings.xml`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/localization/SensorOnboardingLocalizationTest.kt`
  - `docs/requirements.md`
  - `docs/tests.md`

* **Historical Origin & Commit Trace**:
  - `ATT-4051` (commit `1835aaac`, Sprint `2026-42.10`, authored by Rainer Blind): Introduced `REQ-SEN-020`, adding `onboarding_pair_sensor_action`, `devices_empty_state_search_hint`, and `devices_action_search_sensors`.
  - Sprint Review for `2026-42.10`: Human reviewer and Agent 1 observed the duplicate `+` on Google Pixel 10, logged attachment `sensor_onboarding_double_plus.png`, and created bug ticket `ATT-4288` at the top of the backlog.

* **Root Reason for Existing Formulation**:
  In `REQ-SEN-020`, the requirement summary and acceptance criteria specified:
  - *ControlTrackingScreen*: "render a compact, prominent OutlinedButton displaying icon Icons.Default.Add and text stringResource(R.string.onboarding_pair_sensor_action) ('+ Pair Sensor' / '+ Sensor koppeln')."
  - *DeviceListScreen*: "render an OutlinedButton displaying icon Icons.Default.Add and text stringResource(R.string.devices_action_search_sensors) ('+ Search for Sensors' / '+ Sensoren suchen')."
  The author included `"+ "` in the string resources because the UI mockups and user stories labeled the button as "+ Pair Sensor". However, because the Compose layout *also* included `Icons.Default.Add`, the symbol was rendered twice.

* **Preservation of Core Invariants**:
  Removing the leading `"+ "` from the XML string resources leaves the semantic meaning, localized translation, and user orientation 100% intact while eliminating the duplicate glyph. In Compose, the `Icons.Default.Add` vector icon remains in place, ensuring that the button visually communicates an "add/pair" action clearly and elegantly. All onboarding navigation flows (`onPairSensor`), empty-state scan actions (`onScanForSensors`), drawer behaviors, and auto-pairing logic remain completely undisturbed.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 9-Locale XML String Resource Refinement
In `app/src/main/res/values*/strings.xml`:
- For `onboarding_pair_sensor_action`: Remove `"+ "` prefix in all 9 files.
- For `devices_action_search_sensors`: Remove `"+ "` prefix in all 9 files.

### 5.2 Preservation of Compose Button Structure
In `ControlTrackingScreen.kt` and `DeviceListScreen.kt`:
The existing Compose structure:
```kotlin
OutlinedButton(onClick = ...) {
    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(modifier = Modifier.width(8.dp))
    Text(text = stringResource(R.string....))
}
```
remains unchanged. With the string updated to `Pair Sensor` / `Sensor koppeln` and `Search for Sensors` / `Sensoren suchen`, the button cleanly renders `[ + ] Pair Sensor` without duplicate plus characters.

### 5.3 Automated Contract & Localization Test Hardening
In `SensorOnboardingLocalizationTest.kt`:
Add a test method `testActionStringsDoNotContainPlusPrefixAcrossAllLocales()` that parses `onboarding_pair_sensor_action` and `devices_action_search_sensors` across all 9 locales and asserts `!value.startsWith("+")`. This enforces Design Guidelines Section 5.5 and Governance Rule 49 programmatically.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Zero Behavioral or Functional Regressions**: Navigation to `drawer_my_sensors` and opening `PairingProtocolBottomSheet` continue to work identically.
  2. **Strict 9-Locale Parity**: All 9 supported languages remain fully translated and non-empty.
  3. **Visual Guideline Alignment (Rule 49 & Sec 5.5)**: Graphic icons belong in Compose `Icon` slots; XML strings remain purely textual.
  4. **Human Decision Gate**: Parent ticket `ATT-4288` transitions to `Final Review (Human)` at Stage 5; `Erledigt` is reserved exclusively for the human user.
* **Risk Rating**: **LOW**
  - Text-only resource modification and test suite enhancement.
  - Zero changes to application architecture, threading, database transactions, or platform permissions.
