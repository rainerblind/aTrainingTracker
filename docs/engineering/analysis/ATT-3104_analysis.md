# Stage 1 Analysis: ATT-3104 - Remove remaining grey container surfaces from in-ride fork decision card

**Ticket**: [ATT-3104](https://atrainingtracker.atlassian.net/browse/ATT-3104)  
**Sub-task**: [ATT-3138](https://atrainingtracker.atlassian.net/browse/ATT-3138) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.8`  
**Branch**: `feature/ATT-3104`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Statement & Motivation

During Sprint 2026-41.7 Joint Review of ATT-2962, the athlete observed that grey container surfaces still remain on the in-ride fork decision HUD card (`ForkDecisionCard.kt`).

In Sprint 2026-41.6 (`ATT-2962`), `ForkDecisionCard.kt` was restructured from an ungrouped flat list to directional grouping (`ForkDirectionGroup`), and the outer card container was updated from `surfaceVariant` to `MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha)`. However, when inspecting the rendered HUD card on-device (under both direct sunlight and night AMOLED conditions), the card continues to exhibit visible greyish/tinted container surfaces across its nested rows and directional columns rather than clean, pure white in light mode and deep pure black in AMOLED/dark mode.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of `ForkDecisionCard.kt` and Material 3 elevation tinting mechanics revealed three compounding factors that produce the lingering grey container appearance:

### 2.1 Material 3 Surface Tonal Elevation Tinting in `ForkDirectionGroup`
In `ForkDecisionCard.kt` (lines 153–159):
```kotlin
Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    tonalElevation = 1.dp,
    modifier = modifier.fillMaxWidth()
)
```
- Material 3 `Surface` computes its effective background using `surfaceColorAtElevation(color = color, elevation = tonalElevation)`.
- When `tonalElevation = 1.dp`, Material 3 blends an elevation overlay tint (`surfaceTint`) on top of `color`:
  - In light mode with dynamic theming or non-zero tint, pure white (`#FFFFFF`) is tinted with a lavender/grey overlay (~5% tint).
  - In dark mode, pure dark surfaces (`#16161A` or `#000000`) are lightened by an elevation overlay, turning pitch black into a washed-out slate grey.
- Specifying `tonalElevation = 1.dp` directly causes the inner directional group container to render as a distinct grey surface.

### 2.2 Implicit Tonal Elevation in Outer `Card`
In `ForkDecisionCard.kt` (lines 77–87):
```kotlin
Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha)
    ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
)
```
- In Compose Material 3, `Card` delegates directly to an internal `Surface`.
- `CardDefaults.cardElevation(defaultElevation = 6.dp)` sets **both** `shadowElevation` and `tonalElevation` to `6.dp`.
- A tonal elevation of 6.dp applies a heavy elevation tint (Level 3 elevation, ~11% tint overlay), further discoloring the outer container surface away from pure white / AMOLED black.

### 2.3 Opacity & Surface Inconsistency
- The outer `Card` uses `containerColor = MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha)`. When `overlayAlpha` is applied (e.g. default 0.9f), the outer container is slightly translucent.
- However, `ForkDirectionGroup` was assigned `color = MaterialTheme.colorScheme.surface` with full opacity (alpha = 1.0f) and `tonalElevation = 1.dp`.
- This creates an optical discontinuity: the outer card margin and header row are translucent against the underlying map/track, while the inner direction group renders as an opaque, tinted grey patch.

---

## 3. User Scope Grounding (ATT-1250)

### 3.1 In-Scope Goals
1. **Audit and Modernize `ForkDecisionCard.kt` Containers**:
   - Replace outer `Card` with `Surface` configuring explicit `shadowElevation = 6.dp` and strict `tonalElevation = 0.dp`, eliminating any implicit M3 elevation tinting on the outer card.
   - Update `ForkDirectionGroup` to enforce `tonalElevation = 0.dp`, eliminating the 1.dp elevation tint overlay.
   - Propagate `overlayAlpha` into `ForkDirectionGroup` (`color = MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha)`), ensuring uniform surface transparency between outer container and inner groups.
2. **Preserve High-Contrast Crisp Surfaces**:
   - In Light Mode: Pure white surfaces (`#FFFFFF`).
   - In Dark Mode: Deep dark surfaces (`#16161A`).
   - In AMOLED Mode: Pitch black surfaces (`#000000`).
3. **Audit Content & Divider Tokens**:
   - Ensure all nested rows (`ForkRouteRow`), direction columns, and route columns have no conflicting grey backgrounds.
   - Maintain `outlineVariant` subtle borders and dividers for crisp definition.
4. **Author Contract Tests (`ForkDecisionCardTest.kt`)**:
   - Verify `ForkDecisionCard.kt` enforces `tonalElevation = 0.dp` across outer container and direction groups.
   - Assert zero usage of `surfaceVariant` for container backgrounds.

### 3.2 Out-of-Scope Non-Goals (Scope Bounding)
1. Modifying the data structures `ForkDecisionState`, `ForkBranchOption`, or `ForkDirection`.
2. Changing candidate route selection logic, divergence detection, or bearing diff algorithms (`ATT-2964`).
3. Modifying string resources or translations (100% 9-language translation parity already verified in `TST-UI-290`).
4. Refactoring other HUD overlays (`TurnPromptBanner.kt`, `ReturnNavigationHud.kt`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-UI-330` (*In-Ride Fork Route Decision Card HUD Layout & Selection Interactions*, Sprint 2026-41.6, ATT-2962), complementing `REQ-MAP-031` (*In-Ride Fork Route Decision Card HUD Layout & Selection Interactions*).
2. **Historical Origin & Commit Trace**:
   - Commit `0f24b2c0` / `514bf5ee` (ATT-2962): Migrated the flat branch list to directional grouping and updated outer container color from `surfaceVariant` to `surface`.
3. **Root Reason for Existing Formulation**:
   - During ATT-2962 construction, `tonalElevation = 1.dp` was copied from standard Material 3 elevated card patterns under the assumption that a slight elevation would provide subtle visual depth. The developer overlooked that in Material 3, `tonalElevation` applies an elevation color tint that ruins pure white and AMOLED pure black surfaces. Furthermore, using `CardDefaults.cardElevation(defaultElevation = 6.dp)` unintentionally introduced a 6dp tonal elevation tint on the outer container.
4. **Preservation of Core Invariants**:
   - *Directional Grouping*: Preserved via `branches.groupBy { it.direction }`.
   - *Selection Callbacks*: `onRouteSelected(branch.routeId)` remains intact.
   - *Dismissal*: `onDismiss()` remains intact.
   - *HUD Glancing Readability*: Removing grey tinting significantly improves sunlight legibility and AMOLED power efficiency.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Container Hierarchy Refactoring
```kotlin
// Outer Card Container -> Migrated to Surface with decoupled shadow/tonal elevation
Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
    shadowElevation = 6.dp,
    tonalElevation = 0.dp,
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
) {
    Column(...) {
        // Header Row (CallSplit icon, approaching text, dismiss button)
        ...
        // Directional Groups
        groupedBranches.forEach { (direction, branches) ->
            ForkDirectionGroup(
                direction = direction,
                branches = branches,
                overlayAlpha = overlayAlpha,
                onRouteSelected = onRouteSelected
            )
        }
    }
}
```

### 5.2 Inner `ForkDirectionGroup` Surface
```kotlin
Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    tonalElevation = 0.dp,
    modifier = modifier.fillMaxWidth()
)
```
- `tonalElevation = 0.dp`: Strictly prevents M3 from adding elevation tint.
- `color = MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha)`: Perfectly matches the outer container opacity and theme surface.

---

## 6. System Invariants & Risk Assessment

1. **Zero Tonal Elevation Tint Invariant**: Both outer container and nested direction groups MUST enforce `tonalElevation = 0.dp` to ensure surfaces remain pure `#FFFFFF` in light mode, `#16161A` in dark mode, and `#000000` in AMOLED mode.
2. **Zero `surfaceVariant` Invariant**: No container or row background in `ForkDecisionCard.kt` shall use `surfaceVariant`.
3. **Zero Displacement Invariant**: The bounding box, paddings, and animated visibility transitions remain identical, causing zero layout shift.
4. **Clean-Room Regression**: 100% unit test pass rate across the full test suite.
