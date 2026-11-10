# Stage 1 Analysis: ATT-6186 - Inventory and Phase Out Source-Text-Reading Tests

**Ticket**: [ATT-6186](https://rainerblind.atlassian.net/browse/ATT-6186)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6186`  
**Requirement Mapping**: `REQ-PRO-049` | **Test Mapping**: `TST-PRO-049`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
ASPICE pre-assessment finding C-12 flagged that ~290 test files read production source code (`.readText()`, `Files.readString()`) to grep for literals, whitespace, or tokens instead of verifying runtime behaviour. This violates `docs/coding_standards.md` §5.4 (prohibiting UI literal source-grep). To establish ASPICE SWE.4 unit verification criteria without destabilizing existing builds, an automated audit test with a ratcheting baseline must be introduced to block new violations while preserving legitimate system architectural governance guards.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * Historically, tests used `readText()` on `app/src/main/` Kotlin/Java files to confirm class excision or token presence. These brittle checks fail on harmless formatting changes without verifying behavior.
  * No CI or unit test tripwire prevents developers or agents from authoring new source-reading tests.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Forensic audit of `app/src/test/java/` identified 293 total files reading `src/main`:
    1. *Legitimate System Policy & Governance Guards* (13 files): `TryWithResourcesCursorAuditTest`, `ManifestSecurityContractTest`, `TranslationParityTest`, `ReleaseCiAndLintConfigContractTest`, etc. (specifically preserved by §5.4).
    2. *Legacy Source-Grep Tests* (280 files): Dominated by `trainingtracker.ui` (232 files, led by `ui.map` [45], `ui.tracking` [34], `ui.aftermath` [24], `ui.settings` [21], `ui.routes` [15]), `banalservice.ui` (14), `migration` (10), `zones` (5), and others (15).
  * Validated that a ratcheted audit test (`SourceTextReadingAuditTest`) can allow-list the 280 legacy files and 13 governance guards, immediately trapping any unallow-listed additions.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail (REQ-PRO-029)
* **In-Scope Goals**:
  * Author `SourceTextReadingAuditTest` to scan `app/src/test/java` and assert that no test outside the allow-list reads `app/src/main/` production source files.
  * Allow-list the 13 legitimate architectural governance guards and baseline the 280 legacy files.
  * Define structured inventory of legacy files grouped by domain/package for downstream refactoring.
  * Author requirement `REQ-PRO-049` and test spec `TST-PRO-049` in `req_process_stability.md` / `tst_process_stability.md`.
* **Out-of-Scope Non-Goals**: Mass-rewriting the 280 legacy tests in this ticket (split into per-package follow-up tickets to keep complexity bounded).
* **Complexity Rating**: 2 SP (Standard). Scoped to audit test construction, baseline allow-list, and package inventory.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-049` (*Source-Text Reading Test Prohibition & Audit Governance*), refining `REQ-PRO-034` under Epic `ATT-6142`. Targets `SourceTextReadingAuditTest.kt` and `coding_standards.md` §5.4.
* **Historical Origin & Commit Trace**: Pre-assessment finding C-12 (2026-11-08) and ticket ATT-6186.
* **Root Reason for Existing Formulation**: Project lacked behavioral testing infrastructure, leading to static grep assertions as an interim refactoring verification shortcut.
* **Preservation of Core Invariants**: Legitimate governance guards (`TryWithResourcesCursorAuditTest`, `TranslationParityTest`, etc.) remain fully supported.
* **Side-Effect Audit (Rule 68)**: Zero breakage on existing test suites; parallel branches adding valid unit tests are unaffected.

---

## 5. Architectural Strategy & High-Level Solution
* Implement `SourceTextReadingAuditTest` in `com.atrainingtracker.architecture`:
  * Recursively scans test sources for `.readText(`, `readLines(`, `Files.readString(` referencing `src/main/`.
  * Verifies against an allow-list set: permits legitimate governance guards and recorded baseline legacy tests.
  * Fails if any new/unregistered test reads `src/main/` source code.
* Map to `REQ-PRO-049` and `TST-PRO-049` in living docs.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Target Release `V5.0.0`; Active Sprint `2026-46.1`; 100% pass on all existing 3,660+ unit tests; zero regression in existing build scripts.
* **Risk Rating**: **LOW**. Test-scope governance tripwire with comprehensive baseline allow-list preventing false positives.
