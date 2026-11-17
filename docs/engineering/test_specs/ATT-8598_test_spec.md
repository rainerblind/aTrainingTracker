# Stage 2: Requirement & Test Specification - ATT-8598

**Ticket**: [ATT-8598](https://rainerblind.atlassian.net/browse/ATT-8598) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_02_Spät` | **Branch**: `feature/ATT-8598`  
**Requirement Mapping**: `REQ-HRV-043` | **Test Spec ID**: `TST-HRV-043` | **Date**: 2026-11-17  

---

## 1. Requirement Specification (`REQ-HRV-043`)

### 1.1 Attributes & Rationale
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
- **Rationale**: When `showMorningCheckOnStartScreen = false` and `enableMorningSleepAndWellness = true`, `MorningHrvCheckScreen` still renders HRV protocol/posture/duration/pacing cards, HRV TopAppBar metadata, and *Start Measurement* above `MorningWellnessInputCard`.

### 1.2 Functional & Architectural Requirements
1. **`REQ-HRV-043.1` (ViewModel Flag)**: `MorningHrvUiState` SHALL expose `showMorningCheckOnStartScreen` from `tuningConfigFlow`. Pure wellness mode is `!showMorningCheckOnStartScreen && enableMorningSleepAndWellness && phase == MorningHrvPhase.READY`.
2. **`REQ-HRV-043.2` (Pure Wellness Mode & Empty-State)**: In pure wellness mode, `MorningHrvCheckScreen` SHALL hide `WakingProtocolChecklistCard`, `PostureAndDurationCard`, `MeasurementPacingCard`, TopAppBar HRV info button (`HrvInfoBottomSheet`), and `morning_hrv_btn_start`; TopAppBar SHALL display `hrv_morning_check_start_button` + `morning_hrv_wellness_subtitle`; `ActionButtonsSection` SHALL promote `morning_hrv_btn_save_wellness` to primary filled `Button` (green *Saved* state when `isWellnessSaved`); if all 4 wellness sub-toggles are `false`, `MorningWellnessInputCard` SHALL render `morning_hrv_wellness_empty_hint`.
3. **`REQ-HRV-043.3` (HRV & Profile Fallbacks)**: When `showMorningCheckOnStartScreen == true`, OR `!enableMorningSleepAndWellness` (e.g., `AthleteProfileScreen` `hrv_measure_morning_cta`), OR `phase != MorningHrvPhase.READY` (`STABILIZING`/`MEASURING`/`COMPLETED`), full HRV (+ optional Wellness) UI SHALL remain intact.
4. **`REQ-HRV-043.4` (Localization & Previews)**: New strings in `strings_hrv.xml` SHALL have 100% 9-locale parity (zero emojis) and `MorningHrvCheckContent` SHALL include Light/Dark/AMOLED `@Preview`s.

### 1.3 Acceptance Criteria & Invariants
- *Given* `showMorningCheckOnStartScreen = false`, `enableMorningSleepAndWellness = true`, `phase = READY`, *When* opening `MorningHrvCheckScreen`, *Then* only wellness TopAppBar, `MorningWellnessInputCard`, and primary *Save Morning Wellness* button render.
- *Given* `showMorningCheckOnStartScreen = true` OR `!enableMorningSleepAndWellness` OR `phase = MEASURING`, *When* rendering, *Then* full HRV UI remains visible.
- **Invariants**: Zero DB schema changes; `ControlTrackingViewModel`/`MorningCheckSection` untouched; `keepScreenOn` & standalone wellness persistence (`rmssdMs = null`) preserved.

---

## 2. Test Specification (`TST-HRV-043`)

- **`TST-HRV-043.1` (`MorningHrvCheckViewModelTest.kt`)**: Verify `tuningConfigFlow` updates `uiState.showMorningCheckOnStartScreen`; verify `saveStandaloneWellness()` persists `rmssdMs == null` and sets `isWellnessSaved == true`.
- **`TST-HRV-043.2` (`MorningHrvCheckScreenTest.kt`)**: Render `MorningHrvCheckContent` in pure wellness mode: assert wellness title/subtitle, `MorningWellnessInputCard`, primary `Save Morning Wellness` button (`Saved` state when `isWellnessSaved = true`), and absence of protocol/posture/duration/pacing cards, info button, and `Start Measurement`. Assert `morning_hrv_wellness_empty_hint` when all 4 sub-toggles are `false`.
- **`TST-HRV-043.3` (`MorningHrvCheckScreenTest.kt`)**: Assert full HRV layout renders when (a) `showMorningCheckOnStartScreen = true`, (b) `showMorningCheckOnStartScreen = false && !enableMorningSleepAndWellness`, and (c) `phase = MEASURING`.
- **`TST-HRV-043.4` (9-Locale Parity & Rule 8.5 PNG)**: Verify `TranslationParityTest` + zero emojis for `morning_hrv_wellness_subtitle` and `morning_hrv_wellness_empty_hint` across all 9 locales, and inspect `docs/engineering/walkthroughs/img/ATT-8598_morning_wellness_mode.png` (Light & Dark):

| Locale | `morning_hrv_wellness_subtitle` | `morning_hrv_wellness_empty_hint` |
| :--- | :--- | :--- |
| `values` (EN) | Subjective sleep & readiness check | No wellness metrics enabled in Expert Settings. |
| `values-de` (DE) | Subjektiver Schlaf- & Bereitschaftscheck | Keine Wohlbefindens-Metriken in den Experteneinstellungen aktiviert. |
| `values-es` (ES) | Registro subjetivo de sueño y preparación | No hay métricas de bienestar activadas en Ajustes avanzados. |
| `values-fr` (FR) | Suivi subjectif du sommeil et de la forme | Aucune métrique de bien-être activée dans les paramètres experts. |
| `values-it` (IT) | Controllo soggettivo di sonno e recupero | Nessuna metrica di benessere attivata nelle impostazioni avanzate. |
| `values-ja` (JA) | 主観的な睡眠とコンディションの記録 | 詳細設定でウェルネス項目が有効になっていません。 |
| `values-nl` (NL) | Subjectieve slaap- & herstelcheck | Geen welzijnsstatistieken ingeschakeld in Expertinstellingen. |
| `values-pl` (PL) | Subiektywna ocena snu i gotowości | Brak włączonych metryk samopoczucia w ustawieniach zaawansowanych. |
| `values-pt` (PT) | Registro subjetivo de sono e prontidão | Nenhuma métrica de bem-estar ativada nas configurações avançadas. |

- **`TST-HRV-043.5` (Tier 1 Regression)**: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.hrv.MorningHrvCheckViewModelTest" --tests "com.atrainingtracker.trainingtracker.ui.hrv.MorningHrvCheckScreenTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-HRV-043.1` | Unit (VM) | `MorningHrvCheckViewModel` | `REQ-HRV-043.1` | Specified |
| `TST-HRV-043.2` | Compose UI | `MorningHrvCheckContent` (Pure Wellness) | `REQ-HRV-043.2` | Specified |
| `TST-HRV-043.3` | Compose UI | `MorningHrvCheckContent` (HRV & Fallbacks) | `REQ-HRV-043.3` | Specified |
| `TST-HRV-043.4` | L10n & PNG | `TranslationParityTest`, `MorningHrvCheckScreenTest` | `REQ-HRV-043.4`, `REQ-UI-106` | Specified |
| `TST-HRV-043.5` | Regression | Tier 1 HRV + Build Contract Suite | `REQ-PRO-001` | Specified |
