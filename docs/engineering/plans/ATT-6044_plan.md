# Stage 3 Implementation Plan: ATT-6044 - Unify Power-, HeartRate- and GradeZoneDistributionCard into generic ZoneDistributionCard

**Ticket**: [ATT-6044](https://rainerblind.atlassian.net/browse/ATT-6044)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-REV-008` (`008.1`–`008.3`)  
**Test Spec Mapping**: `TST-REV-009` (`009.1`–`009.4`)  
**Branch**: `feature/ATT-6044`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-10  

---

## 1. Technical Architecture & SWE.2 Design
* **Decomposition & Boundaries**:
  - `ZoneDistributionCard.kt`: Reusable `@Composable` in `ui/aftermath/zones` encapsulating Card container, Header Row (`iconRes`, `title`, formatted active duration), Chart Body (`STACKED_BAR` with `showLegend = false`, `COLUMNS`/legacy `FIVE_ZONES`, `HISTOGRAM`), and bottom `SingleChoiceSegmentedButtonRow` (height 28.dp, 2 or 3 buttons).
  - Wrapper Composables: `PowerZoneDistributionCard.kt`, `HeartRateZoneDistributionCard.kt`, and `GradeZoneDistributionCard.kt` refactored into thin wrappers (~15 lines) passing metric-specific icon, title, unit, and isolated state to `ZoneDistributionCard`.
  - Normalization: Deprecated alias `FIVE_ZONES` normalized to `COLUMNS` as default across all wrappers.
* **State Isolation Invariant**: Each wrapper composable manages its own independent `rememberSaveable` state via parameter delegation, preventing cross-card mode collisions when rendered together on one screen.
* **Public API & Call-Site Compatibility**: Composable wrapper function signatures and names remain 100% backward-compatible across all 12 call sites (`TrackOnMapScreen`, `WorkoutDetailTabbedContent`, `WorkoutSummary`).

---

## 2. UI Consistency & Theming (Rule 23 & Rule 80)
* **Reference Component**: Existing `PowerZoneDistributionCard` layout.
* **Design System Tokens**: Shape `RoundedCornerShape(12.dp)`, container color `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)`, icon size `18.dp` tinted `primary`, spacing `spacedBy(10.dp)`.
* **Segmented Mode Switcher**: `SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(28.dp))` with `icon = {}`, `contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)`, `labelSmall` single-line text (`TextOverflow.Ellipsis`).

---

## 3. Atomic Implementation Steps

| Step | Component | Target File | Action & Invariants | Traceability |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Generic Card | `ZoneDistributionCard.kt` | Author generic composable with hoisted displayMode, fallback resolution, header, body branches, and gated switcher. | `REQ-REV-008.1-2`<br>`TST-REV-009.1` |
| **2** | Wrappers | `PowerZoneDistributionCard.kt`<br>`HeartRateZoneDistributionCard.kt`<br>`GradeZoneDistributionCard.kt` | Refactor wrappers to delegate to `ZoneDistributionCard` with respective icons, titles, units, and default `COLUMNS`. | `REQ-REV-008.3`<br>`TST-REV-009.2` |
| **3** | Unit Tests | `ZoneDistributionCardTest.kt`<br>`GradeZoneDistributionCardTest.kt` | Add unit tests for generic card; align contract test source assertions with `ZoneDistributionCard` architecture. | `REQ-REV-008.1-3`<br>`TST-REV-009.1-3` |
| **4** | Contract Tests | `WorkoutSummaryClickContractTest.kt`<br>`TelemetryHistogramCardTest.kt` | Verify `WorkoutSummary` clicks chain `mapClickModifier` and layout contracts pass cleanly. | `REQ-REV-008.3`<br>`TST-REV-009.3` |
| **5** | Verification | Test Suite | Run `./gradlew testDebugUnitTest --tests "*ZoneDistribution*" --tests "*WorkoutSummary*"` and governance checks. | `REQ-PRO-001`<br>`TST-REV-009.4` |

---

## 4. System Invariants & Rollback Safety
* **Code Lock (Rule 4)**: No production source files modified until Gate 3 passes and `check-gate` exits 0.
* **Compatibility Invariant**: All 12 call sites remain unmodified; `WorkoutSummaryClickContractTest` wrapper assertions pass.
* **Risk Rating**: **LOW** (Declarative Compose deduplication; zero schema migrations, threading, or database changes).
