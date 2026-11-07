# Stage 3: Implementation Plan - ATT-5677: Brand Compliance & Feature Title Neutralization

**Ticket**: [ATT-5677](https://rainerblind.atlassian.net/browse/ATT-5677)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5677`  
**Requirement Mapping**: `REQ-UI-460` (`REQ-UI-460.1`–`REQ-UI-460.4`) | **Test Mapping**: `TST-UI-460` (`TST-UI-460.1`–`TST-UI-460.4`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Description & Background
To comply with official Strava API Brand Guidelines (*"Never use Strava in the title of your app or feature"*) and eliminate third-party hardware trademarks (*"Garmin, Wahoo, Hammerhead"*) that risk Google Play automated metadata rejections, aTrainingTracker must neutralize `tip_live_segments_title`, `segments`, and `import_fit_description` across all 9 supported application locales while preserving factual Strava integration descriptions and maintaining 100% navigation routing stability.

---

## 2. Invariants & Preserved Behavior
1. **Factual Strava Integration (`REQ-UI-460.1`)**: Body description `tip_live_segments_desc` retains factual reference to Strava starred segment sync; Strava OAuth flows remain 100% operational.
2. **Navigation Contract Stability (`REQ-UI-460.2`)**: `NavRoutes.SEGMENTS` and drawer menu binding (`R.id.drawer_segments`) remain strictly unchanged.
3. **9-Language Parity & Non-Empty Values (`REQ-UI-460.4`)**: All 3 keys exist and are non-empty across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
4. **Emoji Prohibition (`ATT-5400`)**: Zero decorative inline emojis in updated strings.

---

## 3. UI Consistency & Architectural Strategy
* **Reference Screens**: `AppNavigationDrawer.kt`, `SegmentsTabsScreen.kt`, `TipsGuideScreen.kt`, `ImportBackupTabsScreen.kt`.
* **Theme Tokens**: Standard Material 3 typography and semantic colors (`MaterialTheme.colorScheme.*`). Drawer items and top app bars render neutral titles cleanly without text clipping.
* **New One-Off Styles**: None. Pure string resource updates and regression test validation.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Base English String Resources (`REQ-UI-460.1`–`REQ-UI-460.3`)
* **File**: `app/src/main/res/values/strings.xml`
* **Changes**:
  - `tip_live_segments_title`: Update to `"Live Segments &amp; Scrubbing"`.
  - `segments`: Update to `"Segments"`.
  - `import_fit_description`: Update to `"Import workouts from bike computers, sports watches, or smartwatches (.fit)."`.

### Step 2: 8 Localized Resource Files Parity (`REQ-UI-460.1`–`REQ-UI-460.3`)
* **Files**: `app/src/main/res/values-{de,es,fr,it,ja,nl,pl,pt}/strings.xml`
* **Changes**: Update corresponding 3 keys in each locale:
  - `de`: `"Live-Segmente &amp; Scrubbing"`, `"Segmente"`, `"Aktivitäten von Fahrradcomputern, Sportuhren oder Smartwatches importieren (.fit)."`
  - `es`: `"Segmentos en vivo e interactividad"`, `"Segmentos"`, `"Importar entrenamientos desde ciclocomputadores, relojes deportivos o relojes inteligentes (.fit)."`
  - `fr`: `"Segments en direct et défilement"`, `"Segments"`, `"Importer des entraînements depuis des compteurs vélo, montres de sport ou montres connectées (.fit)."`
  - `it`: `"Segmenti Live e scorrimento"`, `"Segmenti"`, `"Importa allenamenti da ciclocomputer, orologi sportivi o smartwatch (.fit)."`
  - `ja`: `"Live Segmentsと標高スクラブ"`, `"セグメント"`, `"サイクルコンピュータ、ランニングウォッチ、スマートウォッチからワークアウトをインポート (.fit)。"`
  - `nl`: `"Live Segments &amp; scrubbing"`, `"Segmenten"`, `"Importeer trainingen van fietscomputers, sporthorloges of smartwatches (.fit)."`
  - `pl`: `"Segmenty na żywo i interaktywność"`, `"Segmenty"`, `"Importuj treningi z liczników rowerowych, zegarków sportowych lub smartwatchy (.fit)."`
  - `pt`: `"Segmentos ao vivo e navegação"`, `"Segmentos"`, `"Importar treinos de ciclocomputadores, relógios esportivos ou smartwatches (.fit)."`

### Step 3: Localization & Navigation Contract Verification (`TST-UI-460.1`–`TST-UI-460.3`)
* **Tests**: Run `TranslationParityTest`, `ImportFormatLocalizationTest`, `TipsGuideScreenContractTest`, `SingleActivityNavigationTest`.

---

## 5. Verification Commands (`TST-UI-460.4`)
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.migration.ImportFormatLocalizationTest" --tests "com.atrainingtracker.trainingtracker.ui.help.TipsGuideScreenContractTest" --tests "com.atrainingtracker.trainingtracker.ui.navigation.SingleActivityNavigationTest"`
* `python3 tools/verify_requirement_governance.py`
