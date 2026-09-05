# Implementation Plan - ATT-525: Global Translation Parity, Array Normalization & Formatter Safety

## 1. Executive Summary & Problem Context
Ticket **ATT-525** focuses on eliminating internationalization technical debt, closing localization parity gaps, and preventing runtime crashes across all 9 supported locales:
- **Default / English** (`values/`)
- **German** (`values-de/`)
- **Spanish** (`values-es/`)
- **French** (`values-fr/`)
- **Italian** (`values-it/`)
- **Japanese** (`values-ja/`)
- **Dutch** (`values-nl/`)
- **Polish** (`values-pl/`)
- **Portuguese** (`values-pt/`)

### Root Cause & Forensic Findings (from ATT-650 - Erledigt)
1. **Format Specifier Safety Violations (10 Defect Instances)**:
   - **Italian (`values-it/strings.xml`)**: 6 strings violate the `REQ-UI-122` positional specifier standard by using non-positional `%s` instead of `%1$s` (`no_routes_available`, `no_clusters_available`, `cluster_manual_distance_hint`, `cluster_manual_tap_map_format`, `cluster_default_name_format`, `cluster_equipment_mapping_format`).
   - **Japanese (`values-ja/strings.xml`)**: 4 export notification strings dropped the second positional parameter (`%2$s`), creating an argument count mismatch with Java/Kotlin callers (`notification_export_file`, `notification_export_dropbox`, `notification_finished_file`, `notification_finished_dropbox`).
2. **String-Array Deficits & Asymmetry**:
   - `values/arrays.xml` defines 23 string arrays, but localized variants have disparate definitions (`values-de` has 14, `values-pl` has 11, and `es`, `fr`, `it`, `ja`, `nl`, `pt` have only 6).
   - `backup_interval_values` in `values/arrays.xml` is missing `translatable="false"`.
   - `Basic_Sport_Types_values` in `values/arrays.xml` is an obsolete malformed `<string>` tag with `<item>` children.
3. **Resource Placement Inconsistencies**:
   - `filter_max`: Placed in `arrays.xml` in 8 locales, but in `strings.xml` in German. Architecturally belongs in `strings_filters.xml` alongside other filter smoothing types (`filter_instantaneous`, `filter_average`, `filter_exponential_smoothing`).
   - `devices_sensors_title`: In Spanish, placed in `strings.xml` instead of `strings_devices.xml`.
4. **String Key & Plural Parity**:
   - Base defines 1,058 keys (102 non-translatable, 956 translatable). All 8 localized variants possess all 956 translatable keys (0 missing, 0 orphaned), and all 15 plurals are present.

---

## 2. Requirement & Test Mapping
- **Requirement**: `REQ-LOC-001` (*Global Translation Parity, Resource Hierarchy Alignment, and Formatter Safety*)
- **Verification Test**: `TST-STR-018` (*Global Translation Parity, Array Integrity & Formatter Audit*)
- **Jira Tickets**:
  - Parent: `ATT-525` ([Tech Debt] Perform Global Translation Parity Audit)
  - Stage 1 RCA: `ATT-650` (Erledigt)
  - Stage 2 Plan: `ATT-651` (In Bearbeitung)
  - Stage 2 Test: `ATT-652` (Zu erledigen)

---

## 3. Architecture & Technical Design

### A. Format Specifier Correction (Adherence to REQ-UI-122)
1. **Italian (`values-it/strings.xml`)**:
   - `no_routes_available`: `Nessun percorso %s.` -> `Nessun percorso %1$s.`
   - `no_clusters_available`: `Nessun percorso %s disponibile.` -> `Nessun percorso %1$s disponibile.`
   - `cluster_manual_distance_hint`: `Distanza allenamento (%s)` -> `Distanza allenamento (%1$s)`
   - `cluster_manual_tap_map_format`: `Tocca la mappa per impostare %s` -> `Tocca la mappa per impostare %1$s`
   - `cluster_default_name_format`: `Allenamento a %s` -> `Allenamento a %1$s`
   - `cluster_equipment_mapping_format`: `→ %s` -> `→ %1$s`
2. **Japanese (`values-ja/strings.xml`)**:
   - `notification_export_file`: `%1$s %2$s ファイルをローカルへ出力中\n%3$s`
   - `notification_export_dropbox`: `%1$s %2$s ファイルを Dropbox へ送信中\n%3$s`
   - `notification_finished_file`: `%1$s %2$s をローカルへ出力完了\n%3$s`
   - `notification_finished_dropbox`: `%1$s %2$s を Dropbox へ送信完了\n%3$s`

### B. Resource Placement Normalization
1. **Filter Smoothing Strings (`strings_filters.xml`)**:
   - Add `filter_max` to `strings_filters.xml` across all 9 locales:
     - `values`: `<string name="filter_max">max</string>`
     - `values-de`: `<string name="filter_max">Max</string>`
     - `values-es`: `<string name="filter_max">máx</string>`
     - `values-fr`: `<string name="filter_max">max</string>`
     - `values-it`: `<string name="filter_max">max</string>`
     - `values-ja`: `<string name="filter_max">最大</string>`
     - `values-nl`: `<string name="filter_max">max</string>`
     - `values-pl`: `<string name="filter_max">max</string>`
     - `values-pt`: `<string name="filter_max">máx</string>`
   - Remove `<string name="filter_max">` from `arrays.xml` in `values`, `nl`, `es`, `pl`, `pt`, `it`, `ja`, `fr`.
   - Remove `<string name="filter_max">` from `values-de/strings.xml`.
2. **Device Strings (`strings_devices.xml`)**:
   - Move `devices_sensors_title` from `values-es/strings.xml` to `values-es/strings_devices.xml` (`<string name="devices_sensors_title">Sensores</string>`).

### C. String-Array Normalization (`arrays.xml`)
1. In `values/arrays.xml`:
   - Add `translatable="false"` to `backup_interval_values`: `<string-array name="backup_interval_values" translatable="false">`.
   - Remove obsolete malformed `<string name="Basic_Sport_Types_values">` tag.
2. In localized `arrays.xml`:
   - Standardize array definitions across all locales so translatable arrays (`Strava_Sport_Types_UI_Names`, `Runkeeper_Sport_Types`, `Training_Peaks_Sport_Types`, `TCX_Sport_Types`, `GC_Sport_Types`, `wheel_size_names`) are complete and consistent.

---

## 4. Implementation Steps & File Modifications

| Component | File Path | Scope of Modification |
| :--- | :--- | :--- |
| **Italian Strings** | [`values-it/strings.xml`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-it/strings.xml) | Fix 6 non-positional `%s` placeholders to `%1$s`. |
| **Japanese Strings** | [`values-ja/strings.xml`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-ja/strings.xml) | Restore `%2$s` placeholder across 4 export notification strings. |
| **Spanish Devices** | [`values-es/strings_devices.xml`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-es/strings_devices.xml) | Add `devices_sensors_title`. |
| **Spanish Strings** | [`values-es/strings.xml`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-es/strings.xml) | Remove misplaced `devices_sensors_title`. |
| **German Strings** | [`values-de/strings.xml`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-de/strings.xml) | Remove misplaced `filter_max`. |
| **Filter Strings** | `values*/strings_filters.xml` (all 9) | Add `filter_max` with localized translations. |
| **Arrays** | `values*/arrays.xml` (all 9) | Remove `filter_max`; mark `backup_interval_values` non-translatable; remove `Basic_Sport_Types_values`. |
| **Documentation** | [`docs/requirements.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) | Add `REQ-LOC-001` specification. |
| **Documentation** | [`docs/tests.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md) | Add `TST-STR-018` test specification. |
| **Unit Tests** | `app/src/test/.../TranslationParityTest.kt` | Automated tests verifying 100% key parity, format specifier parity, array item count equality, and XML hygiene. |

---

## 5. System Invariants & Regression Safety Checklist
- [x] **No Key Loss Invariant**: 100% of the 956 translatable string keys MUST remain present across all 9 supported locales.
- [x] **Formatter Safety Invariant**: All format strings MUST use valid Java Formatter syntax (`%1$s`), and the set of format specifiers MUST match base exactly in count and type across all translations.
- [x] **Array Index Integrity Invariant**: All string-arrays MUST have identical lengths across locales to prevent adapter index-out-of-bounds exceptions.
- [x] **Build & Test Invariant**: `./gradlew testDebugUnitTest` must pass with zero failures and zero regressions.

---

## 6. Verification & Test Strategy
1. **Automated Unit Tests (`TranslationParityTest.kt`)**:
   - `testStringKeyParity_allTranslatableKeysPresentInAllLocales`: Verifies all 956 translatable keys are present in each of the 8 locales.
   - `testFormatSpecifierParity_allLocalesMatchBaseSpecifiers`: Verifies 100% format specifier count, index, and conversion type parity across all 9 locales.
   - `testStringArrayParity_arrayLengthsMatchBase`: Verifies array length equality across all locales.
   - `testXmlStructure_noStringTagsInArraysXml`: Verifies no misplaced `<string>` tags exist in `arrays.xml`.
2. **Regression Suite**:
   - Run `./gradlew testDebugUnitTest` to ensure all existing unit tests pass cleanly.
