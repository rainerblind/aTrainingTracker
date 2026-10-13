# Stage 5: Walkthrough & Verification - ATT-2763: Make Climbs, Segments, and Waypoints Toggleable on Route Map with Layers Menu while Anchoring Route Line

**Ticket**: [ATT-2763](https://atrainingtracker.atlassian.net/browse/ATT-2763)  
**Sub-task**: [ATT-2836](https://atrainingtracker.atlassian.net/browse/ATT-2836) (`[Test]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-308` (*Route Map Overlay Layers Control, Entity Breakdown Item Toggles, and Unconditional Base Route Anchor*)  
**Test Mapping**: `TST-UI-268` (*Route Map Overlay Layers Menu, Item-Level Breakdown Toggles, Base Route Invariant, and Localization Parity Verification*)  
**Branch**: `improvement/ATT-2763`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary & Verification Overview

This improvement addresses visual clutter and overlapping map elements in `RouteOnMapScreen.kt` when viewing complex routes with multiple climb highlights, matched Strava segments, and waypoints/POIs.

### Core Capabilities Implemented:
1. **Permanent Selected Route Invariant**:
   - The selected route polyline (`TTColor.RouteSelected`) remains permanently anchored and rendered unconditionally in `mapContent`. It cannot be toggled off or hidden by any layer setting.
2. **Top App Bar Map Layers Menu**:
   - Added a `Layers` button (`Icons.Default.Layers`) to `RouteOnMapScreen` top bar, opening a styled dropdown menu matching `TrackOnMapScreen.kt` (Rule 23 UI Consistency).
   - Dropdown options:
     - ☑️ **Climbs** (`route_layer_climbs` with climb category color indicator) toggles all climb span polylines and summit markers.
     - ☑️ **Segments** (`route_layer_segments` with Strava orange indicator) toggles all matched segment polylines.
     - ☑️ **Waypoints** (`route_layer_waypoints` with primary waypoint pin indicator) toggles intermediate waypoint markers.
3. **Item-Level Segment Visibility Toggle**:
   - In `RouteSegmentsBreakdownSection.kt`, each matched segment card features an individual eye toggle button (`Icons.Default.Visibility` / `Icons.Default.VisibilityOff`) enabling granular decluttering.
   - Clicking the card body retains segment map bounds centering and detail sheet invocation, while clicking the visibility icon exclusively toggles its map display.
4. **9-Language Complete Localization**:
   - Added strings across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, and `values-pt/`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-308` | `TST-UI-268.1` | Contract Test: Permanent Selected Route Anchor Invariant | **PASSED** | `Verified` |
| `REQ-UI-308` | `TST-UI-268.2` | Contract Test: Top App Bar Layers Dropdown Menu & Swatches | **PASSED** | `Verified` |
| `REQ-UI-308` | `TST-UI-268.3` | Contract Test: Layer Filter Logic for Climbs, Segments, Waypoints | **PASSED** | `Verified` |
| `REQ-UI-308` | `TST-UI-268.4` | Contract Test: Item-Level Breakdown Segment Visibility Toggle | **PASSED** | `Verified` |
| `REQ-UI-308` | `TST-UI-268.5` | Localization Test: 9-Language Completeness & Parity Audit | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Contract Tests
```text
RouteOverlayLayersContractTest > testRouteOverlayLayer_enumHasExpectedValuesAndColors PASSED
RouteOverlayLayersContractTest > testRouteOnMapScreen_definesLayerMenuAndOverlaySlots PASSED
RouteOverlayLayersContractTest > testRouteSegmentsBreakdownSection_hasItemVisibilityToggle PASSED
RouteOverlayLayersContractTest > testRouteOnMapScreen_anchorsSelectedRouteUnconditionally PASSED
RouteOverlayLayersContractTest > testAccessibility_tagsAndContentDescriptionsArePresent PASSED

RouteLayersLocalizationTest > testRouteLayersStrings_haveCompleteParityAcrossAll9Locales PASSED
```

### Clean-Room Regression Suite (`./gradlew clean testDebugUnitTest`)
- Executed on `improvement/ATT-2763`
- Result: **BUILD SUCCESSFUL** (0 failures, 100% pass rate across the full test suite).

---

## 4. Hardware / Physical Verification (Pixel 10)

| Inspection Point | Pixel 10 Physical / Display Verification | Status |
| :--- | :--- | :--- |
| **Top App Bar Action** | Layers icon button is clearly displayed next to Share in the top app bar with minimum 48dp touch target. | **VERIFIED** |
| **Dropdown Menu Rendering** | Tapping Layers icon displays dropdown menu with Climbs, Segments, and Waypoints with checkbox toggles and color chips. | **VERIFIED** |
| **Route Line Anchor** | Unchecking all layers leaves the cyan/blue base route line intact and clearly visible. | **VERIFIED** |
| **Breakdown Visibility Toggle** | Eye icon button on segment cards toggles visibility without triggering card-click detail modal. | **VERIFIED** |
| **Theme & Dark Mode** | Fully respects dark mode and MaterialTheme surface/onSurface tokens. | **VERIFIED** |

### Visual Consistency (Rule 23)
* **Reference UI Screen**: `TrackOnMapScreen.kt` (lines 235–280).
* **Styling Alignment**: Spacing (8dp padding, 8dp element gap), typography (`MaterialTheme.typography.bodyMedium`), checkbox styling, and 12dp rounded color swatch circles match `TrackOnMapScreen.kt` 1:1.

---

## 5. Invariant & Governance Verification

1. **Chesterton's Fence & Boundary Invariants**:
   - `RouteOnMapScreen.kt` base polyline rendering (`TTColor.RouteSelected`) is completely decoupled from layer filters.
   - Elevation profile scrubbing distance marker and map coordinate tracking are uninhibited.
2. **Living Documentation Synchronization**:
   - `REQ-UI-308` in `docs/requirements.md` set to `Verified`.
   - `TST-UI-268` in `docs/tests.md` set to `Verified`.
   - Requirement governance script `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-41.4` cleanly passes.

---

## 6. Git & Audit Trail
* Feature Branch: `improvement/ATT-2763`
* Stage 1 Analysis: commit `509a25b1`
* Stage 2 Spec: commit `7e8b61e8`
* Stage 3 Plan: commit `b40d6bc9`
* Stage 4 Implementation: commit `72a6b925`
* Stage 5 Deliverables: committed on branch `improvement/ATT-2763` and integrated into `sprint/2026-41.4`.
