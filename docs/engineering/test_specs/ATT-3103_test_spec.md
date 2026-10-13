# Stage 2: Requirement & Test Specification: ATT-3103

## Metadata
* **Ticket ID**: ATT-3103
* **Title**: Iterative visual design optimization for Live Climb cockpit sheet
* **Parent Epic**: ATT-2565 (Climbs: Detection, Live ClimbPro & Elevation Pacing)
* **Author**: Antigravity (Pair Programming Assistant)
* **Date**: 2026-10-10
* **Requirement ID**: `REQ-UI-341`
* **Test Specification ID**: `TST-UI-301`
* **Status**: Specified

---

## 1. Requirement Specification (REQ-UI-341)

### 1.1 Summary
The system SHALL refine the visual styling, graphical elevation profile baseline grounding, and developer tooling for the live climb cockpit bottom sheet (`LiveClimbSheet.kt`) (ATT-3103, complementing `REQ-MAP-027` and `REQ-UI-300`):

1. **Multi-State Jetpack Compose `@Preview` Tooling (`LiveClimbSheet.kt`)**:
   - `LiveClimbSheet.kt` SHALL expose parameter-driven, theme-wrapped `@Preview` composables enabling instant visual inspection across all primary live climb states:
     - `LiveClimbSheet_OnClimb_Preview`: Renders an active climb with non-zero progress fraction, remaining distance/elevation, instantaneous grade, and real-time rider position marker in Light Mode.
     - `LiveClimbSheet_OnClimb_Dark_Preview`: Renders the active climb in Dark AMOLED Mode (`uiMode = Configuration.UI_MODE_NIGHT_YES`).
     - `LiveClimbSheet_Approaching_Preview`: Renders the approaching climb status badge with distance-to-start countdown.
     - `LiveClimbSheet_Finished_Preview`: Renders the summit reached status with 0m remaining.
2. **Elevation Profile Graphical Refinements (`ClimbProfileCanvas`)**:
   - `ClimbProfileCanvas` SHALL draw a subtle horizontal ground line along `y = height - bottomPadding` using `MaterialTheme.colorScheme.outlineVariant` with `0.4f` alpha, anchoring the gradient polygon.
   - The profile canvas SHALL maintain a vertical height of `68.dp`, optimizing slope grade visual resolution while preserving compact bottom sheet height.
3. **Preservation of System Invariants**:
   - All existing structural tokens (32dp Terrain icon, titleLarge bold climb name, labelLarge route counter, headlineMedium monospace grade, bodyLarge remaining elevation, unified surface background) and 100% 9-language localization parity MUST be strictly preserved.

### 1.2 Chesterton's Fence Audit
* **Historical Precedent**: Sprint 2026-41.7 (`ATT-2947`). During the Sprint 2026-41.7 Joint Review, the human user requested an interactive visual design optimization session for the Live Climb cockpit sheet to fine-tune spacing, typography balance, and graphical presentation interactively.
* **Root Reason for Existing Formulation**: `LiveClimbSheet.kt` was constructed without `@Preview` composables and without a visual baseline anchor line on the canvas.
* **Preservation of Intent**: Compact sheet height, surface color tokens, 32dp terrain icon, monospace grade text, and full-suite test pass rate remain preserved.

---

## 2. Test Specification (TST-UI-301)

### 2.1 Test Categories & Verification Scope

| Test ID | Category | Target File | Verification Scope |
| :--- | :--- | :--- | :--- |
| **TST-UI-301.1** | Compose Previews Contract | `LiveClimbSheetLayoutTest.kt` | Verify that `LiveClimbSheet.kt` declares `@Preview` composables covering `APPROACHING`, `ON_CLIMB`, and `FINISHED` states. |
| **TST-UI-301.2** | Dark Mode Preview Contract | `LiveClimbSheetLayoutTest.kt` | Verify that a dark mode preview declares `UI_MODE_NIGHT_YES`. |
| **TST-UI-301.3** | Canvas Ground Line Contract | `LiveClimbSheetLayoutTest.kt` | Verify that `ClimbProfileCanvas` renders the baseline ground line using `outlineVariant`. |
| **TST-UI-301.4** | Canvas Height Contract | `LiveClimbSheetLayoutTest.kt` | Verify that the profile canvas height is at least `68.dp`. |
| **TST-UI-301.5** | Typography & Token Invariants | `LiveClimbSheetLayoutTest.kt` | Verify 32dp Terrain icon, bold titleLarge climb title, labelLarge route counter, headlineMedium monospace grade, bodyLarge remaining elevation, and 9-language localization parity remain 100% compliant. |
| **TST-UI-301.6** | Clean-Room Full Suite Regression | Full Unit Test Suite | Execute `./gradlew testDebugUnitTest` asserting 100% pass rate with zero regressions. |

---

## 3. Acceptance Criteria (Given-When-Then)

### Scenario 1: Compose Previews Available in IDE
* **Given** an engineer opening `LiveClimbSheet.kt` in Android Studio or reviewing Compose previews,
* **When** Compose Preview is rendered,
* **Then** distinct previews for Approaching, On-Climb (Light and Dark), and Finished states are visible.

### Scenario 2: Elevation Profile Canvas Ground Baseline
* **Given** an active live climb in `LiveClimbSheet`,
* **When** `ClimbProfileCanvas` renders the profile curve,
* **Then** a subtle `outlineVariant` ground baseline line anchors the bottom of the profile curve.

---

## 4. Invariant Protection

1. **Compact Sheet Dimensions**: Bottom sheet remains compact and does not crowd out tracking metrics.
2. **Localization Parity**: 100% string resource coverage across all 9 languages.
3. **Clean-Room Test Pass Rate**: Full test suite maintains 100% pass rate.
