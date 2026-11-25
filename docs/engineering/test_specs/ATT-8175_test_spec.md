# Stage 2 Requirement & Test Specification — ATT-8175: Mapsforge Vector `TileProvider` & Offline Base-Map Mode

- **Ticket**: `ATT-8175` (`5 SP`, Feature, `tier-a`, Epic `ATT-8171`) | **Target Release**: `V5.0.0`
- **Living-Doc Mapping**: `REQ-MAP-071` (`[Planned]`, `SYSREQ-TRK-004`, `SYSREQ-TRK-009`) <-> `TST-MAP-074` (`[Planned]`)
- **Localization Parity**: All 9 locales (`values*/strings_offline_maps.xml`: `en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`)

## 1. Formal Requirement Specification (`REQ-MAP-071`)

- **Priority**: High | **Type**: Functional | **Verification Method**: Unit, Integration, On-Device Manual

1. **Mapsforge `0.25.0` Dependencies, ProGuard Rules & Safe `AndroidGraphicFactory` Init (`REQ-MAP-071.1`)**:
   - `app/build.gradle` SHALL declare `org.mapsforge:{mapsforge-core,mapsforge-map,mapsforge-map-reader,mapsforge-map-android,mapsforge-themes}:0.25.0` (`LGPLv3`) and `com.caverock:androidsvg:1.4`, explicitly excluding `net.sf.kxml:kxml2` and `xmlpull:xmlpull` on `implementation` while retaining `testImplementation 'net.sf.kxml:kxml2:2.3.0'` to prevent AGP/R8 `XmlPullParser` duplicate-class conflicts.
   - `app/proguard-rules.pro` SHALL keep `org.mapsforge.**` and `com.caverock.androidsvg.**` (verified by `ProGuardRulesContractTest` alongside `MapBaseSource` enum reflection safety).
   - `TrainingApplication.onCreate()` SHALL invoke `OfflineMapGraphicInitializer.initialize(this)`, which MUST guard `Build.VERSION.SDK_INT > 0`, execute `AndroidGraphicFactory.createInstance(application)` at most once (`AtomicBoolean`), and catch any `Throwable` defensively so plain-JVM (`SDK_INT == 0`) and Robolectric tests never crash.
2. **Thread-Safe `MapsforgeOfflineTileProvider`, Multi-Region Border Stitching, Bounded LRU Cache & Hot-Swap Lifecycle (`REQ-MAP-071.2`)**:
   - `MapsforgeOfflineTileProvider` (`ui/map/offline/`) SHALL implement `com.google.android.gms.maps.model.TileProvider` backed by `MultiMapDataStore(DataPolicy.RETURN_ALL)` across all valid readable `*.map` files in `OfflineMapStorage.getMapsDirectory(context)` (ignoring `.tmp`/`.part`/empty/corrupt files individually without failing adjacent valid `.map` files).
   - `getTile(x, y, zoom)` SHALL check a bounded byte-size PNG `LruCache<Long, ByteArray>` (`MAX_TILE_CACHE_BYTES = 16 MiB`, packed `(zoom, x, y)` key) before acquiring a `ReentrantReadWriteLock` read lock, return `TileProvider.NO_TILE` when closed, empty, or unsupported (`!supportsTile(tile)`), serialize `DatabaseRenderer.executeJob(...)` under `renderLock`, release/decrement `AndroidTileBitmap` in `finally`, and fence cache insertion on the active reload `generation`.
   - `reload(mapFiles, renderTheme)` and `close()` SHALL acquire the `ReentrantReadWriteLock` write lock, close prior `MultiMapDataStore` / `RenderThemeFuture` handles safely, increment `generation`, evict the LRU cache, open the new stores/theme (default `MapsforgeThemes.DEFAULT`), and increment a reactive `revision: StateFlow<Long>` so mounted overlays clear Google Maps' tile cache cleanly for `ATT-8174` and `ATT-8173`.
3. **Orthogonal `MapBaseSource` Persistence (`GOOGLE` vs. `OFFLINE_OSM`) (`REQ-MAP-071.3`)**:
   - `OfflineMapPreferences` (`ui/map/offline/`, isolated `"offline_map_prefs"`, zero changes to `SettingsDataStore.kt` or `MyPreferenceManager.kt`) SHALL persist binary `MapBaseSource { GOOGLE, OFFLINE_OSM }` (default `GOOGLE`, defensive unknown-token fallback to `GOOGLE`, JVM-safe null/error guards) and expose a reactive `StateFlow<MapBaseSource>` without altering `WorkoutBaseMapType`.
4. **`ATrainingTrackerMap` Integration, Z-Order, Attribution, Fallback & Snapshot Compatibility (`REQ-MAP-071.4`)**:
   - When `baseSource == MapBaseSource.OFFLINE_OSM` AND `installedMapCount > 0`, `ATrainingTrackerMap` SHALL resolve `MapProperties(mapType = MapType.NONE, mapStyleOptions = null)` and mount a single process-shared, referentially stable (`===` across 1-Hz emissions per `REQ-MAP-051`) `TileOverlay(tileProvider = offlineProvider, zIndex = -100f)` strictly beneath all tracks, routes, segments, climbs, heatmaps, and markers, invoking `tileOverlayState.clearTileCache()` on provider `revision` updates.
   - When `baseSource == MapBaseSource.GOOGLE` OR `installedMapCount == 0`, `ATrainingTrackerMap` SHALL fall back seamlessly to the existing Google `MapProperties` resolution (`mapTypeOverride` / `useMutedStyle` / `DarkMapStyle`), preserving `WorkoutBaseMapType` untouched.
   - Whenever offline OSM tiles are active, `ATrainingTrackerMap` SHALL display a compact high-contrast `© OpenStreetMap contributors` attribution badge (`R.string.offline_map_osm_attribution`, `testTag = "offline_osm_attribution"`, `Alignment.BottomStart` respecting `contentPadding`) and preserve `map.snapshot` (`WorkoutShareBottomSheet` / `MapDetailLayout`).
5. **`PathPreviewMap` Lite-Mode Scope Invariant (`REQ-MAP-071.5`)**:
   - `PathPreviewMap.kt` MUST remain unchanged (`0` LoC modified), continuing to use `GoogleMapOptions().liteMode(true)` and its offline inspection/Canvas fallback regardless of `MapBaseSource`.

## 2. Acceptance Criteria (Given-When-Then)

- **AC1 (Offline Base Tiles Beneath Overlays + Attribution)**: *Given* `>= 1` valid `.map` file in `offline_maps/` and `MapBaseSource.OFFLINE_OSM`, *When* opening Cockpit Map, Global Map, Workout Details, or Full-Screen Map offline, *Then* `ATrainingTrackerMap` sets `MapType.NONE`, mounts `TileOverlay(zIndex = -100f)` under all polylines/markers/heatmaps, displays `© OpenStreetMap contributors`, supports `map.snapshot`, and switching back to `GOOGLE` restores the previously chosen `WorkoutBaseMapType`.
- **AC2 (Multi-Region Border & Zero-File Graceful Fallback)**: *Given* two adjacent `.map` files in `MultiMapDataStore(DataPolicy.RETURN_ALL)` (or `0` `.map` files installed), *When* requesting border tiles (or opening `ATrainingTrackerMap` with `0` files), *Then* border tiles stitch seamlessly without blank seams, out-of-coverage tiles return `TileProvider.NO_TILE`, and `0` installed files fall back cleanly to Google without crashes.
- **AC3 (1-Hz Stability & Concurrent Hot-Swap)**: *Given* active 1-Hz live tracking and concurrent `getTile()` threads during `reload()`/`close()`, *When* GPS ticks arrive and files/themes hot-swap, *Then* `MapsforgeOfflineTileProvider` remains referentially identical (`===`), LRU hits skip re-rasterization, and `0` exceptions or stale-generation cache entries occur.
- **AC4 (`PathPreviewMap` Non-Regression)**: *Given* `MapBaseSource.OFFLINE_OSM`, *When* rendering list/card previews via `PathPreviewMap`, *Then* `PathPreviewMap` stays in Lite Mode with zero tile overlays and zero crashes.

## 3. Verification Matrix (`TST-MAP-074`) & 9-Language Audit

- **9-Language Strings (`values*/strings_offline_maps.xml`)**: `offline_map_osm_attribution` (`"© OpenStreetMap contributors"` across `en`/`de`/`es`/`fr`/`it`/`ja`/`nl`/`pl`/`pt`, `0` format specifiers, `0` emojis), `offline_map_source_google`, `offline_map_source_osm` (localized across all 9 locales, verified by `TranslationParityTest`).

| Test Case | Scope | Target Test Class(es) | Requirement |
|---|---|---|---|
| **`TST-MAP-074.1`** | Unit (JVM + Concurrency) | `MapsforgeOfflineTileProviderTest.kt` | `REQ-MAP-071.2`: `MultiMapDataStore(DataPolicy.RETURN_ALL)` wiring, LRU cache hit/eviction (`<= 16 MiB`), `NO_TILE` fallback, corrupt-file skip, concurrent `getTile` + `reload()`/`close()` stress (`0` races/exceptions), generation fencing. |
| **`TST-MAP-074.2`** | Unit + Robolectric | `OfflineMapPreferencesAndStorageTest.kt`, `ProGuardRulesContractTest.kt` | `REQ-MAP-071.1`, `REQ-MAP-071.3`: `OfflineMapPreferences` orthogonal persistence & reactive `StateFlow`, `WorkoutBaseMapType` preservation, `OfflineMapStorage` `.map` filtering, `OfflineMapGraphicInitializer` `SDK_INT == 0` guard, ProGuard & enum reflection contracts. |
| **`TST-MAP-074.3`** | Compose UI + Unit | `ATrainingTrackerMapOfflineModeTest.kt`, `PathPreviewMapOfflineInvariantTest.kt`, `MapPolylineMemoryStabilityTest.kt`, `DarkMapStyleResolutionTest.kt` | `REQ-MAP-071.4`, `REQ-MAP-071.5`, `REQ-MAP-051`: `resolveOfflineAwareMapProperties` (`MapType.NONE` vs fallback), `zIndex = -100f`, attribution badge semantics/visibility (`createComposeRule()`), 1-Hz provider `===` stability, `PathPreviewMap` Lite-Mode invariance. |
| **`TST-MAP-074.4`** | Localization, Arch & Emulator | `TranslationParityTest.kt`, `ArchitectureConformanceContractTest.kt`, live `emulator-5554` | `REQ-MAP-071.1`..`.5`, `REQ-LOC-001`, `Rule 8.5`: 9-locale parity, `docs/architecture.md` + LoC caps, live `emulator-5554` Light/Dark screenshots (`read_file` verified) with real `.map` fixture, overlay layering, attribution, fallback & snapshot check. |
