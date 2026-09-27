# Implementation Plan - ATT-1446: [Cockpit] Eliminate tab header color animation delay during theme transitions

**Parent Ticket**: [ATT-1446](https://rainerblind.atlassian.net/browse/ATT-1446)  
**Parent Epic**: [ATT-1157](https://rainerblind.atlassian.net/browse/ATT-1157) (*[Epic] Optimize dark mode*)  
**Sub-task**: [ATT-1465](https://rainerblind.atlassian.net/browse/ATT-1465) (`[Impl-Plan]`)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement ID**: `REQ-UI-176`  
**Test ID**: `TST-UI-128`  
**Analysis Reference**: `docs/engineering/analysis/ATT-1446_analysis.md`  
**Test Spec Reference**: `docs/engineering/test_specs/ATT-1446_test_spec.md`  
**Branch**: `feature/ATT-1446`  

---

## 1. Executive Summary & Architectural Scope

The objective of **ATT-1446** is to eliminate the noticeable ~250ms color animation delay (lag) of tab titles when navigating between the Control Tracking tab (Page 0, rendered in the ambient light theme) and telemetry tabs (Pages 1..N, rendered in AMOLED Pure Black `#000000`) in `TrackingTabsScreen`.

### Root Cause
Compose Material 3's `Tab` composable internally delegates content styling to `TabTransition`, which provides an animated `LocalContentColor` driven by `updateTransition(selected).animateColor(...)` (~250ms tween). While the tab row container background (`containerColor = MaterialTheme.colorScheme.surfaceContainerHighest`) changes synchronously in 0ms on the first recomposition frame, tab `Text` composables lacking an explicit `color` parameter inherit the lagging, animated `LocalContentColor`, producing an irritating intermediate muddy color artifact during theme transitions.

### Proposed Solution
1. **Direct Synchronous Color Binding on `Text`**: In `TrackingTabsScreen.kt`, explicitly pass `color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant` to all tab `Text` composables inside `PrimaryScrollableTabRow`. This directly bypasses `LocalContentColor.current` and resolves the color synchronously on the exact same frame as the container background.
2. **Tab Content Color Alignment**: Explicitly pass `selectedContentColor = MaterialTheme.colorScheme.primary` and `unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant` to `Tab` to ensure ripple feedback and baseline content colors remain fully coherent with the active theme.

---

## 2. Step-by-Step Implementation Strategy

### Phase 1: Composable Refactoring in `TrackingTabsScreen.kt`
In [TrackingTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt) (lines 493–524):
1. **Control Tab (Page 0)**:
   - Calculate `val isSelected = pagerState.currentPage == 0`.
   - Pass `selected = isSelected` to `Tab`.
   - Pass `selectedContentColor = MaterialTheme.colorScheme.primary` and `unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant` to `Tab`.
   - In `text = { ... }`, update `Text`:
     ```kotlin
     Text(
         text = getControlTabTitle(trackingMode),
         color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
     )
     ```
2. **Telemetry Tabs (Pages 1..N)**:
   - Inside `trackingViews.forEachIndexed { index, view ->`:
     - Calculate `val targetPage = if (screenMode == ScreenMode.TRACKING) index + 1 else index`.
     - Calculate `val isSelected = pagerState.currentPage == targetPage`.
     - Pass `selected = isSelected` to `Tab`.
     - Pass `selectedContentColor = MaterialTheme.colorScheme.primary` and `unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant` to `Tab`.
     - In `text = { ... }`, update `Text`:
       ```kotlin
       Text(
           text = view.name,
           color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
       )
       ```

### Phase 2: Unit Testing (`TabHeaderColorResolutionTest.kt`)
Create unit test in `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TabHeaderColorResolutionTest.kt`:
1. `testAmoledDarkThemeTabHeaderColorsAndContrast`:
   - Verify selected tab color resolves to `DarkPrimary` (`#A6C8FF`).
   - Verify unselected tab color resolves to `AmoledOnSurfaceVariant` (`#9E9E9E`).
   - Calculate relative luminance and verify contrast against `#000000`:
     - Selected contrast ratio $\ge 12:1$ (exceeds WCAG AAA).
     - Unselected contrast ratio $\ge 7:1$ (exceeds WCAG AAA).
2. `testLightThemeTabHeaderColorsAndContrast`:
   - Verify selected tab color resolves to `LightPrimary` (`#1464F4`).
   - Verify unselected tab color resolves to `LightOnSurfaceVariant` (`#44474F`).
   - Calculate relative luminance and verify contrast against `#C8E3FF` (`surfaceContainerHighest`):
     - Selected contrast ratio $\ge 4.5:1$ (WCAG AA).
     - Unselected contrast ratio $\ge 5.0:1$ (WCAG AA).
3. `testColorBypassContract`:
   - Verify that passing explicit `color != Color.Unspecified` to Compose `Text` takes precedence over `LocalContentColor.current`, ensuring synchronous 0ms transition.

### Phase 3: Physical Device Verification (Google Pixel 10)
1. Verify device is in Light Mode (`adb shell cmd uimode night` -> `Night mode: no`).
2. Build and install debug APK on connected Pixel 10 (`66020DLCR002FL`).
3. Launch app, navigate to workout tracking (`START_TRACKING`).
4. Perform horizontal swipe and tab tap navigation between Page 0 and Page 1+:
   - Verify tab header typography switches color synchronously in 0ms with the tab row container background (`#000000` $\leftrightarrow$ `#C8E3FF`).
   - Verify zero color animation lag, zero morphing, and zero intermediate muddy color rendering.
   - Verify ripple effect colors match the active theme.
5. Capture on-device screenshots of Page 0 and Page 1+.

### Phase 4: Clean-Room Full Suite Regression Execution
Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with zero regressions.

---

## 3. Preserved Invariants & Boundary Verification

| Invariant | Protection Mechanism |
|:---|:---|
| **Ambient Theme Isolation (REQ-UI-170)** | Page 0 continues to render in ambient system theme (Light Mode) while telemetry tabs (Pages 1..N) render in AMOLED Pure Black. |
| **Pager Navigation & Snapping** | Tap gestures via `scope.launch { pagerState.animateScrollToPage(...) }` and swipe scrolling remain completely unmodified. |
| **WCAG 2.1 Contrast Standards** | Contrast ratios satisfy WCAG AA/AAA in both AMOLED Dark and Light themes. |
| **Caller Stability & Zero Side-Effects** | No database, repository, background service, or sensor processing changes. Modifications are strictly localized to the `Tab` composable invocation in `TrackingTabsScreen.kt`. |
