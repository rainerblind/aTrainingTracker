# Stage 1 Analysis: ATT-2472 - Select one of the favorite locations as home-base for return navigation

**Ticket**: [ATT-2472](https://atrainingtracker.atlassian.net/browse/ATT-2472)  
**Sub-task**: [ATT-2586](https://atrainingtracker.atlassian.net/browse/ATT-2586) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-MAP-034` (Refines & Amends `REQ-MAP-029` Clause 1, interfaces with `REQ-UI-165`)  
**Branch**: `feature/ATT-2472`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Statement & Motivation

During Sprint 2026-40.16 (ATT-1953), "Take Me Home" return navigation was introduced (`REQ-MAP-029`). The system employed an automated heuristic in `HomeLocationResolver.kt` to identify the athlete's home destination:
1. Substring matching against names containing `"haus"`, `"home"`, or `"zuhause"`.
2. Fallback to the location with the highest start frequency (`hitCount`).

In real-world use across diverse European training routes, naive substring matching produced recurring false positives:
* Locations such as "Rathausplatz", "Gasthaus Löwen", "Schulhaus", or "Bootshaus" matched the substring `"haus"` and were erroneously designated as the athlete's home destination, overriding actual home bases.
* Athletes lacked any explicit UI control to specify which of their saved favorite locations (Lieblingsorte / Known Locations) is their true home-base.

The athlete requires explicit control in the favorite locations management UI to designate (and clear) a home-base, accompanied by a clear visual indicator in the list, while retaining a robust fallback to the most frequently visited start spot when no explicit home is set.

---

## 2. Root Cause Analysis (Forensic Investigation)

### A. Substring Heuristic Vulnerability (`HomeLocationResolver.kt:59-65`)
The existing resolution logic in `HomeLocationResolver.kt` evaluates:
```kotlin
val namedMatch = allLocations.firstOrNull { loc ->
    val name = loc.name?.trim()?.lowercase() ?: ""
    HOME_KEYWORDS.any { keyword -> name.contains(keyword) }
}
```
Because `HOME_KEYWORDS` contains `"haus"`, any compound German word containing "haus" (e.g. "Rathaus", "Gasthaus", "Forsthaus") matches unconditionally, regardless of hit count or user intention.

### B. Lack of Persistence for Explicit Home Selection
In `StartLocation2Altitude.db` (`KnownLocationsDatabaseManager.java` / `KnownLocationsDbHelper`), the table schema (v5) contains:
- `_id`, `name`, `extremumType`, `altitude`, `longitude`, `latitude`, `radius`, `hitCount`, `is_locked`, `source`.
There is no column representing user designation as Home Base (`is_home`).

### C. UI Presentation & Action Gaps
In `KnownLocationsScreen.kt` and `KnownLocationCard`:
- The location item card only exposes long-press context menu for "Delete".
- There is no badge, icon, or visual indicator indicating home-base status.
- `EditKnownLocationDialog.kt` allows editing name, altitude, and radius, but has no mechanism to toggle home designation.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Database Schema Upgrade (v6)**: Add `is_home integer default 0` to `StartLocation2Altitude.db` with clean SQLite migration (`ALTER TABLE StartLocation2Altitude ADD COLUMN is_home integer default 0;`).
  2. **Single Home Transactional Invariant**: Guarantee that at most one location has `is_home = 1` across the entire database via atomic SQLite transaction bundling a reset of existing flags with setting the new home ID.
  3. **Repository & Domain Integration**: Expose `isHome: Boolean` in `KnownLocationItem` and `MyLocation`, and provide reactive operations in `KnownLocationsRepository` and `KnownLocationsViewModel`.
  4. **Home Location Resolver Upgrade**:
     - Priority 1: Explicitly designated location where `isHome == true`.
     - Priority 2 (Graceful fallback): Location with highest `hitCount` (where `hitCount > 0`).
     - Priority 3: First available location if any exists.
     - Eliminate naive substring matching that caused "Rathaus"/"Gasthaus" false positives.
  5. **Visual Representation in Known Locations UI**:
     - Prominent Home icon and badge for the designated home location in `KnownLocationCard`.
     - Context menu action: "Als Heim-Basis festlegen" / "Heim-Basis aufheben".
     - Edit dialog switch/toggle: "Als Heim-Basis festlegen".
  6. **100% 9-Language Localization Parity**: Localize all new labels across EN, DE, ES, FR, IT, JA, NL, PL, PT.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Does not alter return corridor snapping (`ReturnCorridorSnapper.kt`), elevation-aware ETA calculations (`ElevationAwareEtaCalculator.kt`), or HUD rendering (`ReturnNavigationHud.kt`).
  2. Does not alter workout start altitude calibration or geofence auto-learning algorithms.

---

## 4. Requirement Traceability & Chesterton's Fence Audit

### A. Traceability Mapping
* **Primary Target Requirement**: `REQ-MAP-034` (*Designated Home-Base Selection for Return Navigation & Substring Disambiguation*).
* **Refined & Amended Requirement**: Amends Clause 1 of `REQ-MAP-029` (*"Take Me Home" Return Navigation, Remaining Distance & Elevation-Aware ETA HUD*).
* **Interfacing Requirements**:
  - `REQ-UI-165` (*Single-Perspective Known Locations List & Management UI*).
  - `REQ-DAT-007` (*KnownLocationsDatabaseManager SQLite Schema & Thread Confinement*).

### B. Chesterton's Fence Archaeology
* **Original Requirement ID & Target**: `REQ-MAP-029` Clause 1 (*Home Base Resolution*).
* **Historical Origin & Commit Trace**: Ticket `ATT-1953`, Sprint `2026-40.16`, commit `0994f794`.
* **Root Reason for Existing Formulation**: In ATT-1953, the home resolver was created to support instant "Take Me Home" functionality without requiring a configuration UI upfront. Heuristic substring matching on "haus"/"home" was used as a rapid expedient.
* **Preservation of Core Invariants**:
  - Full backward compatibility for existing `StartLocation2Altitude.db` files via standard `onUpgrade` (v5 -> v6).
  - Graceful fallback: when no location has been explicitly designated by the user, the resolver seamlessly falls back to highest `hitCount`, preserving functionality for users who do not configure an explicit home.
  - Return navigation HUD, reverse route travel, and 100% test pass rate remain completely intact.

---

## 5. Comprehensive Call-Site Inventory

| Component / File | Member / Method | Architectural Role & Modification |
| :--- | :--- | :--- |
| `KnownLocationsDbHelper` in `KnownLocationsDatabaseManager.java` | `DB_VERSION`, `onCreate`, `onUpgrade` | Increment DB_VERSION to 6; add `IS_HOME = "is_home"` column (`integer default 0`). |
| `KnownLocationsDatabaseManager.java` | `setHomeLocation(long id)`, `clearHomeLocation()`, `getHomeLocation()` | Executes atomic transactional update ensuring single-home exclusivity; queries designated home. |
| `KnownLocationsDatabaseManager.java` | `cursorToMyLocation(Cursor)` | Reads `is_home` column (mapping 1 -> true, 0 -> false) into `MyLocation`. |
| `KnownLocationsDatabaseManager.java` | `MyLocation` class constructor | Adds `boolean isHome` field with constructor overloads for backward compatibility. |
| `KnownLocationItem` in `KnownLocationsRepository.kt` | Data class definition | Adds `val isHome: Boolean = false`. |
| `KnownLocationsRepository.kt` | `loadLocations()`, `setHomeLocation(id)`, `clearHomeLocation()` | Dispatches DB operations onto `KnownLocationsDB-Thread` and emits updated `locationsFlow`. |
| `KnownLocationsViewModel.kt` | `setHomeLocation(id: Long)`, `clearHomeLocation()` | Exposes ViewModel actions launched in `viewModelScope`. |
| `HomeLocationResolver.kt` | `resolveHomeLocation(manager: KnownLocationsDatabaseManager)` | Prioritizes `isHome == true`, then `hitCount > 0`, eliminating substring false positives. |
| `ReturnNavigationRepository.kt` | `startTakeMeHome()`, `recalculateNavigationMetrics()` | Consumer of `HomeLocationResolver.resolveHomeLocation()`. Uses updated resolver seamlessly. |
| `KnownLocationCard` in `KnownLocationsScreen.kt` | Composable card & context menu | Renders Home badge and title icon when `isHome == true`; provides "Set as Home Base" in context menu. |
| `EditKnownLocationDialog.kt` | Composable dialog | Provides a toggle switch for designating/un-designating location as Home Base. |
| `strings.xml` (all 9 locales) | Resource keys | Defines localized strings for home badge, menu items, and edit dialog toggle. |

---

## 6. Architectural Strategy & Detailed Technical Solution

### A. Database Transactional Exclusivity
To prevent any race conditions or multi-home data anomalies, setting a home-base is executed inside an exclusive SQLite transaction block:
```java
public void setHomeLocation(long id) {
    SQLiteDatabase db = getDatabase();
    db.beginTransaction();
    try {
        // Step 1: Clear is_home on all existing rows
        ContentValues clearValues = new ContentValues();
        clearValues.put(KnownLocationsDbHelper.IS_HOME, 0);
        db.update(KnownLocationsDbHelper.TABLE, clearValues, null, null);

        // Step 2: Set is_home = 1 exclusively for the target id
        if (id > 0) {
            ContentValues setValues = new ContentValues();
            setValues.put(KnownLocationsDbHelper.IS_HOME, 1);
            db.update(KnownLocationsDbHelper.TABLE, setValues, 
                      KnownLocationsDbHelper.C_ID + "=?", 
                      new String[]{String.valueOf(id)});
        }
        db.setTransactionSuccessful();
    } finally {
        db.endTransaction();
    }
}
```

### B. Resolution Algorithm
```kotlin
@JvmStatic
fun resolveHomeLocation(knownLocationsManager: KnownLocationsDatabaseManager): HomeDestination? {
    val allLocations: List<MyLocation> = try {
        knownLocationsManager.allLocations ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    if (allLocations.isEmpty()) return null

    // 1. Explicit user designation in database
    val designated = allLocations.firstOrNull { it.isHome }
    if (designated != null) {
        return toHomeDestination(designated)
    }

    // 2. Fallback to highest hitCount among recorded start spots (hitCount > 0)
    val highestHit = allLocations.filter { it.hitCount > 0 }.maxByOrNull { it.hitCount }
    if (highestHit != null) {
        return toHomeDestination(highestHit)
    }

    // 3. Absolute fallback to first available location
    return toHomeDestination(allLocations.first())
}
```

---

## 7. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Thread Confinement**: All database mutations strictly dispatched on `KnownLocationsDB-Thread`.
  2. **Single-Home Exclusivity**: Exactly zero or one location can have `is_home = 1` at any time.
  3. **Non-Breaking Migration**: Database upgrade preserves all existing coordinates, altitudes, locks, and hit counts.
  4. **100% Clean-Room Test Suite Pass Rate**.
* **Risk Rating**: **LOW**
  - Additive column with default 0.
  - Transparent backward compatibility.
