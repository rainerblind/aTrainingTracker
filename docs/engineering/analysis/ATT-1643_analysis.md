# Stage 1 Analysis: ATT-1643 - Dedicated Row and Refined Compact Sizing for Starts and Strecken Badges on KnownLocationCard

**Ticket**: [ATT-1643](https://atrainingtracker.atlassian.net/browse/ATT-1643)  
**Sub-task**: [ATT-1659](https://atrainingtracker.atlassian.net/browse/ATT-1659) (`[Analysis]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1643`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Problem Statement & Motivation

On [KnownLocationCard](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt), the altitude metric, Starts badge, and Strecken (Routes) badge currently share an adaptive `FlowRow`.

1. **Uneven Visual Rhythm**: When a favorite location has many starts (e.g. "625 Starts") or longer names, the Strecken badge is forced onto a second line, while cards with fewer starts or zero linked routes remain single-line. This causes an uneven, staggered card height across the location list.
2. **Oversized Badges**: The badges currently enforce `.defaultMinSize(minHeight = 48.dp)` on their outer `Surface` containers with heavy padding, making them visually disproportionate to the card dimensions.

Athletes require:
1. Interactive badges (Starts-Badge and Strecken-Badge) placed on their own dedicated row beneath the location header and altitude metric.
2. Refined badge sizing to be slightly smaller and more compact (refined padding, typography, and icon scale).
3. Consistent visual alignment and height across all `KnownLocationCard` items.

---

## 2. Root Cause Analysis (Forensic Investigation)

In `KnownLocationsScreen.kt` (lines 352–448):
```kotlin
FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
) {
    // Prominent Altitude Metric
    Row(...) { ... }

    // Number of Starts (Interactive Drill-Down Touch Target)
    Surface(
        ...,
        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
    ) { ... }

    // Number of Routes (Interactive Drill-Down Touch Target)
    if (linkedClusters.isNotEmpty()) {
        Surface(
            ...,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) { ... }
    }
}
```

### Architectural Flaws & Root Causes:
1. **Coupling Metric and Badges in One FlowRow**:
   The altitude metric takes approximately 80–100dp of horizontal width. Placing it inside the same `FlowRow` alongside two interactive badges leaves only ~220–260dp of width on standard mobile viewports. If the starts count or plural localized string exceeds this available width, the Strecken badge wraps down, while cards without routes or with 1-digit hit counts do not wrap.
2. **Explicit 48dp Container Height**:
   Using `defaultMinSize(minHeight = 48.dp)` inflates the visible pill container background to 48dp tall. Material 3 chip/badge design conventions recommend a visual height of ~28–32dp with compact internal padding, while maintaining touch accessibility.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Decouple the altitude metric into its own dedicated row beneath the location name header.
  * Render interactive badges (Starts-Badge and Strecken-Badge) on their own dedicated row beneath the altitude metric.
  * Refine badge styling: `RoundedCornerShape(8.dp)`, compact padding (`horizontal = 8.dp, vertical = 4.dp`), `labelLarge` typography, `14.dp` leading icon, and `12.dp` trailing chevron.
  * Ensure consistent visual alignment across all cards.
  * Preserve all test tags (`location_starts_badge_${item.id}`, `location_routes_badge_${item.id}`).
  * Update requirements and test specifications (`REQ-UI-195`, `TST-UI-149`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not change navigation destinations (`onShowWorkouts`, `onShowRoutes`).
  * Do not alter universal delete context menu semantics (`REQ-UI-061`).
  * Do not modify database schemas, repository queries, or cluster linking logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: `REQ-UI-185` (*Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location*) and `REQ-UI-188` (*Lieblingsorte: Compact Interactive Routes Badge on KnownLocationCard*).
* **Historical Origin & Commit Trace**: Commits `6bfbcfc7` (ATT-1401) and `7868078b` (ATT-1594).
* **Root Reason for Existing Formulation**: `REQ-UI-188` originally placed the altitude metric and badges in a single `FlowRow` to conserve vertical space before high hit counts and localized route counts were observed in production.
* **Preservation of Core Invariants**: Drill-down navigation callbacks, accessibility touch targets, delete context menu integrity (`REQ-UI-061`), single-thread SQLite safety, and 9-language localization parity are 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

Within `KnownLocationCard`:
1. **Location Name Row**: Title text (`titleMedium`, `FontWeight.Bold`).
2. **Altitude Metric Row**: Clean metric indicator with `ic_ascent` icon (`16.dp`) and formatted elevation string (`bodyMedium` / `titleSmall`).
3. **Dedicated Badges Row**:
   - Arranged in `FlowRow` (or `Row`) with `horizontalArrangement = Arrangement.spacedBy(8.dp)`.
   - Starts badge: Compact `Surface(shape = RoundedCornerShape(8.dp))` with `padding(horizontal = 8.dp, vertical = 4.dp)`, `14.dp` pin icon, `labelLarge` count text, and `12.dp` arrow icon.
   - Strecken badge (if `linkedClusters.isNotEmpty()`): Matching compact `Surface` with `14.dp` route icon, `labelLarge` count text, and `12.dp` arrow icon.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Card tap (`onEdit`), long-press delete menu (`onDelete`), starts badge tap (`onShowWorkouts`), and routes badge tap (`onShowRoutes`) remain 100% operational.
  2. Zero regression in unit tests.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** (Visual layout hierarchy and badge styling refinement within a single Compose component).
