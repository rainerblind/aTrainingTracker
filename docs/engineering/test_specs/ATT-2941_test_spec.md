# Stage 2 Requirement & Test Specification: ATT-2941 - Align turn-by-turn navigation hints UI with updated design guidelines

**Ticket**: [ATT-2941](https://atrainingtracker.atlassian.net/browse/ATT-2941)  
**Sub-task**: [ATT-2985](https://atrainingtracker.atlassian.net/browse/ATT-2985) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2941`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-324)

### 1.1 Requirement Definition
* **Requirement ID**: `REQ-UI-324`
* **Title**: Turn-by-Turn Navigation Prompts Top-Level Spatial Overlay Integration & Design Guideline Harmonization
* **Type**: Functional / UI Architecture Specification
* **Target Release**: `V4.9.40`
* **Status**: Specified
* **Amends**: `REQ-MAP-028` (*Visual & Auditory Turn-by-Turn Navigation Prompts*, Clause 4, ATT-1450) and `REQ-UI-287` (*Cockpit Turn Prompt Banner*)
* **Parent Ticket**: ATT-2941

### 1.2 Description
The system shall eliminate cockpit telemetry tile displacement during turn-by-turn navigation guidance, gate navigation prompts strictly behind the tab-level navigation hints setting, and harmonize the visual design of `TurnPromptBanner` with Design Guidelines §§ 5.2, 5.3, 5.4, and 5.7:
1. *Top-Level Spatial Overlay Architecture (`SensorGridScreen.kt`)*:
   - `TurnPromptBanner` shall be removed from the in-flow base cockpit `Column` in `SensorGridScreen.kt`.
   - `TurnPromptBanner` shall be hosted within the top-level spatial overlay `Column` anchored at `Alignment.TopCenter`, stacked directly above `ReturnNavigationHud` and `ForkDecisionCard`.
   - The appearance, vertical expansion, or dismissal of `TurnPromptBanner` shall NOT alter the vertical layout coordinates, position, or scroll offset of the underlying sensor grid tiles or live map.
2. *Strict Per-Tab Navigation Gating*:
   - `TurnPromptBanner` shall be conditionally gated strictly behind `state.showNavigationHints`.
   - On tracking tabs where `state.showNavigationHints == false`, `TurnPromptBanner` shall be completely excluded from the Compose composition tree, ensuring 0% touch interception and complete suppression of turn prompts.
3. *Harmonized Card Visuals & Material 3 Styling (`TurnPromptBanner.kt`)*:
   - **Card Shape**: `TurnCueCard` and `OffRouteCard` shall adopt `RoundedCornerShape(16.dp)`, harmonizing with `ForkDecisionCard` and Section 5.3.
   - **Container Color & Transparency**:
     - Approaching turn cue: `MaterialTheme.colorScheme.surfaceContainer.copy(alpha = overlayAlpha)`.
     - Immediate maneuver (`isTurnNow`): `MaterialTheme.colorScheme.primaryContainer.copy(alpha = overlayAlpha)`.
     - Off-route warning: `MaterialTheme.colorScheme.errorContainer.copy(alpha = overlayAlpha)`.
   - **Border Accent**:
     - Turn cue: `BorderStroke(1.dp, TTColor.RouteActiveNavigation.copy(alpha = 0.5f))` enforcing the Royal Blue navigation domain accent (Section 5.4 / 5.7).
     - Off-route warning: `BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))`.
   - **Spacing Scale**:
     - Outer card padding: `padding(horizontal = 16.dp, vertical = 6.dp)`.
     - Inner content padding: `padding(horizontal = 16.dp, vertical = 12.dp)` conforming strictly to the 4/8/12/16/24 dp spacing scale (Section 5.2).
4. *Preservation of System Invariants*:
   - Turn maneuver countdown distances, directional arrow iconography, street name labels, and auto-dismiss timing (`tuningConfig.navigationCueDismissDurationSec`) remain 100% operational.
   - AMOLED battery saver wake-up and auditory chime dispatch remain intact.
   - Full clean-room test suite pass rate must remain 100%.

### 1.3 Acceptance Criteria (Given-When-Then)

#### Scenario 1: Non-Displacing Spatial Overlay Placement
* **Given** an active workout tracking session on `SensorGridScreen`,
* **When** an approaching turn cue ($D \le 150\text{ m}$) or immediate turn prompt ($D \le 25\text{ m}$) is triggered,
* **Then** `TurnPromptBanner` floats at `Alignment.TopCenter` within the top-level spatial overlay stack,
* **And** the underlying sensor grid tiles and live map remain 100% stationary without any layout shift.

#### Scenario 2: Tab Navigation Hints Gating
* **Given** an active workout tracking tab where `showNavigationHints = false`,
* **When** navigating an active route with upcoming turn maneuvers,
* **Then** `TurnPromptBanner` is completely excluded from the Compose composition tree on that tab.

#### Scenario 3: Visual Design & Material 3 Harmonization
* **Given** `TurnCueCard` rendered on screen,
* **When** inspecting its layout and styling tokens,
* **Then** the card shape is `RoundedCornerShape(16.dp)`,
* **And** the container color resolves to `surfaceContainer` (or `primaryContainer` when `isTurnNow`) multiplied by `overlayAlpha`,
* **And** the border stroke is `1.dp` with `TTColor.RouteActiveNavigation.copy(alpha = 0.5f)`,
* **And** the inner padding is `16.dp` horizontal and `12.dp` vertical.

#### Scenario 4: Off-Route Warning Styling
* **Given** an off-route deviation alert active,
* **When** `OffRouteCard` is rendered,
* **Then** the card shape is `RoundedCornerShape(16.dp)`,
* **And** the container color is `errorContainer.copy(alpha = overlayAlpha)`,
* **And** the border stroke is `1.dp` with `error.copy(alpha = 0.5f)`.

---

## 2. Test Specification (TST-UI-284)

### 2.1 Test Definition
* **Test ID**: `TST-UI-284`
* **Title**: Turn-by-Turn Navigation Prompts Spatial Overlay & Visual Harmonization Verification
* **Target Requirement**: `REQ-UI-324`
* **Test Type**: Automated Architectural & Contract Unit Tests (`SensorGridScreenRouteIntegrationTest.kt`)
* **Status**: Specified

### 2.2 Test Cases

#### Case 1: `testSensorGridScreen_turnPromptBanner_floatsAsTopCenterOverlayAndGatedByNavigationHints`
* **Target**: `SensorGridScreen.kt`
* **Verification Steps**:
  1. Inspect the main in-flow cockpit `Column`.
  2. Verify that `TurnPromptBanner` is NOT rendered inside the in-flow `Column`.
  3. Verify that `TurnPromptBanner` is rendered within `if (state.showNavigationHints)` inside the top-level overlay `Column` at `Alignment.TopCenter`.
  4. Verify that `TurnPromptBanner` binds `overlayAlpha` to `tuningConfig.navigationCueTransparency`.
  5. Verify that `TurnPromptBanner` binds `dismissDurationSec` to `tuningConfig.navigationCueDismissDurationSec`.

#### Case 2: `testTurnPromptBanner_stylingTokens_conformsToDesignGuidelines`
* **Target**: `TurnPromptBanner.kt`
* **Verification Steps**:
  1. Verify `TurnCueCard` uses `RoundedCornerShape(16.dp)`.
  2. Verify `TurnCueCard` uses `surfaceContainer` (approaching) and `primaryContainer` (`isTurnNow`).
  3. Verify `TurnCueCard` uses `BorderStroke(1.dp, TTColor.RouteActiveNavigation.copy(alpha = 0.5f))`.
  4. Verify `OffRouteCard` uses `RoundedCornerShape(16.dp)` and `errorContainer`.
  5. Verify inner padding conforms to `padding(horizontal = 16.dp, vertical = 12.dp)`.

#### Case 3: `testSensorGridScreen_enforcesRuntimeGating`
* **Target**: `TrackingTabWysiwygContractTest.kt`
* **Verification Steps**:
  1. Verify that `state.showNavigationHints` runtime gating is preserved for `TurnPromptBanner`.

---

## 3. Localization Parity (REQ-LOC-001)

No new string resources are introduced in this ticket. Existing string keys (`turn_cue_now`, `turn_cue_off_route`, etc.) are preserved and remain 100% localized across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
