# Stage 1 Analysis: ATT-1530 Remove 3-Dots Overflow Button and Optimize Card Header Actions

**Ticket**: [ATT-1530](https://atrainingtracker.atlassian.net/browse/ATT-1530)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.3`  
**Subtask**: `ATT-1531`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Domain & Background Analysis

In `KnownLocationsScreen.kt`, the favorite location card component (`KnownLocationCard`) historically included a 3-dots vertical overflow icon button (`Icons.Default.MoreVert`) in its top-right header area.

### Friction & Deficiencies:
1. **Application-Wide Inconsistency**: Standard list item cards across the application (e.g. `RouteItem`, `SegmentItem`, `DeviceItem`, `SportTypesTabsScreen`, `EquipmentTabsScreen`) do NOT display a 3-dots overflow button in their header. Their interaction model relies on direct tap for editing/details and long-press for item actions.
2. **Action Redundancy & Confusion**: Tapping the card directly opens `EditKnownLocationDialog`, and long-pressing opens the standardized deletion context menu. The 3-dots overflow button duplicated these action paths, presenting unnecessary choices to the athlete.
3. **Card Header Crowding**: The trailing `IconButton` occupied 48dp of touch target width in the header row, prematurely truncating long location names (e.g. "Olympiapark Olympiaberg Gipfelkreuz") with ellipses.

---

## 2. Chesterton's Fence & Archaeological Investigation

* **Historical Context**: The 3-dots button was introduced in early prototype implementations of `KnownLocationsScreen` before the universal card interaction standard (`REQ-UI-061`) was consolidated.
* **Invariant Preservation**:
  - Direct card tap (`onClick`) MUST open `EditKnownLocationDialog` (`onEdit`).
  - Long-press (`onLongClick`) MUST open the delete-only context menu (`REQ-UI-061`, `ATT-1523`).
  - Item title text MUST utilize the full available card width with `TextOverflow.Ellipsis`.
  - Prominent altitude metric, ascent icon, and number of starts badges MUST remain visible and properly aligned.

---

## 3. Scope Bounding (ATT-1250 Grounding)

* **In-Scope**:
  - Ensuring `KnownLocationCard` header renders strictly clean title text without any trailing 3-dots `IconButton`.
  - Allowing title text to occupy `Modifier.fillMaxWidth()` with single-line ellipsis.
  - Verifying UI test assertions in `KnownLocationsScreenTest.kt` confirming zero 3-dots button presence and valid click/long-click behavior.
* **Out-of-Scope**:
  - SQLite database schema modifications.
  - Geofence calculations or DEM elevation fetching.
  - Secondary map view modifications.

---

## 4. Architectural Call-Site Impact

* **`KnownLocationsScreen.kt`**:
  - `KnownLocationCard`: Header row verified to contain only `Text(text = item.name)` without trailing `IconButton(onClick = ...)` or `MoreVert` icon.
* **`KnownLocationsScreenTest.kt`**:
  - Test cases asserting card rendering, direct tap opening edit dialog, and long-press triggering delete menu.

---

## 5. Risk Assessment & Stage Gate 1 Readiness

* **Risk Level**: LOW. Pure Compose layout simplification.
* **Recommendation**: RECOMMEND PASS. Ready for Stage 2 (Requirement & Test Specification).
