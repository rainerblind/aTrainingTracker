# Stage 2: Requirement & Test Specification - ATT-6044: Generic ZoneDistributionCard

**Ticket**: [ATT-6044](https://rainerblind.atlassian.net/browse/ATT-6044)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6044`  
**Requirement Mapping**: `REQ-REV-008` (*Generic Zone Distribution Card Architecture & Composable Wrapper Deduplication*)  
**Test Spec ID**: `TST-REV-009`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-REV-008)

### 1.1 Problem Statement & Rationale
`PowerZoneDistributionCard.kt`, `HeartRateZoneDistributionCard.kt`, and `GradeZoneDistributionCard.kt` exhibit ~97% duplicate Compose layout, state handling, and structure (~600 lines total). Unifying them into a single parameterized `ZoneDistributionCard` eliminates ~440 lines of boilerplate while preserving public composable APIs, contract compatibility, and per-card state isolation.

### 1.2 Functional & Architectural Requirements
* **REQ-REV-008.1** (*Generic Composable Architecture*): The system SHALL provide `@Composable fun ZoneDistributionCard(...)` in `ui/aftermath/zones` parameterizing `distribution`, `@DrawableRes iconRes`, `title`, `unit`, `modifier`, `displayMode`, `onDisplayModeChange`, `showModeSelector`, and `defaultDisplayMode = ZoneCardDisplayMode.COLUMNS`.
* **REQ-REV-008.2** (*Layout & Visual Invariants*): `ZoneDistributionCard` SHALL render an `ElevatedCard(shape = RoundedCornerShape(12.dp))` with 12.dp padding, Header Row (18.dp vector icon, bold `titleSmall`, duration via `ZoneDistributionChartMath.formatZoneDuration`), Chart Body (`STACKED_BAR` with `showLegend = false`, `COLUMNS`/legacy `FIVE_ZONES`, or `HISTOGRAM`), and bottom `SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(28.dp))` (2 buttons without histogram, 3 buttons with histogram), gated on `if (showModeSelector && distribution.entries.isNotEmpty())`.
* **REQ-REV-008.3** (*Wrapper Deduplication & Public API Compatibility*): `PowerZoneDistributionCard`, `HeartRateZoneDistributionCard`, and `GradeZoneDistributionCard` SHALL remain public composable entry points delegating to `ZoneDistributionCard`. Each wrapper SHALL maintain its own isolated state so modes do not cross-contaminate. Wrapper names and parameter contracts MUST remain 100% backward-compatible with existing callers.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Visual & Functional Parity)**:
  - *Given* workout telemetry with heart rate, cycling power, or slope grade distribution data,
  - *When* rendered in `TrackOnMapScreen`, `WorkoutDetailTabbedContent`, or `WorkoutSummary`,
  - *Then* each card renders identically to legacy implementations and toggles chart modes smoothly.
* **Criterion 2 (Mode Switcher Gating)**:
  - *Given* `showModeSelector = false` (e.g. in `WorkoutSummary.kt`),
  - *When* the card is composed,
  - *Then* the bottom segmented button row is not composed into the layout tree.
* **Criterion 3 (Contract & Click Preservation)**:
  - *Given* `WorkoutSummaryClickContractTest.kt`,
  - *When* validating `WorkoutSummary.kt`,
  - *Then* wrapper invocations chained with `.then(mapClickModifier)` remain present and pass contract assertions.

---

## 2. Test Specification (TST-REV-009)

### Test Case 1: Generic Card Architecture & Mode Rendering (`TST-REV-009.1`)
* **Scope**: Unit & Compose Structural Test (`ZoneDistributionCardTest.kt`)
* **Assertion**: Verify `ZoneDistributionCard` renders icon, title, formatted duration, handles null vs present histogram (2 vs 3 buttons), and dispatches `STACKED_BAR`, `COLUMNS`, and `HISTOGRAM` charts correctly.

### Test Case 2: Wrapper Delegation & State Isolation (`TST-REV-009.2`)
* **Scope**: Unit & Contract Test (`ZoneDistributionCardTest.kt`, `GradeZoneDistributionCardTest.kt`)
* **Assertion**: Verify `PowerZoneDistributionCard`, `HeartRateZoneDistributionCard`, and `GradeZoneDistributionCard` delegate to `ZoneDistributionCard` with their designated icon/title/unit, maintain independent `rememberSaveable` state, and respect `showModeSelector`.

### Test Case 3: WorkoutSummary Click Contract Compatibility (`TST-REV-009.3`)
* **Scope**: Contract Test (`WorkoutSummaryClickContractTest.kt`)
* **Assertion**: Verify `WorkoutSummaryClickContractTest` continues to pass without modifying `WorkoutSummary.kt` call sites.

### Test Case 4: Tier 1 Targeted Regression Suite (`TST-REV-009.4`)
* **Scope**: Targeted Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.*"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-REV-009.1` | Unit / UI | `ZoneDistributionCard.kt`, `ZoneDistributionCardTest.kt` | `REQ-REV-008.1`, `REQ-REV-008.2` | Planned |
| `TST-REV-009.2` | Contract | `PowerZoneDistributionCard.kt`, `HeartRateZoneDistributionCard.kt`, `GradeZoneDistributionCard.kt` | `REQ-REV-008.3` | Planned |
| `TST-REV-009.3` | Contract | `WorkoutSummaryClickContractTest.kt` | `REQ-REV-008.3` | Planned |
| `TST-REV-009.4` | Regression | `ui.aftermath.zones.*`, `ui.aftermath.workoutlist.*` | `REQ-PRO-001` | Planned |
