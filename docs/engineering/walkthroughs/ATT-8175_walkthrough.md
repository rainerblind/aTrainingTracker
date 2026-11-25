# Stage 5 Walkthrough — ATT-8175: Mapsforge Vector `TileProvider` & Offline Base-Map Mode in `ATrainingTrackerMap`

- **Ticket**: `ATT-8175` (`5 SP`, Feature, `tier-a`, Epic `ATT-8171`) | **Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8175`
- **Living Docs Promoted**: `REQ-MAP-071` (`[Verified]`, `SYSREQ-TRK-004`, `SYSREQ-TRK-009`) <-> `TST-MAP-074` (`[Verified]`)
- **Evidence Bundle**: `docs/engineering/test_evidence/tickets/ATT-8175/` (generated via `tools/auto_gate.py --write-bundle`)

## 1. Summary of Implemented Changes (All 6 Gate 3 Watchpoints Addressed)

| Area | Key Files | Implementation Highlights |
|---|---|---|
| **Dependencies, ProGuard & Bootstrap** | `app/build.gradle`, `app/proguard-rules.pro`, `OfflineMapGraphicInitializer.kt`, `TrainingApplication.java` (`1,978/2,050` LoC) | Pinned `org.mapsforge:*:0.25.0` (`LGPLv3`) + `com.caverock:androidsvg:1.4` excluding `kxml2`/`xmlpull` on `implementation` (`:app:checkDebugDuplicateClasses` PASS); targeted ProGuard keep rules; `SDK_INT == 0`-guarded idempotent `AndroidGraphicFactory` init. |
| **Orthogonal Preferences & Storage** | `MapBaseSource.kt`, `OfflineMapPreferences.kt`, `OfflineMapStorage.kt` | Isolated `"offline_map_prefs"` (`StateFlow<MapBaseSource>`, default `GOOGLE`, `0` edits to `SettingsDataStore.kt` / `MyPreferenceManager.kt`), preserving `WorkoutBaseMapType`; app-private `offline_maps/` sorted `*.map` scanner ignoring `.tmp`/`.part`/empty files. |
| **Vector Tile Engine & Manager** | `MapsforgeOfflineTileProvider.kt`, `OfflineMapTileProviderManager.kt` | `MultiMapDataStore(DataPolicy.RETURN_ALL)`, `DatabaseRenderer(renderLabels = true, cacheLabels = false)` + zero-memory `NoOpMapsforgeTileCache` (fixing live-caught Mapsforge `tileCache.containsKey` NPE), synchronous `renderThemeFuture.run()` + `decrementRefCount()`, `DisplayModel` HiDPI scale normalization (`computeNormalizedUserScaleFactor`), pure-JVM `16 MiB` `ByteBoundedTileLruCache`, `zoom 0..22` / `[0, 2^z)` bounds validation, `ReentrantReadWriteLock` + `renderLock` hot-swap `reload()`/`close()`, referentially stable (`===`) singleton manager (`REQ-MAP-051`, zero graphics init on empty/`GOOGLE`). |
| **`ATrainingTrackerMap` & 9-Locale UI** | `ATrainingTrackerMap.kt` (`433/1,500` LoC), `OfflineOsmAttributionBadge.kt`, `values*/strings_offline_maps.xml` (9 locales), `docs/architecture.md` (`v1.0.41`) | `resolveOfflineAwareMapProperties` (`MapType.NONE` iff `OFFLINE_OSM && installedMapCount > 0`, else Google fallback), `TileOverlay(zIndex = -100f)` with `providerRevision > 0L`-gated `clearTileCache()`, `OfflineOsmAttributionBadge` (`© OpenStreetMap contributors`, `Alignment.BottomEnd` + `contentPadding`), `0` LoC edits to `PathPreviewMap.kt`, SWE.2 architecture & `16 MiB` cache budget updated. |

## 2. Verification Results (Tier 1 Targeted Suite + Live `emulator-5554`)

- **Tier 1 Targeted Unit / Robolectric / Compose / Contract Suites (`106/106 PASS`, `0` failures, `0` errors across `16` suites)**:
  - Map / Offline / ProGuard / Localization / Architecture (`71/71`): `MapsforgeOfflineTileProviderTest` (`8/8`), `OfflineMapPreferencesAndStorageTest` (`5/5`), `ATrainingTrackerMapOfflineModeTest` (`4/4`, real `createComposeRule()` behavioral Compose UI), `PathPreviewMapOfflineInvariantTest` (`1/1`), `MapPolylineMemoryStabilityTest` (`4/4`), `DarkMapStyleResolutionTest` (`8/8`), `PathPreviewMapBoundsTest` (`8/8`), `WorkoutMapLayerPreferencesTest` (`8/8`), `ProGuardRulesContractTest` (`3/3`), `TranslationParityTest` (`11/11`, 9 locales), `ArchitectureConformanceContractTest` (`11/11`).
  - Direct `TrainingApplication` Suites (`35/35`): `TrainingApplicationTrackingTest` (`22/22`), `TrainingApplicationStravaSyncTest` (`4/4`), `TrainingApplicationTrackedTest` (`4/4`), `TrainingApplicationDropboxPreferencesTest` (`3/3`), `TrainingApplicationLapTest` (`2/2`).
- **Build, Static Linters & Governance**: `:app:assembleDebug` + `:app:checkDebugDuplicateClasses` PASS; `auto_gate.py --skip-tests` (`static_safety_linters`, `deliverable_lint`, `conflict_markers` across `13,342` files) all PASS; `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-11-25_01_Früh` -> **EXIT `0`**.
- **Documented Deviation (`Rule 1.3`, Coordinator-Approved)**: Adding the 2-line `OfflineMapGraphicInitializer.initialize(this)` hook in `TrainingApplication.java` caused `auto_gate.py`'s dependency fan-out to select `276` unrelated Android test classes (including pre-existing `ATT-9681` `TuningPreferencesDataStore` livelock & `ATT-9701`), exceeding `auto_gate.py`'s `1,800 s` subprocess timeout. All `16` directly affected suites (`106/106 PASS`), `:app:assembleDebug`, `:app:checkDebugDuplicateClasses`, and all `auto_gate.py` static/deliverable/conflict checks passed cleanly and are archived in `docs/engineering/test_evidence/tickets/ATT-8175/`.
- **Live `emulator-5554` Verification & 4-Point Visual Self-Inspection (`Rule 8.5`)**:
  - Installed `app-debug.apk` on `Pixel_8_Android_15`, seeded authentic Mapsforge v5 `monaco.map` (`354 KB`) + `OFFLINE_OSM` + mock GPS `(43.7340, 7.4200)`.
  - Captured and visually inspected via `read_file`:
    - Light: [`ATT-8175_offline_map_light.png`](img/ATT-8175_offline_map_light.png) (`MD5: 40258cedeff683eeddd903c2145d834d`)
    - Dark: [`ATT-8175_offline_map_dark.png`](img/ATT-8175_offline_map_dark.png) (`MD5: 1c942cd36b92a76f71a5ed41a61e0c52`)
  - **Live-Caught & Fixed Defects**: Live testing surfaced (1) Mapsforge `DatabaseRenderer` NPE when `tileCache == null` (fixed via `NoOpMapsforgeTileCache`) and (2) `2.625x` HiDPI double-scaling bloat on labels/roads (fixed via `computeNormalizedUserScaleFactor`).
  - **4-Point Checklist**: (1) *Orientation & Geometry*: Crisp multi-tile Monaco OSM streets/buildings/POIs under Follow-Me 3D tilt with blue location arrow (`zIndex = 100f`) strictly above tiles (`zIndex = -100f`); (2) *Color Mapping*: `OfflineOsmAttributionBadge` adapts cleanly between Light and Dark/AMOLED surfaces via `MaterialTheme.colorScheme`; (3) *Typography*: Single-line `"© OpenStreetMap contributors"` and crisp normalized OSM street labels with zero clipping/wrapping; (4) *Clearance & Stability*: Badge at `Alignment.BottomEnd` cleanly clears both bottom-start `"Google"` watermark and bottom elevation bar; `0` crashes/exceptions in `logcat`.

## 3. Acceptance Criteria Verification

| AC | Status | Evidence |
|---|---|---|
| **AC1 (Offline Base Tiles + Attribution + Orthogonal Restore)** | **MET** | `ATrainingTrackerMapOfflineModeTest`, `OfflineMapPreferencesAndStorageTest`, live `emulator-5554` Light/Dark PNGs |
| **AC2 (Multi-Region `RETURN_ALL` + `NO_TILE` + Zero-File Fallback)** | **MET** | `MapsforgeOfflineTileProviderTest` (`8/8`), `ATrainingTrackerMapOfflineModeTest` (`4/4`) |
| **AC3 (1-Hz `===` Stability + Bounded LRU + Concurrent Hot-Swap)** | **MET** | `MapsforgeOfflineTileProviderTest` (6-reader stress + cache hit), `OfflineMapPreferencesAndStorageTest`, `MapPolylineMemoryStabilityTest` |
| **AC4 (`PathPreviewMap` Lite-Mode Non-Regression)** | **MET** | `0` LoC modified in `PathPreviewMap.kt`, `PathPreviewMapOfflineInvariantTest` (`1/1`), `PathPreviewMapBoundsTest` (`8/8`) |
