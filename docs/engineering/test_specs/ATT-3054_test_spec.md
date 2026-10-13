# Stage 2 Requirement & Test Specification: ATT-3054 - Invert map camera lookahead padding and clarify zoom level direction in settings

**Ticket**: [ATT-3054](https://atrainingtracker.atlassian.net/browse/ATT-3054)  
**Sub-task**: [ATT-3078](https://atrainingtracker.atlassian.net/browse/ATT-3078) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Active Sprint**: `Sprint 2026-41.7`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Formal Requirement Specification (`REQ-MAP-044`)

### 1.1 Requirement Statement
The system SHALL correct the Google Maps camera content padding in `ATrainingTrackerMap.kt` to apply top content padding during follow-me navigation (`MapZoomFocus.FOLLOW_ME`), anchoring the athlete marker in the lower third of the display to maximize forward road visibility, and SHALL clarify the zoom level direction on camera sliders in Advanced Settings across all 9 supported application locales (ATT-3054, amending `REQ-MAP-042` and `REQ-MAP-004`):

1. **Forward Lookahead Top Content Padding (`ATrainingTrackerMap.kt`)**:
   - In `ATrainingTrackerMap.kt`, the root map canvas SHALL be hosted inside `BoxWithConstraints`.
   - When `zoomFocus == MapZoomFocus.FOLLOW_ME`, `GoogleMap` SHALL apply top content padding:
     $$\text{topPadding} = \text{maxHeight} \times \left(\frac{\text{tuningConfig.mapFollowMeLookaheadPaddingPercent}}{100\text{f}}\right)$$
     $$\text{contentPadding} = \text{PaddingValues}(\text{top} = \text{topPadding})$$
     visually shifting the camera focal point downwards into the lower third ($\approx 25\% - 35\%$ from bottom) and dedicating $\approx 65\% - 75\%$ of the viewport to the forward route corridor.
   - When `zoomFocus != MapZoomFocus.FOLLOW_ME` (e.g. `EXPLICIT_BOUNDS`, `FIT_ALL`, `MANUAL`), top padding SHALL resolve to `0.dp` (`PaddingValues(0.dp)`).
   - Bottom content padding SHALL be `0.dp` across all modes.

2. **Directional Clarity for Map Camera Zoom Settings (`NavigationSection.kt`, `strings.xml`)**:
   - The helper text for Follow-Me Base Zoom (`tuning_map_base_zoom_desc`) and Cruising Speed Zoom (`tuning_map_cruising_zoom_desc`) SHALL explicitly clarify that a higher numerical value corresponds to a closer zoom level.
   - The lookahead padding setting title (`tuning_map_lookahead_padding_title`) and helper text (`tuning_map_lookahead_padding_desc`) SHALL be updated to remove misleading "Bottom" references, standardizing on *Forward Lookahead Padding* across all 9 supported languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).

3. **9-Language Localization Parity**:
   - All string tokens (`tuning_map_base_zoom_desc`, `tuning_map_cruising_zoom_desc`, `tuning_map_lookahead_padding_title`, `tuning_map_lookahead_padding_desc`) MUST exist and contain non-empty translations with 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

4. **Preservation of System Invariants**:
   - Linear speed-dependent zoom scaling (`REQ-MAP-042`), low-pass filtered bearing stabilization (`REQ-MAP-003`), 3D tilt pitch angle, DataStore persistence and atomic reset (`resetToDefaults()`), explicit route bounds framing (`REQ-MAP-022`), and 100% full clean-room unit test pass rate MUST be strictly preserved.

### 1.2 Chesterton's Fence Requirement Archaeology
- **Original Requirement ID & Target**: Amends `REQ-MAP-042` (*Calibratable Speed-Dependent Map Zoom Curve, Follow-Me Forward Lookahead Camera Padding & Pitch Tuning*, ATT-2946) and `REQ-MAP-004` (*Zoom depending on speed*, ATT-437).
- **Historical Origin & Commit Trace**: Introduced in commit `d8177e06` (`feat(map): calibrate speed zoom curve, add forward lookahead padding, and wire camera tuning (ATT-2946)`).
- **Root Reason for Existing Formulation**: In ATT-2946, the author mistakenly applied `bottom = bottomPadding` under the assumption that bottom padding positions the rider at the bottom. In Google Maps camera geometry, bottom padding shifts the focal center upwards, pushing the rider to the top of the screen beneath telemetry HUD overlays.
- **Preservation of Core Invariants**: Velocity-dependent zoom scaling, camera animation duration, bearing smoothing, manual zoom override, and full-suite test pass rate remain preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
- **Criterion 1 (Lookahead Camera Top Padding in Follow-Me Mode)**:
  - *Given* `ATrainingTrackerMap` in `FOLLOW_ME` mode on an $800\text{dp}$ tall viewport with default $30\%$ padding,
  - *When* `contentPadding` is calculated,
  - *Then* top padding is $240\text{dp}$ and bottom padding is $0\text{dp}$, anchoring the rider in the lower third.
- **Criterion 2 (Non-Follow-Me Modes Padding Suppression)**:
  - *Given* `ATrainingTrackerMap` in `FIT_ALL` or `EXPLICIT_BOUNDS` mode,
  - *When* `contentPadding` is evaluated,
  - *Then* top padding is $0\text{dp}$ and bottom padding is $0\text{dp}$.
- **Criterion 3 (Zoom Direction Clarity in Advanced Settings)**:
  - *Given* an athlete viewing Map Camera settings in `NavigationSection`,
  - *When* inspecting the helper copy for Base Zoom and Cruising Zoom,
  - *Then* the copy explicitly informs the athlete that higher numerical values mean closer zoom.
- **Criterion 4 (Forward Lookahead Setting Naming Cleanliness)**:
  - *Given* the lookahead padding slider in `NavigationSection`,
  - *When* displayed on screen,
  - *Then* the title reads "Forward Lookahead Padding" (or locale equivalent) without contradictory "Bottom" phrasing.
- **Criterion 5 (9-Language Parity & Clean-Room Regression)**:
  - *Given* the 9 supported application locales,
  - *When* running localization and unit test suites,
  - *Then* 100% of string keys exist and 100% of unit tests pass with zero regressions.

---

## 2. Test Specification (`TST-MAP-046`)

### 2.1 Test Case Architecture & Traceability
| Test Case ID | Test Category | Target File | Verification Method | Associated Requirement |
|---|---|---|---|---|
| `TST-MAP-046.1` | Structural Contract Test | `FollowMePaddingContractTest.kt` | Unit Test | `REQ-MAP-044` (Clause 1) |
| `TST-MAP-046.2` | Calculation Test | `FollowMePaddingContractTest.kt` | Unit Test | `REQ-MAP-044` (Clause 1) |
| `TST-MAP-046.3` | Localization Parity Test | `MapCameraLocalizationTest.kt` | Unit Test | `REQ-MAP-044` (Clauses 2, 3) |
| `TST-MAP-046.4` | Clean-Room Suite Regression | `./gradlew testDebugUnitTest` | Full Suite Execution | `REQ-MAP-044` (Clause 4) |

### 2.2 Detailed Verification Procedures

1. **`TST-MAP-046.1`: Follow-Me Top Padding Structural Contract Test**:
   - Verify `ATrainingTrackerMap.kt` calculates `val topPadding = if (zoomFocus == MapZoomFocus.FOLLOW_ME) maxHeight * (tuningConfig.mapFollowMeLookaheadPaddingPercent / 100f) else 0.dp`.
   - Verify `GoogleMap` receives `contentPadding = PaddingValues(top = topPadding)`.
   - Verify `bottomPadding` is removed or replaced by `topPadding`.

2. **`TST-MAP-046.2`: Padding Percentage Mathematical Test**:
   - Given a $400\text{dp}$ viewport and default $30\%$ lookahead padding, assert calculated top padding is exactly $120\text{dp}$.
   - Given non-follow-me modes (`FIT_ALL`, `EXPLICIT_BOUNDS`), assert calculated top padding is $0\text{dp}$.

3. **`TST-MAP-046.3`: 9-Language Localization Audit**:
   - Verify that all 11 map camera tuning string tokens exist and are non-empty across EN, DE, ES, FR, IT, JA, NL, PL, PT.
   - Verify that `tuning_map_base_zoom_desc` and `tuning_map_cruising_zoom_desc` contain direction guidance substrings.
   - Verify that `tuning_map_lookahead_padding_title` is updated cleanly.

4. **`TST-MAP-046.4`: Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` with `BypassSandbox: true` asserting 100% test pass rate with 0 regressions.
