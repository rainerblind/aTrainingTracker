# Stage 2: Requirement & Test Specification - ATT-5474: Workout Photos in Advanced Settings (WorkoutMasksAndCardsSection)

**Ticket**: [ATT-5474](https://rainerblind.atlassian.net/browse/ATT-5474)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5474`  
**Requirement Mapping**: `REQ-UI-414` (*Workout Cards & Details Settings Streamlining*) | **Test Spec**: `TST-UI-377`  

---

## 1. Requirement Specification (REQ-UI-414.6)
* **Settings Matrix Integration**: `WorkoutSectionType.PHOTOS` and `WorkoutDetailSectionType.PHOTOS` integrated into the reorderable summary card list (10 items) and detail tab configuration with bidirectional mapping and self-healing serialization.
* **Photo Preferences & DataStore**: `WorkoutCardSectionPreferences` and `WorkoutDetailPreferences` support `showPhotos` (default `true`), `showPhotoAddButton` (default `true`), and `photoThumbnailSize` (`PhotoThumbnailSize`: `SMALL`=56dp, `MEDIUM`=76dp, `LARGE`=104dp, default `MEDIUM`).
* **Subordinate Settings & Modularity**: Subordinate controls in `WorkoutMasksAndCardsSection.kt` render "Add Button" toggle and `SingleChoiceSegmentedButtonRow` size selector when `showPhotos == true`. Sub-settings extracted to `WorkoutPhotoPreferencesComponents.kt` preserving the strict < 400 lines threshold (`REQ-PRO-011`).
* **Dynamic Section Slotting**: In `WorkoutSummary.kt`, `WorkoutPhotoStrip` is rendered within `workoutSectionsOrder` respecting `showPhotos`, `showPhotoAddButton`, and `thumbnailSize`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-414` & `REQ-UI-448`. Targets: `WorkoutMasksAndCardsSection.kt`, `WorkoutSectionType.kt`, `WorkoutDetailTabConfig.kt`, `MyPreferenceManager.kt`, `WorkoutPhotoStrip.kt`, `WorkoutSummary.kt`, `strings.xml`.
* **Historical Origin & Commit Trace**: `a8e5e356` (`ATT-4043`, `REQ-UI-414`), `5c9d9653` (`ATT-43`, Workout Photos), `c82c8248` (`ATT-5475`).
* **Root Reason for Existing Formulation**: ATT-43 introduced photos as an MVP anchored statically in `WorkoutSummary` and `WorkoutDetails` without configuration entries in Advanced Settings.
* **Preservation of Core Invariants**: Modularity constraint (< 400 lines) preserved via `WorkoutPhotoPreferencesComponents.kt`; default fallback self-healing preserves backward compatibility; existing photo persistence and viewer contracts remain untouched; 100% 9-language translation parity.

---

## 2. 9-Language Localization Parity Matrix (TST-LOC-001)

| Key | EN (`values`) | DE (`values-de`) | ES (`values-es`) | FR (`values-fr`) | IT (`values-it`) | JA (`values-ja`) | NL (`values-nl`) | PL (`values-pl`) | PT (`values-pt`) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `settings_workout_card_photos` | Workout Photos | Trainingsfotos | Fotos del entrenamiento | Photos de la séance | Foto dell\'allenamento | ワークアウトの写真 | Workoutfoto\'s | Zdjęcia z treningu | Fotos do treino |
| `settings_workout_photos_show_add_button` | Show \"Add Photo\" Button | \"Foto hinzufügen\"-Feld anzeigen | Mostrar botón \"Añadir foto\" | Afficher le bouton \"Ajouter une photo\" | Mostra pulsante \"Aggiungi foto\" | 「写真を追加」ボタンを表示 | Knop \"Foto toevoegen\" tonen | Pokaż przycisk \"Dodaj zdjęcie\" | Mostrar botão \"Adicionar foto\" |
| `settings_workout_photos_thumbnail_size` | Photo Preview Size | Foto-Vorschaugröße | Tamaño de vista previa | Taille de prévisualisation | Dimensioni anteprima foto | 写真プレビューのサイズ | Grootte van fotovoorbeeld | Rozmiar podglądu zdjęć | Tamanho da pré-visualização |
| `settings_workout_photos_size_small` | Small | Klein | Pequeño | Petite | Piccola | 小 | Klein | Mały | Pequeno |
| `settings_workout_photos_size_medium` | Medium | Mittel | Mediano | Moyenne | Media | 中 | Gemiddeld | Średni | Médio |
| `settings_workout_photos_size_large` | Large | Groß | Grande | Grande | Grande | 大 | Groot | Duży | Grande |

---

## 3. Test Specification & Traceability Matrix

| Test ID | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-377.1` | Unit | `WorkoutDetailTabsTest`: Data availability & tab section activation for `PHOTOS` | `REQ-UI-414.1` | Specified |
| `TST-UI-377.2` | Contract | `WorkoutMasksAndCardsLayoutTest`: Single-column checkbox and Details column removal | `REQ-UI-414.1` | Specified |
| `TST-UI-377.3` | Contract | `WorkoutMasksAndCardsLayoutTest`: Collapsible summary reorder list | `REQ-UI-414.3` | Specified |
| `TST-UI-377.5` | Contract | `WorkoutMasksAndCardsLayoutTest`: Line count strictly < 400 lines | `REQ-UI-414.5` | Specified |
| `TST-UI-377.7` | Contract | `WorkoutMasksAndCardsLayoutTest`: `PHOTOS` section, `PhotoThumbnailSize` enum, DataStore persistence | `REQ-UI-414.6` | Specified |
| `TST-UI-377.8` | Localization | `TranslationParityTest`: 100% parity across 9 locales for all 6 new photo settings keys | `REQ-UI-414.6`, `TST-LOC-001` | Specified |
