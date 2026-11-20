# Stage 3 Implementation Plan — ATT-9177: Polar Verity Sense Optical PPI in Morning HRV Check & Baseline Isolation

- **Jira Ticket**: `ATT-9177` (2 SP, Epic `ATT-3539`)
- **Requirements / Tests**: `REQ-HRV-049` (`REQ-HRV-049.1`..`.4`) / `TST-HRV-049` (`TST-HRV-049.1`..`.4`)
- **Branch**: `feature/ATT-9177`
- **Prerequisite Boundary**: `ATT-9176` (`PolarOnlineStreamingBridge` optical PPI streaming) is `Zu erledigen`; `ATT-9177` adds `@JvmOverloads` `RrBeatEvent.isOpticalPpi: Boolean = false` and implements the complete Morning HRV optical UX, signal quality, DB v3 provenance, `.attbackup` symmetry, and SWC baseline isolation without modifying `PolarOnlineStreamingBridge.kt`.

---

## Atomic Implementation Steps

### Step 0: Gate 3 Verification & Code Lock Check
- Verify Gate 3 sign-off before editing any file under `app/`:
  `python3 tools/jira_util.py --as agent1 check-gate <IMPL_PLAN_SUBTASK_KEY>`
- Preserve the 4 permanent user-owned dirty files untouched; never run `git add -A`.

### Step 1: Backward-Compatible Optical PPI Beat Contract & Signal Quality Mode (`REQ-HRV-049.1`)
1. **`app/src/main/java/com/atrainingtracker/banalservice/devices/RrBeatEvent.kt`** (`47` LoC):
   - Convert primary constructor to `data class RrBeatEvent @JvmOverloads constructor(...)` appending `val isOpticalPpi: Boolean = false` (mandatory for Java 6-arg callers at `ANTHeartRateDevice.java:266, 283`).
2. **`app/src/main/java/com/atrainingtracker/banalservice/devices/hrv/HrvSignalQualityAnalyzer.kt`** (`263` LoC):
   - Add `@JvmOverloads` and optional `isOpticalPpi: Boolean = false` to `@JvmStatic fun analyze(...)` and `@JvmStatic fun analyzeBeats(...)`.
   - When `isOpticalPpi == true`: still enforce `INSUFFICIENT_DATA` (`n < MIN_BEATS`) and `HIGH_ARTIFACTS` (`effectiveArtifactRate > MAX_ARTIFACT_RATE_PERCENT`), then return `HrvSignalQualityResult(status = HrvSignalQualityStatus.VALID, details = "Valid optical PPI stream", ...)` bypassing ECG-specific `QUANTIZED_SYNTHETIC` and `SMOOTHED_STREAM` gates.

### Step 2: `HrvSensorModality`, SQLite v2->v3 Migration, `.attbackup` Parity & SWC Isolation (`REQ-HRV-049.3`, `REQ-HRV-049.4`)
1. **New `app/src/main/java/com/atrainingtracker/trainingtracker/database/hrv/HrvSensorModality.kt`** (~35 LoC):
   - `enum class HrvSensorModality { ECG, OPTICAL_PPI }` with `@JvmStatic fun fromNameOrDefault(name: String?): HrvSensorModality` returning `OPTICAL_PPI` for `"OPTICAL_PPI"` (case-insensitive) and defaulting to `ECG` for `null`/legacy/unknown values.
2. **`app/src/main/java/com/atrainingtracker/trainingtracker/database/hrv/HrvReadinessEntry.kt`** (`273` LoC):
   - Add `val sensorModality: HrvSensorModality = HrvSensorModality.ECG` to `HrvReadinessEntry`.
   - Update `toContentValues()`, `fromCursor()`, and `fromCursorBatch()` symmetrically (`Rule 20`) reading `HrvReadinessDbHelper.COLUMN_SENSOR_MODALITY` with graceful `ECG` fallback when absent on legacy cursors.
3. **`app/src/main/java/com/atrainingtracker/trainingtracker/database/hrv/HrvReadinessDatabaseManager.kt`** (`558` LoC — contains `HrvReadinessDbHelper`):
   - Bump `HrvReadinessDbHelper.DB_VERSION` `2 -> 3` (automatically tracked by `DatabaseSchemaRegistry.kt:50-51` per `Rule 62` and `.attbackup` via `DatabaseConnectionManager.kt:126`).
   - Add `COLUMN_SENSOR_MODALITY = "sensor_modality"` to `CREATE_TABLE_HRV_READINESS` as `$COLUMN_SENSOR_MODALITY TEXT NOT NULL DEFAULT 'ECG'`, and add `if (oldVersion < 3) { db.execSQL("ALTER TABLE $TABLE_HRV_READINESS ADD COLUMN $COLUMN_SENSOR_MODALITY TEXT NOT NULL DEFAULT 'ECG';") }` in `onUpgrade` so `onCreate (v3)`, `v1 -> v3`, and `v2 -> v3` produce identical `PRAGMA table_info` & indexes (`Rule 64`).
   - Scope `recomputePrimaryForDayInternal(db, dateIso, posture, sensorModality, pinnedId)` by `(date_iso, posture, sensor_modality)` (`AND sensor_modality = ?`), updating `insertEntry`, `deleteEntry`, `setPrimaryReading`, and `recomputePrimaryForDay(dateIso, posture, sensorModality = HrvSensorModality.ECG)`.
   - In `updateEntry(entry)`: capture `val previous = getEntryById(entry.id)` before updating; if `(previous.dateIso, previous.posture, previous.sensorModality) != (entry.dateIso, entry.posture, entry.sensorModality)`, recompute the old cohort `(previous.dateIso, previous.posture, previous.sensorModality, pinnedId = null)` in the same transaction.
   - Add optional `sensorModality: HrvSensorModality? = null` to `getPrimaryEntryForDate(...)`, `getPrimaryEntriesInWindow(...)`, and `getAllEntries(...)`: when `sensorModality != null`, filter with `AND sensor_modality = ?`; when `sensorModality == null` in `getPrimaryEntriesInWindow`, deduplicate by `entry.dateIso to entry.sensorModality`.
4. **`app/src/main/java/com/atrainingtracker/trainingtracker/database/hrv/HrvReadinessCalculator.kt`** (`213` LoC) & **`HrvReadinessRepository.kt`** (`105` LoC):
   - Add `@JvmOverloads` and `sensorModality: HrvSensorModality = HrvSensorModality.ECG` to `HrvReadinessCalculator.calculateReadiness(...)`, filtering strictly on `entry.posture == posture && entry.sensorModality == sensorModality`.
   - Update `HrvReadinessRepository.calculateReadiness(targetDateIso, posture, sensorModality = HrvSensorModality.ECG)` to query `dbManager.getPrimaryEntriesInWindow(posture, dMinus59Iso, targetDateIso, sensorModality)` and pass `sensorModality` to `HrvReadinessCalculator`.
5. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/HrvReadinessTrendMath.kt`** (`333` LoC):
   - Add `sensorModality: HrvSensorModality = HrvSensorModality.ECG`, `availableModalities: List<HrvSensorModality> = listOf(HrvSensorModality.ECG)`, and `seriesByModality: Map<HrvSensorModality, HrvTrendSeries> = emptyMap()` to `HrvTrendSeries`.
   - Update `calculateTrendSeries(..., selectedModality: HrvSensorModality? = null)` to partition valid posture entries strictly by `sensorModality`, defaulting to the dominant/available modality (e.g., `OPTICAL_PPI` for optical-only athletes, `ECG` when present or dominant) and computing isolated per-modality `HrvTrendSeries` entries in `seriesByModality` (shallow, non-recursive map) so ECG and optical SWC corridors never mix.

### Step 3: Morning HRV Optical UX, Trend Card Segmented Selector & 9-Locale Parity (`REQ-HRV-049.2`, `REQ-HRV-049.4`)
1. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/hrv/MorningHrvCheckViewModel.kt`** (`593` LoC):
   - Add `val isOpticalPpiSession: Boolean = false` to `MorningHrvUiState`.
   - In `handleRrBeat(event)`: during `READY`/`STABILIZING`, set `isOpticalPpiSession = event.isOpticalPpi` non-stickily (clearing optical mode immediately if an ECG chest strap takes over); during `MEASURING`, derive `isOpticalPpiSession = currentBeats.any { it.isOpticalPpi }`.
   - In `completeMeasurement()`: derive `val isOptical = beats.any { it.isOpticalPpi }`, `val modality = if (isOptical) HrvSensorModality.OPTICAL_PPI else HrvSensorModality.ECG`, pass `isOpticalPpi = isOptical` to `HrvSignalQualityAnalyzer.analyzeBeats(...)`, construct `HrvReadinessEntry(..., sensorModality = modality)`, compute preview readiness strictly against `existingEntries` filtered by `modality`, and pass `entry.sensorModality` in `saveReading()` -> `hrvReadinessRepository.calculateReadiness(entry.dateIso, entry.posture, entry.sensorModality)`.
2. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/hrv/MorningHrvOpticalCards.kt`** (new helper, ~110 LoC) & **`MorningHrvCheckScreen.kt`** (`1,366` LoC, MUST stay `<= 1,450` LoC):
   - Extract `OpticalPpiGuidanceCard` (stillness + snug armband skin contact + isolated baseline note, `testTag("morning_hrv_optical_guidance_card")`) and `OpticalPpiProvenanceBadge` (`testTag("morning_hrv_optical_provenance_badge")`) into `MorningHrvOpticalCards.kt` so `MorningHrvCheckScreen.kt` stays well under `1,450` LoC.
   - In `MorningHrvCheckScreen.kt`: render `OpticalPpiGuidanceCard` when `!isPureWellnessMode && uiState.isOpticalPpiSession`, pass `isOpticalPpi = uiState.isOpticalPpiSession` to `ArtifactWarningCard` (selecting optical armband fit/stillness copy instead of wet ECG chest-strap copy), render `OpticalPpiProvenanceBadge` on `ReadinessResultCard` when `entry.sensorModality == HrvSensorModality.OPTICAL_PPI`, and add `private fun` `@Preview` composables for Light/Dark optical states.
3. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/HrvReadinessTrendCard.kt`** (`743` LoC; **`0` edits to `AthleteProfileScreen.kt` `1,975/1,975` LoC**):
   - Hold remembered `activeModality` keyed on `(series.posture, series.availableModalities)` defaulting to `series.sensorModality`, resolving `displayedSeries = series.seriesByModality[activeModality] ?: series`.
   - When `series.availableModalities.size > 1`, render a Material 3 **`SingleChoiceSegmentedButtonRow` (`icon = {}`, `docs/design_guidelines.md` §1)** (`testTag("hrv_trend_modality_selector")`) toggling between `ECG` (`R.string.hrv_modality_ecg_short`) and `Optical PPI` (`R.string.hrv_modality_optical_ppi_short`).
   - When `displayedSeries.sensorModality == HrvSensorModality.OPTICAL_PPI` (and posture != `OVERNIGHT_WEARABLE`), display a compact `Optical PPI` provenance badge (`testTag("hrv_trend_optical_badge")`).
4. **9-Language Localization Parity (`app/src/main/res/values*/strings_hrv.xml` — `en, de, es, fr, it, ja, nl, pl, pt`)**:
   - Add translated strings across all 9 locales: `morning_hrv_optical_guidance_title`, `morning_hrv_optical_guidance_desc`, `morning_hrv_optical_artifact_warning_desc`, `hrv_modality_ecg_short`, `hrv_modality_optical_ppi_short`, `hrv_modality_optical_ppi_badge`.
5. **Protected Unmodified Files (`0` edits)**: `AthleteProfileScreen.kt`, `ControlTrackingViewModel.kt`, `ANTHeartRateDevice.java`, `PolarOnlineStreamingBridge.kt`.

### Step 4: Targeted Unit, SQLite Migration, Localization & Rule 8.5 Compose UI Tests (`TST-HRV-049.1`..`.4`)
- Every new test includes explicit hyphenated `// REQ-HRV-049.x / TST-HRV-049.x` tags (`REQ-PRO-099.3`), `0` `src/main` reads (`SourceTextReadingAuditContractTest`), and `private fun` `@Preview`s (`DeadCodeEliminationContractTest`).
- Extend:
  - `HrvSignalQualityAnalyzerTest`: optical PPI low-variance/integer-ms -> `VALID`, `< 30` -> `INSUFFICIENT_DATA`, `> 5.0%` -> `HIGH_ARTIFACTS`, ECG regression unchanged.
  - `HrvReadinessEntryTest`: `sensorModality` round-trip (`toContentValues`, `fromCursor`, `fromCursorBatch`, legacy missing column -> `ECG`).
  - `HrvReadinessDatabaseManagerTest`: `v1 -> v3`, `v2 -> v3`, and `onCreate (v3)` exact `PRAGMA table_info` & index parity (`Rule 64`); same-day ECG + Optical independent primary flags; `updateEntry` moving `(dateIso, posture, sensorModality)` recomputes old cohort; `getPrimaryEntriesInWindow(..., sensorModality)` SQL filtering.
  - `DatabaseSchemaRegistryHrvTest`: assert `v1`/`v2`/`v3` supported, `v4` rejected (`.attbackup` parity).
  - `HrvReadinessCalculatorTest` & `HrvReadinessRepositoryTest`: ECG 60d SWC baseline untouched by optical readings; optical cohort isolated.
  - `MorningHrvCheckViewModelTest`: non-sticky Verity Sense -> H10 switch in `READY`/`STABILIZING`, optical completion saves `OPTICAL_PPI` with `VALID` status (no smoothed-ECG alert), `> 5.0%` artifact warning preserved.
  - `ControlTrackingViewModelTest`: paired Polar Verity Sense (`DeviceType.HRM`) keeps Start-screen Morning HRV CTA visible and hides after optical check completion.
  - `HrvReadinessTrendMathTest` & `HrvReadinessTrendCardTest`: ECG vs Optical SWC isolation, optical-only default cohort, `SingleChoiceSegmentedButtonRow` (`icon = {}`) cohort toggle & provenance badge.
  - `MorningHrvCheckScreenTest`: optical guidance card, optical armband artifact copy, `Optical PPI` result provenance badge, and deterministic side-by-side Light/Dark PNG capture strictly to **`docs/engineering/walkthroughs/img/ATT-9177_morning_hrv_optical_ppi.png`** (`0` edits to legacy PNGs!).

### Step 5: Locked Verification, Rule 8.5 Inspection & `[Implemented]` Promotion
1. Run locked incremental Gradle test matrix (NEVER `--rerun-tasks`, NEVER `-PstampGitHash=true`):
   `python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --rerun --tests "*HrvSignalQualityAnalyzerTest" --tests "*HrvReadinessEntryTest" --tests "*HrvReadinessDatabaseManagerTest" --tests "*DatabaseSchemaRegistryHrvTest" --tests "*HrvReadinessCalculatorTest" --tests "*HrvReadinessRepositoryTest" --tests "*MorningHrvCheckViewModelTest" --tests "*MorningHrvCheckScreenTest" --tests "*HrvReadinessTrendMathTest" --tests "*HrvReadinessTrendCardTest" --tests "*ControlTrackingViewModelTest" --tests "*TranslationParityTest" --tests "*ArchitectureConformanceContractTest" --tests "*DeadCodeEliminationContractTest" --tests "*SourceTextReadingAuditContractTest"`
2. Inspect `docs/engineering/walkthroughs/img/ATT-9177_morning_hrv_optical_ppi.png` via `read_file` (Rule 8.5 4-point visual audit).
3. Promote `REQ-HRV-049` and `TST-HRV-049` to `[Implemented]`, run `--sync-indexes` and BOTH `verify_requirement_governance.py` modes (`--base-ref` + clean-checkout `--check-verified-evidence`).
