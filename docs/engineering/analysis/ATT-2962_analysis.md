# Stage 1 Analysis Report: ATT-2962 - Modernize in-ride fork decision card with direction grouping, clean white surfaces, and removed tap hint

**Ticket**: [ATT-2962](https://atrainingtracker.atlassian.net/browse/ATT-2964)  
**Sub-task**: [ATT-3029](https://atrainingtracker.atlassian.net/browse/ATT-3029) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2962`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Statement & Forensic Root Cause Investigation

During workout recording with route candidate matching, when approaching a route divergence point, the in-ride fork decision HUD card (`ForkDecisionCard.kt`) displays the candidate routes diverging ahead. Forensic inspection identifies three major UI/UX defects:

1. **Redundant Selection Hint Text ("Tippen zum Auswählen" / "Tap to select")**:
   - Each route row currently displays `stringResource(R.string.fork_select_hint)` on the trailing edge. On phone screens mounted on handlebars, this crowds horizontal space, causing route names to truncate prematurely after just a few characters, adding unnecessary visual noise.
2. **Ungrouped Flat List with Repeated Direction Icons**:
   - When multiple candidate routes branch in the same general direction (e.g. two routes continue straight while one turns right), each route is rendered in its own separate item card, repeating the identical direction icon and label ("Gerade aus"). Athletes cannot quickly group alternatives by maneuver.
3. **Mismatched Grey Surface Styling**:
   - The card container currently uses `surfaceVariant` (`MaterialTheme.colorScheme.surfaceVariant.copy(alpha = overlayAlpha)`), which creates a dark, muddy grey surface in light mode. This clashes with the crisp white surfaces (`MaterialTheme.colorScheme.surface`) and clean borders (`outlineVariant`) established in Design Guidelines Section 5.4.

---

## 2. Chesterton's Fence Requirement Archaeology

1. **Original Requirement ID & Target**:
   - `REQ-MAP-031` (*In-Ride Fork Route Decision Card HUD Layout & Selection Interactions*, Sprint 2026-41.2, ATT-2481).
2. **Historical Origin & Commit Trace**:
   - Created in Sprint 2026-41.2 to render candidate route branches in the Cockpit HUD.
3. **Root Reason for Existing Formulation**:
   - The original design was a rapid proof-of-concept using flat lists and `surfaceVariant`. The tap hint was added for initial discoverability but is redundant in production where cards are explicitly interactive.
4. **Preservation of Core Invariants**:
   - Selection callbacks: Clicking a route must invoke `onRouteSelected(branch.routeId)`.
   - Dismissal callback: Tapping the close button invokes `onDismiss()`.
   - Distance to fork and approaching header text: Countdown distance and `Icons.Default.CallSplit` icon remain present.
   - Animated visibility: Smooth vertical fade in/out transitions remain functional.
   - 9-language localization parity.

---

## 3. Scope Bounding & Invariants Enforcement

### 3.1 In Scope
* In `ForkDecisionCard.kt`:
  - **Clean Surface Styling**: Outer `Card` and inner groups render with `MaterialTheme.colorScheme.surface` (crisp white in light mode) with `overlayAlpha` and subtle light borders (`BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))`).
  - **Directional Grouping**:
    - Group branches by `ForkDirection` (`branches.groupBy { it.direction }`).
    - For each direction, render a unified group card with a dedicated left column showing the direction arrow icon and localized heading label (`R.string.fork_direction_*`).
    - In the right sub-column, stack candidate routes with clean horizontal dividers between multiple routes under the same heading.
  - **Hint Removal**: Completely remove `stringResource(R.string.fork_select_hint)` from route items.
  - **Previews**: Add `@Preview` composables for light and dark modes with multi-route sample states.
* In `ForkDecisionCardTest.kt`:
  - Add contract tests verifying grouping logic, layout assertions, token conformance, and localization parity.

### 3.2 Out of Scope
* Modifying route divergence detection math (handled in `ATT-2964`).
* Changing route database models or repository interfaces.

---

## 4. Proposed Architecture & Verification Strategy

### 4.1 Grouped Layout Hierarchy
```
ForkDecisionCard (Surface / Card: surface, border outlineVariant)
 ├── Header Row: CallSplit icon + "Gabelung voraus (in 200m)" + Close button
 └── Direction Groups (groupBy ForkDirection)
      └── Direction Group Card (Surface: surfaceContainerLow / surface, border outlineVariant)
           └── Row
                ├── Direction Column (width 52dp): Direction Arrow + "Gerade aus" (primary bold)
                ├── Vertical Divider
                └── Routes Column (weight 1f)
                     ├── Route Item 1: Name (bold titleSmall/bodyMedium) + Dist/Elev metrics (bodySmall)
                     ├── Horizontal Divider (if multiple routes in this group)
                     └── Route Item 2: Name + Dist/Elev metrics
```

### 4.2 Verification & Test Strategy
1. Contract tests in `ForkDecisionCardTest.kt` verifying grouping behavior and string resources.
2. Layout tests verifying Material 3 tokens, shape, and 9-language parity.
3. Clean-room full test suite (`./gradlew testDebugUnitTest`).
