# Stage 3: Implementation Plan - ATT-2874: Float fork decision prompt as top-level overlay and gate by tab navigation hints toggle

**Ticket**: [ATT-2874](https://rainerblind.atlassian.net/browse/ATT-2874)  
**Sub-task**: [ATT-2928](https://rainerblind.atlassian.net/browse/ATT-2928) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Requirement Mapping**: `REQ-UI-321` (*Top-Level Spatial Overlay & Tab Navigation Hints Gating for In-Ride Fork Route Decision Prompts*)  
**Test Mapping**: `TST-UI-281` (*Fork Decision Prompt Spatial Overlay & Tab Navigation Hints Gating Verification*)  
**Branch**: `improvement/ATT-2874`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Background

In `SensorGridScreen.kt` (lines 478–487), `ForkDecisionCard` was previously embedded directly inside the linear layout `Column` above the scrollable sensor grid:
```kotlin
// In-Ride Fork-in-the-Road Route Selection & Decision Alerts (REQ-MAP-031 / ATT-1955)
ForkDecisionCard(
    decisionState = forkDecisionState,
    overlayAlpha = tuningConfig.navigationCueTransparency,
    onRouteSelected = { routeId ->
        forkNavRepo.selectRouteManually(routeId)
    },
    onDismiss = {
        forkNavRepo.dismissPrompt()
    }
)

// 1. The Sensor Grid (Scrollable)
Column(...) { ... }
```

This caused two significant usability and architectural flaws:
1. **Cockpit Telemetry Disruption**: When approaching a fork ($D_{\text{fork}} \le 300\text{ m}$), the card animated in and physically shoved down the entire sensor grid by ~120–150 dp, disrupting real-time metric glanceability (speed, heart rate, cadence, power) and violating *Design Guidelines Section 5.7* (*"In-ride navigation hints, turn-by-turn cues, and fork-in-the-road decision prompts must float directly on top of active tracking screen elements rather than displacing or squeezing the cockpit tile layout"*).
2. **Missing Per-Tab Navigation Gating**: Unlike `TurnPromptBanner` (which respects `promptsEnabled = state.showNavigationHints && tuningConfig.turnPromptsEnabled`), `ForkDecisionCard` was rendered unconditionally. Consequently, even on tabs explicitly configured with `showNavigationHints = false`, the fork prompt appeared.

This plan specifies the refactoring to float `ForkDecisionCard` as a top-level non-displacing spatial overlay anchored at `Alignment.TopCenter` and gate it strictly behind `state.showNavigationHints`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-321`
  * `REQ-UI-321.1`: Top-Level Spatial Box Layering in `SensorGridScreen.kt` ensuring the sensor grid remains 100% stationary.
  * `REQ-UI-321.2`: Strict Per-Tab Navigation Gating behind `state.showNavigationHints`.
  * `REQ-UI-321.3`: Glanceable semi-transparency (`tuningConfig.navigationCueTransparency`) and touch event pass-through.
  * `REQ-UI-321.4`: Preservation of manual route selection, dismissal, and autonomous route snapping invariants.
* **Test Mapping**: `TST-UI-281`
  * `TST-UI-281.1`: Contract test in `TrackingTabWysiwygContractTest.kt` verifying that `ForkDecisionCard` is gated by `state.showNavigationHints`.
  * `TST-UI-281.2`: Contract test in `SensorGridScreenRouteIntegrationTest.kt` verifying that `ForkDecisionCard` is anchored at `Alignment.TopCenter` within an overlay container and not in the sensor `Column`.
  * `TST-UI-281.3`: Contract test in `SensorGridScreenRouteIntegrationTest.kt` verifying that `ForkDecisionCard` consumes `tuningConfig.navigationCueTransparency`.
  * `TST-UI-281.4`: Clean-room full regression test suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Telemetry Stationarity Invariant**: Sensor grid coordinates and vertical scroll offsets MUST NOT shift or jump when fork alerts appear, expand, or dismiss.
2. **Touch Event Pass-Through**:
   * When `state.showNavigationHints == false` or `forkDecisionState == null`, 0% touch obstruction must occur; gestures pass freely to underlying sensor tiles.
   * When visible, only the physical card bounds intercept clicks for route selection and dismissal.
3. **Semi-Transparency**: Governed by `tuningConfig.navigationCueTransparency` per Design Guidelines §5.7.
4. **Autonomous Binding & Auto-Dismissal**: Auto-binding ($D_{\text{past}} \ge 50\text{m}$) and deviation dismissal remain 100% intact.
5. **Human Gate Governance**: Sub-task transitions directly to `Erledigt` upon Gate 3 pass; parent `ATT-2874` transitions only to `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `SensorGridScreen.kt` (`com.atrainingtracker.trainingtracker.ui.tracking.tracking`)
Refactor the tracking mode layout container from:
```kotlin
        } else {
            // TRACKING & PREVIEW MODES (Unscrollable root Column, independent inner sensor scroll, weighted map)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding()) // Only pad the top
            ) {
                TurnPromptBanner(...)
                ReturnNavigationHud(...)
                ForkDecisionCard(...)
                // 1. The Sensor Grid (Scrollable)
                Column(...) { ... }
                // 2. The Map (Expanded)
                ...
                // 3. The Elevation Profile (Below the Map)
                ...
            }
        }
```
To:
```kotlin
        } else {
            // TRACKING & PREVIEW MODES (Top-level spatial Box overlay, independent inner sensor scroll, weighted map)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding()) // Only pad the top
            ) {
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

                    // Return Navigation & Dynamic Elevation-Aware ETA HUD Banner (REQ-MAP-029 / ATT-1953)
                    ReturnNavigationHud(
                        navigationState = returnNavState,
                        overlayAlpha = tuningConfig.navigationCueTransparency,
                        onDismiss = { returnNavRepo.dismissHud() }
                    )

                    // 1. The Sensor Grid (Scrollable, strictly stationary)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = if (effectiveSpacing > 0.dp) Arrangement.spacedBy(effectiveSpacing) else Arrangement.Top
                    ) {
                        ...
                    }

                    // 2. The Map (Expanded)
                    if (state.showMap) {
                        ATrainingTrackerMap(...) { ... }
                    }

                    // 3. The Elevation Profile (Below the Map)
                    if (state.showElevationProfile && state.pathPoints.isNotEmpty()) {
                        ...
                    }
                }

                // Top Layer: Floating In-Ride Fork Route Decision Prompt Overlay (REQ-MAP-031 / ATT-1955, ATT-2874)
                // Anchored at TopCenter, strictly gated by state.showNavigationHints
                if (state.showNavigationHints) {
                    ForkDecisionCard(
                        decisionState = forkDecisionState,
                        overlayAlpha = tuningConfig.navigationCueTransparency,
                        onRouteSelected = { routeId ->
                            forkNavRepo.selectRouteManually(routeId)
                        },
                        onDismiss = {
                            forkNavRepo.dismissPrompt()
                        },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
```

### UI Consistency (Rule 23)
* **Closest Reference Screen**: `SensorGridScreen.kt` (existing HUD overlays: `TurnPromptBanner`, `ReturnNavigationHud`).
* **Reused Components**: `ForkDecisionCard`, `Box`, `AnimatedVisibility`.
* **Theme Tokens**: `tuningConfig.navigationCueTransparency`, `surfaceVariant`, `TTAlpha.Medium`, `TTColor.RouteActiveNavigation`.
* **One-Off Styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Gate 3 Audit Sign-Off
* Ensure subtask `ATT-2928` is audited and approved (`Erledigt`).
* Run mandatory check: `python3 tools/jira_util.py check-gate ATT-2928`.

### Step 2: Create Stage 4 Subtask & Transition to In Bearbeitung
* Create `[Implementation]` sub-task under `ATT-2874`.
* Transition subtask to `In Bearbeitung`.

### Step 3: Implement Layout Refactoring in `SensorGridScreen.kt`
* Wrap tracking mode in `Box(modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding()))`.
* Place `ForkDecisionCard` outside the base `Column`, aligned to `Alignment.TopCenter`.
* Gate `ForkDecisionCard` with `if (state.showNavigationHints)`.

### Step 4: Update Contract Test in `TrackingTabWysiwygContractTest.kt`
* In `testSensorGridScreen_enforcesRuntimeGating()`, assert that `ForkDecisionCard` is gated by `state.showNavigationHints`.

### Step 5: Update Contract Test in `SensorGridScreenRouteIntegrationTest.kt`
* Add `testSensorGridScreen_forkDecisionCard_floatsAsTopCenterOverlay()`:
  - Assert `ForkDecisionCard` has `Modifier.align(Alignment.TopCenter)`.
  - Assert `ForkDecisionCard` consumes `tuningConfig.navigationCueTransparency`.
  - Assert `ForkDecisionCard` is gated by `state.showNavigationHints`.

### Step 6: Run Targeted Unit Tests
* Run:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"
  ```

### Step 7: Document Construction Deliverable & Pass Gate 4
* Author `docs/engineering/implementation/ATT-2874_implementation.md`.
* Transition Stage 4 subtask to `In Überprüfung` and run audit (`python3 tools/review_agent.py audit <KEY>`).

---

## 6. Clean-Room Regression Verification (Stage 5)
* Execute full clean-room suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* Update `REQ-UI-321` and `TST-UI-281` to `Verified` in `docs/requirements.md` and `docs/tests.md`.
* Author walkthrough: `docs/engineering/walkthroughs/ATT-2874_walkthrough.md`.
* Commit changes on `improvement/ATT-2874`, merge into `sprint/2026-41.5` (`--no-ff`), and delete branch.
* Advance parent ticket `ATT-2874` to `Final Review (Human)`.
