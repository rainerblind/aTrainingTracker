# Stage 3: Implementation Plan - ATT-2472: Select one of the favorite locations as home-base for return navigation

**Ticket**: [ATT-2472](https://atrainingtracker.atlassian.net/browse/ATT-2472)  
**Sub-task**: [ATT-2588](https://atrainingtracker.atlassian.net/browse/ATT-2588) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-MAP-034` (Refines & Amends `REQ-MAP-029` Clause 1, interfaces with `REQ-UI-165`)  
**Test Mapping**: `TST-MAP-036`  
**Branch**: `feature/ATT-2472`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Description & Background

In ATT-1953, "Take Me Home" return navigation was introduced (`REQ-MAP-029`). The system deduced the home destination via keyword substring matching (`"haus"`, `"home"`, `"zuhause"`). This produced frequent false positives (e.g. "Rathausplatz" or "Gasthaus" matching "haus" and overriding true home locations). Athletes need explicit control in favorite locations management to designate one location as their home-base, with clear visual indicator badges, while retaining a robust fallback to highest start frequency when no explicit home is configured.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-034` (*Designated Home-Base Selection for Return Navigation & Substring Disambiguation*)
* **Test Mapping**: `TST-MAP-036` (*Designated Home-Base Selection, Database Migration & Substring Disambiguation Verification*)
  - `TST-MAP-036.1`: Database Migration & Transactional Exclusivity (`KnownLocationsDatabaseManagerHomeTest.kt`)
  - `TST-MAP-036.2`: Home Location Resolution & False Positive Prevention (`HomeLocationResolverTest.kt`)
  - `TST-MAP-036.3`: Repository Reactive StateFlow Tests (`KnownLocationsRepositoryTest.kt`)
  - `TST-MAP-036.4`: UI & Dialog Contract Tests (`KnownLocationHomeContractTest.kt`)
  - `TST-MAP-036.5`: 9-Language Localization Parity Audit (`TranslationParityTest.kt`)
  - `TST-MAP-036.6`: Full Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Single-Home Transactional Exclusivity**: Setting a location as home atomically resets all other rows (`is_home = 0`) and flags the target row (`is_home = 1`) inside an exclusive SQLite transaction, preventing multi-home state.
2. **Thread Confinement**: All database queries and updates are dispatched strictly onto `KnownLocationsDB-Thread` (`dbDispatcher`).
3. **Graceful Backward-Compatible Fallback**: If no location is explicitly designated as home, the resolution engine seamlessly falls back to the location with the highest `hitCount` (where `hitCount > 0`), ensuring unconfigured users experience zero regressions.
4. **Database Non-Breaking Schema Upgrade**: Table upgrade to v6 is additive (`ALTER TABLE StartLocation2Altitude ADD COLUMN is_home integer default 0;`) preserving all existing entries, locks, and coordinate data.
5. **Human Decision Gate**: Parent ticket transitions to `Final Review (Human)` and assignment to `human` remain strictly enforced.

---

## 4. Proposed Architectural Changes

### Component 1: SQLite Database Engine (`KnownLocationsDatabaseManager.java`)
- Upgrade `KnownLocationsDbHelper.DB_VERSION` from 5 to 6.
- Add column `IS_HOME = "is_home"` (`integer default 0`).
- Update `onCreate` and `onUpgrade` to include `is_home`.
- Implement `setHomeLocation(long id)` with an atomic transaction:
  ```java
  public void setHomeLocation(long id) {
      SQLiteDatabase db = getDatabase();
      db.beginTransaction();
      try {
          ContentValues clear = new ContentValues();
          clear.put(KnownLocationsDbHelper.IS_HOME, 0);
          db.update(KnownLocationsDbHelper.TABLE, clear, null, null);
          if (id > 0) {
              ContentValues set = new ContentValues();
              set.put(KnownLocationsDbHelper.IS_HOME, 1);
              db.update(KnownLocationsDbHelper.TABLE, set, KnownLocationsDbHelper.C_ID + "=?", new String[]{String.valueOf(id)});
          }
          db.setTransactionSuccessful();
      } finally {
          db.endTransaction();
      }
  }
  ```
- Implement `clearHomeLocation()` (delegates to `setHomeLocation(0)`).
- Implement `getHomeLocation(): MyLocation?` querying `is_home = 1`.
- Update `MyLocation` and `cursorToMyLocation` to parse `isHome`.

### Component 2: Repository & Domain (`KnownLocationsRepository.kt`, `KnownLocationsViewModel.kt`)
- Add `val isHome: Boolean = false` to `KnownLocationItem`.
- In `KnownLocationsRepository`: add `setHomeLocation(id: Long)` and `clearHomeLocation()`, re-querying `loadLocations()`.
- In `KnownLocationsViewModel`: expose `setHomeLocation(id: Long)` and `clearHomeLocation()`.

### Component 3: Resolution Engine (`HomeLocationResolver.kt`)
- Update `resolveHomeLocation(manager: KnownLocationsDatabaseManager)`:
  1. `allLocations.firstOrNull { it.isHome }?.let { return toHomeDestination(it) }`
  2. `allLocations.filter { it.hitCount > 0 }.maxByOrNull { it.hitCount }?.let { return toHomeDestination(it) }`
  3. `allLocations.firstOrNull()?.let { return toHomeDestination(it) }`
  4. Return `null` if database is empty.
  - Naive substring keywords (`"haus"`, `"home"`, `"zuhause"`) matching is removed.

### Component 4: Known Locations UI (`KnownLocationsScreen.kt`, `EditKnownLocationDialog.kt`)
- `KnownLocationCard`:
  - When `item.isHome == true`, render `Icon(Icons.Default.Home, tint = MaterialTheme.colorScheme.primary)` next to title.
  - In badges row, render dedicated Home Base Badge:
    ```kotlin
    if (item.isHome) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.testTag("location_home_badge_${item.id}")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.known_locations_home_badge),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
    ```
  - Context menu: Add "Als Heim-Basis festlegen" (or "Heim-Basis aufheben" if already home).
- `EditKnownLocationDialog`: Add Home Base toggle switch row allowing direct toggle during editing.

### UI Consistency (Rule 23)
* **Reference screen / component**: `KnownLocationsScreen.kt` (`KnownLocationCard` starts badge, routes badge).
* **Reused components**: `Surface`, `RoundedCornerShape(8.dp)`, `BorderStroke`, `DropdownMenuItem`, `Switch`, `Icons.Default.Home`.
* **Theme tokens**: shapes `RoundedCornerShape(8.dp)`, spacing `horizontal = 8.dp, vertical = 4.dp`, colors `MaterialTheme.colorScheme.primaryContainer`, `onPrimaryContainer`, `primary`.
* **New one-off styles & justification**: None. Reuses established badge and card design tokens from `KnownLocationsScreen.kt`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Language Localization Parity
* Files: `app/src/main/res/values*/strings.xml` (all 9 locales: values/, values-de/, values-es/, values-fr/, values-it/, values-ja/, values-nl/, values-pl/, values-pt/)
* Keys:
  - `known_locations_home_base`
  - `known_locations_home_badge`
  - `known_locations_set_home`
  - `known_locations_remove_home`

### Step 2: SQLite Schema Upgrade v6 & Transactional Exclusivity
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManager.java`
* Changes:
  - Add `IS_HOME = "is_home"`.
  - Increment `DB_VERSION = 6`.
  - Implement `setHomeLocation(long id)`, `clearHomeLocation()`, `getHomeLocation()`.
  - Update `MyLocation` and `cursorToMyLocation`.

### Step 3: Domain & Repository Integration
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/KnownLocationsRepository.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModel.kt`
* Changes:
  - Add `val isHome: Boolean = false` to `KnownLocationItem`.
  - Expose `setHomeLocation` and `clearHomeLocation` in repository and ViewModel.

### Step 4: Resolution Engine Upgrade
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/HomeLocationResolver.kt`
* Changes:
  - Refine priority ordering: 1) `isHome`, 2) `hitCount > 0`, 3) `first()`. Remove naive substring keyword matching.

### Step 5: Presentation Layer (Card Badges & Dialog Switch)
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/EditKnownLocationDialog.kt`
* Changes:
  - Add Home icon and Home badge to `KnownLocationCard`.
  - Add context menu actions to set/clear home.
  - Add toggle switch row to `EditKnownLocationDialog`.

### Step 6: Automated Test Verification
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManagerHomeTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/routes/HomeLocationResolverTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationHomeContractTest.kt`
* Run:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManagerHomeTest" --tests "com.atrainingtracker.trainingtracker.routes.HomeLocationResolverTest" --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationHomeContractTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests verifying single-home exclusivity, false positive prevention, and UI contract compliance, followed by full clean-room suite in Stage 5.
* **Rollback**: Branch isolation (`feature/ATT-2472`) guarantees clean rollback without touching `sprint/2026-41.1` or `develop`.
