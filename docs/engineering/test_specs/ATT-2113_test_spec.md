# Stage 2: Requirement & Test Specification - ATT-2113: Align Scrubbing Telemetry Badge with Zoom Controls and Offset to the Right

**Ticket**: [ATT-2113](https://rainerblind.atlassian.net/browse/ATT-2113)  
**Sub-task**: [ATT-2121](https://rainerblind.atlassian.net/browse/ATT-2121) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Requirement Mapping**: `REQ-UI-246` (*Aftermath/Scrubbing: Align Scrubbing Telemetry Badge with Zoom Controls and Offset to Top-Right in MapDetailLayout*)  
**Test Spec ID**: `TST-UI-205` (*Scrubbing Telemetry Badge Top-Right Alignment with Zoom Controls Verification*)  
**Branch**: `feature/ATT-2113`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Requirement Specification (REQ-UI-246)

### 1.1 Problem Statement & Rationale
In Sprint 2026-40.12 (`ATT-2016` / `REQ-UI-241`), `ScrubbingTelemetryBadge` was hoisted from within `ElevationProfile.kt` into the persistent lower viewport container of `MapDetailLayout.kt`. However, because the badge was placed in a container below `GlobalTelemetryZoomToolbar` and centered horizontally with `.align(Alignment.TopCenter)` and `.padding(top = 4.dp)`, it was positioned 36dp too low and occluded the top curves of the elevation and telemetry graphs. The right half of the sticky zoom toolbar remained vacant. This requirement restructures the lower viewport container hierarchy so that the badge is anchored to the top-right (`Alignment.TopEnd`), co-located side-by-side with the zoom controls, with its top edge aligned with the zoom buttons ($y \approx 2\text{--}4\text{dp}$).

### 1.2 Functional & Architectural Requirements
1. **Unified Lower Viewport Container (`MapDetailLayout.kt`)**:
   - The lower viewport `Box` (governed by `.weight(1f - splitFraction)` when `showMap && hasScrollableContent`, or `.weight(1f)` when `!showMap && hasScrollableContent`) SHALL encapsulate both `GlobalTelemetryZoomToolbar` (if `hasZoomToolbar`) and the scrollable `lowerColumn` within an internal `Column(modifier = Modifier.fillMaxSize())`.
   - `lowerColumn` SHALL occupy the remaining vertical height with `Modifier.weight(1f).fillMaxWidth().verticalScroll(...)`.
2. **Top-Right Scrubbing Badge Placement (`scrubbingOverlay`)**:
   - The persistent overlay `scrubbingOverlay` SHALL anchor `ScrubbingTelemetryBadge` to `Alignment.TopEnd` within the shared viewport `Box`.
   - The badge SHALL apply `padding(top = 2.dp, end = 8.dp)`, ensuring its top boundary aligns ($y = 2\text{dp}$) with the top edge of the 28dp zoom controls ($y = 4\text{dp}$) centered in the 36dp toolbar.
3. **Scrubbing Visibility & Touch Transparency**:
   - The badge SHALL render IF AND ONLY IF `showZoomControls == true`, `selectedDistance != null`, and `activeScrubPoint != null`.
   - The badge overlay SHALL NOT consume or intercept drag gestures on the graphs or tap gestures on the zoom toolbar buttons.
4. **Preservation of Core Invariants**:
   - Multi-chart lockstep zooming, panning, and synchronized cursor tracking (`REQ-UI-225`, `REQ-UI-233`) remain intact.
   - Dynamic zone colors in active zone badge suffixes (`REQ-UI-242`) remain intact.
   - Trackless workout scrubbing along the time axis remains intact.
   - 9-language translation parity remains intact.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Vertical Top Alignment with Zoom Controls)**:
  * *Given* an athlete scrubbing any post-workout graph in the detailed inspection view,
  * *When* `ScrubbingTelemetryBadge` renders,
  * *Then* its top edge SHALL align with the top edge of the zoom controls in `GlobalTelemetryZoomToolbar` ($y \approx 2\text{--}4\text{dp}$), rather than appearing 36dp below them.
* **AC-2 (Horizontal Top-Right Positioning)**:
  * *Given* an athlete scrubbing any post-workout graph,
  * *When* the badge appears,
  * *Then* it SHALL be positioned on the right side of the screen (`Alignment.TopEnd`), leaving the left-aligned zoom controls (`-`, `+`, Pan/Touch, Reset) unobstructed.
* **AC-3 (Unobstructed Graph Clearance)**:
  * *Given* the scrubbed graphs in `lowerColumn`,
  * *When* inspecting the top curve of the elevation profile or pace graph,
  * *Then* the upper graph area SHALL NOT be occluded by a centered badge.

### 1.4 System Invariants
1. Single-finger vertical scroll on `lowerColumn` MUST NOT be intercepted.
2. Scrubbing gestures and zoom controls MUST NOT experience event conflict.
3. 100% clean-room test suite pass rate.

---

## 2. Test Specification (TST-UI-205)

### Test Case 1: `testMapDetailLayout_anchorsScrubbingTelemetryBadgeAtTopEnd` (`TST-UI-205.1`)
* **Scope**: Structural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutScrubbingBadgeContractTest.kt`
* **Preconditions**: `MapDetailLayout.kt` source code available.
* **Action**: Verify that `ScrubbingTelemetryBadge` modifier specifies `.align(Alignment.TopEnd)` and `.padding(top = 2.dp, end = 8.dp)`.
* **Expected Result**: Assertion passes.

### Test Case 2: `testMapDetailLayout_unifiesToolbarAndLowerColumnInSharedBox` (`TST-UI-205.2`)
* **Scope**: Viewport Container Hierarchy Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutScrubbingBadgeContractTest.kt`
* **Preconditions**: `MapDetailLayout.kt` source code available.
* **Action**: Verify that in both `showMap && hasScrollableContent` and `!showMap && hasScrollableContent`, `GlobalTelemetryZoomToolbar` and `lowerColumn` are housed inside the shared `Box` before `scrubbingOverlay()`.
* **Expected Result**: Assertion passes.

### Test Case 3: 9-Language Localization Audit (`TST-UI-205.3`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Action**: Verify all translation keys across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Expected Result**: 100% parity.

### Test Case 4: Full Clean-Room Regression Suite (`TST-UI-205.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate across all unit and contract tests with zero failures.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-205.1` | Contract | `MapDetailLayout` badge TopEnd alignment | `REQ-UI-246` | Specified |
| `TST-UI-205.2` | Contract | `MapDetailLayout` shared viewport container | `REQ-UI-246` | Specified |
| `TST-UI-205.3` | Localization | `TranslationParityTest` | `REQ-UI-106` | Specified |
| `TST-UI-205.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-014` | Specified |
