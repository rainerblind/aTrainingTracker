# Stage 5: Walkthrough & Verification - ATT-4668: Scope Strava OAuth redirect URI by applicationId

**Ticket**: [ATT-4668](https://rainerblind.atlassian.net/browse/ATT-4668)  
**Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4668`  
**Requirement Mapping**: `REQ-EXT-012` (`REQ-EXT-012.6a`–`d`)  
**Test Mapping**: `TST-EXT-012` (`TST-EXT-012.1`–`6`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Executive Summary & Verification Overview
Scoped Strava OAuth redirect URI path by `/${BuildConfig.APPLICATION_ID}` (`/com.atrainingtracker` for release, `/com.atrainingtracker.debug` for debug). This resolves intent collisions when release and debug variants are co-installed on the same device, eliminating the Android app-disambiguation chooser dialog and ensuring authentication redirects route exclusively to the initiating build variant.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-EXT-012.6b` | `TST-EXT-012.1` | `StravaHelperTest.testGetRedirectUri_scopedByApplicationId` | **PASSED** | `Verified` |
| `REQ-EXT-012.6c` | `TST-EXT-012.2` | `StravaOAuthCallbackActivityTest.testHandleIntent_validStateParameter` | **PASSED** | `Verified` |
| `REQ-EXT-012.6c` | `TST-EXT-012.3` | `StravaOAuthCallbackActivityTest.testHandleIntent_mismatchedApplicationIdPath` | **PASSED** | `Verified` |
| `REQ-EXT-012.6c` | `TST-EXT-012.4` | `StravaOAuthCallbackActivityTest.testHandleIntent_legacyUnscopedUri` | **PASSED** | `Verified` |
| `REQ-EXT-012.6a` | `TST-EXT-012.5` | `StravaOAuthSecurityTest.testManifest_declaresApplicationIdPathScoping` | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-EXT-012.6` | Targeted Package Tests (`onlinecommunities.strava.*`, `build.*`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Contract Test Execution
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.onlinecommunities.strava.*" --tests "com.atrainingtracker.trainingtracker.build.*"
BUILD SUCCESSFUL in 1s
32 actionable tasks: 1 from cache, 31 up-to-date
```

### Requirement Governance Audit (`REQ-PRO-022`)
```text
python3 tools/verify_requirement_governance.py --text-file docs/engineering/test_specs/ATT-4668_test_spec.md
Modified/Altered existing requirement(s) detected: REQ-EXT-012.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Hardware / Physical Verification (Pixel 10)
Pure headless manifest intent filter and background OAuth redirect URI scoping; zero UI composable or layout modifications. Verified via automated AndroidManifest contract test and Robolectric callback activity isolation tests.

### Visual Consistency (Rule 23)
* No UI changes.

---

## 5. Invariant & Governance Verification
1. **Zero Regressions**: CSRF state verification, Custom Tabs integration, and single-variant backwards compatibility preserved.
2. **Living Documentation Synchronized**: `REQ-EXT-012` and `TST-EXT-012` updated to `Verified`.
3. **Subtask Governance**: Stage 5 subtask submitted for Gate 5 review; parent ticket ready for `Final Review (Human)`.
