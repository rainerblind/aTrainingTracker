# Stage 2: Requirement & Test Specification - ATT-2458: Relocate Route Selection Button from Cockpit Sensor Grid Tabs to Exclusively Control Tracking Screen

**Ticket**: [ATT-2458](https://atrainingtracker.atlassian.net/browse/ATT-2458)  
**Sub-task**: [ATT-2561](https://atrainingtracker.atlassian.net/browse/ATT-2561) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-279` (*Branded Route Selection Entry Point on Control Tracking Screen & Cockpit Sensor Grid Decoupling*)  
**Test Spec ID**: `TST-UI-239` (*Branded Route Selection Button on Control Tracking Screen & Cockpit Sensor Grid Decoupling Verification*)  
**Branch**: `feature/ATT-2458`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Requirement Specification (`REQ-UI-279`)

### 1.1 Problem Statement & Rationale
During live workout recording, athletes need high-density, glanceable telemetry without clutter.
Previously (ATT-1835 / REQ-MAP-024), a floating quick route action chip (`RouteActionChipRow`) was rendered at the top of `SensorGridScreen.kt` above the sensor fields on *every* cockpit sensor grid tab (pages 1..N).
This placement caused three problems:
1. **Repetitive Screen Real Estate Waste**: The route chip consumed 48–56dp of vertical height on every swipeable cockpit tab, reducing the visible space available for telemetry sensor fields.
2. **Setup vs. Execution Modality Mismatch**: Route selection is predominantly a pre-ride or setup-phase decision, yet `ControlTrackingScreen` (page 0) lacked a dedicated route selection entry point.
3. **Suboptimal Visual Brand Integration**: The legacy chip lacked distinct route-semantic identity, appearing as a generic grey/primary surface chip rather than a branded route selector.

With the completion of ATT-2189 (`REQ-UI-278`), pairing buttons were relocated to the "Meine Sensoren" tabs, freeing vertical space and establishing a dedicated `bottomContent` slot on `ControlTrackingScreen.kt`.

### 1.2 Functional & Architectural Requirements
The system SHALL relocate the Quick Route Selector entry point from the Cockpit Sensor Grid tabs to exclusively the Control Tracking screen and provide an app-consistent branded presentation with route-semantic green domain accents (ATT-2458):

1. **Removal from Cockpit Sensor Grid Tabs (`SensorGridScreen.kt`)**:
   - `RouteActionChipRow` SHALL be removed from `SensorGridScreen.kt`.
   - No sensor grid tab (pages 1..N in `TrackingTabsScreen`) SHALL render the floating route action chip.
   - Automated Route Detection (`RouteAutoDetector`, `AutoDetectedRouteBanner`) SHALL remain fully active and undisturbed within `SensorGridScreen.kt` to present in-ride route join prompts over live tracking.

2. **Exclusive Entry Point on Control Tracking Screen (`ControlTrackingScreen.kt` & `TrackingTabsScreen.kt`)**:
   - The route selection entry point SHALL be rendered exclusively at the bottom of `ControlTrackingScreen.kt`, occupying the dedicated container slot (`bottomContent`) established in `REQ-UI-278`.
   - The entry point SHALL NOT be visible or duplicated on any other cockpit tracking tab.
   - `TrackingTabsScreen.kt` SHALL instantiate `RouteSelectionButton` in the `bottomContent` slot of `ControlTrackingScreen` and host `RouteSelectorModalBottomSheet`.

3. **App-Consistent Branding & Green Domain Semantic Accents (`RouteSelectionButton.kt`)**:
   - The entry point SHALL be styled as an integrated app element (`RouteSelectionButton`) adhering to Rule 23 design tokens (`docs/design_guidelines.md` Section 5):
     - Shape: `RoundedCornerShape(12.dp)`.
     - Border Accent: Subtle green border stroke `BorderStroke(1.dp, TTColor.RouteSelected.copy(alpha = 0.35f))` semantically connecting to map route geometry.
     - Branded Icon Container: 36.dp box with 8.dp rounded corners, `TTColor.RouteSelected.copy(alpha = 0.12f)` background, housing `R.drawable.ic_route` tinted with `TTColor.RouteSelected`.
     - Inactive State: Displays "Select Route" (`@string/route_action_select`), descriptive subtitle (`@string/route_action_select_desc` / "Aus Datei oder Verlauf wählen"), and trailing forward chevron (`Icons.AutoMirrored.Default.ArrowForwardIos`).
     - Active State: Displays "✓ <Route Name>" with distance + elevation metrics (or dynamic remaining navigation metrics when navigating), and provides a dedicated stop/clear action button (`Icons.Default.Close`) invoking route cancellation (`routesRepository.stopRoute()`).
     - Color Scheme: Seamlessly renders across Light, Dark, and AMOLED themes utilizing `MaterialTheme.colorScheme`.

4. **Full Functional Parity (`TrackingTabsScreen.kt`)**:
   - Tapping the button SHALL present `RouteSelectorModalBottomSheet` for selecting, changing, or searching routes.
   - Route selection and cancellation (`setActiveNavigatedRoute`, `stopRoute`) SHALL immediately reflect on the button.
   - Continuous location forwarding to `RouteSelectorViewModel` SHALL be maintained.

5. **100% 9-Language Localization Parity**:
   - All user-facing strings SHALL maintain 100% translation parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Target & Historical Trace**:
   - Refines and amends `REQ-MAP-024` Clause 1 (*Cockpit 1-Tap Entry Point*).
   - Leverages `REQ-UI-278` (*Control Tracking Layout Optimization & Bottom Slot*).
2. **Historical Origin & Commit Trace**:
   - Sprint `2026-41.1`, Ticket `ATT-2458`, target release `V4.9.39`, Epic `ATT-66` (*Improve Routes*).
3. **Root Reason for Existing Formulation**:
   - ATT-1835 placed `RouteActionChipRow` inside `SensorGridScreen` as a quick expedient because `ControlTrackingScreen` was overloaded with pairing buttons.
   - Following ATT-2189's relocation of pairing buttons to the sensor management tabs, vertical space became available at the bottom of `ControlTrackingScreen`.
   - Athletes requested removing the repetitive chip from every sensor grid tab and providing a beautifully branded, green-accented entry point on the setup screen.
4. **Preservation of Core Invariants**:
   - In-ride auto-detection banner (`AutoDetectedRouteBanner`), `RouteSelectorModalBottomSheet` functionality, `RoutesRepository` contracts, and 100% full-suite test pass rate remain strictly preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

- **Scenario 1: Cockpit sensor grid tabs free of route action chip**
  - **Given** an athlete swiping through Cockpit Sensor Grid tabs (page 1..N),
  - **When** viewing the sensor fields,
  - **Then** `RouteActionChipRow` SHALL NOT be rendered on any sensor tab.

- **Scenario 2: Control Tracking screen renders branded route selector button**
  - **Given** an athlete on the Control Tracking screen (page 0),
  - **When** the screen renders,
  - **Then** `RouteSelectionButton` SHALL be rendered in the `bottomContent` slot with a 12.dp rounded shape, subtle green border accent, and branded route icon container.

- **Scenario 3: Active route presentation and 1-tap route cancellation**
  - **Given** an active route being followed,
  - **When** observing `RouteSelectionButton` on the Control Tracking screen,
  - **Then** it SHALL display "✓ <Route Name>", route distance + elevation metrics, and a close/stop button allowing 1-tap route cancellation.

- **Scenario 4: Auto-detection banner preservation**
  - **Given** an athlete riding along an unselected saved route,
  - **When** auto-detection triggers,
  - **Then** `AutoDetectedRouteBanner` SHALL display normally in `SensorGridScreen.kt`.

- **Scenario 5: 9-language localization completeness**
  - **Given** any of the 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT),
  - **When** rendering route selection strings on `RouteSelectionButton`,
  - **Then** all strings SHALL be localized without missing keys or placeholders.

---

## 4. Test Specification (`TST-UI-239`)

### 4.1 Unit & Contract Tests (`ControlTrackingRouteSelectionContractTest.kt`)
- `verifySensorGridScreenDoesNotContainRouteActionChipRow`:
  - Assert that `SensorGridScreen.kt` code AST/tokens do NOT invoke `RouteActionChipRow` in `ScreenMode.TRACKING` or anywhere in the sensor grid column.
- `verifyRouteSelectionButtonDesignTokens`:
  - Assert that `RouteSelectionButton.kt` references `RoundedCornerShape(12.dp)`.
  - Assert that `RouteSelectionButton.kt` references `TTColor.RouteSelected`.
  - Assert that `RouteSelectionButton.kt` references `R.drawable.ic_route`.
- `verifyRouteSelectionButtonActiveAndInactiveStates`:
  - Assert inactive state displays `route_action_select` and chevron.
  - Assert active state displays active route name and close/stop button.
- `verifyAutoDetectedRouteBannerPreservedInSensorGridScreen`:
  - Assert that `SensorGridScreen.kt` contains `AutoDetectedRouteBanner`.

### 4.2 Sensor Grid Integration Tests (`SensorGridScreenRouteIntegrationTest.kt`)
- Verify that `SensorGridScreen` does NOT render `RouteActionChipRow`.
- Verify that `AutoDetectedRouteBanner` continues to render when an auto-detected candidate route is present.

### 4.3 9-Language Localization Audit (`RouteSelectionLocalizationTest.kt` & `TranslationParityTest.kt`)
- Verify all newly added string keys (`route_action_select_desc`, etc.) exist across all 9 supported locales:
  - `values/strings.xml` (EN)
  - `values-de/strings.xml` (DE)
  - `values-es/strings.xml` (ES)
  - `values-fr/strings.xml` (FR)
  - `values-it/strings.xml` (IT)
  - `values-ja/strings.xml` (JA)
  - `values-nl/strings.xml` (NL)
  - `values-pl/strings.xml` (PL)
  - `values-pt/strings.xml` (PT)
- Verify zero empty strings, zero AAPT entity errors, and matching formatting tokens.

### 4.4 Clean-Room Full Suite Regression Execution
- Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

---

## 5. Traceability Matrix

| Requirement Clause | Test Case / Suite | Target Artifact / Method | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-279.1` (Removal from Cockpit Tabs) | `TST-UI-239.1`, `TST-UI-239.2` | `SensorGridScreen.kt` | Specified |
| `REQ-UI-279.2` (Control Tracking Entry Point) | `TST-UI-239.1` | `ControlTrackingScreen.kt`, `TrackingTabsScreen.kt` | Specified |
| `REQ-UI-279.3` (Branded Green Design Tokens) | `TST-UI-239.1` | `RouteSelectionButton.kt` | Specified |
| `REQ-UI-279.4` (Full Functional Parity) | `TST-UI-239.1` | `TrackingTabsScreen.kt`, `RouteSelectorModalBottomSheet` | Specified |
| `REQ-UI-279.5` (9-Language Parity) | `TST-UI-239.3` | `values-*/strings.xml`, `RouteSelectionLocalizationTest.kt` | Specified |
