# Stage 1 Analysis: ATT-4668 - Scope Strava OAuth redirect URI by applicationId

**Ticket**: [ATT-4668](https://rainerblind.atlassian.net/browse/ATT-4668)  
**Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4668`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Statement & Motivation
When both Release (`com.atrainingtracker`) and Debug (`com.atrainingtracker.debug`) builds are co-installed on the same Android device, returning from Strava OAuth authorization displays a system app-disambiguation chooser (*"Open with..."*). If the user selects the incorrect variant, authentication fails because the cryptographic CSRF `state` token resides in the initiating variant's storage.

---

## 2. Root Cause Analysis (Forensic Investigation)
* **Manifest Intent Filter Collision**: In `app/src/main/AndroidManifest.xml` (L180–192), `StravaOAuthCallbackActivity` declares `<data android:scheme="strava" android:host="rainerblind.github.io" />` without path scoping. Both build variants register an identical filter.
* **Hardcoded Callback URIs**: `StravaHelper.getRedirectUri()` and `StravaOAuthCallbackActivity.REDIRECT_URI_PREFIX` hardcode `"strava://rainerblind.github.io"`.
* **Strava OAuth Domain Compatibility**: Strava validates the authority host (`rainerblind.github.io`) against the registered Authorization Callback Domain, while permitting arbitrary URI paths (`/${applicationId}`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Scope `AndroidManifest.xml` `<data>` filter for `StravaOAuthCallbackActivity` with `android:path="/${applicationId}"`.
  * Update `StravaHelper.getRedirectUri()` to `"strava://rainerblind.github.io/${BuildConfig.APPLICATION_ID}"`.
  * Update `StravaOAuthCallbackActivity` callback validation to require path `/${BuildConfig.APPLICATION_ID}`.
  * Update `StravaHelperTest` and `StravaOAuthCallbackActivityTest` / `StravaOAuthSecurityTest` with variant isolation assertions.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modifications to Strava OAuth token exchange logic, scopes, or CSRF token lifetime.
  * No changes to Dropbox, Google Drive, or other cloud provider redirect schemes.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-EXT-012` (`REQ-EXT-012.2` & Invariants), targeting `AndroidManifest.xml`, `StravaHelper.kt`, `StravaOAuthCallbackActivity.kt`.
* **Historical Origin & Commit Trace**: Ticket `ATT-4022`, commit `943dc3b4` (Sprint `2026-42.10`), refining `REQ-EXT-004` and `REQ-EXT-005` under Epic `ATT-597`.
* **Root Reason for Existing Formulation**: `REQ-EXT-012` introduced CSRF protection for `strava://rainerblind.github.io` without variant path scoping, assuming single-variant installations.
* **Preservation of Core Invariants**: Callback domain `rainerblind.github.io`, CSRF state validation (`REQ-EXT-012.1`), repository scope exchange, Custom Tabs integration, and 100% test pass rate are strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
* **Manifest Scoping**: Add `android:path="/${applicationId}"` to `StravaOAuthCallbackActivity` in `AndroidManifest.xml`. Merged manifests expand this to `/com.atrainingtracker` (release) and `/com.atrainingtracker.debug` (debug).
* **URI Construction & Validation**:
  * `StravaHelper.getRedirectUri()`: Return `"strava://rainerblind.github.io/${BuildConfig.APPLICATION_ID}"`.
  * `StravaOAuthCallbackActivity`: Validate scheme `"strava"`, host `"rainerblind.github.io"`, and path `"/${BuildConfig.APPLICATION_ID}"`.
* **Test Isolation**: Assert redirect URI parameter encoding and verify rejection of mismatched package paths.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in existing Strava OAuth authentication, CSRF checks, and unit tests.
  2. Single-variant installations continue to authenticate seamlessly.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Scoped entirely to redirect URI path construction, manifest path scoping, and matching callback validation with zero impact on database or service layers.
