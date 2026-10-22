# Stage 1 Analysis: ATT-4027 - Review manifest permissions, intent filters and build configuration debt

**Ticket**: [ATT-4027](https://rainerblind.atlassian.net/browse/ATT-4027)  
**Sub-task**: [ATT-4632](https://rainerblind.atlassian.net/browse/ATT-4632) (`[Analysis]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `4.9.40`  
**Active Sprint**: `2026-43.6`  
**Branch**: `feature/ATT-4027`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-22  

---

## 1. Problem Statement & Motivation
An independent whole-app code review flagged potential manifest, permission, and build configuration debt:
1. `ACCESS_BACKGROUND_LOCATION`: Stale comment in `AndroidManifest.xml` claiming it is "not necessary", conflicting with active declaration.
2. `GpxImportActivity`: Overly broad intent filters (`host="*"`, `file://` scheme, unscoped `application/octet-stream`).
3. `ConfigPebbleViewsActivity`: Declared with `android:exported="true"` despite being an internal-only settings component.
4. Build debt: `android:largeHeap="true"`, `local-repo/androidx.core:1.15.0-patched`, AGP 9 deprecation flags, and `StravaSegmentsIntentService`.

---

## 2. Root Cause Analysis & Call Site Audit

| Component / Finding | Status | Forensic Root Cause & Impact | Affected Call Sites |
|---|---|---|---|
| `ACCESS_BACKGROUND_LOCATION` | **Keep & Fix Comment** | Required on Android 14+ (`Build.VERSION_CODES.UPSIDE_DOWN_CAKE`) when `TrackerService` restarts via `START_STICKY` (`intent == null`) to promote to location FGS without crash (`REQ-TRK-015`). Manifest comment (L49) is stale legacy artifact. | `AndroidManifest.xml:33,49`, `TrackerService.java:778` |
| `GpxImportActivity` Filters | **Confirm & Harden** | `host="*"` is invalid/redundant for URI matching. `file://` is obsolete/blocked on API 24+. Unscoped `application/octet-stream` in `VIEW` filter makes app appear in open-with dialogs for all arbitrary binaries. | `AndroidManifest.xml:109-140` |
| `ConfigPebbleViewsActivity` | **Confirm & Harden** | Exported without permission (`android:exported="true"`). Only launched internally; setting `android:exported="false"` closes external attack surface. | `AndroidManifest.xml:147` |
| `StravaSegmentsIntentService` | **Already Fixed** | Residual manifest tag was already excised in `ATT-4025` (commit `09de79e5`). Zero occurrences in repository. | N/A (resolved) |
| `android:largeHeap="true"` | **Retain (Invariant)** | Required by `REQ-PER-012` / `TST-PER-018` for rendering aggregated period heatmaps and multi-session GIS vector tracks without OOM. | `AndroidManifest.xml:75`, `REQ-PER-012` |
| `local-repo/` (Core patch) | **Retain (Invariant)** | Required by `REQ-UI-164` (ATT-1091 / ATT-1347 / ATT-2767) to prevent fatal `LinkageError` crashes on defective Android 14 OEM ROMs missing accessibility methods. | `app/build.gradle:126`, `local-repo/` |
| AGP 9 opt-out flags | **Retain (Build Invariant)** | `android.newDsl=false`, `android.builtInKotlin=false`, etc. in `gradle.properties` required for Secrets Gradle Plugin and variant compatibility until AGP 10. | `gradle.properties:33-44` |

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Update stale `ACCESS_BACKGROUND_LOCATION` manifest comment to document Android 14+ FGS recreation (`REQ-TRK-015`).
  * Restrict `ConfigPebbleViewsActivity` with `android:exported="false"`.
  * Harden `GpxImportActivity` intent filters (remove `host="*"`, narrow/scope `application/octet-stream` with `.gpx` extension constraint).
* **Out-of-Scope Non-Goals**:
  * Do NOT remove `ACCESS_BACKGROUND_LOCATION` (violates `REQ-TRK-015`).
  * Do NOT remove `android:largeHeap="true"` (violates `REQ-PER-012`).
  * Do NOT remove `local-repo/` patched core AAR (violates `REQ-UI-164`, tracked in `ATT-1091`).
  * Do NOT refactor AGP build plugins to AGP 10 APIs in this ticket.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **REQ-TRK-015** (*Permission-Gated Foreground Service Types*): Android 14+ requires `ACCESS_BACKGROUND_LOCATION` for background FGS restart (`START_STICKY`, `TrackerService.java:778`). Preserved.
* **REQ-PER-012** (*Period Map Scalability & Memory Budget*): Explicitly mandates `android:largeHeap="true"` to prevent OOM in multi-session GIS overlays (`TST-PER-018`). Preserved.
* **REQ-UI-164** (*Accessibility Platform Resilience*): Requires local patched core AAR (`ATT-1091`) against defective OEM ROMs. Preserved.
* **REQ-BLD-041** (*Manifest Security & Component Hardening*): Net-new requirement to codify `ConfigPebbleViewsActivity` internal encapsulation, GPX intent filter hardening, and comment accuracy.

---

## 5. Architectural Strategy & High-Level Solution
1. In `app/src/main/AndroidManifest.xml`:
   - Replace misleading L49 comment with documentation of `REQ-TRK-015` / Android 14+ `START_STICKY` FGS recovery.
   - Set `android:exported="false"` on `ConfigPebbleViewsActivity`.
   - In `GpxImportActivity`, remove redundant `android:host="*"`, remove legacy `file` scheme, and constrain `application/octet-stream` to GPX file patterns.
2. Add automated contract test verifying manifest security and permission invariants.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `START_STICKY` service recovery on Android 14+ MUST retain `ACCESS_BACKGROUND_LOCATION`.
  2. Period map rendering memory budget MUST NOT be degraded (`largeHeap="true"` retained).
  3. Accessibility crash immunity on defective Android 14 OEM ROMs MUST NOT be broken (`local-repo/` retained).
  4. GPX import from content URIs and external apps MUST remain 100% operational.
* **Risk Rating**: **LOW**. Zero runtime logic changes to tracking engine; changes isolated to manifest hardening and comment clarification.
