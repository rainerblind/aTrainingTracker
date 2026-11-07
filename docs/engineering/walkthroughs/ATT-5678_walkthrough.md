# Stage 5: Walkthrough & Verification - ATT-5678: Trademark-Free Physiology Terminology

**Ticket**: [ATT-5678](https://rainerblind.atlassian.net/browse/ATT-5678)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5678`  
**Requirement Mapping**: `REQ-UI-459` (`REQ-UI-459.1`–`REQ-UI-459.5`) | **Test Mapping**: `TST-UI-459` (`TST-UI-459.1`–`TST-UI-459.5`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Executive Summary & Verification Overview
Migrated all Peaksware/TrainingPeaks registered trademarks (`Normalized Power®` / `NP®`, `Intensity Factor®` / `IF®`, `Training Stress Score®` / `TSS®`, `Performance Management Chart®` / `PMC®`, and `CTL` / `ATL` / `TSB`) to trademark-free, descriptive terminology across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`):
* `Normalized Power` / `NP` $\rightarrow$ **Weighted Power** (**WP**) / DE: **Gewichtete Leistung**
* `Intensity Factor` / `IF` $\rightarrow$ **Relative Intensity** (**RI**) / DE: **Relative Intensität**
* `Training Stress Score` / `TSS` $\rightarrow$ **Training Load** (**Load**) / DE: **Trainingsbelastung**
* `Performance Management (PMC)` $\rightarrow$ **Training Load & Form** (**Load / Form**) / DE: **Belastungssteuerung**
* `Fitness (CTL)` / `Fatigue (ATL)` / `Form (TSB)` $\rightarrow$ **Fitness (42d)** / **Fatigue (7d)** / **Form (Balance)**
* Formats: `WP: %1$.0f W`, `RI: %1$.2f`, `Load: %1$.0f`, `%1$+.1f/wk` (DE: `%1$+.1f/Wo.`)

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-459.1` | `TST-UI-459.1` | `TranslationParityTest` (WP, RI, Load 9-locale parity) | **PASSED** | `Verified` |
| `REQ-UI-459.2` | `TST-UI-459.1` | `AthleteProfileContractAndLocalizationTest` (formats) | **PASSED** | `Verified` |
| `REQ-UI-459.3` | `TST-UI-459.1` | `AthleteProfileContractAndLocalizationTest` (Load & Form) | **PASSED** | `Verified` |
| `REQ-UI-459.4` | `TST-UI-459.2` | `AdvancedTuningAccordionTest`, `TipsGuideScreenContractTest` | **PASSED** | `Verified` |
| `REQ-UI-459.5` | `TST-UI-459.4` | Mathematical & schema invariance, zero emojis | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Runtime: 50s)
* **Executed Scope**:
  * `--tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.help.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.aftermath.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.*"`
  * `--tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
  * `--tests "com.atrainingtracker.trainingtracker.build.*"`
* **Result**: `BUILD SUCCESSFUL in 50s` (100% PASS across 33+ targeted tests).
* **Governance Check**: `python3 tools/verify_requirement_governance.py` -> `PASS: Living documentation master tables match all 9 domain shards with 100% parity.`

---

## 4. Key Implementation Highlights

```xml
<!-- values/strings.xml -->
<string name="normalized_power">Weighted Power</string>
<string name="normalized_power_short" translatable="false">WP</string>
<string name="intensity_factor">Relative Intensity</string>
<string name="intensity_factor_short" translatable="false">RI</string>
<string name="tss">Training Load</string>
<string name="tss_short" translatable="false">Load</string>

<!-- values-de/strings.xml -->
<string name="normalized_power">Gewichtete Leistung</string>
<string name="intensity_factor">Relative Intensität</string>
<string name="tss">Trainingsbelastung</string>
```

---

## 5. Invariants & Preserved Behavior
1. **Mathematical Invariance (`REQ-UI-459.5`)**: `PowerPhysiologyCalculator.kt` and `CyclingPowerMetricsDevice.java` formulas unchanged.
2. **Database & DTO Stability**: Internal SQLite columns, sensor enums, and DTO fields (`normalizedPowerWatts`, `tss`) remain 100% intact.
3. **Format Specifiers**: All indexed positional format specifiers (`%1$+.1f`, `%1$.0f`, `%1$.2f`) preserved across all 9 locales.
4. **Emoji Prohibition (`ATT-5400`)**: Zero decorative inline emojis in localized strings.
