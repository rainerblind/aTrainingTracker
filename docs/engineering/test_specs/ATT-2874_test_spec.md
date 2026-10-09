# Stage 2: Requirement & Test Specification - ATT-2874: Float fork decision prompt as top-level overlay and gate by tab navigation hints toggle

**Ticket**: [ATT-2874](https://rainerblind.atlassian.net/browse/ATT-2874)  
**Sub-task**: [ATT-2927](https://rainerblind.atlassian.net/browse/ATT-2927) (`[Test-Spec]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Requirement Mapping**: `REQ-UI-321` (*Top-Level Spatial Overlay & Tab Navigation Hints Gating for In-Ride Fork Route Decision Prompts*)  
**Test Spec ID**: `TST-UI-281`  
**Branch**: `improvement/ATT-2874`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Formal Requirement Specification (`REQ-UI-321`)

### Requirement Text
The system SHALL eliminate cockpit telemetry tile displacement during in-ride fork route alerts and enforce per-tab navigation hints configuration in `SensorGridScreen.kt` by floating `ForkDecisionCard` as a top-level spatial overlay and gating it strictly behind `state.showNavigationHints` (ATT-2874, amending `REQ-MAP-031`):

1. **Top-Level Spatial Box Layering (`SensorGridScreen.kt`)**:
   * In tracking and preview modes, `SensorGridScreen` SHALL compose the stationary main screen content (sensor grid, map, elevation profile, and banners) inside a base layout container.
   * `ForkDecisionCard` SHALL be rendered within a top-level non-displacing overlay anchored at `Alignment.TopCenter`.
   * The presence, appearance, animation expansion, or height changes of `ForkDecisionCard` SHALL NOT alter or displace the layout coordinates, vertical position, or scroll offset of the underlying sensor grid tiles.
2. **Strict Per-Tab Navigation Gating**:
   * `ForkDecisionCard` SHALL be conditionally gated strictly behind `state.showNavigationHints`.
   * When `state.showNavigationHints == false` on the active tracking tab, `ForkDecisionCard` SHALL be completely excluded from the Compose composition tree, ensuring 0% touch interception and complete suppression of fork decision alerts on navigation-free tabs.
3. **Glanceable Semi-Transparency & Touch Event Pass-Through**:
   * `ForkDecisionCard` SHALL continue to apply `tuningConfig.navigationCueTransparency` per Design Guidelines Section 5.7.
   * When collapsed or dismissed (`decisionState == null`), the overlay wrapper SHALL NOT intercept touch events or obstruct user interactions with underlying sensor tiles.
4. **Preservation of System Invariants**:
   * Manual route selection via `forkNavRepo.selectRouteManually(routeId)` and prompt dismissal via `forkNavRepo.dismissPrompt()` MUST function identically.
   * Autonomous route snapping ($D_{\text{past}} \ge 50\text{m}$) and 100% test pass rate across the full test suite MUST be strictly preserved.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Refines and amends `REQ-MAP-031` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts*, Clause 3).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1955` (Sprint 2026-40.16).
3. *Root Reason for Existing Formulation*: In ATT-1955, `ForkDecisionCard` was added as an in-flow sequential composable within `Column`, which caused vertical layout displacement whenever triggered. Additionally, tab-level `showNavigationHints` gating was omitted.
4. *Preservation of Core Invariants*: Bounding-box pre-filtering (ATT-2873), candidate route matching, and all autonomous fork binding logic remain 100% preserved.

### Acceptance Criteria (Given-When-Then)
* **AC-1 (Tab Navigation Hints Gating)**:
  * *Given* an active workout tracking tab with `showNavigationHints = false`,
  * *When* approaching a fork in the road ($D_{\text{fork}} \le 300\text{m}$),
  * *Then* `ForkDecisionCard` SHALL NOT be displayed or composed on that tab.
* **AC-2 (Top-Level Spatial Overlay)**:
  * *Given* an active workout tracking tab with `showNavigationHints = true`,
  * *When* approaching a fork in the road ($D_{\text{fork}} \le 300\text{m}$),
  * *Then* `ForkDecisionCard` SHALL float at `Alignment.TopCenter` on top of the screen content as an overlay,
  * *And* the underlying sensor tiles SHALL remain 100% stationary without any layout shift.
* **AC-3 (Touch Event Pass-Through)**:
  * *Given* tracking without an active fork prompt (`decisionState == null`),
  * *When* tapping or scrolling over the upper screen area,
  * *Then* touch events SHALL pass through directly to underlying sensor tiles.

---

## 2. Test Specification (`TST-UI-281`)

### Verification Plan
| Test ID | Scope | Target Component | Method |
| :--- | :--- | :--- | :--- |
| `TST-UI-281.1` | Contract | `TrackingTabWysiwygContractTest` | Assert `ForkDecisionCard` is gated by `state.showNavigationHints` |
| `TST-UI-281.2` | Contract | `SensorGridScreenRouteIntegrationTest` | Assert `ForkDecisionCard` is anchored at `Alignment.TopCenter` in an overlay container and not in sensor `Column` |
| `TST-UI-281.3` | Contract | `SensorGridScreenRouteIntegrationTest` | Assert `ForkDecisionCard` consumes `tuningConfig.navigationCueTransparency` per §5.7 |
| `TST-UI-281.4` | Regression | Full Test Suite | Clean-room `./gradlew testDebugUnitTest` execution |

### 9-Language Localization Audit
No new user-visible strings are introduced in `ATT-2874`. Existing strings (`fork_alert_title`, `fork_approaching_in_m`, `dismiss`, route names) remain 100% verified across all 9 supported locales.

---

## 3. Traceability Matrix

| Requirement Clause | Verification Test | Target File | Status |
| :--- | :--- | :--- | :---: |
| `REQ-UI-321.1` (Spatial overlay & tile stationarity) | `TST-UI-281.2` | `SensorGridScreenRouteIntegrationTest.kt` | Specified |
| `REQ-UI-321.2` (Per-tab navigation hints gating) | `TST-UI-281.1` | `TrackingTabWysiwygContractTest.kt` | Specified |
| `REQ-UI-321.3` (Semi-transparency & touch pass-through) | `TST-UI-281.3` | `SensorGridScreenRouteIntegrationTest.kt` | Specified |
| `REQ-PRO-001` (Zero regressions) | `TST-UI-281.4` | Full Test Suite | Specified |
