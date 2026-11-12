# Stage 1 Analysis: ATT-6564 - Phase out source-text-reading tests in com.atrainingtracker.trainingtracker.ui.map

**Ticket**: [ATT-6564](https://rainerblind.atlassian.net/browse/ATT-6564)  
**Sub-task**: [ATT-7221](https://rainerblind.atlassian.net/browse/ATT-7221) (`[Analysis]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.3`  
**Branch**: `feature/ATT-6564`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

During the ASPICE 4.0 pre-assessment of 2026-11-08 (baseline `develop @ sprint/2026-45.11`), Remediation Item **C-12** (SWE.4 BP) identified legacy unit tests reading Kotlin source code (`.readText()`) from `src/main` to assert token presence and regex patterns rather than verifying runtime bytecode or behavioral contracts.

This pattern violates `docs/coding_standards.md` §5.4 (*Contract Test Standards & Prohibition of UI Literal Source-Grep*). Ticket **ATT-6564** addresses this debt in `com.atrainingtracker.trainingtracker.ui.map`, replacing 45 legacy source-reading tests with behavioral unit tests, JVM reflection, and `LocalizationTestCache`-backed assertions.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification (4-Augen-Prinzip — ATT-3534)**:
  * The ticket description hypothesized 45 legacy source-reading tests in `ui.map`.
  * An exhaustive audit of `app/source-reading-baseline.txt` on `sprint/2026-46.3` (HEAD `1298a643`) confirms exactly **45** test classes under `com/atrainingtracker/trainingtracker/ui/map/`:
    - `DarkMap*` (3 tests): Dark style resolution, anti-flash overlay, styling contracts.
    - `ElevationProfile*` (11 tests): Clearance, climb badge clipping, climb spans, gestures, grade colors, layouts, smoothing.
    - `MapDetailLayout*` (9 tests): Collapsing headers, dynamic viewports, metadata slots, padding, scrub synchronization, sheet chrome, zoom contracts.
    - `TelemetryMetricGraph*` (8 tests): Gestures, landscape alignment, marginal bars, zone tests, trackless scrubbing.
    - Map & Sheet Components (14 tests): Favorite location sheet, FollowMe padding/anchors, general layers, polyline stability, preview lite mode, scrubbing badges, split panes, waypoint badges, workout share sheet.
  * All 45 test classes perform direct file I/O on `src/main/java` or `src/main/res` via `readText()`/`readLines()`.
  * Eliminating all 45 tests from `app/source-reading-baseline.txt` ratchets legacy debt ceiling down from **139 to 94** (139 - 45 = 94).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Remediate all 45 tests in `ui.map` to eliminate all `.readText()`, `readLines()`, and direct `src/main` reads.
  * Ratchet `app/source-reading-baseline.txt` down from 139 to 94 entries.
  * Update ceiling and count assertions in `SourceTextReadingAuditContractTest.kt` to 94.
  * Maintain 9-language translation parity verification via `LocalizationTestCache`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Tests in other packages (`ui.clusters`, `ui.equipment`, `ui.climbs`, etc. remaining in baseline).
  * Functional alterations to map rendering, camera kinematics, elevation profile DSP, or database managers.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-PRO-049` (`REQ-PRO-049.1`–`REQ-PRO-049.4`), `docs/coding_standards.md` §5.4.
* **Historical Origin & Commit Trace**: ASPICE 4.0 Pre-Assessment Item C-12; initial ratchet framework in ATT-6186 (`c91e62b6`), baseline stabilization in ATT-6731 (`88ce1d51`), prior package batches in ATT-6569 (`bed1157d`), ATT-6568 (`17300170`), ATT-6567 (`3b5a5b41`), ATT-6566 (`ef50449a`), and ATT-6565 (`1298a643`).
* **Root Reason for Existing Formulation**: Brittle text grep was used to assert map layout slots, elevation clearance, and padding parameters.
* **Preservation of Core Invariants**: No governance guard removed; all functional contracts (9-language strings, map camera behaviors, elevation geometry) strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Localization Parity**: Migrate XML string checks to `LocalizationTestCache.getStrings(locale)` across all 9 supported locales.
2. **Component & Composable Reflection**: Inspect generated `*Kt` classes (`Class.forName(...)`, `declaredMethods`, `parameterTypes`) to verify composable presence, parameters, and slots without reading `.kt` source text.
3. **Pure Behavioral Verification**: Retain and execute pure data model, algorithm, elevation profile coordinate math, and gesture state tests.
4. **Baseline Ratchet**: Remove all 45 lines from `app/source-reading-baseline.txt` and update `SourceTextReadingAuditContractTest.kt` assertions to 94.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression across JVM unit test suite (`./gradlew testDebugUnitTest`).
  2. 100% 9-language string parity preserved.
  3. Strict ratcheting of baseline debt from 139 to 94.
* **Risk Rating**: **LOW** (Scope strictly bounded to test suites and baseline debt ratcheting; zero production code modifications).
