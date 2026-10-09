# Stage 1 Analysis: ATT-2941 - Align turn-by-turn navigation hints UI with updated design guidelines

**Ticket**: [ATT-2941](https://atrainingtracker.atlassian.net/browse/ATT-2941)  
**Sub-task**: [ATT-2984](https://atrainingtracker.atlassian.net/browse/ATT-2984) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2941`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

During on-device desk testing and physical device review of `ATT-1450` (Turn-by-Turn Navigation Cues) on the Google Pixel 10 mounted on a bicycle handlebar, two ergonomic and visual shortcomings were identified:
1. **Displacing In-Flow Layout (`SensorGridScreen.kt:467`)**: `TurnPromptBanner` is currently composed as an in-flow sequential element inside the base cockpit `Column` immediately preceding the sensor grid. Whenever a turn maneuver is approaching ($D \le 150\text{ m}$), executing ($D \le 25\text{ m}$), or deviating off-route, the card expands vertically and abruptly pushes down all sensor metrics tiles and the live map below it. When the prompt dismisses, the entire screen jumps upward. This layout shift disrupts the athlete's concentration and visual tracking of core metrics (speed, heart rate, power, cadence).
2. **Design Guideline Styling Divergence (`docs/design_guidelines.md` §§ 5.3, 5.7)**:
   - `TurnPromptBanner.kt` uses legacy `surfaceVariant` instead of modern Material 3 `surfaceContainer` tokens.
   - Corner radius is `12.dp` rather than the `16.dp` standard established by `ForkDecisionCard` (`REQ-UI-321`) and Section 5.3/5.7.
   - Inner content padding uses non-standard `14.dp` instead of the 4/8/12/16/24 dp spacing scale (`padding(horizontal = 16.dp, vertical = 12.dp)`).
   - `TurnPromptBanner` is not fully harmonized with the top-level spatial overlay stack shared by `ReturnNavigationHud` and `ForkDecisionCard`.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 In-Flow Layout Placement in `SensorGridScreen.kt`
In `SensorGridScreen.kt:464-474`:
```kotlin
// Base Layer: Stationary Cockpit Content
Column(
    modifier = Modifier.fillMaxSize()
) {
    TurnPromptBanner(
        navigationState = navState,
        promptsEnabled = state.showNavigationHints && tuningConfig.turnPromptsEnabled,
        overlayAlpha = tuningConfig.navigationCueTransparency,
        dismissDurationSec = tuningConfig.navigationCueDismissDurationSec
    )
    // 1. The Sensor Grid (Scrollable)
    Column(...) { ... }
```
Because `TurnPromptBanner` is placed directly inside the main `Column` before the sensor grid, its vertical expansion directly shifts the `y` coordinate of the sensor grid and map.

In contrast, `ReturnNavigationHud` and `ForkDecisionCard` were elevated to a top-level non-displacing spatial overlay container at lines 550-573:
```kotlin
// Floats on top at Alignment.TopCenter, strictly gated by state.showNavigationHints
if (state.showNavigationHints) {
    Column(
        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
    ) {
        ReturnNavigationHud(...)
        ForkDecisionCard(...)
    }
}
```

### 2.2 Visual Divergence in `TurnPromptBanner.kt`
In `TurnPromptBanner.kt:105-121`:
* Uses `MaterialTheme.colorScheme.surfaceVariant` instead of `MaterialTheme.colorScheme.surfaceContainer` (or `primaryContainer` when `isTurnNow`).
* Card shape is `RoundedCornerShape(12.dp)` instead of `RoundedCornerShape(16.dp)`.
* Inner padding is `14.dp` (violates the spacing scale in Section 5.2).

---

## 3. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**: Amends `REQ-MAP-028` (*Visual & Auditory Turn-by-Turn Navigation Prompts*, Clause 4, ATT-1450) and `REQ-UI-287` (*Cockpit Turn Prompt Banner*).
2. **Historical Origin & Commit Trace**: Ticket `ATT-1450` (Sprint 2026-40.16) introduced turn-by-turn navigation cues. It was implemented in-flow before the spatial overlay architecture pattern was established in `ATT-2874` (`REQ-UI-321` / `ForkDecisionCard`) and `ATT-2938` (`REQ-MAP-039` / `ReturnNavigationHud`).
3. **Root Reason for Existing Formulation**: Placing `TurnPromptBanner` in the main column was the simplest MVP solution to guarantee visibility above the sensor grid. However, field desk testing revealed that unexpected layout jumps are highly disorienting when riding.
4. **Preservation of Core Invariants**:
   - Turn countdown distance calculation and maneuver direction detection remain 100% operational.
   - AMOLED battery saver wake-up (`BatterySaverController.onWakeupEvent`) remains triggered on approach and off-route events.
   - Auditory chime triggers remain active.
   - Configurable transparency alpha (`tuningConfig.navigationCueTransparency`) and auto-dismiss duration (`tuningConfig.navigationCueDismissDurationSec`) remain strictly honored.
   - Gating behind `state.showNavigationHints` and `tuningConfig.turnPromptsEnabled` is preserved.

---

## 4. Proposed Technical Solution & Architecture

### 4.1 Top-Level Spatial Overlay Integration (`SensorGridScreen.kt`)
1. Remove `TurnPromptBanner` from the in-flow `Column(modifier = Modifier.fillMaxSize())` at lines 464-474.
2. Move `TurnPromptBanner` into the top-level spatial overlay `Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth())` gated by `if (state.showNavigationHints)`.
3. In the overlay stack, place `TurnPromptBanner` at the top:
   ```kotlin
   if (state.showNavigationHints) {
       Column(
           modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
       ) {
           TurnPromptBanner(
               navigationState = navState,
               promptsEnabled = tuningConfig.turnPromptsEnabled,
               overlayAlpha = tuningConfig.navigationCueTransparency,
               dismissDurationSec = tuningConfig.navigationCueDismissDurationSec
           )
           ReturnNavigationHud(...)
           ForkDecisionCard(...)
       }
   }
   ```
4. This ensures underlying sensor tiles and live map stay 100% stationary with zero layout shifts.

### 4.2 Visual & Shape Alignment (`TurnPromptBanner.kt`)
1. **Container Shape**: Update `TurnCueCard` and `OffRouteCard` to `RoundedCornerShape(16.dp)` matching `ForkDecisionCard`.
2. **Container Colors & Typography**:
   - `TurnCueCard`:
     - Normal approach: `MaterialTheme.colorScheme.surfaceContainer.copy(alpha = overlayAlpha)` with `onSurfaceVariant` text and `primary` icon.
     - Immediate maneuver (`isTurnNow`): `MaterialTheme.colorScheme.primaryContainer.copy(alpha = overlayAlpha)` with `onPrimaryContainer` text and icon.
     - Border: `BorderStroke(1.dp, TTColor.RouteActiveNavigation.copy(alpha = 0.5f))` enforcing the Royal Blue navigation accent (Section 5.4 / 5.7).
   - `OffRouteCard`:
     - Container: `MaterialTheme.colorScheme.errorContainer.copy(alpha = overlayAlpha)`.
     - Border: `BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))`.
     - Content color: `MaterialTheme.colorScheme.onErrorContainer`.
3. **Spacing Scale**: Update inner padding from `14.dp` to `padding(horizontal = 16.dp, vertical = 12.dp)` conforming strictly to Section 5.2.

---

## 5. Scope & Guardrails (`ATT-1250`)

### In-Scope
* Moving `TurnPromptBanner` to the top-center spatial overlay stack in `SensorGridScreen.kt`.
* Updating `TurnPromptBanner.kt` shapes, colors, borders, and padding scale per Design Guidelines §§ 5.3 & 5.7.
* Updating architectural contract test `SensorGridScreenRouteIntegrationTest.kt` to assert that `TurnPromptBanner` is excluded from the in-flow column and floats in the overlay container.
* Maintaining 100% full-suite unit test pass rate.

### Out-of-Scope
* Modifying turn cue detection algorithms or geodesic geometry in `TurnCueDetector.kt`.
* Modifying audio chimes or battery saver wake-up logic.
* Altering localization strings (existing keys `turn_cue_now`, `turn_cue_off_route`, etc. remain untouched).

---

## 6. Verification & Test Strategy

1. **`SensorGridScreenRouteIntegrationTest.kt`**:
   - Assert `TurnPromptBanner` is NOT rendered inside the in-flow sensor grid `Column`.
   - Assert `TurnPromptBanner` is gated behind `if (state.showNavigationHints)`.
   - Assert `TurnPromptBanner` consumes `tuningConfig.navigationCueTransparency`.
2. **`TrackingTabWysiwygContractTest.kt`**:
   - Verify `state.showNavigationHints` runtime gating is preserved.
3. **Clean-Room Full Suite Regression**:
   - Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.
