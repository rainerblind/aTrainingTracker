# Stage 1 Analysis: ATT-1733 - [Lieblingsorte] Make Starts and Strecken badges on KnownLocationCard much more subtle

**Ticket**: [ATT-1733](https://rainerblind.atlassian.net/browse/ATT-1733)  
**Sub-task**: [ATT-1785](https://rainerblind.atlassian.net/browse/ATT-1785) (`[Analysis]`)  
**Parent Epic**: [ATT-1396](https://rainerblind.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Branch**: `feature/ATT-1733`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Executive Summary & User Feedback

During the Sprint Review of sprint `2026-40.5` (specifically evaluating ticket [ATT-1643](https://rainerblind.atlassian.net/browse/ATT-1643)), the athlete user praised the dedicated row separation for Starts and Strecken (Routes) badges, but noted significant visual dissatisfaction with their prominence:
> *"while the separate row is good, the Starts and Strecken buttons/badges are still far too dominant visually (pill container background, saturation, and size)."*
> *(User provided visual reference attachment `screenshot_known_locations.png`)*

### Forensic Visual Inspection (`screenshot_known_locations.png`)
Inspection of the production screen rendering reveals:
1. **Saturated Saliency Competition**: The cards feature a prominent baby-blue saturated container background (`primaryContainer.copy(alpha = 0.5f)`), paired with bold, saturated primary blue text (`primary`, `FontWeight.Bold`, `labelLarge`) and primary icons.
2. **Visual Clutter in List Context**: Across a vertical list of 5–10 locations (e.g. "Zu Hause", "Work", "Schulstraße", "Baad"), the repetitive pairs of saturated blue pills dominate the viewport, visually outshining the primary card title and elevation metric, giving the impression of aggressive action buttons rather than secondary metadata chips.
3. **Typography & Icon Sizing**: `labelLarge` (14sp) in bold weight is disproportionately heavy for contextual metadata that serves as an auxiliary drill-down link.

---

## 2. Root Cause Analysis (Forensic Code Investigation)

In [KnownLocationsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt) lines 372–447:

```kotlin
// Dedicated Badges Row (Starts and Routes, REQ-UI-195)
FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
) {
    // Number of Starts (Interactive Drill-Down Touch Target)
    Surface(
        onClick = onShowWorkouts,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("location_starts_badge_${item.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = stringResource(R.string.known_locations_view_workouts),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = pluralStringResource(R.plurals.known_locations_starts, startsCount, startsCount),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }

    // Number of Routes (Interactive Drill-Down Touch Target per REQ-UI-188, REQ-UI-195)
    if (linkedClusters.isNotEmpty()) {
        Surface(
            onClick = onShowRoutes,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("location_routes_badge_${item.id}")
        ) {
            ...
        }
    }
}
```

### Key Architectural Flaws:
1. **Container Styling (`color`)**: Uses `MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)`. In light mode, `primaryContainer` is a vivid sky blue (`#A1CAF1` / Baby Blue Eye), which commands intense visual focus on a white `#FFFFFF` surface card.
2. **Text & Content Color (`contentColor`)**: Inherits `MaterialTheme.colorScheme.primary` and explicitly hardcodes `tint = MaterialTheme.colorScheme.primary` on icons and `color = MaterialTheme.colorScheme.primary` on text, conflicting with the primary elevation metric (`ic_ascent` + elevation text) which should remain the sole accented metric.
3. **Weight & Scale**: `labelLarge` with `FontWeight.Bold`, paired with `14.dp` and `12.dp` icons, gives badges the visual footprint of primary button CTAs.

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)

* **Original Requirement ID & Target**: `REQ-UI-195` (*Lieblingsorte: Dedicated Row and Compact Sizing for Starts and Strecken Badges on KnownLocationCard*), targeting `KnownLocationCard` in `KnownLocationsScreen.kt`.
* **Historical Origin & Commit Trace**: Introduced in sprint `2026-40.5` ([ATT-1643](https://rainerblind.atlassian.net/browse/ATT-1643)) via commit `073df267`.
* **Root Reason for Existing Formulation**: `ATT-1643` solved the layout wrapping problem by giving badges their own dedicated row and removing the bloated `defaultMinSize(minHeight = 48.dp)`. However, it preserved the legacy color palette (`primaryContainer.copy(alpha = 0.5f)` and bold `primary` text) originating from `ATT-1401` and `ATT-1594`. In a multi-card list, this preserved color scheme created unintended visual dominance.
* **Preservation of Core Invariants**:
  1. The dedicated row placement beneath the altitude metric MUST remain intact.
  2. The interactive drill-down navigation (`onShowWorkouts` -> filtered workouts, `onShowRoutes` -> filtered routes) MUST remain 100% operational.
  3. Accessibility touch target responsiveness MUST be preserved.
  4. Test tags `location_starts_badge_${item.id}` and `location_routes_badge_${item.id}` MUST NOT be removed or altered.
  5. The universal long-press delete context menu (`REQ-UI-061`) MUST NOT be impacted.
  6. 9-language localization parity (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) MUST remain intact.

---

## 4. User Scope Grounding (`ATT-1250`)

* **In-Scope Objectives**:
  - Refine container styling of Starts-Badge and Strecken-Badge on `KnownLocationCard` to be subtle and understated.
  - Adopt low-contrast tonal styling:
    - Container color: Soft tonal surface (`MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)` or `surfaceContainerHigh.copy(alpha = 0.45f)`) with optional subtle border stroke (`outlineVariant.copy(alpha = 0.3f)`).
    - Content color: Secondary metadata token `MaterialTheme.colorScheme.onSurfaceVariant`.
    - Typography: `MaterialTheme.typography.labelMedium` (12sp) with `FontWeight.Medium` (instead of 14sp bold).
    - Leading icon: Scaled to `13.dp`, tinted with `onSurfaceVariant`.
    - Trailing drill-down indicator: Scaled to `11.dp`, tinted with `onSurfaceVariant.copy(alpha = TTAlpha.Medium)` to subtly signal drill-down capability without demanding attention.
  - Refine `@Preview` composables in `KnownLocationsScreen.kt` to include mock clusters and verify visual harmony in both Light and Dark themes.
  - Update unit/contract tests (`KnownLocationCardLayoutTest.kt`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Do NOT modify workout start counting or database reconciliation logic (already resolved and verified in `ATT-1734`).
  - Do NOT change navigation route targets, intent filters, or ViewModel state management.
  - Do NOT alter the altitude metric styling or row position.
  - Do NOT alter filter dialogs or section headings (that belongs to `ATT-1732`).

---

## 5. Architectural Strategy & Design Evaluation

| Attribute | Current (`ATT-1643`) | Proposed Subtle Ghost Badge (`ATT-1733`) | Rationale |
| :--- | :--- | :--- | :--- |
| **Container Background** | `primaryContainer.copy(alpha = 0.5f)` (vivid blue) | `surfaceVariant.copy(alpha = 0.35f)` (soft neutral tint) | Eliminates saturated color patch while defining a clean touch boundary. |
| **Border Stroke** | `null` (borderless) | `BorderStroke(0.5.dp, outlineVariant.copy(alpha = 0.25f))` | Subtly grounds the ghost chip against surface cards without adding heavy lines. |
| **Text Color** | `MaterialTheme.colorScheme.primary` (bold blue) | `MaterialTheme.colorScheme.onSurfaceVariant` (muted slate) | Establishes proper visual hierarchy where altitude is the highlighted metric. |
| **Typography** | `labelLarge` (14sp), `FontWeight.Bold` | `labelMedium` (12sp), `FontWeight.Medium` | Proportionate to card metadata size. |
| **Leading Icon** | `14.dp`, `tint = primary` | `13.dp`, `tint = onSurfaceVariant` | Clean, understated icon accompaniment. |
| **Trailing Chevron** | `12.dp`, `tint = primary` | `11.dp`, `tint = onSurfaceVariant.copy(alpha = TTAlpha.Medium)` | Subtle affordance indicating interactive drill-down. |
| **Shape & Padding** | `RoundedCornerShape(8.dp)`, `padding(h=8.dp, v=4.dp)` | `RoundedCornerShape(8.dp)`, `padding(h=8.dp, v=4.dp)` | Preserves compact footprint and existing contract test invariants. |

---

## 6. System Invariants & Risk Assessment

* **Invariants**:
  1. `REQ-UI-061`: Universal long-press delete context menu top-start pinned.
  2. `REQ-UI-195`: Dedicated badges row decoupled from altitude metric.
  3. `TST-UI-149`: Contract tests verify presence of test tags and layout separation.
  4. 100% clean-room unit test suite pass rate with 0 regressions.
* **Risk Rating**: **MINIMAL** (Pure visual refinement within `KnownLocationCard` composable in `KnownLocationsScreen.kt`).
