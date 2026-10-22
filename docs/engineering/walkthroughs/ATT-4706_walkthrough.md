# Stage 5: Walkthrough & Verification - ATT-4706: Scope Strava OAuth Redirect URI Scheme by applicationId

**Ticket**: [ATT-4706](https://rainerblind.atlassian.net/browse/ATT-4706)  
**Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4706`  
**Requirement Mapping**: `REQ-EXT-012` (`REQ-EXT-012.2b`, `REQ-EXT-012.6a`–`c`)  
**Test Mapping**: `TST-EXT-012` (`TST-EXT-012.1`–`6`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Executive Summary & Verification Overview
Scoped Strava OAuth redirect URI scheme by `strava.${BuildConfig.APPLICATION_ID}` (`strava.com.atrainingtracker://...` for release, `strava.com.atrainingtracker.debug://...` for debug) with host `rainerblind.github.io`. This completely resolves intent collisions between co-installed release, debug, and legacy release builds that lack `android:path`, eliminating Android's `ResolverActivity` ("Öffnen mit") dialog and ensuring instant direct routing to the initiating app variant.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-EXT-012.6b` | `TST-EXT-012.1` | `StravaHelperTest.testGetRedirectUri_scopedByApplicationId` | **PASSED** | `Verified` |
| `REQ-EXT-012.2b` | `TST-EXT-012.2` | `StravaOAuthCallbackActivityTest.testHandleIntent_validStateParameter` | **PASSED** | `Verified` |
| `REQ-EXT-012.6c` | `TST-EXT-012.3` | `StravaOAuthCallbackActivityTest.testHandleIntent_mismatchedApplicationIdScheme` | **PASSED** | `Verified` |
| `REQ-EXT-012.6c` | `TST-EXT-012.4` | `StravaOAuthCallbackActivityTest.testHandleIntent_legacyUnscopedUri` | **PASSED** | `Verified` |
| `REQ-EXT-012.6a` | `TST-EXT-012.5` | `StravaOAuthSecurityTest.testManifest_stravaOAuthCallbackActivity_declaresApplicationIdSchemeScoping` | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-EXT-012.6` | Targeted Package Tests (`onlinecommunities.strava.*`, `build.*`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Contract Test Execution
```text
./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.onlinecommunities.strava.*" --tests "com.atrainingtracker.trainingtracker.build.*"
BUILD SUCCESSFUL in 2s
32 actionable tasks: 1 from cache, 31 up-to-date
```

### Requirement Governance Audit (`REQ-PRO-022`)
```text
python3 tools/verify_requirement_governance.py --text-file docs/engineering/test_specs/ATT-4706_test_spec.md
Modified/Altered existing requirement(s) detected: REQ-EXT-012.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Hardware / Physical Verification (Pixel 10 Live Resolution Proof)

Deploying `app-debug.apk` to connected Pixel 10 and querying Android's `PackageManager` for `strava.com.atrainingtracker.debug://rainerblind.github.io` confirms strictly 1 resolving activity (`com.atrainingtracker.debug`):
```text
$ adb shell cmd package query-activities -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d "strava.com.atrainingtracker.debug://rainerblind.github.io?state=123&code=456"
1 activities found:
  Activity #0:
    priority=0 preferredOrder=0 match=0x308000 specificIndex=-1 isDefault=true
    ActivityInfo:
      name=com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaOAuthCallbackActivity
      packageName=com.atrainingtracker.debug
```
* **Co-installed Release App Collision Check**: Querying `strava.com.atrainingtracker.debug://...` returns **0 matches** on `com.atrainingtracker` (no collision). Legacy `strava://...` matches only the legacy release build.

---

## 5. Invariant & Governance Verification
1. **Zero Regressions**: Host remains `rainerblind.github.io` (Strava Callback Domain); CSRF state validation (`REQ-EXT-012.1`), repository scope exchange, and `<queries>` for `com.strava` remain intact.
2. **Living Documentation Synchronized**: `REQ-EXT-012` and `TST-EXT-012` updated to `Verified`.
3. **Parent Handover**: Ready for Gate 5 sign-off and merge to sprint branch.
