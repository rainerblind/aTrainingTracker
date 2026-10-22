# Stage 3: Implementation Plan - ATT-4027: Review manifest permissions, intent filters and build configuration debt

**Ticket**: [ATT-4027](https://rainerblind.atlassian.net/browse/ATT-4027)  
**Sub-task**: [ATT-4634](https://rainerblind.atlassian.net/browse/ATT-4634) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `4.9.40`  
**Active Sprint**: `2026-43.6`  
**Requirement Mapping**: `REQ-BLD-003`  
**Test Mapping**: `TST-BLD-003`  
**Branch**: `feature/ATT-4027`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-22  

---

## 1. Problem Description & Background
Independent code review identified manifest and configuration hygiene debt: `ConfigPebbleViewsActivity` is exported with `android:exported="true"` without permission; `GpxImportActivity` declares redundant `android:host="*"`, obsolete `android:scheme="file"`, and unscoped `application/octet-stream` in `VIEW` filters; and a stale comment claims `ACCESS_BACKGROUND_LOCATION` is "not necessary", conflicting with Android 14+ `START_STICKY` FGS recovery (`REQ-TRK-015`).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-BLD-003` (*Manifest Component Security, Intent Filter Scoping & Permission Configuration Hygiene*)
  * `REQ-BLD-003.1`: `ConfigPebbleViewsActivity` encapsulation (`android:exported="false"`).
  * `REQ-BLD-003.2`: `GpxImportActivity` filter hardening (remove `host="*"`, remove `file://`, constrain `application/octet-stream`).
  * `REQ-BLD-003.3`: `ACCESS_BACKGROUND_LOCATION` comment parity documenting Android 14+ FGS `START_STICKY` restart contract (`REQ-TRK-015`).
  * `REQ-BLD-003.4`: Invariant preservation (`largeHeap="true"`, `local-repo/`, AGP 9 flags).
  * `REQ-BLD-003.5`: Automated contract verification in `ManifestAndBuildConfigContractTest.kt`.
* **Test Mapping**: `TST-BLD-003` (`TST-BLD-003.1` to `TST-BLD-003.4`).

---

## 3. System Invariants & Preserved Behavior
1. **FGS Location Recovery (`REQ-TRK-015`)**: Active `ACCESS_BACKGROUND_LOCATION` declaration remains strictly intact.
2. **Memory Budget (`REQ-PER-012`)**: `android:largeHeap="true"` remains strictly intact for period heatmap rendering.
3. **Accessibility Crash Immunity (`REQ-UI-164`)**: `local-repo/` core patch remains intact.
4. **GPX Import Parity**: Legitimate GPX route imports via `content://` and file extension matching remain 100% operational.
5. **Human Gate Invariance**: Terminal ticket resolution remains with the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes
* **Component 1 (`app/src/main/AndroidManifest.xml`)**:
  * Set `android:exported="false"` on `ConfigPebbleViewsActivity`.
  * Remove `android:host="*"` and `android:scheme="file"` from `GpxImportActivity`. Scope `application/octet-stream` in `VIEW` filter to `.gpx` path patterns.
  * Replace stale line 49 comment with accurate `REQ-TRK-015` Android 14+ FGS `START_STICKY` recovery documentation.
* **Component 2 (`gradle.properties`)**:
  * Add explanatory header comments clarifying AGP 9 deprecation opt-out flags retention until AGP 10.
* **Component 3 (`ManifestAndBuildConfigContractTest.kt`)**:
  * Unit contract test parsing `AndroidManifest.xml` and asserting component encapsulation, intent filter rules, permission declarations, and memory flags.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Manifest Hardening (`app/src/main/AndroidManifest.xml`)
* Set `ConfigPebbleViewsActivity` `android:exported="false"`.
* Clean `GpxImportActivity` intent filters: remove `host="*"`, remove `file://`, constrain `application/octet-stream` to `.gpx` path patterns.
* Update `ACCESS_BACKGROUND_LOCATION` comment documenting `REQ-TRK-015` / Android 14+ `START_STICKY`.

### Step 2: Build Properties Documentation (`gradle.properties`)
* Document AGP 9 opt-out flags and rationale for retention.

### Step 3: Automated Contract Test Construction
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/build/ManifestAndBuildConfigContractTest.kt`.
* Implement `@Test` methods verifying: (1) `ConfigPebbleViewsActivity` not exported, (2) `GpxImportActivity` filter hygiene, (3) `ACCESS_BACKGROUND_LOCATION` and `largeHeap` presence, (4) stale comment absence.

### Step 4: Targeted Unit Test Verification
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ManifestAndBuildConfigContractTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted contract test in Stage 4, followed by clean-room full suite regression (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Atomic git revert on branch `feature/ATT-4027`.
