# Stage 3 Implementation Plan: ATT-2941 - Align turn-by-turn navigation hints UI with updated design guidelines

**Ticket**: [ATT-2941](https://atrainingtracker.atlassian.net/browse/ATT-2941)  
**Sub-task**: [ATT-2986](https://atrainingtracker.atlassian.net/browse/ATT-2986) (`[Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2941`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Architectural Design & Layout Refactoring

### 1.1 Non-Displacing Top-Level Overlay Placement (`SensorGridScreen.kt`)
* Currently, `TurnPromptBanner` is composed at lines 467-472 inside `Column(modifier = Modifier.fillMaxSize())`, directly above the scrollable sensor grid. This causes unwanted vertical layout shifts when turn prompts appear or dismiss.
* `TurnPromptBanner` will be removed from this in-flow container and placed inside the top-level spatial overlay `Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth())` gated by `if (state.showNavigationHints)`.
* It will sit at the top of the overlay stack above `ReturnNavigationHud` and `ForkDecisionCard`.
* This guarantees that when turn prompts appear, underlying telemetry tiles and live map stay 100% stationary.
* The parameter binding `promptsEnabled = state.showNavigationHints && tuningConfig.turnPromptsEnabled` will be preserved to maintain contract test compliance (`TrackingTabWysiwygContractTest`).

### 1.2 Material 3 & Design Guidelines Harmonization (`TurnPromptBanner.kt`)
* **Card Shapes (§ 5.3)**:
  - `TurnCueCard`: Update shape to `RoundedCornerShape(16.dp)`.
  - `OffRouteCard`: Update shape to `RoundedCornerShape(16.dp)`.
* **Container Colors (§ 5.4)**:
  - `TurnCueCard`: Replace `surfaceVariant` with `MaterialTheme.colorScheme.surfaceContainer` (normal approaching) and `MaterialTheme.colorScheme.primaryContainer` (when `isTurnNow`), multiplied by `overlayAlpha`.
  - `OffRouteCard`: Retain `MaterialTheme.colorScheme.errorContainer.copy(alpha = overlayAlpha)`.
* **Border Accents (§ 5.4 / § 5.7)**:
  - `TurnCueCard`: `BorderStroke(1.dp, TTColor.RouteActiveNavigation.copy(alpha = overlayAlpha.coerceAtLeast(0.4f)))`.
  - `OffRouteCard`: `BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = overlayAlpha.coerceAtLeast(0.4f)))`.
* **Spacing Scale (§ 5.2)**:
  - Outer padding: `padding(horizontal = 16.dp, vertical = 6.dp)`.
  - Inner padding: Replace `14.dp` with `padding(horizontal = 16.dp, vertical = 12.dp)`.
* **Typography Tokens (§ 5.5)**:
  - When `isTurnNow`, use `onPrimaryContainer` for text and icon tint.
  - When approaching, use `onSurfaceVariant` for text and `primary` for icon tint.

---

## 2. Atomic Implementation Steps

### Step 1: Update `SensorGridScreen.kt` Layout
* Remove `TurnPromptBanner` from inside the in-flow `Column(modifier = Modifier.fillMaxSize())`.
* Add `TurnPromptBanner` into the top-level overlay `Column` inside `if (state.showNavigationHints)` at `Alignment.TopCenter`.

### Step 2: Update `TurnPromptBanner.kt` Visual Styling
* Update card shapes to `RoundedCornerShape(16.dp)`.
* Update container colors to `surfaceContainer` / `primaryContainer`.
* Update inner row padding to `padding(horizontal = 16.dp, vertical = 12.dp)`.
* Update text and icon color references for `isTurnNow` state to `onPrimaryContainer`.

### Step 3: Realize & Add Contract Tests (`SensorGridScreenRouteIntegrationTest.kt`)
* Add `testSensorGridScreen_turnPromptBanner_floatsAsTopCenterOverlayAndGatedByNavigationHints()`.
* Add `testTurnPromptBanner_stylingTokens_conformsToDesignGuidelines()`.

### Step 4: Targeted Unit Test Verification
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenRouteIntegrationTest"`.
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingTabWysiwygContractTest"`.

### Step 5: Full Clean-Room Regression Verification
* Execute full clean-room unit test suite: `./gradlew testDebugUnitTest`.

---

## 3. Invariants & Risk Mitigation

* **No Telemetry Jumps**: Cockpit sensor tiles remain stationary when alerts fire or vanish.
* **Navigation Gating**: Turn prompts are completely suppressed on tabs where `showNavigationHints = false`.
* **Zero Regressions**: 100% full-suite test pass rate.
