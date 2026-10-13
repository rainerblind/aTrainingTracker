# Stage 3: Implementation Plan - ATT-2865: Preserve authentic sport type icon colors in route selector sheet

**Ticket**: [ATT-2865](https://rainerblind.atlassian.net/browse/ATT-2865)  
**Sub-task**: [ATT-2917](https://rainerblind.atlassian.net/browse/ATT-2917) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Requirement Mapping**: `REQ-UI-320` (*Preservation of Authentic Sport Type Icon Colors in Quick Route Selector*)  
**Test Mapping**: `TST-UI-280` (*Preservation of Authentic Sport Type Icon Colors in Quick Route Selector Verification*)  
**Branch**: `improvement/ATT-2865`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Background

During Sprint 2026-41.4 review of `ATT-2668` on Google Pixel 10, sprint review feedback highlighted that sport type icons within `RouteCard` (in `RouteSelectorSheet.kt`) were tinted with monochrome theme colors (`if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant`). Because the application's sport type assets (`bsport_bike`, `bsport_run`, `bsport_other`) are multi-color vector illustrations, applying a single-color tint destroyed their internal graphic details and visual identity. Athletes need each route card's sport icon rendered in its authentic, full-fidelity multi-color format using `tint = Color.Unspecified`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-320` (*Preservation of Authentic Sport Type Icon Colors in Quick Route Selector*)
* **Test Mapping**: `TST-UI-280` (*Preservation of Authentic Sport Type Icon Colors in Quick Route Selector Verification*)
  * `TST-UI-280.1`: Architectural contract test verifying `RouteCard` specifies `tint = Color.Unspecified` and eliminates `tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant`.
  * `TST-UI-280.2`: Architectural contract test verifying `RouteCard` preserves `24.dp` sizing, `route.summary.bSportType.iconResId`, and localized accessibility description via `stringResource(id = route.summary.bSportType.stringResId)`.
  * `TST-UI-280.3`: Architectural contract test verifying active route chip (`"ACTIVE"` labelSmall onPrimary text over primary background) and container styling are preserved.
  * `TST-UI-280.4`: Clean-room full regression test suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Active Route Indication**: Active routes continue to display the dedicated `"ACTIVE"` chip (`labelSmall`, `onPrimary` text over `primary` container) and `secondaryContainer` card background.
2. **Typography & Layout**: Route title (`titleMedium`, `SemiBold`), distance and elevation subtitle (`bodySmall`, `onSurfaceVariant`), and card padding (`12.dp`) remain unaltered.
3. **Accessibility**: `contentDescription = stringResource(id = route.summary.bSportType.stringResId)` remains preserved.
4. **Icon Sizing**: 24.dp dimensions remain preserved.
5. **Subtask Direct Completion**: Sub-task transitions directly to `Erledigt` via transition `freigabe` upon Gate 3 audit pass.
6. **Parent Human Decision Gate**: Parent ticket ATT-2865 terminal transition is strictly `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `RouteSelectorSheet.kt` (`com.atrainingtracker.trainingtracker.ui.routes`)
1. Import `androidx.compose.ui.graphics.Color`.
2. In `RouteCard`:
   ```kotlin
   Icon(
       painter = painterResource(id = route.summary.bSportType.iconResId),
       contentDescription = stringResource(id = route.summary.bSportType.stringResId),
       modifier = Modifier.size(24.dp),
       tint = Color.Unspecified
   )
   ```

### Component 2: `RouteSelectorSheetTest.kt` (`com.atrainingtracker.trainingtracker.ui.routes`)
1. Update `testRouteCard_displaysSportIconWithAppropriateTokens`:
   * Assert `content.contains("tint = Color.Unspecified")`.
   * Assert `!content.contains("tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant")`.
2. Add `testRouteCard_preservesActiveRouteChipAndCardColors`:
   * Assert `content.contains("\"ACTIVE\"")`.
   * Assert `content.contains("MaterialTheme.colorScheme.secondaryContainer")`.

### UI Consistency (Rule 23)
* **Closest Reference Screen**: `SportTypeSelector.kt` (`SportItem`), which uses `tint = if (isSelected) Color.Unspecified else Color.Gray`.
* **Reused Components**: Existing `RouteCard` layout and `Icon` composable.
* **Theme Tokens**: `Color.Unspecified` for multi-color assets, `secondaryContainer` and `primary` for active state.
* **One-Off Styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Gate 3 Audit Sign-Off
* Ensure Gate 3 passes and ATT-2917 is `Erledigt`.
* Run mandatory check: `python3 tools/jira_util.py check-gate ATT-2917`.

### Step 2: Update `RouteSelectorSheet.kt`
* Add import `androidx.compose.ui.graphics.Color`.
* Replace `tint` in `RouteCard` with `Color.Unspecified`.

### Step 3: Update `RouteSelectorSheetTest.kt`
* Expand `testRouteCard_displaysSportIconWithAppropriateTokens` to assert `tint = Color.Unspecified`.
* Add test verifying active route indicator and container color contracts.

### Step 4: Execute Targeted Unit Tests
* Run:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteSelectorSheetTest"
  ```

---

## 6. Clean-Room Regression Verification (Stage 5)
* Execute full clean-room suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* Author walkthrough: `docs/engineering/walkthroughs/ATT-2865_walkthrough.md`.
* Mark `REQ-UI-320` and `TST-UI-280` as `Verified`.
