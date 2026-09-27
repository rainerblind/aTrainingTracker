# Test Specification - ATT-1446: [Cockpit] Eliminate tab header color animation delay during theme transitions

**Parent Ticket**: [ATT-1446](https://rainerblind.atlassian.net/browse/ATT-1446)  
**Parent Epic**: [ATT-1157](https://rainerblind.atlassian.net/browse/ATT-1157) (*[Epic] Optimize dark mode*)  
**Sub-task**: [ATT-1464](https://rainerblind.atlassian.net/browse/ATT-1464) (`[Test-Spec]`)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement ID**: `REQ-UI-176`  
**Test ID**: `TST-UI-128`  

---

## 1. Traceability & Scope Alignment

| Item | Reference |
|---|---|
| **Parent Ticket** | [ATT-1446](https://rainerblind.atlassian.net/browse/ATT-1446) (*[Bug] [Cockpit] Eliminate tab header color animation delay during theme transitions*) |
| **Parent Epic** | [ATT-1157](https://rainerblind.atlassian.net/browse/ATT-1157) (*[Epic] Optimize dark mode*) |
| **Requirement Specification** | `REQ-UI-176` in [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) |
| **Test Catalog** | `TST-UI-128` in [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md) |
| **Analysis Deliverable** | [docs/engineering/analysis/ATT-1446_analysis.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/analysis/ATT-1446_analysis.md) |
| **Target Components** | `TrackingTabsScreen.kt` |

---

## 2. Harmonized Requirement Specification (`REQ-UI-176`)

### REQ-UI-176: Synchronous Cockpit Tab Header Color Transitions
The system SHALL ensure that tab header title typography in `PrimaryScrollableTabRow` transitions colors synchronously (0ms delay) with the tab row container background during theme state changes between ambient Light Mode (Page 0) and AMOLED Dark Mode (Pages 1..N) (ATT-1446):

1. **Direct Synchronous Color Binding**:
   - In `TrackingTabsScreen.kt`, all tab `Text` composables inside `PrimaryScrollableTabRow` SHALL explicitly specify `color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant`.
   - The system SHALL bypass Compose Material 3 `TabTransition`'s animated `LocalContentColor` (~250ms tween) to eliminate visual lag, color morphing, and intermediate low-contrast rendering artifacts during theme switches.
   - `Tab` composables SHALL explicitly specify `selectedContentColor = MaterialTheme.colorScheme.primary` and `unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant` for ripple and content coherence.
2. **Contrast & WCAG 2.1 Compliance**:
   - In AMOLED Pure Black (`#000000` container): Selected tab titles SHALL resolve to `DarkPrimary` (`#A6C8FF`) achieving contrast ratio $\ge 12:1$ (WCAG AAA); unselected tab titles SHALL resolve to `AmoledOnSurfaceVariant` (`#9E9E9E`) achieving contrast ratio $\ge 7:1$ (WCAG AAA).
   - In Light Mode (`#C8E3FF` container): Selected tab titles SHALL resolve to `LightPrimary` (`#1464F4`) achieving contrast ratio $\ge 4.5:1$ (WCAG AA); unselected tab titles SHALL resolve to `LightOnSurfaceVariant` (`#44474F`) achieving contrast ratio $\ge 5:1$ (WCAG AA).
3. **Preserved Invariants**:
   - Page 0 (Control Tracking) ambient theme isolation (`REQ-UI-170`) MUST NOT be altered.
   - Pager navigation, swipe animations, and tap scrolling MUST NOT be affected.
   - Custom tab names, tab count, and telemetry data display MUST NOT be altered.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-170` (*Comprehensive Cockpit Dark Theme*).
2. *Historical Origin & Commit Trace*: `REQ-UI-170` (ATT-1413) introduced dynamic AMOLED dark theming for Pages 1..N while isolating Page 0 in the system theme.
3. *Root Reason for Existing Formulation*: When `PrimaryScrollableTabRow` and `Tab` were added to `TrackingTabsScreen.kt`, tab text was rendered via plain `Text(name)` without explicit `color`, delegating text coloring to `LocalContentColor.current`. Material 3's `TabTransition` internally animates `LocalContentColor` across 250ms/150ms tweens. When switching between light Page 0 and AMOLED Page 1, the background flips instantly, but the text color slowly morphs across intermediate muddy shades.
4. *Preservation of Core Invariants*: Passing explicit `color` to `Text` bypasses `LocalContentColor.current` completely, synchronizing text rendering to the 0ms theme swap without altering pager animation, touch targets, or contrast compliance.

**Acceptance Criteria (Given-When-Then)**:
- *Given* an active workout on `TrackingTabsScreen` with the device in Light Mode and cockpit configured for Always Dark,
- *When* navigating from Page 0 (Light Mode) to Page 1+ (AMOLED Dark Mode),
- *Then* tab header text colors SHALL switch synchronously on the first recomposition frame in 0ms together with the container background, with zero animation delay or intermediate color rendering.
- *When* navigating from Page 1+ back to Page 0,
- *Then* tab header text colors SHALL switch synchronously to light-mode primary/onSurfaceVariant colors without animation lag.

**Invariants**: Page 0 ambient theme isolation, tab click navigation, and WCAG contrast ratios MUST NOT be altered.

---

## 3. Detailed Test Specification (`TST-UI-128`)

### TST-UI-128: Cockpit Tab Header Synchronous Color Transition Verification

1. **Color Resolution & Contrast Unit Tests (`TabHeaderColorResolutionTest.kt`)**:
   - *Test 1.1*: Verify that in AMOLED Dark Mode (`AmoledDarkColorScheme`), selected tab color resolves to `DarkPrimary` (`#A6C8FF`, contrast against `#000000` $\ge 12:1$) and unselected tab color resolves to `AmoledOnSurfaceVariant` (`#9E9E9E`, contrast against `#000000` $\ge 7:1$).
   - *Test 1.2*: Verify that in Light Mode (`LightColorScheme`), selected tab color resolves to `LightPrimary` (`#1464F4`, contrast against `#C8E3FF` $\ge 4.5:1$) and unselected tab color resolves to `LightOnSurfaceVariant` (`#44474F`, contrast against `#C8E3FF` $\ge 5:1$).
   - *Test 1.3*: Verify that relative luminance and contrast calculations strictly satisfy WCAG 2.1 AA/AAA criteria across both color schemes.

2. **Static Composable Call Site Audit**:
   - *Test 2.1*: Verify that in `TrackingTabsScreen.kt`, all tab `Text` composables explicitly specify `color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant`, bypassing `TabTransition`'s animated `LocalContentColor`.
   - *Test 2.2*: Verify that `Tab` explicitly specifies `selectedContentColor = MaterialTheme.colorScheme.primary` and `unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant`.

3. **On-Device Pixel 10 Verification (Light Device Mode + Always Dark Cockpit)**:
   - *Test 3.1*: Navigate between Page 0 and Page 1+.
   - *Test 3.2*: Verify tab header typography switches color synchronously in 0ms with the tab bar background container without any visible 250ms animation delay, color morphing, or intermediate muddy colors.
   - *Test 3.3*: Verify Page 0 remains in light theme and Pages 1..N render in pure AMOLED dark theme.

4. **Clean-Room Full Suite Regression Execution**:
   - *Test 4.1*: Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.
