# Stage 5: Walkthrough & Verification - [TICKET_KEY]: [SUMMARY]

**Ticket**: [[TICKET_KEY]](https://rainerblind.atlassian.net/browse/[TICKET_KEY])  
**Sub-task**: [[SUBTASK_KEY]](https://rainerblind.atlassian.net/browse/[SUBTASK_KEY]) (`[Test]`)  
**Parent Epic**: [[EPIC_KEY]](https://rainerblind.atlassian.net/browse/[EPIC_KEY]) (*[EPIC_NAME]*)  
**Target Release**: `[TARGET_RELEASE]`  
**Active Sprint**: `[ACTIVE_SPRINT]`  
**Requirement Mapping**: `[REQ-XXX]`  
**Test Mapping**: `[TST-XXX]`  
**Branch**: `[feature|bugfix]/[TICKET_KEY]`  
**Author**: AI Agent 1 (Implementer)  
**Date**: [YYYY-MM-DD]  

---

## 1. Executive Summary & Verification Overview

<!-- Concise recap of the feature/fix implemented, components modified, and verification results. -->

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `[REQ-XXX]` | `[TST-XXX.1]` | Automated Unit Test | **PASSED** | `Verified` |
| `[REQ-XXX]` | `[TST-XXX.2]` | 9-Language Localization Audit | **PASSED** | `Verified` |
| `REQ-PRO-001` | `[TST-XXX.3]` | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in [X]s
[N] actionable tasks: [N] executed
```

### Targeted Unit & Integration Tests
```text
[Output snippets showing passing tests]
```

---

## 4. Hardware / Physical Verification (Pixel 10)

<!-- If applicable: physical device verification on Pixel 10 (Android 16), Logcat inspection, UI rendering. -->
<!-- If purely process/documentation/tooling: state "Pure process/tooling enhancement; zero APK runtime bytecode changes; verified via automated test suite." -->

### Visual Consistency (Rule 23 — mandatory if UI is added or changed)
<!-- Side-by-side on-device screenshots: new/changed UI vs. reference screen from the plan's "UI Consistency" section. Write "No UI changes." if not applicable. -->

| New / Changed UI | Reference Screen (`...`) |
| :---: | :---: |
| ![new](img/[TICKET_KEY]_new_light.png) | ![reference](img/[TICKET_KEY]_ref_light.png) |
| ![new dark](img/[TICKET_KEY]_new_dark.png) | ![reference dark](img/[TICKET_KEY]_ref_dark.png) |

* **Checked against `docs/design_guidelines.md` §5**: shapes ☐ spacing ☐ colors/themes ☐ typography/icons ☐ placement ☐
* **Deviations & justification**: None / `...`

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite executed with 100% pass rate.
2. **Living Documentation Synchronized**: Status in `docs/requirements.md` and `docs/tests.md` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review**: Parent ticket transitioned to `Final Review (Human)` and assigned to `human` for final release sign-off.
