# Stage 3 Implementation Plan: Enable R8 Minification and DEX Code Obfuscation in Release Builds (ATT-3770)

* **Parent Ticket**: [ATT-3770](https://rainerblind.atlassian.net/browse/ATT-3770) (*[Medium] Enable R8 minification and DEX code obfuscation in release builds with safe ProGuard keep rules to satisfy Google Play threshold (>= 25%)*)
* **Subtask**: [ATT-4108](https://rainerblind.atlassian.net/browse/ATT-4108) (*[Impl-Plan] [Medium] Enable R8 minification and DEX code obfuscation in release builds with safe ProGuard keep rules to satisfy Google Play threshold (>= 25%)*)
* **Requirement**: `REQ-BLD-001` (Specified)
* **Test Specification**: `TST-BLD-001` (Specified)
* **Target Version**: `V4.9.40`
* **Branch**: `feature/ATT-3770`

---

## 1. Architectural Decomposition (SWE.2)

To satisfy Google Play Console's mandatory Android vitals threshold ($\ge 25\%$ DEX code obfuscation) while ensuring 100% runtime reflection stability and zero data loss, the changes are decoupled into four modular engineering areas:

```
┌──────────────────────────────────────────────────────────────┐
│                    app/build.gradle                          │
│ • Enable minifyEnabled = true and shrinkResources = true     │
│   in buildTypes.release                                      │
└──────────────────────────────┬───────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                 app/proguard-rules.pro                       │
│ • Remove blanket -keep class androidx.compose.**             │
│ • Add domain enums keep rules (-keepclassmembers enum ...)    │
│ • Add Garmin FIT SDK (-keep class com.garmin.fit.**)         │
│ • Add ANT+ (-keep class com.dsi.ant.**)                      │
│ • Add PebbleKit (-keep class com.getpebble.android.kit.**)   │
│ • Add Dropbox SDK (-keep class com.dropbox.core.**)          │
│ • Add ViewModel constructors (-keepclassmembers ViewModel)   │
│ • Add SourceFile,LineNumberTable attributes                  │
└──────────────────────────────┬───────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│        ProGuardRulesContractTest.kt (Unit Test)              │
│ • Validates proguard-rules.pro composition via AST/regex    │
│ • Validates all core domain enum lookups via Enum.valueOf    │
└──────────────────────────────┬───────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│        Release Build Assembly (:app:assembleRelease)         │
│ • R8 executes dead-code elimination, optimization & dexing   │
│ • Generates mapping.txt and obfuscated release DEX files     │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. Atomic Implementation Steps

### Step 1: Update `app/build.gradle` (Release Build Configuration)
* **Target File**: `app/build.gradle`
* **Changes**:
  - Update `android.buildTypes.release`:
    ```groovy
    release {
        applicationIdSuffix ""
        debuggable = false

        minifyEnabled = true
        shrinkResources = true
        proguardFiles = [getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro']
        signingConfig = signingConfigs.release
    }
    ```
* **Rationale**: Replaces `minifyEnabled = false` with `true` and enables `shrinkResources = true` so R8 shrinks unused bytecode and assets in release builds.

### Step 2: Refactor `app/proguard-rules.pro` (Comprehensive Keep Rules)
* **Target File**: `app/proguard-rules.pro`
* **Changes**:
  1. Remove blanket rule: `-keep class androidx.compose.** { *; }`.
  2. Preserve domain enums:
     ```proguard
     # --- Domain Enums (SQLite & DataStore Name Resolution) ---
     -keepclassmembers enum com.atrainingtracker.** {
         public static **[] values();
         public static ** valueOf(java.lang.String);
         public static ** safeValueOf(java.lang.String);
         <fields>;
     }
     ```
  3. Preserve hardware SDKs and parsers:
     ```proguard
     # --- Garmin FIT SDK ---
     -keep class com.garmin.fit.** { *; }
     -dontwarn com.garmin.fit.**

     # --- ANT+ Plugin Library ---
     -keep class com.dsi.ant.** { *; }
     -dontwarn com.dsi.ant.**

     # --- PebbleKit ---
     -keep class com.getpebble.android.kit.** { *; }
     -dontwarn com.getpebble.android.kit.**

     # --- Dropbox SDK ---
     -keep class com.dropbox.core.** { *; }
     -dontwarn com.dropbox.core.**

     # --- GPX Parser ---
     -keep class io.ticofab.androidgpxparser.** { *; }
     ```
  4. Preserve ViewModel constructors and stacktrace mappings:
     ```proguard
     # --- AndroidX Lifecycle ViewModels ---
     -keepclassmembers class * extends androidx.lifecycle.ViewModel {
         <init>(...);
     }

     # --- Source Line Number & Stacktrace Preservation ---
     -keepattributes SourceFile,LineNumberTable
     -renamesourcefileattribute SourceFile
     ```

### Step 3: Implement `ProGuardRulesContractTest.kt`
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ProGuardRulesContractTest.kt`
* **Test Cases**:
  - `testProGuardRules_composeBlanketKeepRemoved`: Asserts `app/proguard-rules.pro` does NOT contain `-keep class androidx.compose.**`.
  - `testProGuardRules_requiredRulesPresent`: Asserts domain enums, Garmin FIT, ANT+, Pebble, Dropbox, ViewModel, and stacktrace rules are present.
  - `testDomainEnums_reflectionAndSerializationSafety`: Exercises `Enum.valueOf` across all domain enums (`SensorType`, `BSportType`, `DeviceType`, `MyUnits`, `ZoneType`, `FilterType`, `FileFormat`, `ExportStatus`, `EquipmentType`).

### Step 4: Verification & Release Build Execution
* **Commands**:
  - Run targeted test: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ProGuardRulesContractTest"`
  - Run release assembly: `./gradlew :app:assembleRelease`
  - Verify generated `app/build/outputs/mapping/release/mapping.txt` and verify DEX obfuscation ratio $\ge 25\%$.
  - Run full clean-room unit test suite: `./gradlew testDebugUnitTest`.

---

## 3. UI Consistency (Rule 23)
* **Screen Mode & Visual Footprint**: N/A (Build configuration and ProGuard bytecode rules; zero UI visual layout or styling changes).
* **Reference UI**: Unchanged.

---

## 4. Invariant Protection & Rollback Safety

1. **SQLite & DataStore Enum Safety**: All domain enums maintain un-obfuscated names and standard `valueOf` methods, guaranteeing zero deserialization failure for persisted workouts or user preferences.
2. **Crashlytics Deobfuscation**: `SourceFile` and `LineNumberTable` keep rules ensure that production crashes uploaded to Crashlytics / Play Console can be mapped back to exact source lines via `mapping.txt`.
3. **Debug Build Performance**: Debug builds retain `minifyEnabled false`, maintaining sub-15-second iteration cycles for developers and automated unit tests.
4. **Clean-Room Test Integrity**: 100% of unit tests in `./gradlew testDebugUnitTest` must pass cleanly.
