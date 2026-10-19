# Stage 3: Implementation Plan - ATT-4049: Support selecting and importing multiple GPX route files at once

**Ticket**: [ATT-4049](https://atrainingtracker.atlassian.net/browse/ATT-4049)  
**Sub-task**: [ATT-4278](https://atrainingtracker.atlassian.net/browse/ATT-4278) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-MAP-053` (*Multi-File Route Selection, Batch Import & External Multi-Share Processing*)  
**Test Mapping**: `TST-MAP-055` (*Multi-File Route Selection, Batch Import & External Multi-Share Verification Suite*)  
**Branch**: `feature/ATT-4049`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

Currently, route file import from the Routes screen (`RoutesScreen.kt:67-74`) relies on `ActivityResultContracts.GetContent()`, allowing selection of only one GPX/TCX file at a time and launching `GpxImportActivity` with that single `Uri`. When an athlete transfers a set of route files (e.g. 5–20 stages of a cycling holiday or trail network), selecting and confirming each file individually through the interactive `EditRouteScreen` is tedious and repetitive.

This improvement introduces:
1. **Multi-File Document Picker**: Replaces `GetContent()` with `ActivityResultContracts.GetMultipleContents()`, returning `List<Uri>`.
2. **Single vs. Batch Route Import Discrimination**:
   - *Single File (`uris.size == 1`)*: Preserves the existing interactive `GpxImportActivity` -> `EditRouteScreen` preview and editing flow.
   - *Multiple Files (`uris.size > 1`)*: Executes a headless background batch import on `Dispatchers.IO` via `RoutesViewModel.importRoutesBatch(uris)` with a modal progress indicator and completion Snackbar.
3. **Resilience & Fault Tolerance**: Corrupt or malformed files are isolated and counted as failures without halting the batch.
4. **External Multi-Share Support**: Extends `GpxImportActivity` and `AndroidManifest.xml` to handle `Intent.ACTION_SEND_MULTIPLE`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-053` (*Multi-File Route Selection, Batch Import & External Multi-Share Processing*)
  * `REQ-MAP-053.1`: Multi-File Picker Contract: `RoutesScreen.kt` uses `ActivityResultContracts.GetMultipleContents()` accepting `*/*`.
  * `REQ-MAP-053.2`: Single vs. Batch Route Import Flow Discrimination: `uris.size == 1` launches `GpxImportActivity`; `uris.size > 1` invokes `RoutesViewModel.importRoutesBatch(uris)`.
  * `REQ-MAP-053.3`: Batch Import Execution & State Flows in `RoutesViewModel`: `batchImportProgress`, `batchImportResult`, `clearBatchImportResult()`, constructor injection of `GpxRouteImporter`.
  * `REQ-MAP-053.4`: Modal Progress Dialog & Summary Snackbar in `RoutesScreen`: Modal dialog showing `X of N`, localized completion Snackbar.
  * `REQ-MAP-053.5`: External Multi-File Share Support in `GpxImportActivity`: `ACTION_SEND_MULTIPLE` intent filter and batch stream processing in `GpxImportViewModel`.
  * `REQ-MAP-053.6`: 9-Language Localization Parity: All 4 new string tokens defined across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Test Mapping**: `TST-MAP-055`
  * `TST-MAP-055.1`: All valid files batch import test in `RoutesViewModelTest.kt`.
  * `TST-MAP-055.2`: Partial failure batch import test in `RoutesViewModelTest.kt`.
  * `TST-MAP-055.3`: All files failed test in `RoutesViewModelTest.kt`.
  * `TST-MAP-055.4`: Empty URI list test in `RoutesViewModelTest.kt`.
  * `TST-MAP-055.5`: Clear batch import result test in `RoutesViewModelTest.kt`.
  * `TST-MAP-055.6`: Multi-URI batch handling in `GpxImportViewModelTest.kt`.
  * `TST-MAP-055.7`: 9-language translation parity in `TranslationParityTest.kt`.
  * `TST-MAP-055.8`: Clean-room full regression suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Single-File Preview Flow Invariant**: When selecting exactly 1 file, `GpxImportActivity` and `EditRouteScreen` are launched immediately, preserving full user customization (name, sport type).
2. **Data & Pipeline Integrity Invariant**: All routes imported via batch undergo the exact same validation, distance calculation, DEM elevation enrichment, and climb detection (`ClimbDetector.detectClimbs`) as single imports via `RoutesRepository.insertRoute(...)`.
3. **Non-Blocking UI Invariant**: All batch parsing, network DEM lookups, and SQLite writes execute asynchronously on `Dispatchers.IO` via `viewModelScope`, ensuring the main looper is never blocked.
4. **Fault Tolerance Invariant**: Malformed or unparseable files in a batch must never throw uncaught exceptions or abort processing of subsequent valid files.
5. **Human Decision Gate**: No parent ticket is moved to `Erledigt` autonomously.

---

## 4. Proposed Architectural Changes (SWE.2)

### 4.1 `RoutesViewModel.kt`
- Add data classes:
  ```kotlin
  data class BatchImportProgress(val current: Int, val total: Int)
  data class BatchImportResult(val importedCount: Int, val failedCount: Int, val totalCount: Int)
  ```
- Add constructor dependency with `@JvmOverloads`:
  ```kotlin
  class RoutesViewModel @JvmOverloads constructor(
      application: Application,
      private val routesRepository: RoutesRepository = RoutesRepository.getInstance(application),
      private val banalServiceRepository: BANALServiceRepository = BANALServiceRepository.getInstance(application),
      private val segmentsRepository: SegmentsRepository = SegmentsRepository.getInstance(application),
      private val gpxRouteImporter: GpxRouteImporter = GpxRouteImporter(application)
  )
  ```
- Add StateFlows and methods:
  ```kotlin
  private val _batchImportProgress = MutableStateFlow<BatchImportProgress?>(null)
  val batchImportProgress: StateFlow<BatchImportProgress?> = _batchImportProgress.asStateFlow()

  private val _batchImportResult = MutableStateFlow<BatchImportResult?>(null)
  val batchImportResult: StateFlow<BatchImportResult?> = _batchImportResult.asStateFlow()

  fun clearBatchImportResult() {
      _batchImportResult.value = null
  }

  fun importRoutesBatch(uris: List<Uri>) {
      if (uris.isEmpty()) return
      viewModelScope.launch {
          var imported = 0
          var failed = 0
          val total = uris.size
          for ((index, uri) in uris.withIndex()) {
              _batchImportProgress.value = BatchImportProgress(current = index + 1, total = total)
              try {
                  val result = gpxRouteImporter.importRouteFromGpx(uri)
                  if (result.isSuccess) {
                      val data = result.getOrNull()
                      if (data != null) {
                          routesRepository.insertRoute(data.summary, data.pathPoints, data.waypoints)
                          imported++
                      } else {
                          failed++
                      }
                  } else {
                      failed++
                  }
              } catch (e: Exception) {
                  failed++
              }
          }
          _batchImportProgress.value = null
          _batchImportResult.value = BatchImportResult(importedCount = imported, failedCount = failed, totalCount = total)
      }
  }
  ```

### 4.2 `RoutesScreen.kt` & `RoutesFragment.kt`
- In `RoutesScreen.kt`:
  - Replace `ActivityResultContracts.GetContent()` with `ActivityResultContracts.GetMultipleContents()`:
    ```kotlin
    val gpxPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        if (uris.size == 1) {
            val intent = Intent(context, GpxImportActivity::class.java).apply {
                data = uris.first()
            }
            context.startActivity(intent)
        } else {
            viewModel.importRoutesBatch(uris)
        }
    }
    ```
  - Collect `batchImportProgress` and `batchImportResult`:
    ```kotlin
    val batchImportProgress by viewModel.batchImportProgress.collectAsStateWithLifecycle()
    val batchImportResult by viewModel.batchImportResult.collectAsStateWithLifecycle()
    ```
  - Render modal progress `Dialog`:
    ```kotlin
    if (batchImportProgress != null) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(
                            R.string.routes_import_progress,
                            batchImportProgress!!.current,
                            batchImportProgress!!.total
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
    ```
  - Display summary `Snackbar` when `batchImportResult != null`:
    ```kotlin
    LaunchedEffect(batchImportResult) {
        batchImportResult?.let { result ->
            val message = when {
                result.failedCount == 0 -> context.getString(R.string.routes_import_success, result.importedCount)
                result.importedCount > 0 -> context.getString(R.string.routes_import_partial, result.importedCount, result.failedCount)
                else -> context.getString(R.string.routes_import_all_failed)
            }
            snackbarHostState.showSnackbar(message)
            viewModel.clearBatchImportResult()
        }
    }
    ```
- In `RoutesFragment.kt`:
  - Remove dead orphaned `val gpxPickerLauncher` registration and clean up unused imports.

### 4.3 `GpxImportActivity.kt` & `GpxImportViewModel.kt`
- In `GpxImportViewModel.kt`:
  - Add sealed states:
    ```kotlin
    data class BatchProgress(val current: Int, val total: Int) : ImportState()
    data class BatchSuccess(val importedCount: Int, val failedCount: Int) : ImportState()
    ```
  - Add `handleBatchUris(uris: List<Uri>)`:
    ```kotlin
    fun handleBatchUris(uris: List<Uri>) {
        if (uris.isEmpty()) {
            uiState = ImportState.Error("No files provided")
            return
        }
        if (uris.size == 1) {
            handleIntent(uris.first())
            return
        }
        viewModelScope.launch {
            var imported = 0
            var failed = 0
            val total = uris.size
            for ((index, uri) in uris.withIndex()) {
                uiState = ImportState.BatchProgress(index + 1, total)
                try {
                    val result = importer.importRouteFromGpx(uri)
                    if (result.isSuccess) {
                        val data = result.getOrNull()
                        if (data != null) {
                            repository.insertRoute(data.summary, data.pathPoints, data.waypoints)
                            imported++
                        } else {
                            failed++
                        }
                    } else {
                        failed++
                    }
                } catch (e: Exception) {
                    failed++
                }
            }
            uiState = ImportState.BatchSuccess(imported, failed)
        }
    }
    ```
- In `GpxImportActivity.kt`:
  - Parse intent to extract `uris: List<Uri>`:
    ```kotlin
    val uris: List<Uri> = when (intent.action) {
        Intent.ACTION_SEND_MULTIPLE -> {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.filterNotNull() ?: emptyList()
        }
        else -> {
            val single = intent.data ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            listOfNotNull(single)
        }
    }
    if (uris.size > 1) {
        viewModel.handleBatchUris(uris)
    } else {
        viewModel.handleIntent(uris.firstOrNull())
    }
    ```
  - In `setContent`:
    - Handle `ImportState.BatchProgress`: render `CircularProgressIndicator` with `routes_import_progress`.
    - Handle `ImportState.BatchSuccess`: show Toast with `routes_import_success` or `routes_import_partial` and call `finish()`.
- In `AndroidManifest.xml`:
  - Add `<action android:name="android.intent.action.SEND" />` and `<action android:name="android.intent.action.SEND_MULTIPLE" />` to `GpxImportActivity`.

---

## 5. UI Consistency (Rule 23)

- **Reference Screens**: `RoutesScreen.kt` and `GpxImportActivity.kt`.
- **Reused Components**: Material 3 `Dialog`, `Surface`, `CircularProgressIndicator`, `SnackbarHost`.
- **Theme Tokens**:
  - `shape = RoundedCornerShape(16.dp)` matching standard M3 dialog containers.
  - `color = MaterialTheme.colorScheme.surface`, `tonalElevation = 6.dp`.
  - Spacing: `24.dp` container padding, `16.dp` spacer between progress indicator and text.
  - Typography: `MaterialTheme.typography.bodyMedium`, `color = MaterialTheme.colorScheme.onSurface`.
  - Contrast: Compliant with AMOLED true-black and light themes.

---

## 6. Step-by-Step Implementation Sequence

### Step 1: 9-Language Localization Tokens
- Add the following strings across all 9 locales:
  - `routes_import_progress` (format `%1$d of %2$d`)
  - `routes_import_success` (format `%1$d routes imported successfully.`)
  - `routes_import_partial` (format `%1$d routes imported, %2$d failed.`)
  - `routes_import_all_failed` (`Failed to import routes.`)

### Step 2: `RoutesViewModel.kt` Enhancement
- Implement `BatchImportProgress`, `BatchImportResult`, `batchImportProgress`, `batchImportResult`, `clearBatchImportResult()`, `importRoutesBatch(uris: List<Uri>)`.
- Add `@JvmOverloads` constructor.

### Step 3: `RoutesScreen.kt` & `RoutesFragment.kt` Updates
- Modernize picker launcher to `GetMultipleContents()`.
- Wire `uris.size == 1` vs `uris.size > 1` dispatch.
- Add progress `Dialog` and summary `Snackbar`.
- Remove dead registration code from `RoutesFragment.kt`.

### Step 4: `GpxImportViewModel.kt`, `GpxImportActivity.kt`, and `AndroidManifest.xml`
- Implement `BatchProgress` and `BatchSuccess` states in `GpxImportViewModel`.
- Handle `ACTION_SEND_MULTIPLE` in `GpxImportActivity`.
- Add intent filters in `AndroidManifest.xml`.

### Step 5: Unit Tests (`RoutesViewModelTest.kt` & `GpxImportViewModelTest.kt`)
- Write tests `TST-MAP-055.1` through `TST-MAP-055.6`.
- Verify with targeted test execution:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.*"`

### Step 6: Localization Parity Verification
- Run `TranslationParityTest.kt` to ensure zero missing keys or format specifier mismatches.

### Step 7: Clean-Room Full Suite Regression
- Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 7. Rollback & Contingency Strategy

If any regression occurs during batch import:
- `RoutesScreen.kt` can immediately revert to `ActivityResultContracts.GetContent()` without altering the database or repository layer.
- All changes are contained within the `feature/ATT-4049` branch until Gate 5 validation is completed.
