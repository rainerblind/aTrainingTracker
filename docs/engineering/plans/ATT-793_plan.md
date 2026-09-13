# Implementation Plan: Responsive Pre-Import Cluster Parameter Tuning Bottom Sheet (ATT-793)

* **Parent Ticket**: [ATT-793](https://rainerblind.atlassian.net/browse/ATT-793) ([Verbesserung] Cluster-Parameter Dialog is still too small -> Use botton scaffold (or something else))
* **Sub-Task**: [ATT-799](https://rainerblind.atlassian.net/browse/ATT-799) ([Impl-Plan] Cluster-Parameter Dialog is still too small -> Use botton scaffold (or something else))
* **Target Lösungsversion (Fix Version/s)**: `V4.9.36`
* **Requirement**: `REQ-MIG-012` (*Responsive Pre-Import Workout Cluster Tuning Bottom Sheet*)
* **Test Specification**: `TST-MIG-009` (*Pre-Import Workout Cluster Tuning Bottom Sheet & Info Action Verification*)
* **Branch**: `feature/ATT-793`

---

## 1. Executive Summary & Problem Domain

### 1.1 Problem Statement
When initiating TCX workout imports (bulk cloud recovery or single legacy file pickers), users are prompted to review and adjust workout clustering parameters (`PreImportTuningDialog`).
Previously, this was presented as a standard Android `AlertDialog`. Because `AlertDialog` enforces rigid framework window bounds and cramped margins, multi-slider controls (master slider, individual tolerance sliders, switches) felt constrained and required excessive micro-scrolling. Furthermore, the pre-import dialog lacked access to the educational `ClusterInfoDialog` created in ATT-501.

### 1.2 Proposed Solution
1. **Material 3 `ModalBottomSheet`**: Replace the cramped `AlertDialog` with a modern, responsive `ModalBottomSheet` (`PreImportTuningBottomSheet`):
   - Spans the full device width with adaptive vertical expansion (`rememberModalBottomSheetState(skipPartiallyExpanded = true)`).
   - Standard M3 drag handle (`BottomSheetDefaults.DragHandle()`).
   - Smooth gesture dismissal and outside scrim handling.
2. **Integrated Educational Cluster Info Modal**:
   - Header row contains the localized title (`@string/cluster_tuning_title`), an Info `IconButton` (`Icons.Outlined.Info` with `@string/cluster_info_title`), and a Close `IconButton` (`Icons.Default.Close`).
   - Tapping the Info button launches `ClusterInfoDialog` on top of the sheet without losing state.
3. **Ergonomic Layout & Action Row**:
   - Introductory description localized across all 9 languages (`@string/cluster_tuning_pre_import_desc`).
   - Scrollable `ClusterTuningContent` providing comfortable spacing for all sliders and toggles.
   - Generous bottom action row featuring "Cancel" (`OutlinedButton`) and "OK" (`Button`).

---

## 2. Technical Architecture & Component Design

### 2.1 Component Structure (`PreImportTuningBottomSheet`)
```
ModalBottomSheet (sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true))
└── Column (fillMaxWidth, navigationBarsPadding)
    ├── Header Row (Title: "Cluster-Parameter", Info Button, Close Button)
    ├── Scrollable Column (fillMaxWidth, verticalScroll, weight(1f, fill = false))
    │   ├── Intro Text (cluster_tuning_pre_import_desc)
    │   └── ClusterTuningContent (tolerances, sliders, switches)
    └── Action Row (Cancel button, OK button)
```

### 2.2 Integration with `ImportBackupTabsScreen.kt`
- Replace calls to `PreImportTuningDialog` for:
  - Bulk recovery (`showTuningDialogForBulk == true`)
  - Single file legacy URI (`pendingSingleLegacyUri != null`)
- Retain exact ViewModel calls: `viewModel.saveClusteringTolerances()`, `viewModel.bulkRecoverLegacyData(...)`, and `viewModel.importLegacyFile(...)`.

---

## 3. Detailed Impact Analysis (SWE.1.BP.5 Phase)

### 3.1 `find_usages` Audit
- `PreImportTuningDialog`: Exclusively used in `ImportBackupTabsScreen.kt`. Replaced by `PreImportTuningBottomSheet` (with legacy signature alias preserved for API backward compatibility).
- `ClusterTuningContent`: Located in `ClusterTuningScreen.kt`. Accepts parameters cleanly with comfortable spacing in a bottom sheet container.
- `ClusterInfoDialog`: Reused from `com.atrainingtracker.trainingtracker.ui.clusters.ClusterInfoDialog`.

### 3.2 Mapped Requirements Cross-Check
- `REQ-MIG-012`: Updated and satisfied.
- `REQ-MIG-011` (Tabbed UI): Preserved; sheet launches from Import tab without affecting tab layout.
- `REQ-MIG-013` (Idempotent Recovery): Preserved; skipping logic unaltered.
- `REQ-MIG-014` / `REQ-MIG-017` (Queued User Interaction): Preserved; decoupled import queues unaffected.
- `REQ-UI-137` (Cluster Info Modal): Reused directly.

### 3.3 System Invariants
- [x] No changes to clustering algorithm formulas in `WorkoutClusterEngine.kt`.
- [x] Room database entities and schemas unchanged.
- [x] SharedPreferences tolerance persistence preserved on confirmation.
- [x] 100% localization parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 4. Affected Files & Proposed Modifications

### 4.1 `[MODIFY]` [ImportBackupTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt)
- Implement `@Composable fun PreImportTuningBottomSheet(...)`.
- Update invocations for `showTuningDialogForBulk` and `pendingSingleLegacyUri`.
- Provide alias `@Composable fun PreImportTuningDialog(...)` delegating to `PreImportTuningBottomSheet`.

### 4.2 `[MODIFY]` String Resources (`res/values-*/strings.xml`)
Add localized key `cluster_tuning_pre_import_desc`:
- `values/strings.xml` (EN): "Adjust workout clustering sensitivity to optimize route matching for imported workouts."
- `values-de/strings.xml` (DE): "Passe die Sensitivität der Streckenerkennung an, um den Streckenabgleich für importierte Workouts zu optimieren."
- `values-es/strings.xml` (ES): "Ajusta la sensibilidad del agrupamiento de rutas para optimizar la coincidencia en entrenamientos importados."
- `values-fr/strings.xml` (FR): "Ajustez la sensibilité du regroupement de parcours pour optimiser la correspondance des entraînements importés."
- `values-it/strings.xml` (IT): "Regola la sensibilità del raggruppamento dei percorsi per ottimizzare la corrispondenza degli allenamenti importati."
- `values-ja/strings.xml` (JA): "インポートされたワークアウトのルート照合を最適化するために、クラスタリングの感度を調整します。"
- `values-nl/strings.xml` (NL): "Pas de gevoeligheid voor routegroepering aan om de routevergelijking voor geïmporteerde workouts te optimaliseren."
- `values-pl/strings.xml` (PL): "Dostosuj czułość grupowania tras, aby zoptymalizować dopasowywanie tras dla importowanych treningów."
- `values-pt/strings.xml` (PT): "Ajuste a sensibilidade do agrupamento de percursos para otimizar a correspondência de treinos importados."

### 4.3 `[NEW]` [PreImportTuningBottomSheetTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/migration/PreImportTuningBottomSheetTest.kt)
- Unit test suite verifying string localization parity and key presence across all 9 languages.

---

## 5. Verification Plan

### 5.1 Automated Unit Tests
- Execute `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.migration.PreImportTuningBottomSheetTest`
- Execute `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.TranslationParityTest`
- Verify project compilation: `./gradlew assembleDebug`

### 5.2 Manual Verification Steps (`TST-MIG-009`)
1. Navigate to 'Import & Backup' -> 'Import' tab.
2. Trigger pre-import tuning (via bulk scan or local TCX file).
3. Verify that the Material 3 `ModalBottomSheet` renders with full screen width, drag handle, and header.
4. Tap the Info button in the header and verify that `ClusterInfoDialog` opens on top, displays educational sections, and dismisses cleanly back to the bottom sheet.
5. Verify smooth scrolling and comfortable slider spacing.
6. Adjust sliders, tap 'OK', and verify preferences are saved and import runs.
