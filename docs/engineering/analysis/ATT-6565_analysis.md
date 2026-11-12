# Stage 1 Analysis: ATT-6565 - Phase out source-text-reading tests in com.atrainingtracker.trainingtracker.ui.tracking

**Ticket**: [ATT-6565](https://rainerblind.atlassian.net/browse/ATT-6565)  
**Sub-task**: [ATT-7204](https://rainerblind.atlassian.net/browse/ATT-7204) (`[Analysis]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.3`  
**Branch**: `feature/ATT-6565`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

During the ASPICE 4.0 pre-assessment of 2026-11-08 (baseline `develop @ sprint/2026-45.11`), Remediation Item **C-12** (SWE.4 BP) identified legacy unit tests reading Kotlin/Java source code (`.readText()`) from `src/main` to assert token presence and regex patterns rather than verifying runtime bytecode or behavioral contracts.

This pattern violates `docs/coding_standards.md` §5.4 (*Contract Test Standards & Prohibition of UI Literal Source-Grep*). Ticket **ATT-6565** addresses this debt in `com.atrainingtracker.trainingtracker.ui.tracking`, replacing legacy source-reading tests with behavioral unit tests, JVM/Kotlin reflection, and `LocalizationTestCache`-backed assertions.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification (4-Augen-Prinzip — ATT-3534)**:
  * The ticket description hypothesized 40 legacy source-reading tests in `ui.tracking`.
  * An exhaustive audit of `app/source-reading-baseline.txt` on `sprint/2026-46.3` reveals exactly **42** test classes under `com/atrainingtracker/trainingtracker/ui/tracking/`:
    - `ui.tracking.controltracking`: 15 tests
    - `ui.tracking` (root): 10 tests
    - `ui.tracking.tracking`: 9 tests
    - `ui.tracking.trackingtabs`: 6 tests
    - `ui.tracking.editsensorfield`: 1 test
    - `ui.tracking.typography`: 1 test
  * All 42 test classes perform direct file I/O on `src/main/java` or `src/main/res` via `readText()`/`readLines()`.
  * Eliminating all 42 tests from `app/source-reading-baseline.txt` ratchets legacy debt ceiling down from **181 to 139** (181 - 42 = 139).
  * *Audit Note*: Audit test execution flagged 6 tests outside `ui.tracking` (`ui.aftermath`: 4, `ui.hrv`: 1, `cloud.healthconnect`: 1) introduced during parallel branch merges; bounded as out-of-scope per ATT-1250.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Remediate all 42 tests in `ui.tracking` to eliminate all `.readText()`, `readLines()`, and direct `src/main` reads.
  * Ratchet `app/source-reading-baseline.txt` down from 181 to 139 entries.
  * Update ceiling and count assertions in `SourceTextReadingAuditContractTest.kt` to 139.
  * Maintain 9-language translation parity verification via `LocalizationTestCache`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Tests in other packages (`ui.aftermath`, `ui.hrv`, `cloud.healthconnect`, `ui.map` assigned to ATT-6564).
  * Functional alterations to tracking logic, GPS filtering, or Compose layout mechanics.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-PRO-049` (`REQ-PRO-049.1`–`REQ-PRO-049.4`), `docs/coding_standards.md` §5.4.
* **Historical Origin & Commit Trace**: ASPICE 4.0 Pre-Assessment Item C-12; initial ratchet framework in ATT-6186 (`c91e62b6`), baseline stabilization in ATT-6731 (`88ce1d51`), prior package batches in ATT-6569 (`bed1157d`), ATT-6568 (`17300170`), ATT-6567 (`3b5a5b41`), and ATT-6566 (`ef50449a`).
* **Root Reason for Existing Formulation**: Brittle text grep was used to assert layout slots and parameter defaults; replacing with JVM reflection and behavioral tests preserves structural safety while removing refactoring fragility.
* **Preservation of Core Invariants**: No governance guard removed; all functional contracts (9-language strings, token compliance, parameter signatures) strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Localization Parity**: Migrate XML string checks to `LocalizationTestCache.getStrings(locale)` across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
2. **Component & Composable Reflection**: Inspect generated `*Kt` classes (`Class.forName(...)`, `declaredMethods`, `parameterTypes`) to verify composable presence, parameters, and slots without reading `.kt` source text.
3. **Pure Behavioral Verification**: Retain and execute pure data model, algorithm, and layout spacing tests (e.g. `SensorFieldStyleContractTest`, `SensorFieldZoneGaugeTest`, `TrackingTabsLandscapeLayoutTest`).
4. **Baseline Ratchet**: Remove all 42 lines from `app/source-reading-baseline.txt` and update `SourceTextReadingAuditContractTest.kt` assertions to 139.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression across JVM unit test suite (`./gradlew testDebugUnitTest`).
  2. 100% 9-language string parity preserved.
  3. Strict ratcheting of baseline debt from 181 to 139.
* **Risk Rating**: **LOW** (Scope strictly bounded to test suites and baseline debt ratcheting; zero production code modifications).
