# Stage 2: Requirement & Test Specification - ATT-4668: Scope Strava OAuth redirect URI by applicationId

**Ticket**: [ATT-4668](https://rainerblind.atlassian.net/browse/ATT-4668)  
**Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4668`  
**Requirement Mapping**: `REQ-EXT-012` (*Strava OAuth 2.0 State Parameter CSRF Protection, Structured Repository Scope Exchange, Application ID Callback Scoping, and Legacy Callback Deletion*)  
**Test Spec ID**: `TST-EXT-012`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Requirement Specification (`REQ-EXT-012`)

### 1.1 Problem Statement & Rationale
When release (`com.atrainingtracker`) and debug (`com.atrainingtracker.debug`) builds are co-installed on the same device, returning from Strava OAuth displays an Android system app chooser because both variants declare identical intent filters. If the user picks the opposite variant, authentication fails due to mismatched CSRF `state`. Scoping the callback path to `/${applicationId}` eliminates collisions.

### 1.2 Functional & Architectural Requirements
1. **Manifest Path Scoping (`REQ-EXT-012.6a`)**: `AndroidManifest.xml` intent filter for `StravaOAuthCallbackActivity` SHALL declare `<data android:scheme="strava" android:host="rainerblind.github.io" android:path="/${applicationId}" />`.
2. **Dynamic Redirect URI Construction (`REQ-EXT-012.6b`)**: `StravaHelper.getRedirectUri()` SHALL return `"strava://rainerblind.github.io/${BuildConfig.APPLICATION_ID}"`.
3. **Callback Path Validation (`REQ-EXT-012.6c`)**: `StravaOAuthCallbackActivity` SHALL validate that incoming callback URIs match prefix `"strava://rainerblind.github.io/${BuildConfig.APPLICATION_ID}"`. Incoming callbacks with mismatched or absent package paths MUST be rejected/ignored without initiating token exchange.
4. **Invariant Preservation (`REQ-EXT-012.6d`)**: CSRF state token generation and single-use consumption (`REQ-EXT-012.1`, `REQ-EXT-012.2`), Custom Tabs launch (`REQ-EXT-004`), and repository scope exchange (`REQ-EXT-012.4`) MUST remain strictly intact.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-EXT-012` (`REQ-EXT-012.2`, `REQ-EXT-012.6`), targeting `AndroidManifest.xml`, `StravaHelper.kt`, `StravaOAuthCallbackActivity.kt`, and test suites `StravaHelperTest.kt` / `StravaOAuthCallbackActivityTest.kt`.
* **Historical Origin & Commit Trace**: Ticket `ATT-4022`, commit `943dc3b4` (Sprint `2026-42.10`), refining `REQ-EXT-004` and `REQ-EXT-005` under Epic `ATT-597`. Scoping refined in ATT-4668 (Sprint `2026-43.7`).
* **Root Reason for Existing Formulation**: `REQ-EXT-012` introduced CSRF protection for `strava://rainerblind.github.io` without variant path scoping, assuming single-variant installations. Because both release and debug variants registered identical filters, co-installation caused an Android intent chooser dialog that broke authentication if the wrong variant was selected.
* **Preservation of Core Invariants**: Callback domain `rainerblind.github.io`, CSRF state validation (`REQ-EXT-012.1`), repository scope exchange, Custom Tabs integration, and 100% test pass rate are strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Auth URL Construction)**: *Given* an authorization request, *When* `StravaHelper.getAuthorizationUrl(state)` executes, *Then* `redirect_uri` contains `strava://rainerblind.github.io/${BuildConfig.APPLICATION_ID}`.
* **Criterion 2 (Variant Routing & Disambiguation)**: *Given* co-installed release and debug builds, *When* Strava redirects to `strava://rainerblind.github.io/com.atrainingtracker.debug?...`, *Then* Android routes directly to the debug activity with zero disambiguation dialog.
* **Criterion 3 (Mismatched Path Rejection)**: *Given* a callback URI targeting another variant, *When* `StravaOAuthCallbackActivity` receives it, *Then* the activity finishes without consuming state or exchanging code.

---

## 2. Test Specification (`TST-EXT-012`)

### Test Cases
* `TST-EXT-012.1` (**Authorization URL Path Scoping**): Verify `StravaHelper.getAuthorizationUrl()` includes `redirect_uri=strava%3A%2F%2Frainerblind.github.io%2F${BuildConfig.APPLICATION_ID}` in `StravaHelperTest.kt`.
* `TST-EXT-012.2` (**Callback Activity Application ID Match**): Verify `StravaOAuthCallbackActivity` accepts matching variant path, validates state, and dispatches token exchange in `StravaOAuthCallbackActivityTest.kt`.
* `TST-EXT-012.3` (**Callback Activity Mismatched Variant Rejection**): Verify callback with mismatched application ID path is ignored/rejected without state consumption or token exchange.
* `TST-EXT-012.4` (**Callback Activity Missing Path Rejection**): Verify legacy un-scoped URI (`strava://rainerblind.github.io`) is rejected cleanly.
* `TST-EXT-012.5` (**Manifest Intent Filter Contract**): Verify `AndroidManifest.xml` configures `android:path="/${applicationId}"` for `StravaOAuthCallbackActivity`.
* `TST-EXT-012.6` (**Clean-Room Regression Suite**): `./gradlew testDebugUnitTest` achieves 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-EXT-012.1` | Unit | `StravaHelperTest.kt`, `StravaHelper.kt` | `REQ-EXT-012.6b` | Proposed |
| `TST-EXT-012.2` | Unit | `StravaOAuthCallbackActivityTest.kt`, `StravaOAuthCallbackActivity.kt` | `REQ-EXT-012.6c` | Proposed |
| `TST-EXT-012.3` | Unit | `StravaOAuthCallbackActivityTest.kt`, `StravaOAuthCallbackActivity.kt` | `REQ-EXT-012.6c` | Proposed |
| `TST-EXT-012.4` | Unit | `StravaOAuthCallbackActivityTest.kt`, `StravaOAuthCallbackActivity.kt` | `REQ-EXT-012.6c` | Proposed |
| `TST-EXT-012.5` | Contract | `AndroidManifest.xml` | `REQ-EXT-012.6a` | Proposed |
| `REQ-PRO-001` / `TST-EXT-012.6` | Regression | Full test suite (`./gradlew testDebugUnitTest`) | `REQ-EXT-012` | Proposed |
