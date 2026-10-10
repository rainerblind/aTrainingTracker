# Stage 2: Requirement & Test Specification: ATT-3104

## Metadata
* **Ticket ID**: ATT-3104
* **Title**: Remove remaining grey container surfaces from in-ride fork decision card
* **Parent Epic**: ATT-2564 (Navigation: Turn-by-Turn Guidance & Cockpit Prompts)
* **Author**: Antigravity (Pair Programming Assistant)
* **Date**: 2026-10-10
* **Requirement ID**: `REQ-UI-337`
* **Test Specification ID**: `TST-UI-297`
* **Status**: Specified

---

## 1. Requirement Specification (REQ-UI-337)

### 1.1 Summary
The system SHALL eliminate lingering grey container surfaces, enforce strict zero tonal elevation across all container surfaces, and harmonize opacity and surface coloring in the in-ride fork decision HUD card (`ForkDecisionCard.kt`) (ATT-3104, amending `REQ-UI-330` and complementing `REQ-MAP-031`):

1. **Strict Zero Tonal Elevation Across All Containers (`ForkDecisionCard.kt`)**:
   - The outer container SHALL NOT apply tonal elevation (`tonalElevation = 0.dp`), completely eliminating implicit Material 3 elevation tinting (`surfaceColorAtElevation`) that degrades pure white (`#FFFFFF`) in light mode and deep pitch black (`#000000`) in AMOLED mode. The outer container SHALL be implemented via `Surface` with `shadowElevation = 6.dp` and explicit `tonalElevation = 0.dp`, cleanly decoupling shadow depth from surface elevation tinting.
   - The nested directional group container (`ForkDirectionGroup`) SHALL strictly enforce `tonalElevation = 0.dp`, eliminating the previously hardcoded `1.dp` elevation overlay tint.
2. **Transparent & Harmonized Surface Coloring (`ForkDecisionCard.kt`)**:
   - The nested directional group container (`ForkDirectionGroup`) SHALL match the outer card surface and transparency via `color = MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha)`, eliminating opaque rectangular patch artifacts inside translucent card margins.
   - In Light Mode, the card container, nested direction groups, and route rows SHALL render on clean pure white surfaces (`LightSurface = #FFFFFF`).
   - In Dark Mode, all containers and rows SHALL render on deep dark surfaces (`DarkSurface = #16161A`).
   - In AMOLED Mode, all containers and rows SHALL render on deep pitch black surfaces (`AmoledSurface = #000000`).
   - All nested rows (`ForkRouteRow`), direction columns, and route columns SHALL remain free of `surfaceVariant` or conflicting background fills.
3. **Preservation of Structural Elements & Visual Dividers**:
   - Subtle vertical and horizontal dividers separating directions and candidate routes SHALL use `MaterialTheme.colorScheme.outlineVariant` with subdued alpha (`0.4f` - `0.5f`), preserving crisp visual separation without adding grey container backgrounds.
   - Outer container border (`BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))`) and inner group border (`BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))`) SHALL be preserved.
4. **Preservation of System Invariants**:
   - Directional grouping (`decisionState.branches.groupBy { it.direction }`), route selection callback (`onRouteSelected`), dismiss callback (`onDismiss`), countdown distance, and 100% 9-language localization parity MUST be strictly preserved.

### 1.2 Chesterton's Fence Audit
* **Historical Precedent**: `ATT-2962` (Sprint 2026-41.6, commit `0f24b2c0`) introduced directional grouping and changed the outer card container from `surfaceVariant` to `MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha)`.
* **Root Reason for Existing Formulation**: During ATT-2962, `tonalElevation = 1.dp` was assigned to `ForkDirectionGroup`, and `CardDefaults.cardElevation(defaultElevation = 6.dp)` was retained on the outer card. In Compose Material 3, tonal elevation adds a color tint (`surfaceTint`) overlay on top of the surface, creating visible grey/tinted container rectangles in both light mode and dark/AMOLED modes.
* **Preservation of Intent**: Directional grouping, route selection, and HUD glanceability are preserved while visual contrast and sunlight legibility are restored.

---

## 2. Test Specification (TST-UI-297)

### 2.1 Test Categories & Verification Scope

| Test ID | Category | Target File | Verification Scope |
| :--- | :--- | :--- | :--- |
| **TST-UI-297.1** | Tonal Elevation & Container Tokens Contract | `ForkDecisionCardTest.kt` | Verify that `ForkDecisionCard.kt` specifies `tonalElevation = 0.dp` for the outer container and `tonalElevation = 0.dp` for `ForkDirectionGroup`. |
| **TST-UI-297.2** | Clean Surface & Zero surfaceVariant Contract | `ForkDecisionCardTest.kt` | Verify that `ForkDecisionCard.kt` uses `MaterialTheme.colorScheme.surface` and does not use `surfaceVariant` for container backgrounds. |
| **TST-UI-297.3** | Overlay Alpha Uniformity Contract | `ForkDecisionCardTest.kt` | Verify that `ForkDirectionGroup` accepts and applies `overlayAlpha` to maintain uniform transparency. |
| **TST-UI-297.4** | Clean-Room Full Suite Regression | Full Unit Test Suite | Execute `./gradlew testDebugUnitTest` asserting 100% pass rate with zero regressions. |

---

## 3. Acceptance Criteria (Given-When-Then)

### Scenario 1: Clean Pure White Surfaces in Light Mode
* **Given** an athlete navigating with candidate routes approaching a fork in Light Mode,
* **When** `ForkDecisionCard` is displayed,
* **Then** the outer container and nested direction groups render on pure white surfaces without grey elevation tinting.

### Scenario 2: Deep AMOLED Black in Dark/AMOLED Mode
* **Given** an athlete riding at night with Dark or AMOLED theme active,
* **When** `ForkDecisionCard` is displayed,
* **Then** all nested rows, direction groups, and container backgrounds render in pure black (`#000000`) without grey washed-out elevation overlays.

### Scenario 3: Uniform Translucency with overlayAlpha
* **Given** an active `overlayAlpha` setting (e.g. 0.85f),
* **When** `ForkDecisionCard` renders over the map or telemetry grid,
* **Then** the inner direction groups and the outer card container share the identical alpha opacity, avoiding opaque patches.

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-297.1` | Contract | `ForkDecisionCard.kt` | `REQ-UI-337` | Specified |
| `TST-UI-297.2` | Contract | `ForkDecisionCard.kt` | `REQ-UI-337` | Specified |
| `TST-UI-297.3` | Contract | `ForkDecisionCard.kt` | `REQ-UI-337` | Specified |
| `TST-UI-297.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
