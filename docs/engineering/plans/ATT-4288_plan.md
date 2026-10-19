# Stage 3: Implementation Plan - ATT-4288: Remove duplicate '+' prefix from onboarding_pair_sensor_action and devices_action_search_sensors strings

**Ticket**: [[ATT-4288]](https://rainerblind.atlassian.net/browse/ATT-4288)  
**Sub-task**: [[ATT-4327]](https://rainerblind.atlassian.net/browse/ATT-4327) (`[Impl-Plan]`)  
**Parent Epic**: [[ATT-2617]](https://rainerblind.atlassian.net/browse/ATT-2617) (*Optimize first impression*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-SEN-020` (*Streamlined Sensor Onboarding, Empty-State Action & Discovery Auto-Pairing*, sub-clauses `REQ-SEN-020.1`, `REQ-SEN-020.2`, `REQ-SEN-020.4`)  
**Test Mapping**: `TST-SEN-020.12` (*SensorOnboardingLocalizationTest.testActionStringsDoNotContainPlusPrefixAcrossAllLocales*), `TST-SEN-020.5` (*9-Language Localization & Specifier Audit*), `TST-SEN-020.6` (*Clean-Room Regression Suite*)  
**Branch**: `feature/ATT-4288`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

During physical on-device testing of `ATT-4051` on a Google Pixel 10 (Sprint Review 2026-42.10, documented in Jira attachment `sensor_onboarding_double_plus.png`), a visual defect was identified on two core sensor onboarding and discovery surfaces:
1. **Start Cockpit Sensor Onboarding Action (`ControlTrackingScreen.kt`)**:
   When zero remote sensors are paired (`hasPairedRemoteDevices == false` and `devices.isEmpty()`), the Start screen displays an `OutlinedButton` inside the sensor header slot. The button renders a leading `Icon(imageVector = Icons.Default.Add, modifier = Modifier.size(18.dp))` adjacent to `Text(text = stringResource(R.string.onboarding_pair_sensor_action))`. Because `onboarding_pair_sensor_action` starts with a literal `"+ "` character across all 9 locales, the rendered button displays `+  + Sensor koppeln` (German), `+  + Pair Sensor` (English), etc.
2. **"My Sensors" Empty-State Action (`DeviceListScreen.kt`)**:
   When viewing *"My Sensors"* with an empty device list and no active scan, `EmptyStatePlaceholder` renders an action `OutlinedButton` with a leading `Icon(imageVector = Icons.Default.Add, modifier = Modifier.size(18.dp))` adjacent to `Text(text = stringResource(R.string.devices_action_search_sensors))`. Because this string resource also starts with a literal `"+ "` prefix across all 9 locales, the button displays `+  + Sensoren suchen` (German), `+  + Search for Sensors` (English), etc.

This duplicate plus symbol creates an unpolished, glitchy appearance on the user's initial interaction with the app. Under **Governance Rule 49** and **Design Guidelines Section 5.5**, graphic icons belong strictly in Compose `Icon` slots, and localized string resources must remain purely textual without embedded ASCII glyph prefixes.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-SEN-020` (*Streamlined Sensor Onboarding, Empty-State Action & Discovery Auto-Pairing*)
  * `REQ-SEN-020.1`: The Start screen sensor onboarding action button SHALL display exactly one `Icons.Default.Add` vector icon and purely textual localized action text (`Pair Sensor` / `Sensor koppeln`).
  * `REQ-SEN-020.2`: The "My Sensors" empty-state search action button SHALL display exactly one `Icons.Default.Add` vector icon and purely textual localized action text (`Search for Sensors` / `Sensoren suchen`).
  * `REQ-SEN-020.4`: All 9 supported application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) SHALL provide 100% complete, non-blank translations for `onboarding_pair_sensor_action` and `devices_action_search_sensors` without literal `'+'` prefixes.
* **Test Mapping**:
  * `TST-SEN-020.12`: `SensorOnboardingLocalizationTest.testActionStringsDoNotContainPlusPrefixAcrossAllLocales` — asserts that `onboarding_pair_sensor_action` and `devices_action_search_sensors` across all 9 locales do not start with `'+'`.
  * `TST-SEN-020.5`: `SensorOnboardingLocalizationTest.testAllRequiredKeysExistAndAreNonEmptyAcrossAllLocales` — verifies that all required sensor onboarding keys exist and are non-empty across all 9 locales.
  * `TST-SEN-020.3`: `ControlTrackingSensorHeaderContractTest` / `ControlTrackingScreenTest` — verifies Start screen onboarding button composition and navigation contract.
  * `TST-SEN-020.4`: `DeviceListScreenContractTest` / `DeviceListScreenTest` — verifies empty-state button composition and scan trigger contract.
  * `TST-SEN-020.6`: Clean-room regression test suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Behavioral or Navigation Regressions**:
   * Tapping `onboarding_pair_sensor_action` in `ControlTrackingScreen.kt` invokes `onPairSensor()` (navigating to `drawer_my_sensors`).
   * Tapping `devices_action_search_sensors` in `DeviceListScreen.kt` invokes `onScanForSensors()` (opening `PairingProtocolBottomSheet`).
   * All ViewModel state flows, event dispatches, Bluetooth LE/ANT+ scanning, and auto-pairing routines remain 100% unaltered.
2. **Strict 9-Locale Localization Parity & Text Integrity**:
   * All 9 application locales maintain 100% definition completeness with identical localized semantic meaning.
   * Wildcard filter pill strings (e.g. `+ all +`, `+ all bikes +`) remain untouched as they intentionally use plus signs as visual delimiters and are never accompanied by `Icons.Default.Add` vector drawables.
3. **UI Consistency & Iconography Separation (Rule 49 & Design Guidelines Sec 5.5)**:
   * Action vector iconography remains strictly in Compose `Icon` slots (`Icons.Default.Add`).
   * Localized XML `<string>` resources remain purely textual.
4. **Thread Safety & Dispatcher Affinity**:
   * String resolution occurs entirely on `Dispatchers.Main` via Compose `stringResource()`.
   * No background threading, SQLite transactions, or service bindings are touched.
5. **Human Decision Gate & Subtask Direct Completion (Rule 3)**:
   * Subtask `ATT-4327` transitions autonomously to `Erledigt` via transition `freigabe` upon passing Gate 3 audit.
   * Terminal completion of parent ticket `ATT-4288` to `Erledigt` is strictly reserved for the human user in `Final Review (Human)`.
6. **Code Lock in Stage 3**:
   * No production source code or resources are edited during Stage 3 planning. Modifications occur strictly in Stage 4 following Gate 3 approval (`check-gate`).

---

## 4. Proposed Architectural Changes

### Architectural Decomposition (SWE.2)
The application architecture comprises:
* **UI Layer (Jetpack Compose)**: `ControlTrackingScreen.kt` and `DeviceListScreen.kt` render the onboarding button using `OutlinedButton` with `Icon(Icons.Default.Add)` and `Text(stringResource(...))`. This layer requires **zero code changes** because the composable structure is already architecturally sound and conforms to Material 3 standards.
* **Localization / Resource Layer (XML)**: `app/src/main/res/values*/strings.xml` defines localized strings across 9 locales. This layer will be updated to remove the redundant `"+ "` prefix from `onboarding_pair_sensor_action` and `devices_action_search_sensors` (18 string definitions updated).
* **Automated Test Layer (JUnit 4 / XML Parsing)**: `SensorOnboardingLocalizationTest.kt` parses XML string resources across all 9 locales. A new contract test method `testActionStringsDoNotContainPlusPrefixAcrossAllLocales()` will be added to enforce Rule 49 and Design Guidelines Sec 5.5 continuously.
* **ViewModel / Repository / Database / Service Layers**: Untouched. No changes to `ControlTrackingViewModel`, `DeviceListViewModel`, `DeviceManager`, or SQLite databases.

### Component 1: 9-Locale XML String Resources (`app/src/main/res/values*/strings.xml`)
Update exactly 18 string definitions across 9 files to remove the leading `"+ "` characters:

| Locale | File | Key | Original | Proposed Value |
| :--- | :--- | :--- | :--- | :--- |
| **EN (default)** | `values/strings.xml` | `onboarding_pair_sensor_action` | `+ Pair Sensor` | `Pair Sensor` |
| | | `devices_action_search_sensors` | `+ Search for Sensors` | `Search for Sensors` |
| **DE (German)** | `values-de/strings.xml` | `onboarding_pair_sensor_action` | `+ Sensor koppeln` | `Sensor koppeln` |
| | | `devices_action_search_sensors` | `+ Sensoren suchen` | `Sensoren suchen` |
| **ES (Spanish)** | `values-es/strings.xml` | `onboarding_pair_sensor_action` | `+ Vincular sensor` | `Vincular sensor` |
| | | `devices_action_search_sensors` | `+ Buscar sensores` | `Buscar sensores` |
| **FR (French)** | `values-fr/strings.xml` | `onboarding_pair_sensor_action` | `+ Associer un capteur` | `Associer un capteur` |
| | | `devices_action_search_sensors` | `+ Rechercher des capteurs` | `Rechercher des capteurs` |
| **IT (Italian)** | `values-it/strings.xml` | `onboarding_pair_sensor_action` | `+ Associa sensore` | `Associa sensore` |
| | | `devices_action_search_sensors` | `+ Cerca sensori` | `Cerca sensori` |
| **JA (Japanese)**| `values-ja/strings.xml` | `onboarding_pair_sensor_action` | `+ センサーをペアリング` | `センサーをペアリング` |
| | | `devices_action_search_sensors` | `+ センサーを検索` | `センサーを検索` |
| **NL (Dutch)** | `values-nl/strings.xml` | `onboarding_pair_sensor_action` | `+ Sensor koppelen` | `Sensor koppelen` |
| | | `devices_action_search_sensors` | `+ Sensoren suchen` | `Sensoren zoeken` |
| **PL (Polish)** | `values-pl/strings.xml` | `onboarding_pair_sensor_action` | `+ Sparuj czujnik` | `Sparuj czujnik` |
| | | `devices_action_search_sensors` | `+ Szukaj czujników` | `Szukaj czujników` |
| **PT (Portuguese)**| `values-pt/strings.xml`| `onboarding_pair_sensor_action` | `+ Emparelhar sensor` | `Emparelhar sensor` |
| | | `devices_action_search_sensors` | `+ Procurar sensores` | `Procurar sensores` |

*(Note: In `values-nl/strings.xml`, `devices_action_search_sensors` was previously misspelled in Dutch as `+ Sensoren suchen` [German]; this will be cleaned to `Sensoren zoeken` to ensure proper Dutch localization).*

### Component 2: Automated Localization Contract Test (`SensorOnboardingLocalizationTest.kt`)
Extend `SensorOnboardingLocalizationTest.kt` with a new test method:
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

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
Per `docs/design_guidelines.md` Section 5:
* **Reference screen / component**:
  * `ControlTrackingScreen.kt`: Start screen sensor row header (`OutlinedButton` with `Icons.Default.Add`).
  * `DeviceListScreen.kt`: Empty-state action button within `EmptyStatePlaceholder`.
* **Reused components**:
  * Existing Material 3 `OutlinedButton`, `Icon(Icons.Default.Add)`, `Spacer`, and `Text`. No new UI components are created; existing layout structure is preserved 100%.
* **Theme tokens**:
  * Shapes: Standard `OutlinedButton` shape (`MaterialTheme.shapes.extraLarge` / `RoundedCornerShape(12.dp)`).
  * Spacing: `Modifier.size(18.dp)` for icon, `Spacer(modifier = Modifier.width(8.dp))` for icon-to-text separation, `Modifier.padding(horizontal = 16.dp)`.
  * Colors: Standard theme tokens from `MaterialTheme.colorScheme` (`primary` / `outline` / `onSurface`).
* **New one-off styles & justification**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Check Gate 3 Approval (Rule 4)
* Verify that Stage 3 subtask `ATT-4327` is `Erledigt` before modifying any files:
  ```bash
  python3 tools/jira_util.py check-gate ATT-4327
  ```

### Step 2: Refine 9-Locale XML String Resources (18 string entries)
* Target Files:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* Changes:
  - Remove `"+ "` prefix from `onboarding_pair_sensor_action` in each file.
  - Remove `"+ "` prefix from `devices_action_search_sensors` in each file (and correct Dutch translation to `Sensoren zoeken`).

### Step 3: Implement Automated Contract Test in `SensorOnboardingLocalizationTest.kt`
* Target File:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/localization/SensorOnboardingLocalizationTest.kt`
* Changes:
  - Add `import org.junit.Assert.assertFalse`
  - Add test method `testActionStringsDoNotContainPlusPrefixAcrossAllLocales()` asserting `!value.trim().startsWith("+")` for both action keys across all 9 locales.

### Step 4: Targeted Unit & Localization Test Execution
* Target Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.SensorOnboardingLocalizationTest"
  ```
* Expected Outcome: All tests in `SensorOnboardingLocalizationTest` pass cleanly.

---

## 6. Verification & Rollback Plan

### Verification
1. **Targeted Unit Test Verification**:
   Execute `SensorOnboardingLocalizationTest` to prove that:
   - All keys exist and are non-blank across 9 locales (`testAllRequiredKeysExistAndAreNonEmptyAcrossAllLocales`).
   - Neither action key begins with `'+'` in any of the 9 locales (`testActionStringsDoNotContainPlusPrefixAcrossAllLocales`).
2. **Clean-Room Regression Verification (Stage 5)**:
   Execute `./gradlew testDebugUnitTest` to confirm zero regressions across all modules.
3. **On-Device Walkthrough Verification**:
   Inspect `ControlTrackingScreen` with zero sensors paired and `DeviceListScreen` with empty list to verify that buttons render cleanly with a single leading `+` icon and text without duplicate plus characters.

### Rollback Plan
* If unexpected issues arise, `git restore` on modified files restores the branch to pristine state.
* Feature branch isolation (`feature/ATT-4288`) ensures that `sprint/2026-43.1` and `develop` remain completely isolated and protected.
