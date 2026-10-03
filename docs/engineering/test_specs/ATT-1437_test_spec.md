# Test Specification - ATT-1437: [Cockpit] In dark mode, the missing sensors at the top must be visible again

**Parent Ticket**: [ATT-1437](https://rainerblind.atlassian.net/browse/ATT-1437)  
**Parent Epic**: [ATT-1157](https://rainerblind.atlassian.net/browse/ATT-1157) (*[Epic] Optimize dark mode*)  
**Sub-task**: [ATT-1442](https://rainerblind.atlassian.net/browse/ATT-1442) (`[Test-Spec]`)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement ID**: `REQ-UI-173`  
**Test ID**: `TST-UI-125`  

---

## 1. Traceability & Scope Alignment

| Item | Reference |
|---|---|
| **Parent Ticket** | [ATT-1437](https://rainerblind.atlassian.net/browse/ATT-1437) |
| **Parent Epic** | [ATT-1157](https://rainerblind.atlassian.net/browse/ATT-1157) (*[Epic] Optimize dark mode*) |
| **Requirement Specification** | `REQ-UI-173` in [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) |
| **Test Catalog** | `TST-UI-125` in [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md) |
| **Analysis Deliverable** | [docs/engineering/analysis/ATT-1437_analysis.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/analysis/ATT-1437_analysis.md) |
| **Target Components** | `SensorStatus.kt`, `TrackingTabsScreen.kt` |

---

## 2. Harmonized Requirement Specification (`REQ-UI-173`)

### REQ-UI-173: Legible Inactive Sensor Status Indicators in Dark Mode
The system SHALL ensure that inactive and disconnected sensor icons in the top `SensorStatus` header bar remain clearly legible and discernible across all application themes (Light, Dark, and AMOLED Pure Black) while maintaining clear visual subordination to active connected sensors (ATT-1437):

1. **Content Color & Opacity Decoupling**:
   - In `SensorStatus.kt`, all sensor icons SHALL evaluate `tint = MaterialTheme.colorScheme.onSurface` (pure white `#FFFFFF` in dark mode, near-black `#1A1C1E` in light mode).
   - The system SHALL eliminate the compounded low-luminance `outline` color tint for inactive sensors.
   - Inactive sensors (`isAvailable == false`) SHALL render with a defined disabled opacity of `0.38f` (`alpha = 0.38f`), aligning with Material Design 3 disabled content standards.
   - Active sensors (`isAvailable == true`) SHALL render with full opacity (`alpha = 1.0f`).
2. **Contrast & WCAG 2.1 Compliance**:
   - Inactive sensor icons SHALL achieve a minimum contrast ratio of $\ge 3:1$ against the underlying header surface in AMOLED Pure Black (`#000000`), standard Dark (`#1B1B1F`), and Light (`#FFFFFF` / `#D3E3FD`) modes, complying with WCAG 2.1 Non-text Contrast (SC 1.4.11).
   - Active sensor icons SHALL maintain a minimum contrast ratio of $\ge 7:1$ (exceeding WCAG AAA).
3. **Preserved Invariants & Interactivity**:
   - Tapping an inactive sensor icon SHALL continue to open the `SensorSourceDialog` (REQ-UI-049) to allow sensor inspection, pairing, or diagnostics.
   - Sensor icon ordering (`TIME_ACTIVE`, `ACCURACY`, `ALTITUDE`, `DISTANCE_m`, `SPEED_mps`, `CADENCE`, `HR`, `POWER`) and size (22dp with 6dp horizontal padding) MUST NOT be altered.
   - The hamburger navigation menu button and top bar layout in `TrackingTabsScreen.kt` MUST NOT be affected.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-048` (*Clean Sensor Status Header*) and `REQ-UI-170` (*Comprehensive Cockpit Dark Theme*).
2. *Historical Origin & Commit Trace*: `REQ-UI-048` established the icon-only header bar; `REQ-UI-170` (ATT-1413) themed the cockpit top header surface to pure black `#000000` in AMOLED dark mode.
3. *Root Reason for Existing Formulation*: Originally, inactive sensors used `tint = outline` with `.alpha(0.2f)`. On light backgrounds, this produced a soft ghost icon. However, when the top header was darkened to `#000000` in ATT-1413, applying 20% alpha to `AmoledOutline` (`#38383A`) caused the rendered pixel values to drop to `#0B0B0B` (1.02:1 contrast), rendering missing sensors completely invisible to the human eye.
4. *Preservation of Core Invariants*: Decoupling tint to `onSurface` and using standard `0.38f` alpha preserves the visual distinction between active and inactive sensors (6.25x contrast ratio differential) while restoring full visibility and interactivity in dark mode without altering layout or dialog behavior.

**Acceptance Criteria (Given-When-Then)**:
- *Given* an active workout on `TrackingTabsScreen` in AMOLED dark mode (`isCockpitDark == true`),
- *When* viewing the top `SensorStatus` header bar with one or more disconnected/inactive sensors,
- *Then* inactive sensor icons SHALL be clearly visible against the pure black `#000000` background with contrast ratio $\ge 3:1$ and opacity `0.38f`.
- *When* viewing active connected sensors,
- *Then* active sensor icons SHALL render in full white `#FFFFFF` with opacity `1.0f`.
- *When* the athlete taps an inactive sensor icon,
- *Then* the `SensorSourceDialog` SHALL open displaying current sensor status and available hardware.
- *Given* the device is in Light Mode,
- *When* viewing the top `SensorStatus` bar,
- *Then* inactive sensor icons SHALL render legibly with `0.38f` opacity on the light header surface without disappearing.

**Invariants**: Sensor icon size (22dp), horizontal spacing (6dp), sensor order sequence, and `SensorSourceDialog` navigation MUST NOT be altered.

---

## 3. Detailed Test Specification (`TST-UI-125`)

### TST-UI-125: Sensor Status Inactive Indicator Legibility Verification

1. **Alpha and Tint State Resolution Unit Tests (`SensorStatusLegibilityTest.kt`)**:
   - *Test 1.1*: Verify active sensors evaluate `tint = MaterialTheme.colorScheme.onSurface` and `alpha = 1.0f`.
   - *Test 1.2*: Verify inactive sensors evaluate `tint = MaterialTheme.colorScheme.onSurface` and `alpha = 0.38f`.
   - *Test 1.3*: Verify no compounded `outline` color dimming is applied to inactive icons (ensuring opacity is the sole state indicator).

2. **Contrast Ratio Calculation Verification**:
   - *Test 2.1*: In AMOLED Dark Mode (`#000000` surface, `#FFFFFF` onSurface):
     - Active icon luminance = $1.0$, contrast ratio = $21 : 1$.
     - Inactive icon effective luminance ($255 \times 0.38 \approx 97 \rightarrow \text{rgb}(97, 97, 97)$) has contrast ratio $3.36 : 1 \ge 3 : 1$ (passes WCAG 2.1 Non-text Contrast SC 1.4.11).
   - *Test 2.2*: In Light Mode (`#FFFFFF` surface, `#000000` onSurface):
     - Active icon contrast ratio = $21 : 1$.
     - Inactive icon effective color ($\text{rgb}(158, 158, 158)$) has contrast ratio $3.1 : 1 \ge 3 : 1$.
   - *Test 2.3*: Verify contrast differential between active ($21:1$) and inactive ($3.36:1$) icons is $\ge 6:1$, preventing user confusion.

3. **Interactivity & Dialog Triggering Verification**:
   - *Test 3.1*: Verify clicking an inactive sensor icon triggers `selectedSensor = type` and renders `SensorSourceDialog`.
   - *Test 3.2*: Verify clicking an active sensor icon continues to open `SensorSourceDialog`.

4. **Visual Previews Verification**:
   - *Test 4.1*: `PreviewSensorStatusRowDark()` renders inactive sensors distinctly visible against dark surface.
   - *Test 4.2*: `PreviewSensorStatusRow()` renders inactive sensors distinctly visible against light surface.

5. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` across all project modules to verify 100% clean-room test pass rate.
