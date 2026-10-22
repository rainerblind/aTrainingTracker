# Stage 2: Requirement & Test Specification - ATT-4027: [Low] Review manifest permissions, intent filters and build configuration debt

**Ticket**: [ATT-4027](https://rainerblind.atlassian.net/browse/ATT-4027) | **Sub-task**: Pending Submission (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*) | **Target Release**: `4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4027`  
**Requirement Mapping**: `REQ-BLD-003` (*Manifest Component Security, Intent Filter Scoping & Permission Configuration Hygiene*)  
**Test Spec ID**: `TST-BLD-003`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`) | **Date**: 2026-10-22  

---

## 1. Requirement Specification (REQ-BLD-003 Clauses 1–5)

### 1.1 Problem Statement & Rationale
An independent code review flagged manifest and configuration debt: `ConfigPebbleViewsActivity` was exported with `android:exported="true"` despite being internal-only; `GpxImportActivity` included invalid `android:host="*"`, obsolete `android:scheme="file"` (blocked since API 24), and unscoped `application/octet-stream` in `VIEW` filters; and a stale comment claimed `ACCESS_BACKGROUND_LOCATION` is "not necessary", conflicting with Android 14+ FGS restart requirements (`START_STICKY`, `REQ-TRK-015`).

### 1.2 Functional & Architectural Requirements
* **Internal Component Encapsulation (`REQ-BLD-003.1`)**: `ConfigPebbleViewsActivity` in `AndroidManifest.xml` SHALL specify `android:exported="false"`, closing external invocation surfaces.
* **GPX Intent Filter Hardening & Path Scoping (`REQ-BLD-003.2`)**: `GpxImportActivity` intent filters SHALL remove `android:host="*"` and `android:scheme="file"`. Generic `application/octet-stream` MIME matching in `VIEW` filters SHALL be constrained to `.gpx` path patterns (`.*\\.gpx`, `.*\\..*\\.gpx`, `.gpx`).
* **Background Location Permission Documentation Parity (`REQ-BLD-003.3`)**: `AndroidManifest.xml` SHALL retain active `<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />` for Android 14+ `START_STICKY` FGS recovery (`REQ-TRK-015`) and update the comment to document this requirement.
* **Preservation of Build & Memory Invariants (`REQ-BLD-003.4`)**: The app SHALL retain `android:largeHeap="true"` (`REQ-PER-012`), local patched core library `local-repo/androidx.core:1.15.0-patched` (`REQ-UI-164`), and AGP 9 build flags.
* **Automated Manifest Security Contract Verification (`REQ-BLD-003.5`)**: `ManifestSecurityContractTest.kt` SHALL verify `ConfigPebbleViewsActivity` encapsulation, GPX intent filter hardening, background location declaration, and `largeHeap="true"` retention.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* `AndroidManifest.xml`, *When* audited by `ManifestSecurityContractTest`, *Then* `ConfigPebbleViewsActivity` has `android:exported="false"`, `GpxImportActivity` contains no `host="*"` or `file` scheme, and `ACCESS_BACKGROUND_LOCATION` and `largeHeap="true"` are declared.
* *Given* an external app sharing a `.gpx` file via `content://`, *When* resolved, *Then* `GpxImportActivity` appears as a valid handler.
* *Given* an arbitrary binary file (`application/octet-stream`) without `.gpx` extension, *When* `ACTION_VIEW` resolves, *Then* `GpxImportActivity` does NOT appear.
* *Given* `TrackerService` restarting on Android 14+ via `START_STICKY`, *When* promoting to location FGS, *Then* `ACCESS_BACKGROUND_LOCATION` prevents crashes.

### 1.4 System Invariants
1. `TrackerService` Android 14+ `START_STICKY` background restart safety (`REQ-TRK-015`) MUST NOT be compromised.
2. Period map rendering memory budget (`largeHeap="true"`, `REQ-PER-012`) and OEM accessibility crash immunity (`REQ-UI-164`) MUST be preserved.
3. 100% clean-room test suite pass rate across all unit tests and `TranslationParityTest`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

* `Original Requirement ID & Target`: Net-new requirement `REQ-BLD-003`, extending `REQ-BLD-002` and aligning with `REQ-TRK-015`, `REQ-PER-012`, and `REQ-UI-164`. Targets `app/src/main/AndroidManifest.xml` and `ManifestSecurityContractTest.kt`.
* `Historical Origin & Commit Trace`: Independent whole-app code review (branch `feature/ATT-3921` @ `0234bfc0`, Oct 2026). Stale background location comment dates to early Android 10 migration; `ConfigPebbleViewsActivity` export added during Android 12 migration; GPX filters date to early file provider setups.
* `Root Reason for Existing Formulation`: Background location comment assumed notification FGS was sufficient prior to Android 14 background start restrictions. `ConfigPebbleViewsActivity` was exported blindly to satisfy manifest compiler. `file://` and `host="*"` were legacy boilerplate.
* `Preservation of Core Invariants`: Android 14+ FGS recovery (`REQ-TRK-015`), GPX content URI import, GIS large heap budget (`REQ-PER-012`), and OEM accessibility fix (`REQ-UI-164`) strictly preserved.

---

## 3. Test Specification (TST-BLD-003)

* **`TST-BLD-003.1`**: Manifest security & encapsulation contract test (`ManifestSecurityContractTest.kt`).
* **`TST-BLD-003.2`**: GPX intent filter MIME & path specification verification.
* **`TST-BLD-003.3`**: Manifest background location comment and documentation parity audit.
* **`TST-BLD-003.4`**: Clean-room regression suite and translation parity execution (`./gradlew testDebugUnitTest`).

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test / Subject | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-BLD-003.1]` | Contract | `ConfigPebbleViewsActivity` export & `largeHeap` | `REQ-BLD-003.1`, `REQ-BLD-003.4`, `REQ-BLD-003.5` | Specified (ATT-4027) |
| `[TST-BLD-003.2]` | Contract | `GpxImportActivity` filter hardening | `REQ-BLD-003.2`, `REQ-BLD-003.5` | Specified (ATT-4027) |
| `[TST-BLD-003.3]` | Audit | Manifest background location comments | `REQ-BLD-003.3` | Specified (ATT-4027) |
| `[TST-BLD-003.4]` | Regression | Full test suite (`testDebugUnitTest`) | `REQ-PRO-001`, `REQ-LOC-001` | Specified (ATT-4027) |
