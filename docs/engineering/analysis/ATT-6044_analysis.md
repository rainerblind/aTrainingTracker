# Stage 1 Analysis: ATT-6044 - ZoneDistributionCards: Power-, HeartRate- und GradeZoneDistributionCard in einer generischen Komponente zusammenführen

**Ticket**: [ATT-6044](https://rainerblind.atlassian.net/browse/ATT-6044)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-6044`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/`, three card implementations:
* `PowerZoneDistributionCard.kt` (199 lines)
* `HeartRateZoneDistributionCard.kt` (199 lines)
* `GradeZoneDistributionCard.kt` (199 lines)
exhibit ~97% duplicate Compose layout, state handling, and structure (~600 lines total).
1. **Shared Layout & Elements**: All three cards share identical `RoundedCornerShape(12.dp)` container styling, Header Row (18.dp vector icon, localized title, and formatted active time via `ZoneDistributionChartMath.formatZoneDuration`), Body branches (`STACKED_BAR` with `showLegend = false`, `COLUMNS`/legacy `FIVE_ZONES`, and `HISTOGRAM`), and bottom `SingleChoiceSegmentedButtonRow` (height 28.dp, 2 or 3 buttons) gated by `if (showModeSelector && distribution.entries.isNotEmpty())` (`REQ-UI-388`).
2. **Minimal Differences**: Differences are strictly limited to icon resource (`R.drawable.ic_power` / `ic_heart_rate` / `ic_grade`), title resource (`R.string.aftermath_power_zones_title` / `aftermath_hr_zones_title` / `aftermath_grade_zones_title`), histogram unit (`"W"` / `"bpm"` / `"%"`), and legacy default enum literal (`FIVE_ZONES` vs `COLUMNS`, which are functionally identical since `FIVE_ZONES` is `@Deprecated("Legacy alias for COLUMNS")`).

---

## 2. Root Cause Analysis & Independent Peer Review
* **Root Cause**:
  Organic iterative feature growth across multiple sprints: `HeartRateZoneDistributionCard` was authored first (`REQ-UI-372`), cloned for `PowerZoneDistributionCard` (`REQ-UI-388`), and cloned again for `GradeZoneDistributionCard` (`REQ-UI-453`). No shared base component was extracted, creating ~440 lines of boilerplate.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  - Validated all 12 consumer call sites: `TrackOnMapScreen.kt` (6), `WorkoutDetailTabbedContent.kt` (3), `WorkoutSummary.kt` (3). Preserving existing composable signatures ensures zero disruption to callers.
  - Validated source contract tests: `WorkoutSummaryClickContractTest.kt:87, 93` inspects `WorkoutSummary.kt` for `HeartRateZoneDistributionCard(` and `PowerZoneDistributionCard(`. The wrappers MUST retain exact names and contract behavior.
  - Contract tests `TelemetryHistogramCardTest.kt`, `GradeZoneDistributionCardTest.kt`, `ZoneDistributionStackedBarTest.kt`, and `WorkoutSummaryZoneDisplayModeTest.kt` assert specific structural tokens in card files. Unified `ZoneDistributionCard` implementation must fulfill these contracts and preserve wrapper compatibility.
  - Independent State Isolation: Each wrapper composable must maintain its own independent `rememberSaveable` state so multiple cards displayed together on one screen do not conflict or overwrite their selected display modes.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals**:
  - Extract reusable generic `ZoneDistributionCard` composable in `ui/aftermath/zones/ZoneDistributionCard.kt` parameterizing icon, title, unit, and hoisted mode controls.
  - Refactor `PowerZoneDistributionCard`, `HeartRateZoneDistributionCard`, and `GradeZoneDistributionCard` into thin wrappers (~15–20 lines each) delegating to `ZoneDistributionCard`.
  - Normalize default display mode to `ZoneCardDisplayMode.COLUMNS` across all wrappers.
  - Ensure all 12 call sites remain unmodified and all existing contract and unit tests pass cleanly.
* **Out-of-Scope Non-Goals**: Modifying histogram calculation engines (`TelemetryHistogramCalculator`, `ZoneDistributionCalculator`), changing chart renderers (`ZoneDistributionColumnChart`, `ZoneDistributionStackedBar`, `TelemetryHistogramChart`), or modifying database schemas / preferences DataStore.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Refined Requirements**: Extends `REQ-REV-008` (*Generic Zone Distribution Card Architecture* in living shard `docs/requirements/req_aftermath_reviews.md`), consolidating UI implementations of `REQ-UI-372` (*HR Zone Card*), `REQ-UI-386`/`REQ-UI-388` (*Power Zone Card & Compact Summary Gating*), and `REQ-UI-453` (*Grade Zone Card*).
* **Historical Origin**: Introduced across `ATT-1845`, `ATT-3390`, `ATT-3773`, and `ATT-5356`.
* **Root Reason for Existing Formulation**: Rapid feature velocity prioritized isolated components per telemetry metric over early abstraction to avoid cross-coupling during visual refinements.
* **Preservation of Core Invariants**: `showModeSelector: Boolean = true` gating preserved; optional hoisted displayMode parameters preserved; per-card `rememberSaveable` state isolation preserved; 100% visual layout parity preserved across all 9 locales.

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: Zero UI drift or regression across `TrackOnMapScreen`, `WorkoutDetailTabbedContent`, and `WorkoutSummary`; contract tests remain green; zero breaking changes to public composable APIs.
* **Risk Rating**: **LOW** (Pure Compose refactoring and deduplication; zero database, background service, or threading changes).
