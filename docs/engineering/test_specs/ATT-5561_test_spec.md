# Stage 2: Requirement & Test Specification - ATT-5561: Restore org.apache.http.legacy for Google Maps

**Ticket**: [ATT-5561](https://rainerblind.atlassian.net/browse/ATT-5561)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5561`  
**Requirement Mapping**: `REQ-EXP-020`, `REQ-BLD-003` | **Test Spec ID**: `TST-EXP-017`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-EXP-020`, `REQ-BLD-003`)

### 1.1 Problem Statement & Rationale
On Android 9+ (API $\ge 28$), the platform excludes `org.apache.http.*` from the bootclasspath. When Google Maps initializes its dynamic runtime module (`com.google.android.gms.dynamite_mapsdynamite`), Volley's `HurlStack` worker threads instantiate `org.apache.http.ProtocolVersion`, crashing with `NoClassDefFoundError: Failed resolution of: Lorg/apache/http/ProtocolVersion;` (Firebase Crashlytics issue `a98c3428ef94b7c3f0bf39941f9a97a6`). While ATT-3551 correctly decommissioned dead Runkeeper/TrainingPeaks code and `org.apache.httpcomponents:*` dependencies, it mistakenly purged `<uses-library android:name="org.apache.http.legacy" android:required="false" />` and `-dontwarn org.apache.http.**`.

### 1.2 Functional & Architectural Requirements
1. **Manifest Platform Library Declaration (`REQ-EXP-020.2` / `REQ-BLD-003.6`)**: `app/src/main/AndroidManifest.xml` SHALL declare `<uses-library android:name="org.apache.http.legacy" android:required="false" />` inside `<application>` directly preceding `<meta-data android:name="com.google.android.maps.v2.API_KEY" ... />` accompanied by an explicit architectural comment.
2. **ProGuard Rule Restoration (`REQ-EXP-020.2`)**: `app/proguard-rules.pro` SHALL retain `-dontwarn org.apache.http.**` under `# --- Legacy Dependency Fixes ---`.
3. **Automated Manifest Protection Contract (`REQ-EXP-020.2` / `TST-EXP-017`)**: `LegacyHttpClientPurgeContractTest.kt` SHALL verify presence of `org.apache.http.legacy` in `AndroidManifest.xml` with `android:required="false"`, while continuing to verify zero `org.apache.http.*` imports in `app/src/main/java` and zero `org.apache.httpcomponents` dependencies in `app/build.gradle`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Manifest Declaration)**: *Given* `AndroidManifest.xml`, *When* inspected, *Then* `<uses-library android:name="org.apache.http.legacy" android:required="false" />` is declared inside `<application>` before Google Maps API key metadata.
* **AC-2 (ProGuard Safety)**: *Given* `proguard-rules.pro`, *When* inspected, *Then* `-dontwarn org.apache.http.**` is present.
* **AC-3 (Dependency Cleanliness Invariant)**: *Given* `app/build.gradle` and `app/src/main/java`, *When* scanned, *Then* zero `org.apache.httpcomponents` dependencies and zero `org.apache.http.*` imports exist.
* **AC-4 (Contract Test Verification)**: *Given* `LegacyHttpClientPurgeContractTest.kt`, *When* executed, *Then* contract assertions pass.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-EXP-020` (amended in ATT-5561), targeting `AndroidManifest.xml`, `proguard-rules.pro`, and `LegacyHttpClientPurgeContractTest.kt`.
* `Historical Origin & Commit Trace`: Commit `4818be7f` originally added `org.apache.http.legacy` for Google Maps. Commit `8bb3398a` (ATT-3551) excised it while purging Runkeeper/TrainingPeaks, mistakenly assuming it was dead third-party code.
* `Root Reason for Existing Formulation`: ATT-3551 conflated standalone Apache HttpClient dependencies with the system's `org.apache.http.legacy` library required by Google Maps SDK on API 28+.
* `Preservation of Core Invariants`: Zero `org.apache.httpcomponents` Gradle dependencies; zero `org.apache.http.*` imports in `app/src/main/`; `android:required="false"` ensures device boot safety; 100% test pass rate.

---

## 2. Test Specification (`TST-EXP-017`)

### Test Case 1: Google Maps Legacy Platform Library Contract (`TST-EXP-017.1`)
* **Scope**: Contract test in `LegacyHttpClientPurgeContractTest.kt` (`verifyGoogleMapsApacheHttpLegacyLibraryInAndroidManifest`).
* **Procedure**: Read `app/src/main/AndroidManifest.xml` and assert it contains `<uses-library android:name="org.apache.http.legacy" android:required="false" />`.

### Test Case 2: ProGuard Legacy Warning Suppression (`TST-EXP-017.2`)
* **Scope**: ProGuard configuration check in `app/proguard-rules.pro`.
* **Procedure**: Assert `-dontwarn org.apache.http.**` is present under `# --- Legacy Dependency Fixes ---`.

### Test Case 3: Zero Apache Source Imports & Gradle Dependencies (`TST-EXP-017.3`)
* **Scope**: Contract tests in `LegacyHttpClientPurgeContractTest.kt` (`verifyNoApacheHttpImportsInMainSource`, `verifyNoApacheHttpDependenciesInBuildGradle`).
* **Procedure**: Assert 0 `import org.apache.http` in `app/src/main/java` and 0 `org.apache.httpcomponents` in `app/build.gradle`.

### Test Case 4: Targeted Build & Exporter Test Regression (`TST-EXP-017.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.exporter.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% test pass rate across targeted test classes.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-EXP-020.2` | Manifest `<uses-library>` declaration for Maps | `TST-EXP-017.1` | Contract Test (`LegacyHttpClientPurgeContractTest.kt`) |
| `REQ-EXP-020.2` | ProGuard `-dontwarn org.apache.http.**` rule | `TST-EXP-017.2` | ProGuard inspection & R8 build |
| `REQ-EXP-020.2` | Zero source imports & Gradle dependencies | `TST-EXP-017.3` | Contract Test (`LegacyHttpClientPurgeContractTest.kt`) |
| `REQ-EXP-020` | Full regression suite and build invariants | `TST-EXP-017.4` | `./gradlew testDebugUnitTest` |
