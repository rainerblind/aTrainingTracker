# Stage 5 Walkthrough & Verification: ATT-4027

**Ticket**: [ATT-4027](https://rainerblind.atlassian.net/browse/ATT-4027) Review manifest permissions, intent filters and build configuration debt  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `4.9.40` | **Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4027`  
**Requirement Mapping**: `REQ-BLD-003` (`REQ-BLD-003.1` – `REQ-BLD-003.5`)  
**Test Mapping**: `TST-BLD-003` (`TST-BLD-003.1` – `TST-BLD-003.4`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-10-22  

---

## 1. Executive Summary & Verification Overview

Prior to ATT-4027, an independent whole-app code review flagged manifest, permission, and build configuration debt: `ConfigPebbleViewsActivity` was exported without permission (`android:exported="true"`); `GpxImportActivity` declared redundant `android:host="*"`, obsolete `android:scheme="file"` (blocked on API 24+), and unscoped `application/octet-stream` in `VIEW` filters; and a stale comment claimed `ACCESS_BACKGROUND_LOCATION` was "not necessary", conflicting with active Android 14+ FGS restart requirements (`START_STICKY`, `REQ-TRK-015`).

Remediation was fully implemented and verified across all components:
1. **Component Encapsulation**: Declared `android:exported="false"` on `ConfigPebbleViewsActivity` in `AndroidManifest.xml`, eliminating external invocation surface.
2. **Intent Filter Hardening**: Excised `host="*"` and `file://` scheme from `GpxImportActivity`; constrained generic `application/octet-stream` VIEW filters strictly to `.gpx` path extension patterns; removed unscoped octet-stream from SEND intents.
3. **Permission Documentation Parity**: Retained `ACCESS_BACKGROUND_LOCATION` and documented Android 14+ `START_STICKY` FGS recovery contract (`REQ-TRK-015`), removing misleading legacy comment.
4. **Build Invariant Retention**: Preserved `android:largeHeap="true"` (`REQ-PER-012`), local patched core library (`REQ-UI-164`), and documented AGP 9 deprecation opt-out flags in `gradle.properties`.
5. **Contract Verification**: Established `ManifestAndBuildConfigContractTest.kt` verifying all 5 contract invariants deterministically.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Scope & Test Class | Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-BLD-003.1` | `TST-BLD-003.1` | `ConfigPebbleViewsActivity` encapsulation (`ManifestAndBuildConfigContractTest`) | **PASS** | `Verified` |
| `REQ-BLD-003.2` | `TST-BLD-003.2` | `GpxImportActivity` filter hardening & path scoping (`ManifestAndBuildConfigContractTest`) | **PASS** | `Verified` |
| `REQ-BLD-003.3` | `TST-BLD-003.3` | `ACCESS_BACKGROUND_LOCATION` FGS contract comment (`ManifestAndBuildConfigContractTest`) | **PASS** | `Verified` |
| `REQ-BLD-003.4` | `TST-BLD-003.1` | `largeHeap="true"` and AGP 9 build flags retention (`ManifestAndBuildConfigContractTest`) | **PASS** | `Verified` |
| `REQ-BLD-003.5` | `TST-BLD-003.1` | Automated contract test verification suite (`ManifestAndBuildConfigContractTest`) | **PASS** | `Verified` |
| `REQ-PRO-001` | `TST-BLD-003.4` | Targeted unit test regression suite (`ManifestAndBuildConfigContractTest` + CI contract) | **PASS** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit Suites & Contract Tests
```text
ManifestAndBuildConfigContractTest (5 tests) - ALL PASSED
  • testConfigPebbleViewsActivity_notExported: PASSED (137ms)
  • testGpxImportActivity_intentFiltersHardened: PASSED (12ms)
  • testManifest_permissionAndMemoryInvariantsPreserved: PASSED (7ms)
  • testManifest_backgroundLocationRationaleCommentDocumented: PASSED (7ms)
  • testGradleProperties_agp9OptOutFlagsDocumented: PASSED (2ms)

ReleaseCiAndLintConfigContractTest (4 tests) - ALL PASSED

BUILD SUCCESSFUL in 2s (32 actionable tasks, 9 tests executed, 0 failures, 0 regressions)
```

---

## 4. Invariant & Governance Verification

- **Requirement Governance**: `python3 tools/verify_requirement_governance.py` passed with exit code 0.
- **Living Documentation**: `REQ-BLD-003` in `docs/requirements.md` and `TST-BLD-003` in `docs/tests.md` updated to `Verified`.
- **System Invariants**: Android 14+ FGS location recovery (`REQ-TRK-015`), GIS heatmap largeHeap memory budget (`REQ-PER-012`), and OEM accessibility fix (`REQ-UI-164`) preserved intact. Zero git commits created on `feature/ATT-4027` (Rule 8).
