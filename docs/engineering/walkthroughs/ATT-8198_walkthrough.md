# Stage 5: Walkthrough & Verification - ATT-8198: Behavioral Compose UI Tests & Reflection-Only Ratchet

**Ticket**: [ATT-8198](https://rainerblind.atlassian.net/browse/ATT-8198) | **Sub-task**: [ATT-8988](https://rainerblind.atlassian.net/browse/ATT-8988) (`[Test]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_02_Spät`  
**Requirement / Test Mapping**: `REQ-PRO-132` (`REQ-PRO-132.1`–`.4`) / `TST-PRO-132` (`TST-PRO-132.1`–`.4`, strengthening `TST-CON-031.5b`, `TST-TRK-062.5`)  
**Branch**: `feature/ATT-8198` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

---

## 1. Executive Summary & Verification Overview
ATT-8198 establishes behavioral Compose UI testing (`createComposeRule()`) as the mandatory verification standard under `SWE.4` `CL 2` and bans new reflection-only `*Kt` UI tests:
1. **UI Reflection Ratchet (`UiReflectionOnlyTestAuditContractTest.kt`, `app/ui-reflection-baseline.txt`)**: Scans `(?:^|/)ui/` across both `trainingtracker/ui/` (`178`) and `banalservice/ui/` (`11`) for multiline `Class.forName("...Kt")` without `createComposeRule()`, locking an exact `189`-entry ceiling with zero stale entries and zero `app/src/main/` reads while protecting `build/`, non-`*Kt`, and `createComposeRule()`-backed tests.
2. **Process & Coding Standard Alignment**: Updated `docs/coding_standards.md` (`v1.0.1` §5.4), `.agents/skills/stage4-implementation/SKILL.md`, and `.agents/skills/stage5-verification/SKILL.md` to mandate behavioral `createComposeRule()` UI tests while preserving all `AspiceSweAuditContractTest` and §1.1–§1.5 markers.
3. **`RadarSettingsSectionTest.kt` Remediation**: Retired stale `RadarLightControlMode` / `TuningConfig` tautologies (`:38-129`) and converted the suite to Robolectric `createComposeRule()`, verifying conditional visibility (`FULL_SCREEN` hides `radar_overlay_clear_mode`; `radarSpatialAudioEnabled` reveals spatial sub-controls), retired-control absence, interactive toggles/DataStore updates, simulator callbacks, and preserved 9-locale XML checks (`:131-226`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-132.1`, `.2` | `TST-PRO-132.1`, `.2` | `UiReflectionOnlyTestAuditContractTest` (`4/4`) + `SourceTextReadingAuditContractTest` (`4/4`) + `AndroidViewModelReflectionContractTest` (`2/2`) | **PASSED** (`10/10`) | `Verified` |
| `REQ-PRO-132.3` | `TST-PRO-132.3` | `AspiceSweAuditContractTest` (`13/13`) + `UiReflectionOnlyTestAuditContractTest` §5.4/Skill check | **PASSED** (`13/13`) | `Verified` |
| `REQ-PRO-132.4` | `TST-PRO-132.4`, `TST-CON-031.5b`, `TST-TRK-062.5` | Robolectric `createComposeRule()` (`RadarSettingsSectionTest`, `8/8`) | **PASSED** (`8/8`) | `Verified` |
| `REQ-PRO-120` | Governance Audit | `python3 tools/verify_requirement_governance.py --repo-root .` | **PASSED** (`exit 0`) | `Verified` |

---

## 3. Tier 1 Targeted Verification Evidence (Reused per Rule 7.5 — Zero Code Changes Since Stage 4)
```text
Command: python3 tools/parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.build.UiReflectionOnlyTestAuditContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.AndroidViewModelReflectionContractTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.settings.radar.RadarSettingsSectionTest"
Result: BUILD SUCCESSFUL — 31/31 tests PASSED (0 failures, 0 errors, 0 skipped)
  - UiReflectionOnlyTestAuditContractTest: 4/4 PASS
  - SourceTextReadingAuditContractTest: 4/4 PASS (22 guards, 105 baseline preserved)
  - AspiceSweAuditContractTest: 13/13 PASS
  - AndroidViewModelReflectionContractTest: 2/2 PASS
  - RadarSettingsSectionTest: 8/8 PASS
```

---

## 4. On-Device / Visual Verification (`Rule 7.6` & `Rule 8.5`)
Pure process, build-contract, and unit-test enhancement (`0` `app/src/main/` edits, `0` UI/image asset modifications); APK deployment and `Rule 8.5` image inspection are not applicable (`N/A - no UI strings`).

---

## 5. Invariant & Governance Verification
1. **Zero Production Changes**: `app/src/main/`, SQLite schemas, and `values*/strings*.xml` remain untouched; `ANT-Android-SDKs` gitlink preserved (`160000 0e8cea714a231fb9565170d4ed01eafa868f9798`).
2. **Existing Ratchets Preserved**: `SourceTextReadingAuditContractTest` (`22` guards, `105` baseline) and `build/` / non-`*Kt` reflection checks remain intact.
3. **Living Docs & Stage 6 Handover**: `REQ-PRO-132` and `TST-PRO-132` promoted to `Verified`; `feature/ATT-8198` preserved intact for Stage 6 Cold Final Review (`Final Review (Agent)`).
