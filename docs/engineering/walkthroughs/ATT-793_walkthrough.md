# Walkthrough: Responsive Pre-Import Cluster Parameter Tuning Bottom Sheet (ATT-793)

* **Parent Ticket**: [ATT-793](https://rainerblind.atlassian.net/browse/ATT-793) ([Verbesserung] Cluster-Parameter Dialog is still too small -> Use botton scaffold (or something else))
* **Sub-Task**: [ATT-800](https://rainerblind.atlassian.net/browse/ATT-800) ([Implementation] Cluster-Parameter Dialog is still too small -> Use botton scaffold (or something else))
* **Target Version**: `V4.9.36`
* **Requirement**: `REQ-MIG-012` (*Responsive Pre-Import Workout Cluster Tuning Bottom Sheet*)
* **Test Specification**: `TST-MIG-009` (*Pre-Import Workout Cluster Tuning Bottom Sheet & Info Action Verification*)
* **Branch**: `feature/ATT-793`

---

## 1. Summary of Changes

### 1.1 Responsive Material 3 `ModalBottomSheet`
* Replaced the cramped `AlertDialog` (`PreImportTuningDialog`) with a spacious, modern Material 3 `ModalBottomSheet` (`PreImportTuningBottomSheet`) in [`ImportBackupTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt).
* Leveraged `rememberModalBottomSheetState(skipPartiallyExpanded = true)` and `navigationBarsPadding()` so the sheet spans full device width and dynamically adapts vertically up to the full screen height without clipping or cramped borders.
* Integrated the standard M3 drag handle (`BottomSheetDefaults.DragHandle()`) and native dismiss gestures.

### 1.2 Educational Cluster Info Modal Integration
* Added an `IconButton` displaying `Icons.Outlined.Info` with content description `@string/cluster_info_title` into the top header row of the bottom sheet.
* Tapping the info button displays [`ClusterInfoDialog`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterInfoDialog.kt) (introduced in ATT-501) directly on top of the bottom sheet, allowing athletes to learn about the 3D topological algorithm, sport firewall, and sensitivity tuning without leaving or resetting the import flow.

### 1.3 100% Localization Parity Across All 9 Locales
* Replaced the previously hardcoded English intro string with `@string/cluster_tuning_pre_import_desc` across all 9 supported locales:
  * **EN**: *"Adjust workout clustering sensitivity to optimize route matching for imported workouts."*
  * **DE**: *"Passe die Sensitivität der Streckenerkennung an, um den Streckenabgleich für importierte Workouts zu optimieren."*
  * **ES**: *"Ajusta la sensibilidad del agrupamiento de rutas para optimizar la coincidencia en entrenamientos importados."*
  * **FR**: *"Ajustez la sensibilité du regroupement de parcours pour optimiser la correspondance des entraînements importés."*
  * **IT**: *"Regola la sensibilità del raggruppamento dei percorsi per ottimizzare la corrispondenza degli allenamenti importati."*
  * **JA**: *"インポートされたワークアウトのルート照合を最適化するために、クラスタリングの感度を調整します。"*
  * **NL**: *"Pas de gevoeligheid voor routegroepering aan om de routevergelijking voor geïmporteerde workouts te optimaliseren."*
  * **PL**: *"Dostosuj czułość grupowania tras, aby zoptymalizować dopasowywanie tras dla importowanych treningów."*
  * **PT**: *"Ajuste a sensibilidade do agrupamento de percursos para otimizar a correspondência de treinos importados."*

### 1.4 Backwards Compatibility & Persistence
* Retained `PreImportTuningDialog(...)` as an inline delegate to `PreImportTuningBottomSheet(...)`.
* Maintained all ViewModel persistence contracts (`viewModel.saveClusteringTolerances()`, `viewModel.bulkRecoverLegacyData(...)`, `viewModel.importLegacyFile(...)`).

---

## 2. Verification & Test Evidence

### 2.1 Automated Unit Tests
Executed dedicated unit tests verifying resource completeness and localization parity:
```bash
./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.migration.PreImportTuningBottomSheetTest
./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.localization.TranslationParityTest
```
**Results**:
* `PreImportTuningBottomSheetTest`: **PASSED** (100% of required string keys present across all 9 locales; localized descriptions verified).
* `TranslationParityTest`: **PASSED** (Zero missing translation keys across the application).
* Full project compilation: **BUILD SUCCESSFUL**.
