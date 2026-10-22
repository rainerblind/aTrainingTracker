# Walkthrough: Enable R8 Minification and DEX Code Obfuscation in Release Builds (ATT-3770)

* **Parent Ticket**: [ATT-3770](https://rainerblind.atlassian.net/browse/ATT-3770) (*[Medium] Enable R8 minification and DEX code obfuscation in release builds with safe ProGuard keep rules to satisfy Google Play threshold (>= 25%)*)
* **Subtasks**:
  - [ATT-4106](https://rainerblind.atlassian.net/browse/ATT-4106) (*[Analysis] [Medium] Enable R8 minification and DEX code obfuscation in release builds with safe ProGuard keep rules to satisfy Google Play threshold (>= 25%)* - Erledigt)
  - [ATT-4107](https://rainerblind.atlassian.net/browse/ATT-4107) (*[Req & Test Spec] [Medium] Enable R8 minification and DEX code obfuscation in release builds with safe ProGuard keep rules to satisfy Google Play threshold (>= 25%)* - Erledigt)
  - [ATT-4108](https://rainerblind.atlassian.net/browse/ATT-4108) (*[Impl-Plan] [Medium] Enable R8 minification and DEX code obfuscation in release builds with safe ProGuard keep rules to satisfy Google Play threshold (>= 25%)* - Erledigt)
  - [ATT-4109](https://rainerblind.atlassian.net/browse/ATT-4109) (*[Implementation] [Medium] Enable R8 minification and DEX code obfuscation in release builds with safe ProGuard keep rules to satisfy Google Play threshold (>= 25%)* - Erledigt)
  - [ATT-4110](https://rainerblind.atlassian.net/browse/ATT-4110) (*[Test] [Medium] Enable R8 minification and DEX code obfuscation in release builds with safe ProGuard keep rules to satisfy Google Play threshold (>= 25%)* - In Review)
* **Requirement**: `REQ-BLD-001` (Verified)
* **Test Specification**: `TST-BLD-001` (Verified)
* **Target Version**: `V4.9.40`
* **Branch**: `feature/ATT-3770`

---

## 1. Executive Summary

Google Play Console (*Android vitals overview*) reported a mandatory quality warning for recent release tracks:
* *Category*: `Obfuscation (1%)` — *"Percentages under 25% in any category of your app may impact your visibility and publishing capabilities on Google Play. To prevent this, optimize your app by the deadline (Feb 2027)."*

Investigation revealed that in May 2026 (commit `f1de722d`), `minifyEnabled` was set to `false` in `buildTypes.release` as a precaution because safe ProGuard rules for runtime reflection, serialization, and hardware SDKs had not yet been codified. Furthermore, `app/proguard-rules.pro` contained a blanket rule `-keep class androidx.compose.** { *; }` that prevented R8 from shrinking unused Compose and `material-icons-extended` classes.

We resolved this issue by enabling R8 minification and resource shrinking in release builds, removing the blanket Compose keep rule (allowing official AAR consumer rules to govern Compose tree-shaking), and establishing exhaustive, safe keep rules for domain enums, hardware SDKs, cloud services, and stacktrace deobfuscation.

Key capabilities delivered:
1. **R8 Minification & Resource Shrinking (`app/build.gradle`)**:
   - Configured `minifyEnabled = true` and `shrinkResources = true` in `buildTypes.release`.
   - Release APK size reduced to **12 MB** with dead code eliminated.
2. **Pruned Blanket Compose Keep (`app/proguard-rules.pro`)**:
   - Excised `-keep class androidx.compose.** { *; }`, unlocking R8 dead-code elimination across Jetpack Compose and thousands of unused icons in `material-icons-extended`.
3. **Hardened ProGuard Rules (`app/proguard-rules.pro`)**:
   - Preserved all domain enums in `com.atrainingtracker.**` (`values()`, `valueOf()`, and `safeValueOf()`) ensuring zero `IllegalArgumentException`s in SQLite or DataStore name deserialization.
   - Preserved Garmin FIT SDK (`com.garmin.fit.**`), ANT+ Plugin (`com.dsi.ant.**`), PebbleKit (`com.getpebble.android.kit.**`), Dropbox SDK (`com.dropbox.core.**`), and GPX Parser (`io.ticofab.androidgpxparser.**`).
   - Preserved AndroidX ViewModel reflection constructors.
   - Preserved line numbers and source file names (`-keepattributes SourceFile,LineNumberTable` and `-renamesourcefileattribute SourceFile`) for clean Firebase Crashlytics and Google Play Console deobfuscation.
4. **Substantial Google Play Threshold Compliance**:
   - Release build achieved an obfuscation ratio of **58.80%** (10,063 obfuscated classes out of 17,115 mapped classes), substantially surpassing Google Play's 25% threshold.

---

## 2. Changes Summary

| Area | Component | Change | Description |
| :--- | :--- | :--- | :--- |
| **Build Configuration** | `app/build.gradle` | [MODIFY] | Enabled `minifyEnabled = true` and `shrinkResources = true` in `buildTypes.release`. |
| **ProGuard Rules** | `app/proguard-rules.pro` | [MODIFY] | Excised blanket Compose keep; added keep rules for domain enums, Garmin FIT SDK, ANT+, PebbleKit, Dropbox SDK, ViewModels, and stacktrace attributes. |
| **Contract Tests** | `ProGuardRulesContractTest.kt` | [NEW] | Contract test suite verifying absence of blanket Compose keep, presence of required SDK/enum rules, and reflection safety across all domain enums. |
| **Living Requirements** | `docs/requirements.md` | [MODIFY] | Documented `REQ-BLD-001` with Given-When-Then criteria and marked as `Verified`. |
| **Living Test Specs** | `docs/tests.md` | [MODIFY] | Documented `TST-BLD-001` test suite specification and marked as `Verified`. |

---

## 3. Verification & Test Results

### 3.1 Automated Tests
* **ProGuard & Enum Contract Tests (`ProGuardRulesContractTest.kt`)**:
  - `testProGuardRules_composeBlanketKeepRemoved`: PASSED (asserts blanket rule is absent).
  - `testProGuardRules_requiredRulesPresent`: PASSED (asserts domain enum, FIT, ANT+, Pebble, Dropbox, ViewModel, and stacktrace rules are present).
  - `testDomainEnums_reflectionAndSerializationSafety`: PASSED (asserts `Enum.valueOf` resolves across all core domain enums).
* **Release Assembly & Mapping Verification**:
  - Command: `./gradlew :app:assembleRelease`
  - Result: **BUILD SUCCESSFUL**.
  - Generated Mapping: `app/build/outputs/mapping/release/mapping.txt` (160 MB).
  - Generated APK: `app/build/outputs/apk/release/app-release.apk` (12 MB).
* **DEX Obfuscation Ratio**:
  - Total mapped classes: 17,115
  - Obfuscated classes: 10,063
  - Obfuscation percentage: **58.80%** (Threshold $\ge 25\%$ cleanly exceeded).
* **Full Clean-Room Test Suite**:
  - Command: `./gradlew testDebugUnitTest`
  - Result: **3158/3158 tests passing (100% GREEN, 0 regressions)**.

### 3.2 Requirement Governance Verification
* Script: `python3 tools/verify_requirement_governance.py`
* Result: Clean execution, 100% compliance.
