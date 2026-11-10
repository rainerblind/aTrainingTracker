# Stage 1 Analysis: ATT-936 - Decouple Compose screens from formatting and math via dedicated UI State models

**Ticket**: [ATT-936](https://rainerblind.atlassian.net/browse/ATT-936)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-936`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Several core Jetpack Compose composables perform formatting and metric math directly during composition:
* `WorkoutLaps.kt`: Computes fastest/slowest lap speeds, rabbit (`🐇`)/hedgehog (`🦔`) badges, and formats pace, speed, time, and distance strings inside `@Composable` loops on every recomposition.
* `WorkoutSummaryCompact.kt` (and `WorkoutSummary.kt`): Formats active time, distance, speed/pace, and ascent directly via `LocalMetricFormatter.current` and concatenates date/equipment text in rendering bodies.
* `WorkoutExtrema.kt`: Performs null-coalescing and relevance filtering (`minValue ?: "-"`, `if (isMinRelevant)...`) inside row rendering.
This couples UI composables to Android framework formatters, incurs redundant formatting work during recomposition, hinders lightweight JVM unit testing, and complicates Compose `@Preview` authoring.

---

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
* **Root Cause**: Organic evolution of Compose screens without an intermediate pure UI state layer between domain models (`LapData`, `WorkoutData`, `ExtremaData`) and composable nodes.
* **Independent Peer Review & Verification**:
  - *Location Discrepancies*: Ticket cited `ui/screens/workoutsummarylist/WorkoutSummaryCard.kt` and `ui/components/extrema/WorkoutExtrema.kt`. Forensic audit confirms actual locations are `ui/aftermath/workoutlist/WorkoutSummaryCompact.kt` (and `WorkoutSummary.kt`) and `ui/components/workoutextrema/WorkoutExtrema.kt`.
  - *WorkoutLaps Duplication*: `WorkoutLapsHelper.determineBadges()` already exists in `WorkoutLaps.kt:399`, yet `WorkoutLaps` duplicates badge filtering and min/max speed math inline (`WorkoutLaps.kt:77-88, 179-183`).
  - *Consumer Footprint*: `WorkoutLaps` has only 2 call sites (`WorkoutSummary.kt:304`, `WorkoutDetailTabbedContent.kt:855`); `WorkoutSummaryCompact` has 1 call site (`WorkoutList.kt:115`); `WorkoutExtrema` has 3 call sites (`WorkoutSummary.kt:290`, `TrackOnMapScreen.kt:472`, `WorkoutDetailTabbedContent.kt:839`). Decoupled UI state models can be introduced with zero breaking changes to existing public composable signatures by adding overloaded entry points or default model mappers.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Introduce immutable `LapRowUiState` and `LapTableUiState` with pure mapping functions (`WorkoutLapsHelper.buildUiState(...)`).
  - Introduce immutable `WorkoutCardUiState` (or `WorkoutSummaryCompactUiState`) decoupling metric/date formatting from `WorkoutSummaryCompact.kt`.
  - Introduce immutable `ExtremaRowUiState` / `ExtremaUiState` decoupling fallback string logic (`"-"`) from `WorkoutExtrema.kt`.
  - Add realistic `@Preview` composables using the pure UI states without requiring mock formatters.
  - Add pure JVM unit tests verifying formatting and badge assignments without Robolectric or Compose harnesses.
* **Out-of-Scope Non-Goals**: Database schema modifications, altering underlying formatter conversion logic in `formatters/`, or redesigning visual card layouts.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Refined Requirements**: Refines `REQ-UI-506` (*Decoupled Compose UI State Models*) in living shard `docs/requirements/req_ui_design_system.md` (tested by `TST-UI-506`), formalizing architectural standards established across `REQ-UI-141` (Laps badges), `REQ-UI-229` (Laps display modes), and `REQ-UI-255` (WorkoutSummary card hierarchy).
* **Historical Origin**: `ATT-510`, `ATT-1742`, `ATT-1870`, `ATT-3390`.
* **Root Reason for Existing Formulation**: Formatters were initially passed via `LocalMetricFormatter` composition locals for quick prototyping before formal MVVM state hoisting.
* **Preservation of Core Invariants**: Exact numerical formatting, unit strings, rabbit/hedgehog highlight rules, and interactive click callbacks remain 100% identical.

---

## 5. Architectural Strategy & High-Level Solution
* Define immutable UI state data classes in their respective component packages (`ui/components/workoutlaps/LapRowUiState.kt`, `ui/aftermath/workoutlist/WorkoutCardUiState.kt`, `ui/components/workoutextrema/ExtremaUiState.kt`).
* Provide pure transformation functions (`buildLapRowUiStates(...)`, `WorkoutCardUiState.from(...)`) accepting domain models and formatters.
* Maintain backward-compatible composable overloads so existing callers continue working without migration disruption.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: 100% visual and functional parity; zero regression across all 9 supported languages; zero breaking changes to existing screen callers; all contract tests remain green.
* **Risk Rating**: **LOW** (Pure presentation layer refactoring and state model extraction; zero database or threading changes).
