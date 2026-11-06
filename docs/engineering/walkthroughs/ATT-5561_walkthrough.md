# Stage 5 Walkthrough: ATT-5561 — Restore org.apache.http.legacy in AndroidManifest.xml for Google Maps

**Ticket**: [ATT-5561](https://rainerblind.atlassian.net/browse/ATT-5561)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5561`  
**Requirement Mapping**: `REQ-EXP-020` (`REQ-EXP-020.2`), `REQ-BLD-003` (`REQ-BLD-003.6`)  
**Test Mapping**: `TST-EXP-017` (`TST-EXP-017.1`–`TST-EXP-017.4`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Executive Summary
Resolved a fatal production runtime crash (`NoClassDefFoundError: Failed resolution of: Lorg/apache/http/ProtocolVersion;`, Firebase Crashlytics issue `a98c3428ef94b7c3f0bf39941f9a97a6`) occurring when opening Google Maps on Android 9+ (API $\ge 28$) devices. Forensic analysis revealed that while ATT-3551 correctly decommissioned legacy Runkeeper/TrainingPeaks code and `org.apache.httpcomponents:*` dependencies, it inadvertently deleted `<uses-library android:name="org.apache.http.legacy" android:required="false" />` from `AndroidManifest.xml` and `-dontwarn org.apache.http.**` from `proguard-rules.pro`. The Google Maps dynamic runtime module (`com.google.android.gms.dynamite_mapsdynamite`) relies on Volley `HurlStack`, which instantiates `ProtocolVersion` during map tile retrieval. The system library was restored inside `<application>` directly preceding the Maps API key declaration, ProGuard warning suppression re-established, and `LegacyHttpClientPurgeContractTest.kt` converted into an automated regression guard asserting the library's presence while preserving zero-Apache dependency and import invariants.

---

## 2. Key Architectural Changes
| File / Component | Summary of Changes |
| :--- | :--- |
| `app/src/main/AndroidManifest.xml` | Restored `<uses-library android:name="org.apache.http.legacy" android:required="false" />` inside `<application>` directly preceding Google Maps API key metadata declaration, accompanied by explanatory architectural documentation (`REQ-EXP-020.2`, `REQ-BLD-003.6`). |
| `app/proguard-rules.pro` | Restored `-dontwarn org.apache.http.**` under `# --- Legacy Dependency Fixes ---` to eliminate R8 class resolution warnings (`REQ-EXP-020.2`). |
| `LegacyHttpClientPurgeContractTest.kt` | Replaced `verifyNoApacheHttpInAndroidManifest()` with `verifyGoogleMapsApacheHttpLegacyLibraryInAndroidManifest()`, asserting presence of `org.apache.http.legacy` with `android:required="false"` while retaining tests for 0 Apache source imports and 0 Gradle dependencies (`TST-EXP-017.1`, `TST-EXP-017.3`). |
| Living Documentation | Updated `docs/requirements/req_data_migration_export.md` (`REQ-EXP-020`), `docs/tests/tst_data_migration_export.md` (`TST-EXP-017`), `docs/requirements.md`, and `docs/tests.md` to `Verified` status with 100% mathematical parity. |

---

## 3. Verification & Governance
* **Tier 1 Targeted Unit Tests**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.exporter.*" --tests "com.atrainingtracker.trainingtracker.build.*"` -> `BUILD SUCCESSFUL` (204 tests completed, 0 failures, 100% PASS).
* **AAPT2 Resource Compilation**: `./gradlew processDebugResources` -> `BUILD SUCCESSFUL` (18 tasks up-to-date, zero manifest or XML errors).
* **Requirement Governance**: `python3 tools/verify_requirement_governance.py` -> `PASS` (100% master table parity across all 9 shards, Chesterton's Fence archaeology audit verified).
* **Physical Device Deployment**: Non-UI manifest and ProGuard configuration ticket (no composable or navigation modifications); verified via contract and build automation.

---

## 4. Requirement & Test Traceability
| Requirement ID | Test ID | Status | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-EXP-020.2` | `TST-EXP-017.1` | `Verified` | Contract Test: `LegacyHttpClientPurgeContractTest.verifyGoogleMapsApacheHttpLegacyLibraryInAndroidManifest` |
| `REQ-EXP-020.2` | `TST-EXP-017.2` | `Verified` | Configuration: `app/proguard-rules.pro` `-dontwarn org.apache.http.**` |
| `REQ-EXP-020.2` | `TST-EXP-017.3` | `Verified` | Contract Tests: `verifyNoApacheHttpImportsInMainSource` & `verifyNoApacheHttpDependenciesInBuildGradle` |
| `REQ-EXP-020` | `TST-EXP-017.4` | `Verified` | Full Tier 1 targeted suite (`exporter.*`, `build.*`) & governance verification (100% PASS) |
