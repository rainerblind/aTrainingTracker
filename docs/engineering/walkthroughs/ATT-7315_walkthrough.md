# Stage 5: Walkthrough & Verification - ATT-7315: Differentiate map marker label for A->B routes

**Ticket**: [ATT-7315](https://rainerblind.atlassian.net/browse/ATT-7315)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-SET-097` (`REQ-SET-097.5`)  
**Test Mapping**: `TST-SET-097` (`TST-SET-097.5`)  
**Branch**: `subagent-Lane-3-Agent---ATT-7315-self-2da9c00a`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview

In Sprint 2026-46.3 Joint Review of ATT-6686, the Product Owner observed that the apex marker for A->B commute routes was labeled "Weiteste Entfernung" (Max Displacement). Since the bipolar product optimization in ATT-6686 anchors the apex marker near the geometric midpoint or corridor curve, labeling it "Weiteste Entfernung" was misleading (as destination is furthest away).

ATT-7315 resolves this labeling inconsistency:
- **Differentiated Labeling**: Point-to-point routes ($d(\text{start}, \text{end}) \ge 150\text{ m}$) are dynamically labeled `route_apex` ("Scheitelpunkt" in German, "Apex" across all other locales).
- **Closed-Loop Preservation**: Closed-loop routes ($d(\text{start}, \text{end}) < 150\text{ m}$) strictly preserve `max_line_distance` ("weiteste Entfernung" / "furthest away") representing the physical turnaround point.
- **9-Language Parity**: Professional localization across English, German, Spanish, French, Italian, Japanese, Dutch, Polish, and Portuguese.
- **Comprehensive Verification**: 100% pass rate in `WorkoutRepositoryMapMarkerApexTest`, `TranslationParityTest`, and broader cluster regressions.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SET-097.5` | `TST-SET-097.5a` | Automated Unit Test (`WorkoutRepositoryMapMarkerApexTest`) | **PASSED** | `Verified` |
| `REQ-SET-097.5` | `TST-SET-097.5b` | 9-Language Localization Audit (`TranslationParityTest`) | **PASSED** | `Verified` |
| `REQ-SET-097` | `TST-SET-097.4` | Cluster Regression (`WorkoutCluster*`, `ui.clusters.*`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-SET-097.5c` | Tier 1 Targeted Regression Suite | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Unit & Localization Test Execution
```text
> Task :app:compileDebugKotlin
> Task :app:compileDebugUnitTestKotlin
> Task :app:testDebugUnitTest

WorkoutRepositoryMapMarkerApexTest > testMapMarkerApexLabel_pointToPoint_usesRouteApex PASSED
WorkoutRepositoryMapMarkerApexTest > testMapMarkerApexLabel_closedLoop_usesMaxLineDistance PASSED
WorkoutRepositoryMapMarkerApexTest > testWorkoutCluster_isPointToPoint PASSED
TranslationParityTest > testAllTranslationsComplete PASSED

BUILD SUCCESSFUL in 3m 50s
33 actionable tasks: 9 executed, 24 up-to-date
```

### Cluster Regression Suite
```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 52s
33 actionable tasks: 1 executed, 32 up-to-date
```

---

## 4. Hardware / Physical Verification & Visual Consistency

### Visual Consistency (Rule 23)
* **Reference UI**: `docs/attachments/commute_apex_screenshot.png` (captured on Pixel 10).
* **Observed Change**:
  - On point-to-point / commute tracks: The midpoint apex marker InfoWindow title displays **"Scheitelpunkt"** instead of **"weiteste Entfernung"**.
  - On circular / closed-loop tracks: The turnaround point continues to display **"weiteste Entfernung"**.
* **Checked against `docs/design_guidelines.md` §5**:
  - Shapes: Unchanged (standard Google Maps marker InfoWindow).
  - Spacing / Layout: Unchanged.
  - Colors / Theme: `TTColor.ApexPoint` and `R.drawable.ic_distance` unchanged.
  - Typography / Iconography: Reused existing tokens.
* **Deviations**: None.

---

## 5. Invariant & Governance Verification

1. **Zero Regressions**: 100% pass rate across targeted and cluster test suites.
2. **Living Documentation Synchronized**: `docs/requirements/req_settings_profiles.md` and `docs/tests/tst_settings_profiles.md` updated with `REQ-SET-097.5` and `TST-SET-097.5`.
3. **Subtask Governance**: Stages 1–4 completed and audited; Stage 5 verification submitted.
4. **Code Lock / Zero Schema Change**: Telemetry database schema and sensor extrema enums remain untouched.
