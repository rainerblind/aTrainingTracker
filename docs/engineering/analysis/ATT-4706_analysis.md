# Stage 1 Analysis: ATT-4706 - Scope Strava OAuth Redirect URI Scheme by applicationId

**Ticket**: [ATT-4706](https://rainerblind.atlassian.net/browse/ATT-4706)  
**Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Active Sprint**: `2026-43.7` | **Target Release**: `Sprint 2026-43.7`  
**Branch**: `feature/ATT-4706` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Statement & Motivation
When testing ATT-4668 with both debug (`com.atrainingtracker.debug`) and release (`com.atrainingtracker`, 2026-10-21 build without `android:path`) installed, Android's `ResolverActivity` ("Öffnen mit") still appeared after Strava OAuth authorization. Path scoping (`strava://rainerblind.github.io/${applicationId}`) fails because legacy builds match all paths as wildcards. Scoping the URI scheme (`strava.${applicationId}://rainerblind.github.io`) provides complete, collision-free intent isolation while fully satisfying Strava's domain-level validation.

---

## 2. Root Cause Analysis (Forensic Investigation)
| Layer / Component | Root Cause Mechanism & Failure Dynamics |
| :--- | :--- |
| `IntentFilter.matchData()` | In AOSP `IntentFilter`, declaring `scheme="strava"` and `host="rainerblind.github.io"` without `android:path` treats omitted paths as wildcards (`/*`), returning `MATCH_DATA_AUTHORITY` (`0x308000`). While path-scoped filters return `MATCH_DATA_PATH` (`0x508000`), both match scores are $\ge 0$. |
| `IntentResolver.buildResolveList()` | Android's `IntentResolver` groups and sorts candidates strictly by `android:priority` (both default to 0). It does not prune `MATCH_DATA_AUTHORITY` candidates when a `MATCH_DATA_PATH` candidate exists. Thus, the OS collects both activities and displays the system disambiguation dialog (`ResolverActivity`). |
| Strava API Validation | Live verification against Strava OAuth (`client_id=344`) confirms Strava validates only the `host` (`rainerblind.github.io`) against the registered Callback Domain, returning `HTTP 200` for custom schemes `strava.${applicationId}://rainerblind.github.io`. |

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Update `AndroidManifest.xml` on `StravaOAuthCallbackActivity` to `android:scheme="strava.${applicationId}"` and `android:host="rainerblind.github.io"`, removing `android:path`.
  * Update `StravaHelper.getRedirectUri()` and `StravaOAuthCallbackActivity.REDIRECT_URI_PREFIX` to `"strava.${BuildConfig.APPLICATION_ID}://rainerblind.github.io"`.
  * Update `handleIntent()` in `StravaOAuthCallbackActivity` to validate `uri.scheme == "strava.${BuildConfig.APPLICATION_ID}" && uri.host == "rainerblind.github.io"`.
  * Preserve `<queries>` `<intent><data android:scheme="strava" /></intent>` intact (queries external Strava app `com.strava`).
  * Update unit test suites (`StravaHelperTest`, `StravaOAuthCallbackActivityTest`, `StravaOAuthSecurityTest`) to verify scheme-based scoping.
* **Out-of-Scope Non-Goals**:
  * Modifying Strava OAuth token exchange logic, token refresh, or CSRF state validation algorithms (`REQ-EXT-012.1-4`).
  * Modifying `<queries>` or external app launch intents for `com.strava`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-EXT-012` (`REQ-EXT-012.2`, `REQ-EXT-012.6`), targeting `AndroidManifest.xml`, `StravaHelper.kt`, `StravaOAuthCallbackActivity.kt`, and test suites `StravaHelperTest.kt`, `StravaOAuthCallbackActivityTest.kt`, and `StravaOAuthSecurityTest.kt`.
* **Historical Origin & Commit Trace**: `ATT-4022` (commit `943dc3b4f6`, Sprint `2026-42.10`) introduced CSRF state protection; `ATT-4668` (commit `2ff1f4c74f`, Sprint `2026-43.7`) introduced path scoping (`android:path="/${applicationId}"`).
* **Root Reason for Existing Formulation**: ATT-4668 assumed path scoping would prevent intent collisions on co-installed builds. It overlooked that co-installed legacy builds without `android:path` treat the path as a wildcard and match with equal priority in `IntentResolver`.
* **Preservation of Core Invariants**: Callback host `rainerblind.github.io` (`REQ-EXT-004`), CSRF state validation (`REQ-EXT-012.1`), repository scope exchange (`REQ-EXT-012.4`), Custom Tabs launch, and 100% clean-room test pass rate are strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **Manifest Configuration**: Configure `<data android:scheme="strava.${applicationId}" android:host="rainerblind.github.io" />` on `StravaOAuthCallbackActivity` and retain `<queries>` `<intent><data android:scheme="strava" /></intent>` for the Strava app.
2. **Redirect URI Construction**: Update `StravaHelper.getRedirectUri()` to return `"strava.${BuildConfig.APPLICATION_ID}://rainerblind.github.io"`.
3. **Intent Validation**: Update `REDIRECT_URI_PREFIX` and `handleIntent()` in `StravaOAuthCallbackActivity` to enforce `data.scheme == "strava.${BuildConfig.APPLICATION_ID}" && data.host == "rainerblind.github.io"`.
4. **Automated Verification**: Update contract and security tests in `StravaHelperTest`, `StravaOAuthCallbackActivityTest`, and `StravaOAuthSecurityTest` to assert scheme-level scoping.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression in Strava OAuth authentication; no intent collisions between co-installed variants; official Strava app query in `<queries>` remains intact; 100% clean-room test pass rate.
* **Risk Rating**: **LOW** — Narrowly scoped intent filter scheme adjustment and redirect URI string alignment with verified Strava API backend compatibility.
