# Architectural & Problem Analysis - ATT-919: Known Start Locations UI & Manual Altitude Lock

## 1. Executive Summary & Problem Statement

* **Issue Key**: `ATT-919` / `ATT-1377`
* **Parent Issue**: `ATT-919` (*[Feature] Altitude Correction: Create UI for this DB such that the user can set the correct altitude that is then never ever updated*)
* **Milestone Cluster**: Stage 3 of 3 in the Altitude Calibration Cluster (`ATT-1278` -> `ATT-1366` -> `ATT-919`)
* **Target Version**: `V4.9.38` (Sprint `2026-39.2`)
* **Associated Requirements**: `REQ-UI-165` (Known Start Locations Management & Altitude Lock UI), `REQ-DAT-007`, `REQ-DAT-014`

### Problem Statement
`KnownLocationsDatabaseManager` maintains geographical start locations in `StartLocation2Altitude.db` (Schema V5) to calibrate hardware barometric altimeters upon workout start. With `ATT-1278` (Internet DEM ground truth) and `ATT-1366` (elimination of flawed running mean altitude drift), the underlying data layer is robust, thread-safe, and capable of distinguishing between `INTERNET_DEM`, `MANUAL_USER`, `AUTO_LEARNED`, `GPS_FALLBACK`, and `LEGACY_RAW` elevations, as well as honoring `is_locked` write-protection.

However, **there is currently zero user interface** for this database in the application:
1. **Zero Visibility**: The user cannot view stored locations, their reference altitudes, the source of elevation data, or the number of workouts initiated from them.
2. **No Manual Calibration**: If a user knows their true start altitude (e.g. from an official cadastral benchmark, building elevation plan, or calibrated survey instrument), they cannot manually enter it.
3. **No User Locking**: The user cannot mark a location as write-protected (`is_locked = 1`), preventing them from asserting authority over their reference points.
4. **No Spatial Context**: Users cannot visually inspect the 200m spatial geofence circles on a map to see which areas surrounding their home, trailheads, or tracks are covered by calibration points.
5. **No Data Housekeeping**: Obsolete, misplaced, or redundant auto-discovered start locations cannot be renamed or deleted.

---

## 2. User Experience & UI/UX Architecture

To maintain consistency with existing high-density Jetpack Compose screens (e.g. `RoutesScreen`, `WorkoutClustersScreen`, `StarredSegmentsScreen`), the Known Locations UI will be implemented as a full-featured screen accessible from the Navigation Drawer.

### A. Navigation Architecture
* **Drawer Entry Point**: Added under the `drawer__maps` group in [AppNavigationDrawer.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt):
  - ID: `R.id.drawer_start_locations`
  - Route: `NavRoutes.START_LOCATIONS` (`"start_locations"`)
  - Icon: `R.drawable.ic_map_marker_check` (or `ic_my_location`)
  - Localized Title: `R.string.drawer_start_locations` (*"Start Locations"* / *"Startorte"*)
* **Single-Activity Integration**: Integrated into [ATrainingTrackerApp.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt) `NavHost` composable routing.

### B. Screen Structure (`KnownLocationsScreen.kt`)
A top-level tabbed container offering two synchronized perspectives:
1. **List Tab**:
   - Header with search/filter bar and total location count badge.
   - High-density Material 3 cards for each start location:
     - **Location Name**: Primary title (e.g., *"Home"*, *"Trailhead North"*, *"Internet DEM start"*) with edit shortcut.
     - **Elevation & Unit**: Formatted in meters (`m`) or feet (`ft`) according to user preference (`MyPreferenceManager.getUnitSystem()`).
     - **Provenance Badge**: Chip indicating source (`DEM`, `Manual`, `Auto`, `GPS`, `Legacy`).
     - **Hit Count Badge**: Number of recordings started at this location (e.g., *"42 Starts"*).
     - **Coordinates**: Quantized latitude and longitude ($\approx 5$ decimal places).
     - **Fast Lock Switch**: Instant toggle for `is_locked`. When locked, altitude is permanently protected.
     - **Action Menu (Overflow or Row)**: Edit details, Re-query DEM from Internet, Delete.
2. **Map Tab & Scalability Guardrails**:
   - Google Map displaying start locations as pins/markers with 200m circular geofence overlays.
   - **Performance Guardrails for Large Datasets**:
     - Viewport Bounds Culling: The map query filters markers by active camera bounding box (`visibleRegion.latLngBounds`).
     - Maximum Screen Density Limit: At zoomed-out camera levels where hundreds of locations exist, markers are clustered or capped at 100 on-screen elements to prevent UI thread frame drops.
   - Marker color coding:
     - Green / Primary: Locked manual or authoritative DEM locations (`is_locked = 1`).
     - Cyan / Secondary: Authoritative unlocked DEM locations (`INTERNET_DEM`).
     - Amber / Muted: Uncalibrated or fallback locations (`AUTO_LEARNED`, `GPS_FALLBACK`, `LEGACY_RAW`).
   - Tapping a marker centers the camera and opens a bottom sheet or selection card with quick actions (Edit, Lock, Delete).
3. **Action Triggers**:
   - **Floating Action Button (FAB)**: *"Add Location"* allows picking a point on the map or using current GPS coordinates.
4. **Edit / Manual Calibration Dialog**:
   - Name input field.
   - Reference altitude number input (with current units: m / ft).
   - Lock switch (*"Lock reference altitude against automatic changes"*).
   - *"Fetch DEM Elevation"* button to auto-populate ground truth from Open-Meteo on demand.
   - Save and Cancel actions.

---

## 3. Data Layer, Concurrency & Reactive Architecture

### A. Dedicated Single-Thread SQLite Coroutine Dispatcher
* **Thread-Confinement & Serialization**:
  - To prevent deadlocks and race conditions between foreground Kotlin Coroutines and background sensor callbacks (`AltitudeFromPressureDevice`), all database queries, mutations, and transactions in `KnownLocationsRepository` are confined to a dedicated single-threaded dispatcher:
    ```kotlin
    val databaseDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "KnownLocationsDB-Thread").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    ```
  - This eliminates Java `synchronized(this)` lock contention across arbitrary coroutine worker threads.
  - Background sensor updates and UI CRUD operations are serialized deterministically through this single-threaded pipeline.
  - Changes atomically update a `MutableStateFlow<List<MyLocation>>`, which Compose screens collect as state without risk of TOCTOU race conditions.

### B. Unit Conversion Precision & Round-Trip Invariants
* **SI Storage Canonical Invariant**: All elevations in `StartLocation2Altitude.db` MUST be stored strictly in meters (`Double`).
* **Conversion Invariant**:
  - $1\text{ ft} = 0.3048\text{ m}$ ($1\text{ m} \approx 3.28084\text{ ft}$).
  - When displaying in Imperial units, display elevation is rounded to the nearest integer foot:
    $$\text{alt}_{\text{ft}} = \text{round}(\text{alt}_{\text{m}} \times 3.280839895013123)$$
  - When a user enters an altitude in feet ($h_{\text{ft}}$), the conversion back to meters MUST be rounded to 1 decimal place ($0.1\text{m}$ precision):
    $$\text{alt}_{\text{m}} = \text{round}(h_{\text{ft}} \times 0.3048 \times 10.0) / 10.0$$
  - When a user enters an altitude in meters ($h_{\text{m}}$), it is rounded to 1 decimal place ($0.1\text{m}$ precision).
  - This eliminates floating-point drift and sub-meter precision loss across repeated edit-save round-trips.

### C. ViewModel Layer (`KnownLocationsViewModel.kt`)
* Manages UI state:
  - `locations`: `StateFlow<List<MyLocation>>` of start locations.
  - `selectedLocation`: Currently focused location for edit or map inspection.
  - `isEditDialogOpen`: Boolean state controlling modal edit dialog.
  - `isFetchingDem`: Loading state during on-demand DEM retrieval.
  - `currentTab`: Active tab (List vs Map).
  - `searchQuery`: Filter query for location names.

---

## 4. Preservation of System Invariants & Non-Target Features

1. **Schema V5 Integrity**: No modifications to SQLite table schemas or column definitions (`is_locked` and `source` were already established in Schema V5 under ATT-1278).
2. **Sensor Calibration Invariants**: `AltitudeFromPressureDevice.java` will continue reading from `KnownLocationsDatabaseManager` without alterations to calibration logic.
3. **Immutability Contract**: Any location saved with `is_locked = true` and `source = MANUAL_USER` is strictly immutable against background batch healing (`healLegacyLocations`) and runtime discovery (`learnLocation`).
4. **Hit Count Invariant**: Locations continue to increment `hitCount` on repeated workout starts regardless of `is_locked` state.

---

## 5. Exhaustive 9-Language Localization Specification

To satisfy static localization linting and ASPICE compliance, all newly introduced strings are completely defined across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`):

### Table of Localized Strings:

| String Resource Key | English (`values`) | German (`values-de`) | Spanish (`values-es`) | French (`values-fr`) | Italian (`values-it`) | Japanese (`values-ja`) | Dutch (`values-nl`) | Polish (`values-pl`) | Portuguese (`values-pt`) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `drawer_start_locations` | Start Locations | Startorte | Ubicaciones de inicio | Lieux de départ | Posizioni di partenza | スタート地点 | Startlocaties | Lokalizacje początkowe | Locais de início |
| `loc_tab_list` | List | Liste | Lista | Liste | Elenco | リスト | Lijst | Lista | Lista |
| `loc_tab_map` | Map | Karte | Mapa | Carte | Mappa | マップ | Kaart | Mapa | Mapa |
| `loc_empty_list` | No start locations recorded yet | Noch keine Startorte erfasst | Aún no hay ubicaciones de inicio registradas | Aucun lieu de départ enregistré | Nessuna posizione di partenza registrata | スタート地点はまだ記録されていません | Nog geen startlocaties vastgelegd | Brak zarejestrowanych lokalizacji początkowych | Nenhum local de início registrado |
| `loc_starts_count` | %d starts | %d Starts | %d inicios | %d départs | %d partenze | %d回スタート | %d starts | %d startów | %d partidas |
| `loc_badge_dem` | DEM | DEM | DEM | DEM | DEM | DEM | DEM | DEM | DEM |
| `loc_badge_manual` | Manual | Manuell | Manual | Manuel | Manuale | 手動 | Handmatig | Ręczny | Manual |
| `loc_badge_auto` | Auto | Auto | Auto | Auto | Auto | 自動 | Auto | Auto | Auto |
| `loc_badge_gps` | GPS | GPS | GPS | GPS | GPS | GPS | GPS | GPS | GPS |
| `loc_badge_legacy` | Legacy | Legacy | Heredado | Hérité | Legacy | レガシー | Legacy | Starszy | Legado |
| `loc_locked_tooltip` | Altitude write-protected | Höhe schreibgeschützt | Altitud protegida | Altitude protégée | Altitudine protetta | 高度は保護されています | Hoogte beveiligd | Wysokość chroniona | Altitude protegida |
| `loc_unlocked_tooltip` | Altitude unlocked | Höhe entsperrt | Altitud desbloqueada | Altitude déverrouillée | Altitudine sbloccata | 高度は未保護です | Hoogte ontgrendeld | Wysokość odblokowana | Altitude desbloqueada |
| `loc_edit_title` | Edit Start Location | Startort bearbeiten | Editar ubicación de inicio | Modifier le lieu de départ | Modifica posizione di partenza | スタート地点を編集 | Startlocatie bewerken | Edytuj lokalizację początkową | Editar local de início |
| `loc_name_label` | Location Name | Name des Ortes | Nombre de la ubicación | Nom du lieu | Nome della posizione | 地点名 | Naam locatie | Nazwa lokalizacji | Nome do local |
| `loc_altitude_label` | Reference Altitude | Referenzhöhe | Altitud de referencia | Altitude de référence | Altitudine di riferimento | 基準高度 | Referentiehoogte | Wysokość referencyjna | Altitude de referência |
| `loc_lock_checkbox` | Lock altitude against automatic changes | Höhe vor automatischen Änderungen sperren | Bloquear altitud contra cambios automáticos | Verrouiller l'altitude contre les modifications automatiques | Blocca altitudine da modifiche automatiche | 高度を自動変更から保護してロック | Hoogte vergrendelen tegen automatische wijzigingen | Zablokuj wysokość przed automatycznymi zmianami | Bloquear altitude contra alterações automáticas |
| `loc_btn_fetch_dem` | Fetch from Internet (DEM) | Aus Internet laden (DEM) | Obtener de Internet (DEM) | Récupérer d'Internet (DEM) | Scarica da Internet (DEM) | インターネットから取得 (DEM) | Ophalen van internet (DEM) | Pobierz z Internetu (DEM) | Obter da Internet (DEM) |
| `loc_delete_title` | Delete Start Location | Startort löschen | Eliminar ubicación de inicio | Supprimer le lieu de départ | Elimina posizione di partenza | スタート地点を削除 | Startlocatie verwijderen | Usuń lokalizację początkową | Excluir local de início |
| `loc_delete_confirm` | Delete \"%s\"? This cannot be undone. | „%s“ löschen? Dies kann nicht rückgängig gemacht werden. | ¿Eliminar \"%s\"? Esta acción no se puede deshacer. | Supprimer « %s » ? Cette action est irréversible. | Eliminare \"%s\"? L'operazione non può essere annullata. | 「%s」を削除しますか？この操作は取り消せません。 | \"%s\" verwijderen? Dit kan niet ongedaan worden gemaakt. | Usunąć „%s”? Tej operacji nie można cofnąć. | Excluir \"%s\"? Isso não pode ser desfeito. |
| `loc_add_location` | Add Location | Ort hinzufügen | Añadir ubicación | Ajouter un lieu | Aggiungi posizione | 地点を追加 | Locatie toevoegen | Dodaj lokalizację | Adicionar local |

---

## 6. ASPICE Traceability & Next Steps

* **Requirement**: `REQ-UI-165` (to be synchronized in `docs/requirements.md` during Stage 2).
* **Verification Test Specification**: `TST-UI-117` (to be specified in `docs/tests.md` during Stage 2).
* **Sub-task Transition**: Upon approval of this Stage 1 analysis, proceed to Stage 2 (`[Test-Spec]`).
