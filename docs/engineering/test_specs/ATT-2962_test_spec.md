# Stage 2 Requirement & Test Specification: ATT-2962 - Modernize in-ride fork decision card with direction grouping, clean white surfaces, and removed tap hint

**Ticket**: [ATT-2962](https://atrainingtracker.atlassian.net/browse/ATT-2962)  
**Sub-task**: [ATT-3030](https://atrainingtracker.atlassian.net/browse/ATT-3030) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2962`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Requirement Specification (REQ-UI-330)

### 1.1 Requirement Definition
* **Requirement ID**: `REQ-UI-330`
* **Title**: In-Ride Fork Decision Card Directional Grouping, Clean Surface Styling & Glanceability Optimization
* **Type**: User Interface & Navigation Ergonomics Specification
* **Target Release**: `V4.9.39`
* **Status**: Specified
* **Amends/Complements**: Amends `REQ-MAP-031` (*In-Ride Fork Route Decision Card HUD Layout & Selection Interactions*, Sprint 2026-41.2, ATT-2481).
* **Parent Ticket**: ATT-2962

### 1.2 Description
The system shall modernize `ForkDecisionCard.kt` to optimize glanceability on bike mounts through directional route grouping, crisp white surface styling, and elimination of redundant selection hints:

1. *Clean Surface & Border Styling*:
   - The outer container card SHALL use `MaterialTheme.colorScheme.surface` with `overlayAlpha` (crisp white in light theme) instead of `surfaceVariant`.
   - The outer card SHALL apply a subtle border: `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))`.
   - Inner direction group cards SHALL use `MaterialTheme.colorScheme.surface` or `surfaceContainerLow` with `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))` and `RoundedCornerShape(12.dp)`.
2. *Directional Route Grouping*:
   - The card SHALL group `decisionState.branches` by `ForkDirection` (`branches.groupBy { it.direction }`).
   - For each direction group:
     - Render a single direction indicator column on the left containing the direction arrow icon (`Icons.AutoMirrored.Filled.ArrowBack`, `Icons.Default.ArrowUpward`, or `Icons.AutoMirrored.Filled.ArrowForward`) and localized heading label (`R.string.fork_direction_*`) in `MaterialTheme.typography.labelSmall` bold primary color.
     - Render a vertical divider separating the direction column from the routes column.
     - In the right-hand column, stack all candidate routes matching that direction, with a 0.5dp `HorizontalDivider` between multiple routes under the same heading.
3. *Redundant Hint Text Removal*:
   - The selection hint text (`R.string.fork_select_hint` / "Tippen zum Auswählen" / "Tap to select") SHALL be completely removed from candidate route rows, dedicating the entire row width to the route title (`maxLines = 1`, `TextOverflow.Ellipsis`) and distance/elevation metrics.
4. *Interaction & Callback Preservation*:
   - Each route row item SHALL remain interactive, invoking `onRouteSelected(branch.routeId)` upon user click.
   - The dismissal close button SHALL invoke `onDismiss()`.
5. *9-Language Localization Parity*:
   - Ensure all utilized string resources exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.
6. *Previews & Invariants*:
   - Provide `@Preview` composables for light and dark themes.
   - Maintain 100% full clean-room unit test pass rate.

---

### 1.3 Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Amends `REQ-MAP-031` (*In-Ride Fork Route Decision Card HUD Layout & Selection Interactions*, Sprint 2026-41.2, ATT-2481).
2. **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.2 (`ATT-2481`).
3. **Root Reason for Existing Formulation**: The original design was a flat list where every route candidate was displayed in a separate card repeating the direction icon and adding a verbose tap hint.
4. **Preservation of Core Invariants**: `ForkDecisionState` consumption, `onRouteSelected` callback, `onDismiss` callback, animated entrance/exit, and countdown distance header formatting remain strictly preserved.

---

## 2. Acceptance Criteria (Given-When-Then)

* **Scenario 1: Redundant Hint Text Elimination**:
  - *Given* an active fork decision card rendered in the Cockpit HUD,
  - *When* inspecting candidate route rows,
  - *Then* the "Tippen zum Auswählen" / "Tap to select" hint text is NOT displayed, allowing maximum width for route names and metrics.

* **Scenario 2: Directional Route Grouping**:
  - *Given* candidate routes diverging at an approaching fork (e.g. 2 routes straight ahead and 1 route turning right),
  - *When* the fork decision card renders,
  - *Then* the routes are grouped under their respective directions (one unified group for "Gerade aus" containing both straight routes separated by a divider, and a distinct group for "Rechts" containing the right-turning route).

* **Scenario 3: White Surface & Border Styling**:
  - *Given* the fork decision card rendered on screen,
  - *When* inspecting the visual theme,
  - *Then* the card container and inner groups render with `MaterialTheme.colorScheme.surface` and subtle light borders (`outlineVariant`), with no grey `surfaceVariant` container styling.

* **Scenario 4: 9-Language Localization Parity**:
  - *Given* all string resources utilized in `ForkDecisionCard`,
  - *When* inspecting values across all 9 supported locales,
  - *Then* all localized strings are non-blank and defined.

---

## 3. Test Specification (TST-UI-290)

### 3.1 Unit Tests (`ForkDecisionCardTest.kt`)
1. `forkDirectionGrouping_groupsBranchesByDirectionCorrectly`:
   - Group a multi-branch list with 2 STRAIGHT routes and 1 RIGHT route.
   - Assert resulting groups have size 2, with STRAIGHT having 2 branches and RIGHT having 1 branch.
2. `forkDecisionCardLayout_contractsAndTokens`:
   - Assert `surface` and `outlineVariant` usage.
   - Assert `fork_select_hint` is no longer required in layout composition.
3. `forkDecisionCardLocalization_9LanguageParity`:
   - Verify non-empty translations for all fork alert strings (`fork_alert_title`, `fork_approaching_in_m`, `fork_dismiss`, `fork_direction_left`, `fork_direction_straight`, `fork_direction_right`) across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 3.2 Full Regression Suite
* Run `./gradlew testDebugUnitTest` verifying 100% test pass rate.

---

## 4. Traceability Matrix

| Requirement Clause | Test Specification ID | Verification Method | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-330` (Clause 1) | `TST-UI-290.1` | `ForkDecisionCardTest` | Specified |
| `REQ-UI-330` (Clause 2) | `TST-UI-290.2` | `ForkDecisionCardTest` | Specified |
| `REQ-UI-330` (Clause 3) | `TST-UI-290.3` | `ForkDecisionCardTest` | Specified |
| `REQ-UI-330` (Clause 4) | `TST-UI-290.4` | `ForkDecisionCardTest` | Specified |
| `REQ-UI-330` (Clause 5) | `TST-UI-290.5` | `ForkDecisionCardTest` | Specified |
| `REQ-UI-330` (Clause 6) | `TST-UI-290.6` | Clean-room full test suite | Specified |
