# Stage 1: Problem Domain & Root Cause Analysis - ATT-2865: Preserve authentic sport type icon colors in route selector sheet

**Ticket**: [ATT-2865](https://rainerblind.atlassian.net/browse/ATT-2865)  
**Sub-task**: [ATT-2915](https://rainerblind.atlassian.net/browse/ATT-2915) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Branch**: `improvement/ATT-2865`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Forensic Analysis

### 1.1 Field Symptom & User Feedback
During Sprint 2026-41.4 review of `ATT-2668` on Google Pixel 10, sprint review feedback highlighted a visual fidelity regression in the quick route selection bottom sheet:
> *"In RouteSelectorSheet (RouteCard), render the sport type icons with their authentic original colors (e.g., using tint = Color.Unspecified or original multi-color assets) instead of tinting them with onSurfaceVariant/primary theme colors."*

### 1.2 Forensic Source Investigation
Inspection of `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheet.kt` (`RouteCard`) reveals lines 264–269:
```kotlin
            Icon(
                painter = painterResource(id = route.summary.bSportType.iconResId),
                contentDescription = stringResource(id = route.summary.bSportType.stringResId),
                modifier = Modifier.size(24.dp),
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
```
In Jetpack Compose, the `Icon` composable applies a color filter to tint the underlying drawable. Because `tint` was explicitly assigned `if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant`, the multi-color sport vector assets (`R.drawable.bsport_bike`, `R.drawable.bsport_run`, `R.drawable.bsport_other`) were forcibly flattened into a monochrome silhouette (either primary royal blue or onSurfaceVariant muted grey).

---

## 2. Chesterton's Fence & Requirement Archaeology

### 2.1 Original Requirement ID & Target
* **Requirement**: `REQ-UI-310` (*Sport-Specific Visual Representation & Active Navigation Indication in Quick Route Selector*).
* **Target Release**: `V4.9.39` (Sprint 2026-41.4).
* **Intent**: To display sport-specific iconography for each route card in `RouteSelectorModalBottomSheet`, differentiating cycling, running, and other workout routes.

### 2.2 Historical Origin & Commit Trace
* **Originating Ticket**: `ATT-2668` (*[Verbesserung] Quick Route Selector Bottom Sheet: Sport iconography, active route indication, and HUD alignment*).
* **Precedent Ticket**: `ATT-2856` (*[Bug] SportTypeSelectorKt.SportItem*), which resolved the identical issue in `SportTypeSelector.kt` by replacing monochrome tinting with `tint = Color.Unspecified`.

### 2.3 Root Reason for Existing Formulation
In standard Material 3 design patterns, icons rendered within cards typically inherit on-surface or primary tokens to maintain monochrome contrast. However, `aTrainingTracker` uses custom multi-color vector drawables for sport types with distinct color codes (e.g., bike frame geometry, runner silhouette, sport accents). Applying a monochrome tint flattens all internal color layers into a single solid color, destroying the authentic design language and visual cues.

### 2.4 Preservation of Core Invariants
1. **Active Route Indication**: Active routes continue to display the dedicated `"ACTIVE"` chip (`labelSmall`, `onPrimary` text over `primary` container) and `secondaryContainer` card background.
2. **Typography & Layout**: Route title (`titleMedium`, `SemiBold`), distance and elevation subtitle (`bodySmall`, `onSurfaceVariant`), and card padding (`12.dp`) remain unaltered.
3. **Accessibility**: `contentDescription = stringResource(id = route.summary.bSportType.stringResId)` remains preserved.
4. **Icon Sizing**: 24.dp dimensions remain preserved.
5. **Regression Safety**: 100% test pass rate across the full test suite.

---

## 3. Scope Bounding & Non-Goals

### In-Scope
* Modifying `RouteCard` in `RouteSelectorSheet.kt` to set `tint = Color.Unspecified`.
* Updating `RouteSelectorSheetTest.kt` to enforce `tint = Color.Unspecified` and prohibit monochrome theme tinting.
* Formulating requirement `REQ-UI-320` and test specification `TST-UI-280`.

### Out-of-Scope (Non-Goals)
* Modifying route database models or repository queries.
* Modifying other cards (e.g. `MidRideHeimwegCard`, which properly uses `Icons.Default.Home` with `TTColor.RouteSelected`).
* Changing route selection or active navigation business logic.

---

## 4. Proposed Solution & Architecture

In `RouteCard` (`RouteSelectorSheet.kt`):
```kotlin
            Icon(
                painter = painterResource(id = route.summary.bSportType.iconResId),
                contentDescription = stringResource(id = route.summary.bSportType.stringResId),
                modifier = Modifier.size(24.dp),
                tint = Color.Unspecified
            )
```
Setting `tint = Color.Unspecified` instructs Jetpack Compose's `Icon` (and `PainterModifier`) to bypass tint color filtering, drawing the vector asset with its intrinsic XML path colors.
