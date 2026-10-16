# Stage 2 Requirement & Test Specification: ATT-3772

## 1. Requirement Specification (`REQ-UI-387`)

- **Requirement ID**: `REQ-UI-387`
- **Title**: *Compact Legend-Free Stacked Bar Display Mode in Zone Distribution Cards.*
- **Statement**:
  The system SHALL omit the detailed per-zone time and percentage breakdown list (`FlowRow` of `StackedBarLegendItem`) underneath the horizontal stacked bar when `HeartRateZoneDistributionCard` or `PowerZoneDistributionCard` is rendered in `ZoneCardDisplayMode.STACKED_BAR` mode (ATT-3772, amending `REQ-UI-372`):
  1. *Default Legend Suppression (`REQ-UI-387.1`)*:
     - `ZoneDistributionStackedBar` in `ZoneDistributionStackedBar.kt` SHALL default its `showLegend: Boolean` parameter to `false`.
     - When `showLegend == false`, `ZoneDistributionStackedBar` SHALL render strictly the continuous horizontal stacked bar (`Box` with proportional `weight(entry.percentage)` segments, `1.dp` dividers, and `0.5.dp` outline border) without rendering the `FlowRow` per-zone breakdown list underneath.
  2. *Zone Distribution Card Stacked Bar Invocation (`REQ-UI-387.2`)*:
     - In `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`, the `ZoneCardDisplayMode.STACKED_BAR` branch SHALL invoke `ZoneDistributionStackedBar(distribution = distribution, modifier = Modifier.fillMaxWidth(), showLegend = false)`.
  3. *Preservation of Detailed Breakdown in Columns Mode (`REQ-UI-387.3`)*:
     - Detailed per-zone durations (`m:ss` / `h:mm:ss`), percentages (`%`), and zone badges (`Z1..Z7`) SHALL continue to be displayed in `ZoneCardDisplayMode.COLUMNS` (`ZoneDistributionColumnChart`).
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-387`, amending `REQ-UI-372` (`ATT-3390` & `ATT-3676`). Targets `ZoneDistributionStackedBar.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, and `ZoneDistributionStackedBarTest.kt`.
  2. *Historical Origin & Commit Trace*: Sprint 2026-42.4 (`ATT-3390` commit `63ccd164`) and Sprint 2026-42.5 (`ATT-3676` commit `b989e1ce`).
  3. *Root Reason for Existing Formulation ("Why was this fence built?")*: `ATT-3390` initially defaulted `showLegend = true` on `ZoneDistributionStackedBar` before `STACKED_BAR` mode was designated as the compact overview mode in `ATT-3772` / `ATT-3773`.
  4. *Preservation of Core Invariants ("Why is it safe to modify now?")*: Defaulting `showLegend = false` and passing `showLegend = false` in `STACKED_BAR` mode makes the stacked bar representation compact for workout summaries while preserving detailed per-zone duration/percentage inspection in `COLUMNS` mode.
- **Acceptance Criteria (Given-When-Then)**:
  - *Given* `HeartRateZoneDistributionCard` or `PowerZoneDistributionCard` with `effectiveDisplayMode == ZoneCardDisplayMode.STACKED_BAR`,
  - *When* `ZoneDistributionStackedBar` is rendered,
  - *Then* `showLegend` is `false` and the detailed per-zone time breakdown list (`FlowRow` of `StackedBarLegendItem`) is omitted underneath the horizontal stacked bar.
  - *Given* `HeartRateZoneDistributionCard` or `PowerZoneDistributionCard` with `effectiveDisplayMode == ZoneCardDisplayMode.COLUMNS`,
  - *When* `ZoneDistributionColumnChart` is rendered,
  - *Then* per-zone durations (`m:ss`), percentages (`%`), and `Z1..Z7` labels remain displayed.

## 2. Test Specification (`TST-UI-350`)

- **Test ID**: `TST-UI-350`
- **Ticket**: `ATT-3772`
- **Title**: *Compact Legend-Free Stacked Bar Display Mode Verification Suite*
- **Traces To**: `REQ-UI-387`
- **Test Procedures**:
  1. *Source & Default Parameter Contract Audit (`ZoneDistributionStackedBarTest.kt`)*:
     - Verify that `ZoneDistributionStackedBar.kt` declares `showLegend: Boolean = false` as the default parameter value on `ZoneDistributionStackedBar`.
     - Verify that `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` invoke `ZoneDistributionStackedBar` with `showLegend = false`.
     - Verify that `ZoneDistributionStackedBarMath` active entry filtering, 5-zone HR / 7-zone Power parity, and `RoundedCornerShape(6.dp)` / `0.5.dp` border invariants remain 100% intact.
  2. *Clean-Room Full Suite Regression Execution*:
     - Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

## 3. Traceability Matrix

| Requirement ID | Test Case ID | Verification Artifact | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-387` | `TST-UI-350` | `ZoneDistributionStackedBarTest.kt` | `Planned` |
