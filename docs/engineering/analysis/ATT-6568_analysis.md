# Stage 1 Analysis: ATT-6568 - Phase out source-text-reading tests in com.atrainingtracker.trainingtracker.ui.routes

**Ticket**: [ATT-6568](https://rainerblind.atlassian.net/browse/ATT-6568)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6568`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

During the ASPICE 4.0 pre-assessment of 2026-11-08 (baseline `develop @ sprint/2026-45.11`), Remediation Item **C-12** (SWE.4 BP) identified extensive legacy unit tests that read production Kotlin/Java source code (`.readText()`) targeting `src/main` to assert token presence or regex patterns rather than exercising runtime bytecode and behavioral contracts.

This anti-pattern violates `docs/coding_standards.md` §5.4 (*Contract Test Standards & Prohibition of UI Literal Source-Grep*). Ticket **ATT-6568** addresses this debt in `com.atrainingtracker.trainingtracker.ui.routes`, replacing 15 brittle source-reading tests with behavioral unit tests, JVM/Kotlin reflection, and `LocalizationTestCache`-backed assertions.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  * Live sprint branch audit of `app/source-reading-baseline.txt` at HEAD `bed1157d` confirms exactly 15 test classes under `com/atrainingtracker/trainingtracker/ui/routes/` reading source text (`TurnCueDetectorTest.kt` is under package `routes/` and correctly excluded).
  * The tests fail governance due to direct file I/O against `src/main/java` and `src/main/res/*/strings.xml`, causing false positives on innocent formatting changes while failing to verify actual component runtime signatures.

### Inventory of 15 Target Test Classes

| # | Test Class | Current Anti-Pattern | Behavioral Replacement Strategy |
|---|---|---|---|
| 1 | `BatchImportSportTypeDialogTest` | Reads `BatchImportSportTypeDialog.kt` & `strings.xml` | Reflection on `BatchImportSportTypeDialogKt`, `LocalizationTestCache` across 9 locales |
| 2 | `ForkDecisionCardTest` | Reads `ForkDecisionCard.kt` & `strings.xml` | Reflection on `ForkDecisionCardKt`, `LocalizationTestCache` for fork alert keys |
| 3 | `RouteClimbSegmentLayeringContractTest` | Reads `MapContentScope.kt`, `MapLayers.kt`, `RouteOnMapScreen.kt` | Instantiate `ClimbHighlightData` defaults, reflection on `MapLayersKt` parameters |
| 4 | `RouteClimbsBreakdownContractTest` | Reads `RouteOnMapScreen.kt` | Reflection on `RouteOnMapScreenKt` (`RouteClimbsBreakdownSection` signature/types) |
| 5 | `RouteDetailSheetContractTest` | Reads `RouteDetailSheet.kt` | Reflection on `RouteDetailSheetKt` (`isActiveNavigation` param, obsolete card absence) |
| 6 | `RouteDetailsContractTest` | Reads `ElevationProfile.kt`, `RouteSummaryHeader.kt`, `ClimbDetector.kt` | Behavioral `ClimbDetector` run, reflection on breakdown header parameters |
| 7 | `RouteItemClickStandardizationTest` | Reads `RouteItem.kt`, `RouteSummaryHeader.kt`, `strings.xml` | Reflection on `RouteSummaryHeaderKt`, `LocalizationTestCache` for `route_edit` |
| 8 | `RouteItemWaypointContractTest` | Reads `PathPreviewMap.kt`, `MapLayers.kt` | Reflection on `MapLayersKt` (`RouteWaypointsLayer` `markerSizeDp` parameter) |
| 9 | `RouteItemZeroElevationContractTest` | Reads `RouteItem.kt` | Keep 5 pure behavioral `hasValidElevationData` tests; drop source grep |
| 10 | `RouteOnMapScreenClimbContractTest` | Reads `RouteOnMapScreen.kt` | Reflection on `RouteOnMapScreenKt` composables, verify marker/climb contracts |
| 11 | `RouteOverlayLayersContractTest` | Reads `RouteOnMapScreen.kt`, `RouteOverlayLayer.kt` | Reflection on breakdown toggle parameters, pure filtering algorithm tests |
| 12 | `RouteSegmentsBreakdownContractTest` | Reads `RouteSegmentsBreakdownSection.kt`, `RouteOnMapScreen.kt` | Reflection on `RouteSegmentsBreakdownSectionKt`, `RouteBreakdownTab` enum check |
| 13 | `RouteSelectorMidRideContractTest` | Reads `RouteSelectorSheet.kt`, `TrackingTabsScreen.kt` | Reflection on `RouteSelectorSheetKt` mid-ride params, `LocalizationTestCache` |
| 14 | `RouteSelectorSheetTest` | Reads `RouteSelectorSheet.kt` | Reflection on `RouteSelectorSheetKt` composable methods, `LocalizationTestCache` |
| 15 | `RoutesScreenContractTest` | Reads `RoutesScreen.kt`, `strings.xml` | Reflection on `RoutesScreenKt`, `LocalizationTestCache` for 9-locale parity |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Remediate all 15 tests in `ui.routes` to eliminate all `.readText()` and direct `src/main` file reads.
  * Ratchet `app/source-reading-baseline.txt` down from 251 to 236 entries.
  * Update ceiling and count assertions in `SourceTextReadingAuditContractTest.kt` to 236.
  * Maintain 9-language translation parity verification via `LocalizationTestCache`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Non-ui routes test `routes/TurnCueDetectorTest.kt` (assigned to separate ticket).
  * Functional changes to route recording, GPX parsing, or navigation state machines.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-PRO-049` (`REQ-PRO-049.1`–`REQ-PRO-049.4`), `docs/coding_standards.md` §5.4.
* **Historical Origin & Commit Trace**: ASPICE 4.0 Pre-Assessment Item C-12; initial ratchet framework in ATT-6186 (`c91e62b6`), governance baseline stabilized in ATT-6731 (`88ce1d51`), prior package batch in ATT-6569 (`bed1157d`).
* **Root Reason for Existing Formulation**: Brittle string grep was originally introduced as a quick check during UI migration; replacing with reflection and cache-backed checks fulfills the original intent without false positives.
* **Preservation of Core Invariants**: No governance guard removed; all functional contracts (M3 token compliance, parameter types, 9-language strings, bounds/elevation math) strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Localization Parity**: Migrate XML string checks to `LocalizationTestCache.getStrings(locale)` across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
2. **Component & Composable Reflection**: Inspect generated `*Kt` classes (`Class.forName(...)`, `declaredMethods`, parameter signatures) to guarantee composables declare expected parameters without reading `.kt` source text.
3. **Pure Behavioral Verification**: Retain and execute algorithmic unit tests (e.g. `calculateRouteBounds`, `hasValidElevationData`, `ClimbDetector.detectClimbs`, `RouteOverlayLayer` enums, background path filtering).
4. **Baseline Ratchet**: Remove the 15 lines from `app/source-reading-baseline.txt` and update `SourceTextReadingAuditContractTest.kt` assertions to 236.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression across JVM unit test suite (`./gradlew testDebugUnitTest`).
  2. 100% 9-language string parity preserved.
  3. Strict ratcheting of baseline debt from 251 to 236.
* **Risk Rating**: **LOW** (Scope strictly bounded to test suites and baseline debt ratcheting; zero production runtime code modifications).
