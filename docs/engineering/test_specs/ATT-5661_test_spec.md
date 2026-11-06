# Stage 2: Requirement & Test Specification - ATT-5661: Appreciative copy and 'Support Development' CTA

**Ticket**: [ATT-5661](https://rainerblind.atlassian.net/browse/ATT-5661)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5661`  
**Requirement Mapping**: `REQ-SET-084`, `REQ-UI-149` | **Test Spec ID**: `TST-SET-084`, `TST-UI-102`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-SET-084`, `REQ-UI-149`)

### 1.1 Problem Statement & Rationale
In `SwapOrUpgradeDialog` and `KeepTwoSensorsBottomSheet`, copy previously used transactional SaaS phrasing (*Free-Tier*, *Free-Plätze*, *Pay what you want*, *Unbegrenzte Sensoren freischalten*). The copy SHALL be updated across all 9 supported locales to use appreciative terminology (*"In der Basisversion"* / *"In the base version"*), frame sensor expansion around supporting independent app development (*"Entwicklung unterstützen"* / *"Support Development"*), and maintain the 2-device base quota.

### 1.2 9-Locale Translation Matrix (5 String Keys)

| Locale | `swap_dialog_title` | `swap_dialog_action_unlock` / `keep_two_sensors_action_unlock` | `swap_dialog_message` (`%1$s`) | `keep_two_sensors_message` |
| :--- | :--- | :--- | :--- | :--- |
| **EN** | Device Limit (2/2 Slots Used) | Support Development | In the base version, up to 2 devices can be paired simultaneously (e.g. GPS + Barometer). To pair \'%1$s\', unpair an existing device — or support the ongoing development of the app to unlock unlimited sensors. | Your subscription has ended and the base version supports up to 2 active sensors. Select 2 sensors to keep active. Excess sensors will be unpaired without deleting any calibration or equipment data. |
| **DE** | Geräte-Limit (2/2 Plätze belegt) | Entwicklung unterstützen | In der Basisversion können maximal 2 Geräte gleichzeitig gekoppelt sein (z. B. GPS + Barometer). Um „%1$s“ zu koppeln, wähle ein bestehendes Gerät ab – oder unterstütze die Weiterentwicklung der App und schalte damit unbegrenzt viele Sensoren frei. | Dein Abonnement ist abgelaufen und in der Basisversion können maximal 2 Sensoren aktiv sein. Wähle 2 Sensoren aus, die aktiv bleiben sollen. Die übrigen Sensoren werden entkoppelt, bleiben aber mit allen Kalibrierungs- und Ausrüstungsdaten erhalten. |
| **ES** | Límite de dispositivos (2/2 espacios usados) | Apoyar el desarrollo | En la versión base se pueden emparejar hasta 2 dispositivos simultáneamente (p. ej. GPS + Barómetro). Para emparejar \'%1$s\', desempareja un dispositivo existente o apoya el desarrollo continuo de la aplicación para desbloquear sensores ilimitados. | Tu suscripción ha finalizado y la versión base admite hasta 2 sensores activos. Selecciona 2 sensores para mantener activos. Los sensores restantes se desvincularán sin eliminar datos de calibración ni equipamiento. |
| **FR** | Limite d\'appareils (2/2 emplacements occupés) | Soutenir le développement | Dans la version de base, vous pouvez coupler jusqu\'à 2 appareils simultanément (ex. GPS + Baromètre). Pour coupler « %1$s », désélectionnez un appareil existant — ou soutenez le développement continu de l\'application pour débloquer des capteurs illimités. | Votre abonnement a pris fin et la version de base prend en charge jusqu\'à 2 capteurs actifs. Sélectionnez 2 capteurs à conserver. Les autres capteurs seront dissociés sans supprimer vos données d\'étalonnage ni d\'équipement. |
| **IT** | Limite dispositivi (2/2 posti utilizzati) | Sostieni lo sviluppo | Nella versione base possono essere associati al massimo 2 dispositivi contemporaneamente (es. GPS + Barometro). Per associare \'%1$s\', deseleziona un dispositivo esistente o sostieni lo sviluppo continuo dell\'app per sbloccare sensori illimitati. | Il tuo abbonamento è scaduto e la versione base supporta fino a 2 sensori attivi. Seleziona 2 sensori da mantenere attivi. I sensori in eccesso verranno dissociati senza cancellare dati di calibrazione o equipaggiamento. |
| **JA** | デバイス制限（2/2枠使用中） | 開発を支援 | 基本版では最大2台のデバイスを同時接続できます（例：GPS + 気圧計）。「%1$s」を接続するには、既存のデバイスを解除するか、アプリの開発を支援して無制限のセンサー接続を解放してください。 | サブスクリプションが終了しました。基本版では最大2つのセンサーをアクティブにできます。維持する2つのセンサーを選択してください。余分なセンサーは解除されますが、キャリブレーションや機材データは保持されます。 |
| **NL** | Apparaatlimiet (2/2 plaatsen bezet) | Ontwikkeling ondersteunen | In de basisversie kunnen maximaal 2 apparaten tegelijkertijd gekoppeld zijn (bijv. GPS + Barometer). Om \'%1$s\' te koppelen, ontkoppel een bestaand apparaat — of steun de continue ontwikkeling van de app om onbeperkt sensoren te ontgrendelen. | Je abonnement is beëindigd en de basisversie ondersteunt maximaal 2 actieve sensors. Selecteer 2 sensors om actief te houden. Overtollige sensors worden ontkoppeld zonder kalibratie- of uitrustingsgegevens te verwijderen. |
| **PL** | Limit urządzeń (zajęte 2/2 miejsca) | Wesprzyj rozwój | W wersji podstawowej można sparować maksymalnie 2 urządzenia jednocześnie (np. GPS + barometr). Aby sparować „%1$s”, odłącz istniejące urządzenie lub wesprzyj dalszy rozwój aplikacji, aby odblokować nielimitowane czujniki. | Twoja subskrypcja wygasła, a wersja podstawowa obsługuje do 2 aktywnych czujników. Wybierz 2 czujniki, które chcesz zachować. Pozostałe czujniki zostaną rozłączone bez usuwania danych kalibracji ani wyposażenia. |
| **PT** | Limite de dispositivos (2/2 vagas ocupadas) | Apoiar o desenvolvimento | Na versão base, até 2 dispositivos podem ser pareados simultaneamente (ex. GPS + Barômetro). Para parear \'%1$s\', desemparelhe um dispositivo existente ou apoie o desenvolvimento contínuo do aplicativo para desbloquear sensores ilimitados. | A sua assinatura terminou e a versão base suporta até 2 sensores ativos. Selecione 2 sensores para manter ativos. Os sensores em excesso serão desemparelhados sem eliminar dados de calibração ou equipamento. |

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1 (SwapOrUpgradeDialog)**: *Given* an athlete in the base version with 2 paired devices attempting to pair a 3rd, *When* `SwapOrUpgradeDialog` opens, *Then* the title displays *"Geräte-Limit (2/2 Plätze belegt)"*, the body explains the base version limit with `%1$s` candidate device name injected and frames sensor expansion around supporting development, and the secondary CTA button displays *"Entwicklung unterstützen"*.
* **AC-2 (KeepTwoSensorsBottomSheet)**: *Given* an athlete downgraded to the base version with $> 2$ paired sensors, *When* `KeepTwoSensorsBottomSheet` opens, *Then* the body references *"Basisversion"* / *"base version"* and the secondary CTA button displays *"Entwicklung unterstützen"* / *"Support Development"*.
* **AC-3 (9-Locale Parity)**: *Given* all 9 locale directories, *When* `TranslationParityTest` executes, *Then* 100% string presence and exact `%1$s` specifier parity are verified.
* **Invariants**: Max free paired quota (`2`) unchanged; Rule 73 stacked button layout intact; resource keys unchanged; zero emoji policy (`ATT-5400`).

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-SET-084` (`REQ-SET-084.4`, `REQ-SET-084.8`) and `REQ-UI-149`, targeting `SwapOrUpgradeDialog.kt`, `KeepTwoSensorsBottomSheet.kt`, and `strings.xml`.
* `Historical Origin & Commit Trace`: `ATT-51` (quota & swap dialog), `ATT-5398` (`KeepTwoSensorsBottomSheet`), `ATT-5470` (Rule 73 stacked action buttons), and `ATT-5490` (Sprint `2026-45.8`).
* `Root Reason for Existing Formulation`: Early copy adopted transactional SaaS phrasing (*Free-Tier*, *Pay what you want*). Appreciative framing (*Basisversion*, *Entwicklung unterstützen*) reflects community-driven open-source development while maintaining quota clarity.
* `Preservation of Core Invariants`: 2-device quota invariant preserved; Rule 73 stacked layout preserved; key names preserved; single `%1$s` placeholder preserved across all 9 locales.

---

## 2. Test Specification (`TST-SET-084`, `TST-UI-102`)

### Test Case 1: 9-Locale Translation Parity Audit (`TST-SET-084.1`)
* **Scope**: Localization Unit Test (`TranslationParityTest.kt`)
* **Action**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
* **Expected Result**: 100% presence and format specifier parity across all 9 locales.

### Test Case 2: Dialog Copy & Support CTA Contract Test (`TST-SET-084.2`)
* **Scope**: Contract Test (`SwapOrUpgradeDialogContractTest.kt` / `DeviceStringsContractTest.kt`)
* **Action**: Assert `swap_dialog_title`, `swap_dialog_message`, `swap_dialog_action_unlock`, `keep_two_sensors_action_unlock`, and `keep_two_sensors_message` match the specified appreciative copy in DE and EN.
* **Expected Result**: 100% PASS.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-084.1` | Localization | `strings.xml` (9 locales) | `REQ-SET-084`, `REQ-LOC-001` | Specified |
| `TST-SET-084.2` | Contract | `SwapOrUpgradeDialogContractTest.kt` | `REQ-SET-084.8`, `REQ-UI-149` | Specified |