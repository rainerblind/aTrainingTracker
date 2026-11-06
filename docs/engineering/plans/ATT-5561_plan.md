# Stage 3: Implementation Plan - ATT-5561: Restore org.apache.http.legacy for Google Maps

**Ticket**: [ATT-5561](https://rainerblind.atlassian.net/browse/ATT-5561)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5561`  
**Requirement Mapping**: `REQ-EXP-020` (`REQ-EXP-020.2`), `REQ-BLD-003` (`REQ-BLD-003.6`)  
**Test Mapping**: `TST-EXP-017` (`TST-EXP-017.1`–`TST-EXP-017.4`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
On Android 9+ (API $\ge 28$), the platform excludes `org.apache.http.*` from the bootclasspath. When Google Maps initializes its dynamically loaded runtime module (`com.google.android.gms.dynamite_mapsdynamite`), Volley's `NetworkDispatcher` instantiates `org.apache.http.ProtocolVersion`, crashing with `NoClassDefFoundError` (Crashlytics issue `a98c3428ef94b7c3f0bf39941f9a97a6`). While ATT-3551 correctly purged dead Runkeeper/TrainingPeaks code and `org.apache.httpcomponents` dependencies, it mistakenly deleted the system library declaration `<uses-library android:name="org.apache.http.legacy" android:required="false" />` and `-dontwarn org.apache.http.**`. This plan defines the exact restoration steps and contract test updates.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-EXP-020.2` (Retain Google Maps legacy platform library in manifest & ProGuard), `REQ-BLD-003.6` (Manifest configuration hygiene).
* **Test Mapping**: `TST-EXP-017.1` (Manifest contract test), `TST-EXP-017.2` (ProGuard verification), `TST-EXP-017.3` (Source & Gradle dependency isolation), `TST-EXP-017.4` (Targeted test regression).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Source Imports Invariant**: Production code in `app/src/main/java` strictly retains 0 imports of `org.apache.http.*`.
2. **Clean Dependencies Invariant**: `app/build.gradle` strictly retains 0 `org.apache.httpcomponents` dependencies.
3. **Optional Library Invariant**: `android:required="false"` ensures device compatibility and prevents boot failure on environments lacking the library.
4. **Code Lock (Rule 4)**: Zero production code edits in `app/src/` until Gate 3 passes audit.

---

## 4. Proposed Architectural Changes
### Component 1: `app/src/main/AndroidManifest.xml`
* Declare `<uses-library android:name="org.apache.http.legacy" android:required="false" />` directly preceding `<meta-data android:name="com.google.android.maps.v2.API_KEY" ... />` inside `<application>`.
* Include architectural documentation clarifying that Google Maps SDK (`dynamite_mapsdynamite`) requires this platform library for Volley HTTP protocol handling on API 28+.

### Component 2: `app/proguard-rules.pro`
* Add `-dontwarn org.apache.http.**` under `# --- Legacy Dependency Fixes ---` to eliminate R8 missing-class warnings during build.

### Component 3: `LegacyHttpClientPurgeContractTest.kt`
* Replace `verifyNoApacheHttpInAndroidManifest()` with `verifyGoogleMapsApacheHttpLegacyLibraryInAndroidManifest()`.
* Assert `<uses-library android:name="org.apache.http.legacy" android:required="false" />` is declared in `AndroidManifest.xml`.
* Preserve existing assertions verifying zero `org.apache.http` imports in `app/src/main/` and zero `org.apache.httpcomponents` in `app/build.gradle`.

### UI Consistency (Rule 23)
* No UI changes (configuration, manifest, and test harness only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Update `app/src/main/AndroidManifest.xml` (`REQ-EXP-020.2`)
* Add `<uses-library android:name="org.apache.http.legacy" android:required="false" />` with explanatory comment.

### Step 2: Update `app/proguard-rules.pro` (`REQ-EXP-020.2`)
* Add `-dontwarn org.apache.http.**` under `# --- Legacy Dependency Fixes ---`.

### Step 3: Update `LegacyHttpClientPurgeContractTest.kt` (`TST-EXP-017.1`)
* Update contract test to verify presence of `org.apache.http.legacy` in `AndroidManifest.xml`.

### Step 4: Verification Gate (`TST-EXP-017.4`)
* Execute targeted unit test suite and governance checks:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.exporter.LegacyHttpClientPurgeContractTest"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Run `LegacyHttpClientPurgeContractTest` and build verification (`./gradlew assembleDebug`).
* **Rollback**: Revert working tree via `git checkout feature/ATT-5561 -- .`.
