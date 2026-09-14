# Stage 3 Implementation Plan: Standardize Edge-to-Edge Scaffold Insets & Core UI Design Components (ATT-939)

* **Ticket**: [ATT-939](https://rainerblind.atlassian.net/browse/ATT-939) (*Standardize edge-to-edge Scaffold insets and extract core UI design components*)
* **Sub-task**: [ATT-1026](https://rainerblind.atlassian.net/browse/ATT-1026) (*[Impl-Plan] Standardize edge-to-edge Scaffold insets and extract core UI design components*)
* **Branch**: `feature/ATT-939`
* **Requirement Traced**: `REQ-UI-148`
* **Test Specification**: `TST-UI-101`

---

## 1. Context & Motivation
As aTrainingTracker migrates toward edge-to-edge display and Material 3 standards under Epic ATT-355:
1. **Window Insets Disparity**: Different bottom sheets handle system bar insets inconsistently. For example, `FilterBottomSheetScaffold` uses `navigationBarsPadding()`, while `RichStatsSheet` uses hardcoded `padding(bottom = 32.dp)`, leading to bottom clipping on tall 3-button navigation bars or excessive empty whitespace on gesture navigation bars.
2. **Missing Universal Modal Sheet Primitive**: Dialogs across drawer settings and configuration (targeted for migration in ATT-900) currently use centered `AlertDialog` popups because there is no standardized, turnkey `AppModalBottomSheet` component.
3. **Duplicated Tabular Typography**: Both `WorkoutLaps.kt` (`LapTableHeader`) and `WorkoutExtrema.kt` duplicate identical baseline alignment typography configurations (`PlatformTextStyle(includeFontPadding = false)` and `LineHeightStyle(Alignment.Bottom, Trim.Both)`).
4. **Scattered UI Foundations**: Reusable core primitives lack a consolidated package home (`com.atrainingtracker.trainingtracker.ui.components.core`).

---

## 2. Proposed Architectural Changes

### 2.1 New Core Design Package (`ui.components.core`)
Create package `com.atrainingtracker.trainingtracker.ui.components.core` containing:

#### `AppModalBottomSheet.kt`
A universal Material 3 `ModalBottomSheet` container designed for modal configuration sheets, settings, and analytical dialogs:
* **System Insets**: Automatically applies `navigationBarsPadding()` and `imePadding()` to ensure content and bottom action bars never overlap the navigation bar or on-screen keyboard.
* **Drag Handle**: Uses standard `MinimumDragHandle()` with balanced top padding (16dp) and minimal vertical footprint.
* **Header Bar**: Displays optional leading icon, localized title text styled with `MaterialTheme.typography.titleLarge`, and optional close dismiss `IconButton` (`Icons.Default.Close`).
* **Dividers & Body**: Provides a top `HorizontalDivider()`, an adaptive body column with optional scrolling (`verticalScroll(rememberScrollState())`) and standard padding (horizontal = 20.dp, vertical = 12.dp).
* **Action Bar Slot**: Optional slot `actions: (@Composable RowScope.() -> Unit)? = null` separated by a bottom `HorizontalDivider()`, standardizing two-button (e.g. Cancel / Save) or single-button bottom action bars.

#### `AppTableHeader.kt` & `AppTableTypography.kt`
Standardized tabular typography and header row primitives:
* **`appTableHeaderStyle()`**: Returns `MaterialTheme.typography.labelSmall` configured with:
  * `platformStyle = PlatformTextStyle(includeFontPadding = false)`
  * `lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Bottom, trim = LineHeightStyle.Trim.Both)`
* **`TextStyle.withBottomBaselineAlignment()`**: Extension allowing any text style to achieve pixel-perfect bottom baseline alignment with adjacent icons and numeric units.
* **`AppTableHeader`**: Standardized row layout (`fillMaxWidth()`, `verticalAlignment = Alignment.Bottom`).
* **`AppTableHeaderCell`**: Standardized column header cell applying `appTableHeaderStyle()`, `onSurfaceVariant` color, `maxLines = 1`, and optional alignment.

### 2.2 Insets Harmonization (`RichStatsSheet.kt`)
* Refactor `RichStatsSheet.kt` to replace hardcoded `padding(bottom = 32.dp)` with adaptive `navigationBarsPadding()`.
* Ensure stats block cards in `LazyColumn` scroll and fit above the navigation bar on all device configurations.

### 2.3 Refactoring `WorkoutLaps.kt`
* Refactor `LapTableHeader` in `WorkoutLaps.kt` to compose `AppTableHeader` and `appTableHeaderStyle()`.
* Preserve exact column weights (`WEIGHT_LAP_NAME = 1.6f`, `WEIGHT_TIME = 0.85f`, `WEIGHT_DISTANCE = 0.85f`, `WEIGHT_PACE_SPEED = 1.1f`, `BADGE_WIDTH_DP = 26.dp`) and localized strings.

---

## 3. Invariants & Guardrails ("What MUST NOT Change")

1. **Filter Bottom Sheet Invariant**: `FilterBottomSheetScaffold` filtering behavior and contracts across Workouts (`REQ-UI-132`), Routes (`REQ-UI-133`), and Segments (`REQ-UI-134`) MUST remain 100% functional and visually intact.
2. **Map Status Bar Boundary Invariant**: Map detail bottom sheets (`BottomSheetScaffold` in `PeriodMapScreen`, `MapFragmentWithTrack`, `WorkoutClusterHeatmapScreen`) MUST retain their status bar height constraints (`REQ-SET-069`, ATT-832).
3. **Live Tracking Lap Summary HUD Invariant**: `LapSummaryDialog` (5-second auto-dismiss dialog shown during active live tracking) MUST remain a centered HUD dialog and MUST NOT be altered.
4. **Table Formatting & Badge Alignment Invariant**: Column width proportions, single-line text constraints, and vertical badge centering in `WorkoutLaps` (`REQ-UI-143`) and `WorkoutExtrema` (`REQ-UI-011`) MUST NOT be regressed.

---

## 4. Impact Analysis & Mitigation

* **Keyboard & Form Input Handling**:
  * Combining `navigationBarsPadding()` with `imePadding()` ensures text fields inside modal bottom sheets (such as equipment names or numerical threshold inputs) automatically scroll above the soft keyboard without obscuring action buttons.
* **Gesture vs. 3-Button Navigation Parity**:
  * `navigationBarsPadding()` dynamically resolves to the physical navigation bar height (e.g. 48dp on 3-button navigation, ~16-24dp on gesture bars), completely eliminating the hardcoded 32dp layout bug in `RichStatsSheet`.
* **Zero Disruption to Existing Screens**:
  * New components reside in `ui.components.core`. Existing callers of `MinimumDragHandle` in `BottomSheetUtils` continue to work without breaking changes.

---

## 5. Verification Plan (TST-UI-101)

### 5.1 Automated Unit Tests
1. **`AppTableHeaderTest.kt`**:
   * Verify `appTableHeaderStyle()` contains `includeFontPadding = false` and `Alignment.Bottom`.
   * Verify `TextStyle.withBottomBaselineAlignment()` correctly overrides platform and line height styles.
   * Verify `AppTableHeader` composable layout and header cell rendering.
2. **`AppModalBottomSheetTest.kt`**:
   * Verify component composition, header row, dismiss callback, and action slot parameter contracts.
3. **Regression Test Suite**:
   * Run `./gradlew testDebugUnitTest --no-daemon` to ensure 100% clean test execution across all modules.

### 5.2 Visual & Manual Verification
* Inspect `RichStatsSheet` on a 3-button navigation device: verify no content clipping at the bottom.
* Inspect `WorkoutLaps` summary card: verify lap table headers retain crisp bottom-baseline alignment and exact column proportions.
