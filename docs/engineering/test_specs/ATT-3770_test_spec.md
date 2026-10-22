# Stage 2 Requirement and Test Specification: Enable R8 Minification and DEX Code Obfuscation in Release Builds (ATT-3770)

* **Parent Ticket**: [ATT-3770](https://rainerblind.atlassian.net/browse/ATT-3770) (*[Medium] Enable R8 minification and DEX code obfuscation in release builds with safe ProGuard keep rules to satisfy Google Play threshold (>= 25%)*)
* **Subtask**: [ATT-4107](https://rainerblind.atlassian.net/browse/ATT-4107) (*[Req & Test Spec] [Medium] Enable R8 minification and DEX code obfuscation in release builds with safe ProGuard keep rules to satisfy Google Play threshold (>= 25%)*)
* **Requirement**: `REQ-BLD-001` (Specified)
* **Test Specification**: `TST-BLD-001` (Specified)
* **Target Version**: `V4.9.40`
* **Branch**: `feature/ATT-3770`

---

## 1. Requirement Specification (REQ-BLD-001)

### REQ-BLD-001: R8 Minification, Resource Shrinking and DEX Code Obfuscation with Safe ProGuard Keep Rules

The build system SHALL configure `app/build.gradle` and `app/proguard-rules.pro` to enable R8 minification, resource shrinking, dead-code optimization, and DEX code obfuscation in release builds conforming to Google Play vitals threshold ($\ge 25\%$) while guaranteeing 100% runtime reflection and data persistence safety (ATT-3770):

1. **R8 Minification and Resource Shrinking in Release Build Variant (`REQ-BLD-001.1`)**:
   - In `app/build.gradle`, `buildTypes.release` SHALL configure `minifyEnabled = true` and `shrinkResources = true`.
   - R8 optimization SHALL use standard rules `getDefaultProguardFile('proguard-android-optimize.txt')` and application rules `'proguard-rules.pro'`.
2. **Removal of Blanket Compose Keep Rule (`REQ-BLD-001.2`)**:
   - The blanket rule `-keep class androidx.compose.** { *; }` SHALL be removed from `app/proguard-rules.pro`, delegating Compose keep semantics to official AAR consumer rules bundled in Jetpack Compose libraries and unlocking tree-shaking of unused `material-icons-extended` classes.
3. **Domain Enum Preservation (`REQ-BLD-001.3`)**:
   - All enum classes in package `com.atrainingtracker.**` SHALL retain their constant members, values array, and `valueOf(String)` / `safeValueOf(String)` resolution methods to protect SQLite string persistence and SharedPreferences/DataStore deserialization against runtime `IllegalArgumentException`.
4. **Hardware SDK, FIT & Cloud Service Interoperability (`REQ-BLD-001.4`)**:
   - Public and reflectively accessed classes in Garmin FIT SDK (`com.garmin.fit.**`), ANT+ Plugin library (`com.dsi.ant.**`), PebbleKit (`com.getpebble.android.kit.**`), Dropbox SDK (`com.dropbox.core.**`), and GPX parser (`io.ticofab.androidgpxparser.**`) SHALL be preserved.
5. **Serialization, ViewModels & Crash Reporting Traceability (`REQ-BLD-001.5`)**:
   - Kotlinx serialization descriptors and AndroidX ViewModel reflection constructors SHALL be preserved.
   - Stacktrace deobfuscation line numbers and source files SHALL be preserved via `-keepattributes SourceFile,LineNumberTable` and `-renamesourcefileattribute SourceFile`.
6. **Google Play Vitals Obfuscation Threshold Compliance (`REQ-BLD-001.6`)**:
   - Release build DEX optimization SHALL achieve $\ge 25\%$ obfuscated classes, generating a valid `mapping.txt` bundled into the release AAB/APK.

---

## 2. Given-When-Then Acceptance Criteria

* **Acceptance Criterion 1 (Release Build & Mapping Generation)**:
  - **Given** `app/build.gradle` and `app/proguard-rules.pro` configured with R8 release minification and keep rules,
  - **When** `./gradlew :app:assembleRelease` is executed,
  - **Then** the build completes successfully with exit code 0, generating `mapping.txt` at `app/build/outputs/mapping/release/mapping.txt`.
* **Acceptance Criterion 2 (DEX Obfuscation Ratio Threshold)**:
  - **Given** release build DEX output files (`classes.dex`, `classes2.dex`, etc.),
  - **When** class names are inspected for obfuscation (e.g. single-letter package or class identifiers),
  - **Then** the obfuscated class ratio is $\ge 25\%$ (target $> 50\%$), satisfying Google Play's vitals threshold.
* **Acceptance Criterion 3 (ProGuard Rules Contract & Dead Keep Pruning)**:
  - **Given** `app/proguard-rules.pro`,
  - **When** validated via `ProGuardRulesContractTest`,
  - **Then** blanket rule `-keep class androidx.compose.**` is absent, and rules for domain enums, Garmin FIT SDK, ANT+, PebbleKit, Dropbox, and serialization are present.
* **Acceptance Criterion 4 (Domain Enum Persistence & Reflection Safety)**:
  - **Given** domain enums `SensorType`, `BSportType`, `DeviceType`, `MyUnits`, `ZoneType`, `FilterType`, `FileFormat`, `ExportStatus`, and `EquipmentType`,
  - **When** serialized and deserialized via `Enum.valueOf` and `safeValueOf`,
  - **Then** all enum values resolve without `IllegalArgumentException`.
* **Acceptance Criterion 5 (Full Clean-Room Regression Suite)**:
  - **Given** the complete unit test suite,
  - **When** executing `./gradlew testDebugUnitTest`,
  - **Then** 100% of unit tests pass with zero failures.

---

## 3. Traceability Matrix

| Requirement Clause | Test Case ID | Test Implementation | Target Invariant |
| :--- | :--- | :--- | :--- |
| `REQ-BLD-001.1`, `REQ-BLD-001.6` | `TST-BLD-001.1` | Gradle `:app:assembleRelease` build execution | Valid build and `mapping.txt` generation |
| `REQ-BLD-001.6` | `TST-BLD-001.2` | DEX class obfuscation analysis script/tool | DEX obfuscation ratio $\ge 25\%$ |
| `REQ-BLD-001.2`, `REQ-BLD-001.4`, `REQ-BLD-001.5` | `TST-BLD-001.3` | `ProGuardRulesContractTest.kt` | ProGuard rules syntax and composition integrity |
| `REQ-BLD-001.3` | `TST-BLD-001.4` | `ProGuardRulesContractTest.kt` | Domain enum reflection & SQLite compatibility |
| System Invariants | `TST-BLD-001.5` | `./gradlew testDebugUnitTest` | 100% clean-room unit test suite pass rate |
| Rule 29 Parity | `TST-BLD-001.6` | `TranslationParityTest.kt` | 9-language localization parity |

---

## 4. 9-Language Localization Audit

This ticket modifies build configuration (`app/build.gradle`) and ProGuard rules (`app/proguard-rules.pro`). Zero new user-facing strings are introduced. Existing localized strings across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, and `values-pt/` remain intact and will be re-verified by `TranslationParityTest`.

---

## 5. Pass Criteria & Invariants

1. Release build compiles cleanly with zero R8 warnings or errors.
2. `mapping.txt` is produced and non-empty.
3. Obfuscation percentage is $\ge 25\%$.
4. All unit tests pass cleanly in clean-room environment.
