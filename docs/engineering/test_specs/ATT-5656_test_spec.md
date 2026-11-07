# Stage 2: Requirement & Test Specification - ATT-5656: WorkoutShareBottomSheet 3-Tier Photo Preselection

**Ticket**: [ATT-5656](https://rainerblind.atlassian.net/browse/ATT-5656)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (Good and consistent UI) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5656`  
**Requirement Mapping**: `REQ-UI-447` (`REQ-UI-447.6`) | **Test Spec ID**: `TST-UI-447` (`TST-UI-447.6`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-UI-447.6`)

### 1.1 Problem Statement & Rationale
When choosing Option B ("Foto mit Track & Daten") or clicking "Foto auswählen" in `WorkoutShareBottomSheet`, the app directly opened the generic system photo picker (`PickVisualMedia`). While `attachedPhotos` is passed into `WorkoutShareBottomSheet`, it was unused. The system SHALL provide a stepped, 3-tier cascade: 1. Attached Photos $\rightarrow$ 2. Time-Window Photos $\rightarrow$ 3. All Photos (System Picker) with seamless switching and Rule 73 stacked buttons.

### 1.2 Functional Specification (3-Tier Photo Cascade)
In `WorkoutShareBottomSheet.kt`, selecting Option B or tapping "Foto auswählen" in preview SHALL execute:
1. **Tier 1 (Attached Photos)**: If `attachedPhotos.isNotEmpty()`, show a 3-column grid (`share_photos_attached_subtitle`) of attached photos. Provide Rule 73 stacked buttons: secondary CTA `share_photos_show_time_window` (switching to Tier 2) and tertiary CTA `workout_photos_show_all` (switching to Tier 3). Tapping any thumbnail advances to preview.
2. **Tier 2 (Time-Window Photos)**: If `attachedPhotos.isEmpty()` or user requests time-window photos, query `WorkoutPhotoManager.queryWorkoutTimePhotos(context, startMs, endMs, 300_000L)`. If matching photos exist, display a 3-column grid with capture timestamps and `workout_photos_time_picker_subtitle`. Provide `workout_photos_show_all` (Tier 3) and `share_photos_show_attached` (if attached photos exist). Tapping any thumbnail advances to preview.
3. **Tier 3 (All Photos / System Picker)**: If both tiers contain 0 photos or user requests all photos, launch `PickVisualMedia(ImageOnly)`.
4. **Preview Re-selection**: Tapping `share_choose_photo` in preview returns to the cascade (Tier 1 if `attachedPhotos.isNotEmpty()`, else Tier 2/3).

### 1.3 9-Language Localization Parity

| String Key | EN | DE | ES | FR | IT | JA | NL | PL | PT |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `share_photos_attached_subtitle` | Photos attached to this workout | Der Einheit hinzugefügte Fotos | Fotos adjuntas a este entrenamiento | Photos associées à cet entraînement | Foto allegate a questo allenamento | このワークアウトに追加された写真 | Foto\'s toegevoegd aan deze workout | Zdjęcia dołączone do tego treningu | Fotos anexadas a este treino |
| `share_photos_show_time_window` | Show photos taken during workout | Während der Einheit aufgenommene Fotos anzeigen | Mostrar fotos tomadas durante el entrenamiento | Afficher les photos prises pendant l\'entraînement | Mostra foto scattate durante l\'allenamento | ワークアウト中に撮影された写真を表示 | Foto\'s gemaakt tijdens de workout tonen | Pokaż zdjęcia zrobione podczas treningu | Mostrar fotos tiradas durante o treino |
| `share_photos_show_attached` | Back to attached photos | Zurück zu Fotos der Einheit | Volver a las fotos adjuntas | Retour aux photos associées | Torna alle foto allegate | 追加された写真に戻る | Terug naar toegevoegde foto\'s | Powrót do dołączonych zdjęć | Voltar para fotos anexadas |

### 1.4 Acceptance Criteria & Invariants
* **AC-1 (Attached Photos First)**: *Given* a workout with attached photos, *When* Option B is selected, *Then* Tier 1 (Attached Photos) is displayed with switches to Tier 2 and Tier 3.
* **AC-2 (Time-Window Fallback)**: *Given* 0 attached photos and matching time-window photos, *When* Option B is selected, *Then* Tier 2 is displayed with a switch to Tier 3.
* **AC-3 (System Picker Fallback)**: *Given* 0 attached and 0 time-window photos, *When* Option B is selected, *Then* `PickVisualMedia` launches.
* **AC-4 (Preview Return)**: *Given* an active preview, *When* clicking "Foto auswählen", *Then* the sheet returns to the appropriate cascade tier.
* **Invariants**: Option A (Map/Analytics snapshot) untouched; Rule 73 stacked buttons; bounded LazyGrid height; no OOM on downsampled decoding.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Amends `REQ-UI-447` (`REQ-UI-447.6`), targeting `WorkoutShareBottomSheet.kt`.
* `Historical Origin & Commit Trace`: Sprint 2026-40.8 (`ATT-1472`), Sprint 2026-45.6 (`ATT-30`, `584284ff`; `ATT-43`, `5c9d9653`), Sprint 2026-45.8 (`ATT-5472`, `ATT-5475`), Sprint 2026-45.9 (`ATT-5656`).
* `Root Reason for Existing Formulation`: In ATT-30, `PickVisualMedia` was a placeholder before attached photos (`ATT-43`) and time-window queries (`ATT-5475`) existed.
* `Preservation of Core Invariants`: Option A snapshot invariant; `initialPhotoUri` fast-path preserved; Rule 73 stacked buttons preserved; 9-locale parity preserved.

---

## 2. Test Specification (`TST-UI-447.6`)

* **TST-UI-447.6a (Tier 1 Attached Photos Contract)**: Verify `WorkoutShareBottomSheet` displays attached photo grid when `attachedPhotos.isNotEmpty()`, with switches to Tier 2 and Tier 3.
* **TST-UI-447.6b (Tier 2 Time-Window Photos Contract)**: Verify querying `WorkoutPhotoManager.queryWorkoutTimePhotos` and displaying time-window grid with timestamps when Tier 1 is empty or user requests it.
* **TST-UI-447.6c (Tier 3 System Picker Fallback Contract)**: Verify fallback to `PickVisualMedia` when both tiers are empty or user clicks `workout_photos_show_all`.
* **TST-UI-447.6d (Rule 73 Button Hierarchy & Spacing Contract)**: Verify full-width stacked buttons with `spacedBy(8.dp)` and no `actions =` slot across all states.
* **TST-UI-447.6e (9-Language Parity & Targeted Suite)**: `TranslationParityTest` 100% PASS for new keys across all 9 locales; targeted suite `com.atrainingtracker.trainingtracker.ui.map.*` 100% PASS.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-447.6a` | Unit / Contract | `WorkoutShareBottomSheet.kt` | `REQ-UI-447.6` | Specified |
| `TST-UI-447.6b` | Unit / Contract | `WorkoutShareBottomSheet.kt` | `REQ-UI-447.6` | Specified |
| `TST-UI-447.6c` | Unit / Contract | `WorkoutShareBottomSheet.kt` | `REQ-UI-447.6` | Specified |
| `TST-UI-447.6d` | Architecture Contract | `WorkoutShareBottomSheetContractTest.kt` | `REQ-UI-447.6` | Specified |
| `TST-UI-447.6e` | Localization & Unit | `TranslationParityTest.kt` / `map.*` | `REQ-UI-447.6` | Specified |
