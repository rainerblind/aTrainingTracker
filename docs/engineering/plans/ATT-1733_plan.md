# Stage 3: Implementation Plan - ATT-1733: [Lieblingsorte] Make Starts and Strecken badges on KnownLocationCard much more subtle

**Ticket**: [ATT-1733](https://rainerblind.atlassian.net/browse/ATT-1733)  
**Sub-task**: [ATT-1787](https://rainerblind.atlassian.net/browse/ATT-1787) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1396](https://rainerblind.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Requirement Mapping**: `REQ-UI-207` (*Lieblingsorte: Subtle Low-Contrast Ghost Badges for Starts and Strecken on KnownLocationCard*)  
**Test Spec ID**: `TST-UI-161`  
**Branch**: `feature/ATT-1733`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Architectural Design & System Decomposition (SWE.2)

```
+-----------------------------------------------------------------------------------+
|                           KnownLocationCard (Compose)                             |
|                                                                                   |
|  1. Title Row:       "Zu Hause" [titleMedium, Bold, onSurface]                    |
|                                                                                   |
|  2. Altitude Metric: ^ 436 m [bodyMedium, Bold, primary] (SOLE HIGHLIGHTED METRIC) |
|                                                                                   |
|  3. Dedicated Badges Row (REQ-UI-195, REQ-UI-207):                                |
|     +-----------------------------+     +-------------------------------+         |
|     |  [13dp Place] 45 Starts [>] |     |  [13dp Route] 12 Strecken [>] |         |
|     |                             |     |                               |         |
|     |  Surface:                   |     |  Surface:                     |         |
|     |  - surfaceVariant(0.35f)    |     |  - surfaceVariant(0.35f)      |         |
|     |  - outlineVariant(0.25f)    |     |  - outlineVariant(0.25f)      |         |
|     |  - onSurfaceVariant         |     |  - onSurfaceVariant           |         |
|     |  - labelMedium, Medium      |     |  - labelMedium, Medium        |         |
|     |  - Chevron: 11dp (TTAlpha)  |     |  - Chevron: 11dp (TTAlpha)    |         |
|     +-----------------------------+     +-------------------------------+         |
+-----------------------------------------------------------------------------------+
```

### Component Decomposition:
1. **Target UI Composable**:
   - `com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationsScreen.kt`
   - Function: `KnownLocationCard`
   - Imports: Ensure `androidx.compose.foundation.BorderStroke` and `com.atrainingtracker.trainingtracker.ui.theme.TTAlpha` are imported.
2. **Tokens & Theme Consistency**:
   - `color`: `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)`
   - `contentColor`: `MaterialTheme.colorScheme.onSurfaceVariant`
   - `border`: `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))`
   - `shape`: `RoundedCornerShape(8.dp)`
   - `padding`: `padding(horizontal = 8.dp, vertical = 4.dp)`
   - Typography: `MaterialTheme.typography.labelMedium`, `fontWeight = FontWeight.Medium`
   - Leading icon: `13.dp`, `tint = MaterialTheme.colorScheme.onSurfaceVariant`
   - Trailing chevron: `11.dp`, `tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)`
3. **Previews**:
   - Update `PreviewKnownLocationCardLight` and `PreviewKnownLocationCardDark` in `KnownLocationsScreen.kt` to include mock linked clusters, verifying dual badge rendering in both themes.
4. **Structural & Contract Test Suite**:
   - `com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationCardLayoutTest.kt`
   - Assert all updated style invariants and rule out legacy `primaryContainer.copy(alpha = 0.5f)` usage.

---

## 2. Atomic Step-by-Step Implementation Sequence

### Step 1: Update Contract & Structural Test Suite (`TST-UI-161.1`, `TST-UI-161.2`)
- **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationCardLayoutTest.kt`
- **Actions**:
  - Update `testCompactBadgeDimensionsAndStyling()` to verify:
    - `shape = RoundedCornerShape(8.dp)`
    - `padding(horizontal = 8.dp, vertical = 4.dp)`
    - Absence of `defaultMinSize(minHeight = 48.dp)`
    - Presence of test tags `location_starts_badge_${item.id}` and `location_routes_badge_${item.id}`.
  - Add test `testSubtleGhostBadgeStylingAndTokens()`:
    - Verify `content.contains("surfaceVariant.copy(alpha = 0.35f)")`
    - Verify `content.contains("contentColor = MaterialTheme.colorScheme.onSurfaceVariant")`
    - Verify `content.contains("style = MaterialTheme.typography.labelMedium")`
    - Verify `content.contains("fontWeight = FontWeight.Medium")`
    - Verify `content.contains("BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))")`
    - Assert `!content.contains("primaryContainer.copy(alpha = 0.5f)")` (saturated pill background eradicated).

### Step 2: Implement Subtle Ghost Badges in `KnownLocationsScreen.kt` (`REQ-UI-207`)
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt`
- **Actions**:
  - Ensure imports for `BorderStroke` and `TTAlpha`.
  - Refactor the Starts-Badge `Surface`:
    - Set `color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)`.
    - Set `contentColor = MaterialTheme.colorScheme.onSurfaceVariant`.
    - Set `border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))`.
    - Set leading `Place` icon to `13.dp`, `tint = MaterialTheme.colorScheme.onSurfaceVariant`.
    - Set count `Text` to `style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant`.
    - Set trailing `ArrowForward` icon to `11.dp`, `tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)`.
  - Refactor the Strecken-Badge `Surface`:
    - Apply matching ghost styling: `surfaceVariant.copy(alpha = 0.35f)`, `border = BorderStroke(0.5.dp, outlineVariant.copy(alpha = 0.25f))`.
    - Set leading `ic_favorite_route` icon to `13.dp`, `tint = MaterialTheme.colorScheme.onSurfaceVariant`.
    - Set count `Text` to `style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant`.
    - Set trailing `ArrowForward` icon to `11.dp`, `tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)`.
  - In `PreviewKnownLocationCardLight` and `PreviewKnownLocationCardDark`, provide a mock `linkedClusters` item so both Starts and Strecken badges render in preview.

### Step 3: Targeted Unit Test Validation
- **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationCardLayoutTest
  ```
- **Goal**: Confirm all layout and token assertions pass cleanly.

### Step 4: Full Clean-Room Regression Test Suite
- **Command**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Goal**: Confirm 100% test pass rate across all 1100+ tests with zero regressions.

---

## 3. Invariants & Rollback Plan

### Non-Negotiable Invariants:
1. **Layout Hierarchy**: Altitude metric remains on its own dedicated row above the badges (`REQ-UI-195`).
2. **Interaction & Drill-Down**: Single-tap edit dialog (`EditKnownLocationDialog`), long-press delete menu (`REQ-UI-061`), starts badge tap (`onShowWorkouts`), and routes badge tap (`onShowRoutes`) remain 100% functional.
3. **Accessibility**: Touch targets remain accessible.
4. **Test Tags**: `location_starts_badge_${item.id}` and `location_routes_badge_${item.id}` are strictly preserved.
5. **Localization**: Plural format strings across all 9 languages remain untouched and valid.

### Rollback Plan:
The feature branch `feature/ATT-1733` is cleanly isolated from `sprint/2026-40.6`. If any unexpected defect arises, `git checkout sprint/2026-40.6` restores the pre-ticket state immediately.
