# Stage 1 Analysis: ATT-5561 - Restore <uses-library android:name="org.apache.http.legacy" /> in AndroidManifest.xml for Google Maps (dynamite_mapsdynamite)

**Ticket**: [ATT-5561](https://rainerblind.atlassian.net/browse/ATT-5561)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5561`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
* **Production Crash**: Firebase Crashlytics issue `a98c3428ef94b7c3f0bf39941f9a97a6` (v4.9.41): `Fatal Exception: java.lang.NoClassDefFoundError: Failed resolution of: Lorg/apache/http/ProtocolVersion;` caused by `ClassNotFoundException: org.apache.http.ProtocolVersion`.
* **Crash Trigger**: Crash occurs when Google Maps initializes its dynamically loaded Google Play Services module (`dynamite_mapsdynamite`), whose internal Volley `RequestQueue` (`NetworkDispatcher`) instantiates `org.apache.http.ProtocolVersion` ("HTTP/1.1").
* **Expected Behavior**: Map initializes cleanly without throwing `NoClassDefFoundError` on Android 9+ (API $\ge 28$) devices.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: Since Android 9 (API 28), `org.apache.http.*` is excluded from the default bootclasspath. The Google Maps SDK runtime module (`com.google.android.gms.dynamite_mapsdynamite`) relies on Volley `HurlStack`, which references `org.apache.http.ProtocolVersion`. Android requires `<uses-library android:name="org.apache.http.legacy" android:required="false" />` in `<application>` to inject `/system/framework/org.apache.http.legacy.jar` into the app ClassLoader.
* **Stacktrace Deobfuscation Collision**: The Crashlytics trace references Compose frames (`AndroidSelectionHandles_androidKt`, `AppCompatDelegateImpl$4$1.run`), which are R8 mapping collisions with `dynamite_mapsdynamite` background queue worker threads (4 `NetworkDispatcher` threads + 1 `CacheDispatcher` thread waiting on `PriorityBlockingQueue.take()`).
* **Independent Peer Review & Verification**:
  * Audited commit `4818be7f`: `<uses-library android:name="org.apache.http.legacy" android:required="false" />` was originally added above `com.google.android.maps.v2.API_KEY` specifically for Google Maps integration.
  * Audited commit `8bb3398a` (`ATT-3551`): Deleting this tag as a supposed legacy Runkeeper artifact broke Maps runtime on API 28+ devices.
  * Audited `LegacyHttpClientPurgeContractTest.kt`: `verifyNoApacheHttpInAndroidManifest()` enforced this broken state via an inverted assertion.
  * Audited `app/proguard-rules.pro`: `-dontwarn org.apache.http.**` was deleted in `8bb3398a` and must be restored to prevent R8 build warnings.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * Restore `<uses-library android:name="org.apache.http.legacy" android:required="false" />` in `app/src/main/AndroidManifest.xml` above Google Maps API key with explanatory documentation.
  * Restore `-dontwarn org.apache.http.**` in `app/proguard-rules.pro` under `# --- Legacy Dependency Fixes ---`.
  * Update `LegacyHttpClientPurgeContractTest.kt` to assert presence of `org.apache.http.legacy` in manifest while maintaining prohibition of app source imports and Gradle dependencies.
  * Align living documentation in `docs/requirements/` (`req_data_migration_export.md`, `req_process_stability.md`) and `docs/tests/` (`tst_data_migration_export.md`).
* **Out-of-Scope Non-Goals**:
  * Re-adding `org.apache.httpcomponents:*` dependencies to `app/build.gradle` (production source remains 100% OkHttp3/Coroutines).
  * Re-introducing dead Runkeeper/TrainingPeaks activities or files.
* **Complexity Rating**: **1 SP (Trivial)** (Single manifest tag, ProGuard rule, test assertion update).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-EXP-020` (*Decommission Obsolete Runkeeper and TrainingPeaks Legacy Exporters*), `REQ-BLD-003` (*Manifest Component Security & Hygiene*), and `TST-EXP-017`. Targets `AndroidManifest.xml`, `proguard-rules.pro`, and `LegacyHttpClientPurgeContractTest.kt`.
* **Historical Origin & Commit Trace**: Added in `4818be7f` for Google Maps; removed in `8bb3398a` (ATT-3551) due to confusion between standalone Apache HttpClient dependencies and the Android system legacy library for Google Maps.
* **Root Reason for Existing Formulation**: ATT-3551 intended to purge obsolete third-party libraries and conflated the platform's `org.apache.http.legacy` library with dead Runkeeper code.
* **Preservation of Core Invariants**:
  1. *Zero Source Import Invariant*: Production source code retains 0 imports of `org.apache.http.*`.
  2. *Build Dependency Cleanliness*: Zero `org.apache.httpcomponents` dependencies in `app/build.gradle`.
  3. *Optional Library Invariant*: `android:required="false"` ensures devices lacking the optional library will still boot.

---

## 5. Architectural Strategy & High-Level Solution
* **Manifest**: Add `<uses-library android:name="org.apache.http.legacy" android:required="false" />` inside `<application>` in `app/src/main/AndroidManifest.xml` directly preceding `<meta-data android:name="com.google.android.maps.v2.API_KEY" ... />`.
* **ProGuard**: Append `-dontwarn org.apache.http.**` under `# --- Legacy Dependency Fixes ---` in `app/proguard-rules.pro`.
* **Contract Test**: In `LegacyHttpClientPurgeContractTest.kt`, replace `verifyNoApacheHttpInAndroidManifest()` with `verifyGoogleMapsApacheHttpLegacyLibraryInAndroidManifest()`, asserting `content.contains("<uses-library\n            android:name=\"org.apache.http.legacy\"\n            android:required=\"false\" />")`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero `org.apache.httpcomponents` Gradle dependencies; zero `org.apache.http.*` imports in `app/src/main/`; full test suite passes.
* **Risk Rating**: **LOW** (Declarative manifest and ProGuard restore with zero logic modifications, standard requirement per Google Maps SDK documentation).
