# Stage 2: Requirement & Test Specification - ATT-5570: Strava Pro Detection for Garage League & Dialog Copy

**Ticket**: [ATT-5570](https://rainerblind.atlassian.net/browse/ATT-5570)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (Good and consistent UI) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5570`  
**Requirement Mapping**: `REQ-SET-088` (`REQ-SET-088.1`, `REQ-SET-088.5`) | **Test Spec ID**: `TST-SET-088` (`TST-SET-088.1`, `TST-SET-088.7`), `TST-LOC-001`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Requirement Specification (`REQ-SET-088`)

### 1.1 Problem Statement & Rationale
Athletes with an active Strava Pro subscription (`summit == true` or `premium == true`) already invest in premium fitness software. The system SHALL ingest this subscriber flag, augment `GarageLeagueDetector` (Gold League qualification on Strava Pro alone; Diamond League on Gold hardware + Strava Pro), and present tailored dialog copy referencing Strava Pro status in `GarageUpgradeNudgeDialog`.

### 1.2 Functional Specification
1. **Preference Persistence & Null Safety (`Rule 75`)**: `TrainingApplication.java` SHALL expose `isStravaSubscriber(): Boolean` and `setStravaSubscriber(Boolean)` with `cSharedPreferences != null` guards. `deleteStravaToken()` and `StravaDataPurgeManager.executeLocalDataPurge()` MUST reset it to `false`.
2. **Telemetry Ingestion**: `StravaHelper.storeJSONData` and `StravaEquipmentSynchronizeThread.fillDbFromJsonObject` SHALL read `athlete.optBoolean("summit") || athlete.optBoolean("premium")` and persist via `TrainingApplication`.
3. **Garage League Booster**: `GarageStats` SHALL include `val isStravaSubscriber: Boolean = false`. In `GarageNudgeEvaluator.evaluateLeague()`:
   - **DIAMOND**: `hardwareDiamond || (hardwareGold && isStravaSubscriber)`.
   - **GOLD**: `hardwareGold || isStravaSubscriber`.
4. **Tailored Dialog Copy**: If `isStravaSubscriber == true`, `GarageUpgradeNudgeDialog` SHALL use `garage_detector_dialog_message_gold_strava` or `garage_detector_dialog_message_diamond_strava` with zero inline emojis and 5 positional specifiers (`%1$d`..`%5$s`).

### 1.3 9-Language Localization Parity

| String Key | EN | DE | ES | FR | IT | JA | NL | PL | PT |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `garage_detector_dialog_message_gold_strava` | As an active Strava Pro athlete with %1$d bikes, %2$d shoes, and %3$d sensors (incl. %4$d power meters), you take your training seriously! Your app status is still %5$s. Complete your pro ecosystem with Gold Pro+ Supporter! | Als aktiver Strava-Pro-Athlet mit %1$d Fahrrädern, %2$d Laufschuhen und %3$d Sensoren (inkl. %4$d Powermetern) nimmst du dein Training ernst! Dein App-Status steht aber noch auf %5$s. Komplettiere dein Profi-Setup mit Gold Pro+ Supporter! | Como atleta activo de Strava Pro con %1$d bicicletas, %2$d zapatillas y %3$d sensores (incl. %4$d potenciómetros), ¡te tomas tu entrenamiento en serio! Tu estado en la app es %5$s. ¡Completa tu ecosistema profesional con Partidario Oro Pro+! | En tant qu\'athlète actif Strava Pro avec %1$d vélos, %2$d chaussures et %3$d capteurs (dont %4$d capteurs de puissance), vous prenez votre entraînement au sérieux ! Votre statut est encore %5$s. Complétez votre écosystème pro avec Supporter Or Pro+ ! | Come atleta attivo Strava Pro con %1$d bici, %2$d scarpe e %3$d sensori (inclusi %4$d potenziometri), prendi sul serio i tuoi allenamenti! Il tuo stato è ancora %5$s. Completa il tuo ecosistema professionale con Sostenitore Oro Pro+! | アクティブなStrava Proアスリートとして%1$d台のバイク、%2$d足のシューズ、%3$d個のセンサー（うち%4$d個のパワーメーター）を使いこなす本気度が伝わります！ アプリのステータスはまだ%5$sです。ゴールドPro+サポーターでプロ仕様の環境を完成させましょう！ | Als actieve Strava Pro-atleet met %1$d fietsen, %2$d schoenen en %3$d sensoren (incl. %4$d vermogensmeters) neem je je training serieus! Je app-status is nog %5$s. Maak je prof-ecosysteem compleet met Goud Pro+ Supporter! | Jako aktywny sportowiec Strava Pro z %1$d rowerami, %2$d parami butów i %3$d czujnikami (w tym %4$d miernikami mocy) traktujesz trening poważnie! Twój status w aplikacji to wciąż %5$s. Dopełnij swój profesjonalny zestaw statusem Złoty Wspierający Pro+! | Como atleta ativo do Strava Pro com %1$d bicicletas, %2$d sapatilhas e %3$d sensores (incl. %4$d medidores de potência), leva o seu treino a sério! O seu estado na app ainda é %5$s. Complete o seu ecossistema profissional com o Apoiante Ouro Pro+! |
| `garage_detector_dialog_message_diamond_strava` | Active Strava Pro athlete with a world-class garage: %1$d bikes, %2$d shoes, and %3$d sensors (incl. %4$d power meters)! Your app status is %5$s. Complement your pro training setup with Diamond Sponsor! | Aktiver Strava-Pro-Athlet mit einem Weltklasse-Fuhrpark: %1$d Fahrräder, %2$d Laufschuhe und %3$d Sensoren (inkl. %4$d Powermetern)! Dein App-Status steht noch auf %5$s. Perfektioniere dein Profi-Setup mit dem Diamond Sponsor! | ¡Atleta activo de Strava Pro con un garaje de clase mundial: %1$d bicicletas, %2$d zapatillas y %3$d sensores (incl. %4$d potenciómetros)! Tu estado es %5$s. ¡Complementa tu equipamiento profesional con Patrocinador Diamante! | Athlète actif Strava Pro avec un garage de classe mondiale : %1$d vélos, %2$d chaussures et %3$d capteurs (dont %4$d capteurs de puissance) ! Votre statut est %5$s. Complétez votre configuration pro avec le Sponsor Diamant ! | Atleta attivo Strava Pro con una dotazione di classe mondiale: %1$d bici, %2$d scarpe e %3$d sensori (inclusi %4$d potenziometri)! Il tuo stato è %5$s. Completa il tuo assetto professionale con Sponsor Diamante!` | ワールドクラスの機材を持つアクティブなStrava Proアスリート：%1$d台のバイク、%2$d足のシューズ、%3$d個のセンサー（うち%4$d個のパワーメーター）！ ステータスはまだ%5$sです。ダイヤモンドスポンサーでプロのトレーニング環境を完璧に整えましょう！ | Actieve Strava Pro-atleet met een garage van wereldklasse: %1$d fietsen, %2$d schoenen en %3$d sensoren (incl. %4$d vermogensmeters)! Je app-status is %5$s. Vervoeg je professionele uitrusting met Diamant Sponsor! | Aktywny sportowiec Strava Pro ze sprzętem światowej klasy: %1$d rowerów, %2$d par butów i %3$d czujników (w tym %4$d mierników mocy)! Twój status w aplikacji to %5$s. Uzupełnij swój profesjonalny zestaw statusem Sponsor Diamentowy! | Atleta ativo do Strava Pro com uma garagem de nível mundial: %1$d bicicletas, %2$d sapatilhas e %3$d sensores (incl. %4$d medidores de potência)! O seu estado na app é %5$s. Complemente o seu setup profissional com o Patrocinador Diamante! |

### 1.4 Acceptance Criteria & Invariants
* **AC-1 (Strava Pro Gold Qualification)**: *Given* an athlete on Free/Silver tier with 0 bikes/sensors and `isStravaSubscriber == true`, *When* league evaluation runs, *Then* Gold League is detected and `garage_detector_dialog_message_gold_strava` is displayed.
* **AC-2 (Strava Pro Diamond Boost)**: *Given* an athlete with Gold hardware (3 bikes) and `isStravaSubscriber == true`, *When* league evaluation runs, *Then* Diamond League is detected and `garage_detector_dialog_message_diamond_strava` is displayed.
* **AC-3 (Data Purge & Disconnect)**: *Given* an athlete with `isStravaSubscriber == true`, *When* `deleteStravaToken()` or `executeLocalDataPurge()` executes, *Then* `isStravaSubscriber` resets to `false`.
* **Invariants**: Rule 75 SharedPreferences null-safety; tier monotonicity preserved; once-in-a-lifetime non-spam persistence; Rule 73 stacked buttons; zero inline Unicode emojis; 100% 9-language translation parity.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Amends `REQ-SET-088` (`REQ-SET-088.1`, `REQ-SET-088.5`), targeting `TrainingApplication.java`, `StravaHelper.kt`, `StravaEquipmentSynchronizeThread.java`, `StravaDataPurgeManager.kt`, `GarageLeagueDetector.kt`, `GarageUpgradeNudgeDialog.kt`, and `strings.xml` across all 9 locales.
* `Historical Origin & Commit Trace`: Sprint 2026-45.1 (`ATT-5299`), Sprint 2026-45.3 (`ATT-5400`), Sprint 2026-45.6 (`ATT-5468`), Sprint 2026-45.9 (`ATT-5570`).
* `Root Reason for Existing Formulation`: In ATT-5299, qualification relied solely on local hardware counts because Strava account subscription telemetry had not yet been ingested into application preferences.
* `Preservation of Core Invariants`: Rule 75 SharedPreferences null-safety (`cSharedPreferences != null`); complete data purge hygiene; tier monotonicity; once-in-a-lifetime non-spam persistence; Rule 73 stacked action buttons; zero-emoji localization parity across all 9 languages.

---

## 2. Test Specification (`TST-SET-088`, `TST-LOC-001`)

* **TST-SET-088.1a (Strava Subscriber Gold & Diamond Boost Unit Test)**: Verify `GarageNudgeEvaluator.evaluateLeague()` returns `GOLD` for `isStravaSubscriber == true` without hardware, and `DIAMOND` for Gold hardware + `isStravaSubscriber == true`.
* **TST-SET-088.1b (Ingestion & Purge Contract Test)**: Verify JSON parsing of `summit`/`premium` in `StravaHelper` and `StravaEquipmentSynchronizeThread`; verify reset in `TrainingApplication.deleteStravaToken` and `StravaDataPurgeManager`.
* **TST-SET-088.5 (Dialog Copy Audit Test)**: Verify `GarageUpgradeNudgeDialog` selects `_strava` strings when `isStravaSubscriber == true`; verify 0 emojis and 5 positional specifiers.
* **TST-LOC-001 (9-Language Parity)**: Run `TranslationParityTest` verifying 100% key parity and format specifier match across all 9 locales.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-088.1a` | Unit | `GarageLeagueDetector.kt` | `REQ-SET-088.1` | Specified |
| `TST-SET-088.1b` | Unit / Integration | `StravaHelper.kt` / `StravaDataPurgeManager.kt` | `REQ-SET-088.1` | Specified |
| `TST-SET-088.5` | Unit / Contract | `GarageUpgradeNudgeDialog.kt` | `REQ-SET-088.5` | Specified |
| `TST-LOC-001` | Localization | `res/values*/strings.xml` / `TranslationParityTest.kt` | `REQ-SET-088.5` | Specified |
