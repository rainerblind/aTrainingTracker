# Stage 2: Requirement & Test Specification - ATT-43: Add photos taken during a workout

**Ticket**: [ATT-43](https://rainerblind.atlassian.net/browse/ATT-43)  
**Parent Epic**: None (*Standalone Feature*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.6`  
**Requirement Mapping**: `REQ-DAT-038`, `REQ-UI-448`, `REQ-MIG-048.6`  
**Test Spec ID**: `TST-DAT-034`, `TST-UI-448`, `TST-MIG-046.8`  
**Branch**: `feature/ATT-43`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-DAT-038, REQ-UI-448, REQ-MIG-048.6)

### 1.1 Problem Statement & Rationale
Athletes capture photos during outdoor workouts and export archives from Strava containing workout media. Currently, `aTrainingTracker` lacks photo attachment, storage, and visualization capabilities: workouts cannot store media references, photos are omitted during Strava bulk ZIP import (ATT-4957), and photos cannot be previewed in workout details, displayed as markers on the workout map, or shared via ATT-30's Photo Track Overlay.

### 1.2 Functional & Architectural Requirements
1. **Persistence & Schema v27 (`REQ-DAT-038.1`)**: `WorkoutSummaries.db` (v27) SHALL create table `workout_photos` (`_id`, `workout_id`, `file_path`, `taken_at_ms`, `latitude`, `longitude`, `caption`) with index `idx_workout_photos_workout_id`. `DatabaseSchemaRegistry.kt` SHALL reference `WorkoutSummariesDbHelper.DB_VERSION` directly. Deleting a workout SHALL cascade-delete photo records and files from `filesDir/workout_photos/<workout_id>/`.
2. **EXIF & Trackpoint Interpolation (`REQ-DAT-038.2`)**: The system SHALL extract EXIF capture timestamp (`TAG_DATETIME_ORIGINAL`) and GPS coordinates. When EXIF GPS is absent, coordinates SHALL be interpolated from `WorkoutSamples.db` trackpoints matching `taken_at_ms` within the workout time window.
3. **Strava Bulk Export Media Ingestion (`REQ-MIG-048.6`)**: `StravaCsvParser` SHALL parse the pipe-separated `Medien` / `Media` column in `activities.csv` and captions in `media.csv`. `StravaBulkExportImporter` SHALL extract `media/*` files to app storage with Zip-Slip path sanitization, falling back to workout start time when EXIF is stripped.
4. **Workout Photo Strip, Viewer & ATT-30 Share (`REQ-UI-448.1`)**: `WorkoutDetails` / `WorkoutDetailTabbedContent` SHALL render a horizontal photo strip. Tapping a photo opens `WorkoutPhotoViewerSheet` (`AppModalBottomSheet`) displaying image preview, caption/timestamp, delete action, and direct handoff to `WorkoutShareBottomSheet` (ATT-30). Photos with GPS coordinates SHALL render as pin markers on `TrackOnMapScreen` / `ATrainingTrackerMap`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Photo Ingestion & Storage)**: *Given* an athlete selecting photos via system Photo Picker or importing a Strava archive with `media/*`, *When* ingestion executes, *Then* photos are copied to `filesDir/workout_photos/<id>/`, EXIF/GPS interpolated, and persisted in `workout_photos`.
* **Criterion 2 (Viewer & Map Pins)**: *Given* a workout with attached photos, *When* viewing Workout Details or Map, *Then* the photo strip displays thumbnails, photo markers appear on the GPS track, and tapping opens `WorkoutPhotoViewerSheet` with ATT-30 share.
* **Criterion 3 (Cascade Cleanup)**: *Given* an attached photo or entire workout deleted, *When* deleted, *Then* SQLite rows and local image files are purged.

---

## 2. Test Specification (TST-DAT-034, TST-UI-448, TST-MIG-046.8)

### 2.1 Test Cases
* `TST-DAT-034.1` (Unit): `WorkoutSummariesMigrationV27Test` — Symmetrical `onCreate`/`onUpgrade` v27 schema, index, and cascade deletion.
* `TST-DAT-034.2` (Unit): `WorkoutPhotoManagerTest` — EXIF parsing, GPS trackpoint interpolation, and safe downsampling.
* `TST-UI-448.1` (Contract): `WorkoutPhotoUiContractTest` — Photo strip rendering, viewer bottom sheet, and ATT-30 share handoff.
* `TST-UI-448.2` (Unit): `WorkoutMarkersPhotoTest` — Generation of `LocationMarker` photo pins from `workout_photos`.
* `TST-MIG-046.8` (Unit): `StravaBulkExportMediaTest` — Bilingual `Medien`/`Media` parsing, `media.csv` captions, and Zip-Slip verification.
* `TST-LOC-043` (Localization): `TranslationParityTest` — 100% parity across 9 locales without inline decorative emojis.
* `TST-REG-043` (Regression): Targeted package tests (`database.*`, `migration.*`, `ui.aftermath.*`, `ui.map.*`).

### 2.2 9-Language Localization Parity

| Key | en | de | es | fr | it | ja | nl | pl | pt |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `workout_photos_title` | Photos | Fotos | Fotos | Photos | Foto | 写真 | Foto's | Zdjęcia | Fotos |
| `workout_photos_add` | Add Photos | Fotos hinzufügen | Añadir fotos | Ajouter des photos | Aggiungi foto | 写真を追加 | Foto's toevoegen | Dodaj zdjęcia | Adicionar fotos |
| `workout_photos_empty` | No photos attached | Keine Fotos angehängt | No hay fotos adjuntas | Aucune photo jointe | Nessuna foto allegata | 添付された写真はありません | Geen foto's bijgevoegd | Brak dołączonych zdjęć | Nenhuma foto anexada |
| `workout_photos_delete` | Delete Photo | Foto löschen | Eliminar foto | Supprimer la photo | Elimina foto | 写真を削除 | Foto verwijderen | Usuń zdjęcie | Excluir foto |
| `workout_photos_delete_confirm` | Do you want to delete this photo from the workout? | Möchtest du dieses Foto aus dem Workout löschen? | ¿Deseas eliminar esta foto del entrenamiento? | Voulez-vous supprimer cette photo de l'entraînement ? | Vuoi eliminare questa foto dall'allenamento? | この写真をワークアウトから削除しますか？ | Wil je deze foto van de workout verwijderen? | Czy chcesz usunąć to zdjęcie z treningu? | Deseja excluir esta foto do treino? |
| `workout_photos_share_overlay` | Share with Track Overlay | Mit Track-Overlay teilen | Compartir con superposición de ruta | Partager avec superposition du tracé | Condividi con traccia in sovrimpressione | トラックオーバーレイで共有 | Delen met track-overlay | Udostępnij z nakładką trasy | Compartilhar com sobreposição de percurso |
| `workout_photos_marker_title` | Photo | Foto | Foto | Photo | Foto | 写真 | Foto | Zdjęcie | Foto |

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-DAT-034.1` | Unit | `WorkoutSummariesMigrationV27Test.kt` | `REQ-DAT-038.1` | Specified |
| `TST-DAT-034.2` | Unit | `WorkoutPhotoManagerTest.kt` | `REQ-DAT-038.2` | Specified |
| `TST-UI-448.1` | Contract | `WorkoutPhotoUiContractTest.kt` | `REQ-UI-448.1` | Specified |
| `TST-UI-448.2` | Unit | `WorkoutMarkersPhotoTest.kt` | `REQ-UI-448.1` | Specified |
| `TST-MIG-046.8` | Unit | `StravaBulkExportMediaTest.kt` | `REQ-MIG-048.6` | Specified |
| `TST-LOC-043` | Localization | `TranslationParityTest.kt` | `REQ-UI-106`, `REQ-UI-448.1` | Specified |
| `TST-REG-043` | Regression | Full targeted unit test suite | `REQ-PRO-001` | Specified |
