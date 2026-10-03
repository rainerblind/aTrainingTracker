# Stage 3: Implementation Plan - ATT-2113: Align Scrubbing Telemetry Badge with Zoom Controls and Offset to the Right

**Ticket**: [ATT-2113](https://rainerblind.atlassian.net/browse/ATT-2113)  
**Sub-task**: [ATT-2122](https://rainerblind.atlassian.net/browse/ATT-2122) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Requirement Mapping**: `REQ-UI-246`  
**Test Mapping**: `TST-UI-205`  
**Branch**: `feature/ATT-2113`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Description & Background

In Sprint 2026-40.12 (`ATT-2016` / `REQ-UI-241`), the floating `ScrubbingTelemetryBadge` was pinned above scrollable telemetry graphs in `MapDetailLayout.kt`. On physical device inspection on Pixel 10 (Android 16), the human tester observed that the badge appeared below the zoom controls, occluding the upper curves of the graphs, while the right half of the zoom toolbar remained empty.

The tester requested:
1. Moving the badge container upwards so that its top aligns with the top of the zoom buttons.
2. Shifting the badge towards the right side of the screen (`TopEnd`) to leave the left-aligned zoom controls clear.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-246` (*Aftermath/Scrubbing: Align Scrubbing Telemetry Badge with Zoom Controls and Offset to Top-Right in MapDetailLayout*)
* **Test Mapping**: `TST-UI-205` (*Scrubbing Telemetry Badge Top-Right Alignment with Zoom Controls Verification*)
  - `TST-UI-205.1`: Structural Contract Test in `MapDetailLayoutScrubbingBadgeContractTest.kt`
  - `TST-UI-205.2`: Viewport Hierarchy Contract Test in `MapDetailLayoutScrubbingBadgeContractTest.kt`
  - `TST-UI-205.3`: 9-Language Localization Audit via `TranslationParityTest.kt`
  - `TST-UI-205.4`: Clean-room test suite execution (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Scrubbing Touch Transparency**: The overlay badge does NOT consume or intercept horizontal drag gestures for scrubbing or vertical scroll gestures for `lowerColumn`.
2. **Zoom Toolbar Accessibility**: Zoom In (`+`), Zoom Out (`-`), Pan Mode, and Reset Pill remain completely clickable and unobstructed on the left side of the toolbar.
3. **Multi-Chart Synchrony**: Cross-graph scrubbing (`selectedDistance`), altitude interpolation, active scrub points, and active zone colors (`REQ-UI-242`) remain 100% intact.
4. **Backward Compatibility**: Non-aftermath callers of `MapDetailLayout` continue rendering cleanly without layout jitter.
5. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `MapDetailLayout.kt` (Scrubbing Overlay TopEnd Alignment)
* In `val scrubbingOverlay: @Composable BoxScope.() -> Unit`:
  ```kotlin
  ScrubbingTelemetryBadge(
      point = activeScrubPoint,
      bSportType = bSportType,
      altitude = activeScrubAltitude,
      unit = unit,
      xAxisDomain = if (isTrackless) ProfileXAxisDomain.TIME
                    else if (showElevationProfile) tuningConfig.elevationXAxisDomain
                    else tuningConfig.telemetryXAxisDomain,
      modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(top = 2.dp, end = 8.dp),
      hrZoneThresholds = hrThresholds,
      powerZoneThresholds = powerThresholds
  )
  ```

### Component 2: `MapDetailLayout.kt` (Shared Lower Viewport Container Hierarchy)
* For `showMap && hasScrollableContent`:
  Encapsulate `GlobalTelemetryZoomToolbar` and `lowerColumn` inside the viewport `Box`, with `lowerColumn` given `.weight(1f)`:
  ```kotlin
  Box(
      modifier = Modifier
          .weight(1f - splitFraction)
          .fillMaxWidth()
  ) {
      Column(modifier = Modifier.fillMaxSize()) {
          if (hasZoomToolbar) {
              GlobalTelemetryZoomToolbar(...)
          }
          lowerColumn(
              Modifier
                  .weight(1f)
                  .fillMaxWidth()
                  .verticalScroll(rememberScrollState())
          )
      }
      scrubbingOverlay()
  }
  ```
* For `!showMap && hasScrollableContent`:
  Apply identical shared `Box` hierarchy.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Implementation Gate Check
* Command: `python3 tools/jira_util.py check-gate ATT-2122`
* Verification: Ensure exit code 0 (`GATE_PASSED`) before any source code edits.

### Step 2: Update `MapDetailLayout.kt` Layout & Alignment
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* Changes:
  1. Change `scrubbingOverlay` modifier to `.align(Alignment.TopEnd).padding(top = 2.dp, end = 8.dp)`.
  2. In `showMap && hasScrollableContent`, move `GlobalTelemetryZoomToolbar` inside the viewport `Box` within an internal `Column(modifier = Modifier.fillMaxSize())`.
  3. In `!showMap && hasScrollableContent`, unify `GlobalTelemetryZoomToolbar` and `lowerColumn` inside the viewport `Box`.

### Step 3: Update `MapDetailLayoutScrubbingBadgeContractTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutScrubbingBadgeContractTest.kt`
* Changes:
  1. Update `testMapDetailLayout_hostsScrubbingTelemetryBadgeOverlayAtTopCenter` -> `testMapDetailLayout_hostsScrubbingTelemetryBadgeOverlayAtTopEnd` asserting `Alignment.TopEnd` and `padding(top = 2.dp, end = 8.dp)`.
  2. Add `testMapDetailLayout_unifiesToolbarAndLowerColumnInSharedBox` verifying shared `Box` container hierarchy.

### Step 4: Targeted Unit Tests
* Command:
  `./gradlew testDebugUnitTest --tests "*ScrubbingBadgeContractTest*" --tests "*MapDetailLayoutTest*"`

### Step 5: Localization & Regression Checks
* Run translation parity: `./gradlew testDebugUnitTest --tests "*TranslationParityTest*"`
* Run full regression suite: `./gradlew testDebugUnitTest`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - JVM structural contract tests asserting TopEnd alignment, padding, and shared Box hierarchy.
  - Clean-room test suite pass rate: 100%.
* **Rollback Plan**:
  - `feature/ATT-2113` is isolated off `sprint/2026-40.13`. Reverting git commits restores previous center alignment cleanly.
