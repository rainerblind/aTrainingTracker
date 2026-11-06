# Stage 2: Requirement & Test Specification - ATT-5659: App development support copy without buzzwords

**Ticket**: [ATT-5659](https://rainerblind.atlassian.net/browse/ATT-5659)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5659`  
**Requirement Mapping**: `REQ-SET-086`, `REQ-REV-003` | **Test Spec ID**: `TST-SET-086`, `TST-REV-003`, `TST-REV-005`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-SET-086`, `REQ-REV-003`)

### 1.1 Problem Statement & Rationale
On `SupporterScreen` and `FirstJubileeCelebrationDialog`, financial support copy previously contained defensive marketing buzzwords (*"15+ Jahre Indie-Entwicklung"*, *"seit 2011"*, *"unabhängiges Fitnesstracking"*, *"dem Datenschutz verpflichtet"*). The copy SHALL be updated across all 9 supported locales to simple, appreciative language directly asking to support ongoing app development (*"Weiterentwicklung unterstützen"*), harmonizing with the upcoming `ATT-5658` layout.

### 1.2 9-Locale Translation Matrix (5 String Keys)

| Locale | `supporter_screen_subtitle` | `supporter_active_tier_thank_you` | `supporter_core_invariant_body` | `jubilee_supporter_appeal_title` | `jubilee_supporter_appeal_message` |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **EN** | Support the ongoing development of aTrainingTracker. | Thank you for supporting the development of aTrainingTracker! | Core workout recording, map display, sensor telemetry, and offline GPX/TCX/FIT export remain 100% free and unmonetized forever. Subscriptions fund continuous development of the app. | Support Development | You can also financially support the ongoing development of aTrainingTracker: |
| **DE** | Unterstütze die Weiterentwicklung von aTrainingTracker. | Danke, dass du die Entwicklung von aTrainingTracker unterstützt! | Trainingsaufzeichnung, Kartenanzeige, Sensordaten und Offline-GPX/TCX/FIT-Exporte bleiben dauerhaft 100% kostenlos und werbefrei. Abonnements finanzieren die kontinuierliche Weiterentwicklung der App. | Weiterentwicklung unterstützen | Außerdem kannst du die Weiterentwicklung von aTrainingTracker auch finanziell unterstützen: |
| **ES** | Apoya el desarrollo continuo de aTrainingTracker. | ¡Gracias por apoyar el desarrollo de aTrainingTracker! | El registro de entrenamientos, mapas, telemetría de sensores y exportación GPX/TCX/FIT sin conexión permanecen 100% gratuitos para siempre. Las suscripciones financian el desarrollo continuo de la aplicación. | Apoyar el desarrollo | También puedes apoyar económicamente el desarrollo continuo de aTrainingTracker: |
| **FR** | Soutenez le développement continu d\'aTrainingTracker. | Merci de soutenir le développement d\'aTrainingTracker ! | L\'enregistrement des entraînements, l\'affichage des cartes, la télémétrie des capteurs et l\'exportation hors ligne GPX/TCX/FIT restent 100% gratuits pour toujours. Les abonnements financent le développement continu de l\'application. | Soutenir le développement | Vous pouvez également soutenir financièrement le développement continu d\'aTrainingTracker : |
| **IT** | Sostieni lo sviluppo continuo di aTrainingTracker. | Grazie per sostenere lo sviluppo di aTrainingTracker! | La registrazione degli allenamenti, le mappe, la telemetria dei sensori e l\'esportazione offline GPX/TCX/FIT rimangono gratuiti al 100% per sempre. Gli abbonamenti finanziano lo sviluppo continuo dell\'app. | Sostieni lo sviluppo | Puoi anche sostenere economicamente lo sviluppo continuo di aTrainingTracker: |
| **JA** | aTrainingTracker の継続的な開発を支援する。 | aTrainingTracker の開発をご支援いただきありがとうございます！ | ワークアウト記録、地図表示、センサーデータ、オフラインGPX/TCX/FIT出力は、今後も永続的に100%無料で利用できます。サブスクリプションはアプリの継続的な開発に充てられます。 | 開発を支援 | aTrainingTracker の継続的な開発を財政的に支援することもできます： |
| **NL** | Steun de continue ontwikkeling van aTrainingTracker. | Bedankt voor het steunen van de ontwikkeling van aTrainingTracker! | Trainingregistratie, kaartweergave, sensortelemetrie en offline GPX/TCX/FIT-export blijven voor altijd 100% gratis. Abonnementen financieren de continue ontwikkeling van de app. | Ontwikkeling ondersteunen | Je kunt de continue ontwikkeling van aTrainingTracker ook financieel ondersteunen: |
| **PL** | Wesprzyj dalszy rozwój aTrainingTracker. | Dziękujemy za wspieranie rozwoju aTrainingTracker! | Rejestrowanie treningów, mapy, telemetria czujników i eksport GPX/TCX/FIT offline pozostają w 100% darmowe na zawsze. Subskrypcje finansują ciągły rozwój aplikacji. | Wesprzyj rozwój | Możesz także wesprzeć finansowo dalszy rozwój aTrainingTracker: |
| **PT** | Apoie o desenvolvimento contínuo do aTrainingTracker. | Obrigado por apoiar o desenvolvimento do aTrainingTracker! | Gravação de treinos, mapas, telemetria de sensores e exportação GPX/TCX/FIT offline permanecem 100% gratuitos para sempre. As assinaturas financiam o desenvolvimento contínuo do aplicativo. | Apoiar o desenvolvimento | Você também pode apoiar financeiramente o desenvolvimento contínuo do aTrainingTracker: |

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1 (SupporterScreen Copy)**: *Given* an athlete navigating to `SupporterScreen`, *When* inspecting headers and invariant card, *Then* the copy directly invites supporting ongoing development without "Indie", "15+ Jahre", or "unabhängiges Fitnesstracking".
* **AC-2 (FirstJubileeCelebrationDialog Copy)**: *Given* `FirstJubileeCelebrationDialog` shown to an unsubscribed athlete, *When* viewing the financial support section, *Then* the header displays *"Weiterentwicklung unterstützen"* and the message introduces financial support plainly.
* **AC-3 (9-Locale Parity & Zero Emojis)**: *Given* all 9 locale files, *When* `TranslationParityTest` executes, *Then* 100% string presence, zero missing keys, zero emojis (`ATT-5400`), and zero forbidden buzzwords are verified.
* **Invariants**: 100% free core invariant body intact; Google Play Billing plans, prices, and IDs unchanged.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-SET-086` (`REQ-SET-086.5`) in `req_settings_profiles.md` and `REQ-REV-003` in `req_aftermath_reviews.md`.
* `Historical Origin & Commit Trace`: `ATT-51` (initial supporter model), `ATT-2308` (first jubilee dialog), `ATT-5371` (`100%` percent sign fix).
* `Root Reason for Existing Formulation`: Historical marketing copy highlighted developer pedigree and privacy commitments to differentiate from corporate fitness trackers.
* `Preservation of Core Invariants`: 100% free core invariant body intact; tier cards, prices, and Google Play billing mechanisms 100% preserved.

---

## 2. Test Specification (`TST-SET-086`, `TST-REV-003`, `TST-REV-005`)

### Test Case 1: 9-Locale Translation Parity & Buzzword Audit (`TST-REV-005`, `TST-LOC-001`)
* **Scope**: Localization Unit Test (`TranslationParityTest.kt` + `SupporterScreenContractTest.kt`)
* **Action**: Run `TranslationParityTest` and assert all 5 string keys exist across 9 locales without forbidden phrases (*"15+ Jahre"*, *"Indie"*, *"seit 2011"*, *"Datenschutz"*).
* **Expected Result**: 100% PASS.

### Test Case 2: Visual Copy Correctness in DE and EN (`TST-SET-086`)
* **Scope**: Contract Test (`SupporterScreenContractTest.kt` / `FirstJubileeCelebrationDialogTest.kt`)
* **Action**: Assert `supporter_screen_subtitle`, `supporter_active_tier_thank_you`, `supporter_core_invariant_body`, `jubilee_supporter_appeal_title`, and `jubilee_supporter_appeal_message` match the specified appreciative copy.
* **Expected Result**: 100% PASS.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-086` | Contract | `strings.xml` (9 locales), `SupporterScreen.kt` | `REQ-SET-086.5` | Specified |
| `TST-REV-003` | Contract | `FirstJubileeCelebrationDialog.kt` | `REQ-REV-003` | Specified |
| `TST-REV-005` | Localization | `strings.xml` (9 locales) | `REQ-LOC-001`, `REQ-REV-003` | Specified |