# Stage 2: Requirement & Test Specification - ATT-5198: DEM Device Details Internet Notice Across 9 Locales

**Ticket**: [ATT-5198](https://rainerblind.atlassian.net/browse/ATT-5198)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-SEN-026` (*Specialized Altitude Devices Educational Operation Details*)  
**Test Spec ID**: `TST-SEN-026`  
**Branch**: `feature/ATT-5198`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-SEN-026)

### 1.1 Problem Statement & Rationale
In `EditDeviceDialog.kt`, the educational trade-offs card for `DeviceType.ALTITUDE_FROM_POSITION` renders string resource `device_altitude_position_pros_cons`. While pros (`+`) highlight weather immunity and calibration independence, and cons (`-`) cite GPS accuracy and terrain stepped response, the text omits the requirement of an active internet connection when traversing new, uncached areas outside saved routes. Athletes offline without pre-seeded routes may experience unexpected missing elevation.

### 1.2 Functional & Architectural Requirements
1. **Explicit Internet Requirement Disclosure (`REQ-SEN-026.3`)**: In `device_altitude_position_pros_cons`, line 2 (`-`) SHALL explicitly state that an active internet connection is required for uncached areas outside saved routes across all 9 supported locales.
2. **Two-Line Format & Literal Linebreak Invariant (`REQ-SEN-026.4`)**: The string resource SHALL maintain the exact two-line structure (`+ <Pros>\n- <Cons>`) using literal `\n` linebreaks (never XML entity `&#10;` which AAPT2 collapses).
3. **100% 9-Language Parity (`REQ-SEN-026.4`)**: All 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) SHALL have identical semantic structure and full translation coverage.

### 1.3 9-Locale Exact Text Specification (`device_altitude_position_pros_cons`)
* **EN (`values`)**: `+ Immune to weather and barometric drift; accurate absolute height without calibration.\n- Requires active internet connection for uncached areas; depends on GPS accuracy and can be stepped on bridges or cliffs.`
* **DE (`values-de`)**: `+ Wetterunabhängig und ohne Barometerdrift; absolute Höhe ohne Kalibrierung.\n- Erfordert aktive Internetverbindung für ungecachte Gebiete; abhängig von GPS-Genauigkeit, auf Brücken und Steilhängen ungenauer.`
* **ES (`values-es`)**: `+ Inmune al clima y deriva barométrica; altitud absoluta sin calibración.\n- Requiere conexión activa a internet para áreas sin caché; depende de la precisión del GPS; escalonada en puentes y desfiladeros.`
* **FR (`values-fr`)**: `+ Insensible à la météo et aux dérives ; altitude absolue sans calibrage.\n- Nécessite une connexion Internet active pour les zones non mises en cache ; dépend de la précision GPS (moins précis sur ponts et falaises).`
* **IT (`values-it`)**: `+ Immune a meteo e derive barometriche; quota assoluta senza calibrazione.\n- Richiede connessione Internet attiva per aree non memorizzate nella cache; dipende dalla precisione GPS (a scalini su ponti e pareti scoscese).`
* **JA (`values-ja`)**: `+ 天候や気圧ドリフトに影響されず、校正不要で正確な絶対標高を提供。\n- 未キャッシュ地域ではアクティブなインターネット接続が必要；GPS精度に依存し、橋や急崖などでは誤差が生じる場合があります。`
* **NL (`values-nl`)**: `+ Immuun voor weer en barometrische drift; absolute hoogte zonder kalibratie.\n- Vereist actieve internetverbinding voor niet-gecachte gebieden; afhankelijk van GPS-nauwkeurigheid (kan verspringen op bruggen en kliffen).`
* **PL (`values-pl`)**: `+ Niewrażliwa na pogodę i dryf ciśnienia; bezwzględna wysokość bez kalibracji.\n- Wymaga aktywnego połączenia z internetem w niebuforowanych obszarach; zależy od dokładności GPS (schodkowa na mostach i urwiskach).`
* **PT (`values-pt`)**: `+ Imune ao clima e deriva barométrica; altitude absoluta sem calibração.\n- Requer conexão ativa à internet para áreas sem cache; depende da precisão do GPS (pode variar em pontes e despenhadeiros).`

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* an athlete viewing device details for `ALTITUDE_FROM_POSITION` in `EditDeviceDialog`,
* *When* the "Pros & Cons" card renders in any of the 9 supported locales,
* *Then* the cons (`-`) text explicitly mentions that an active internet connection is required for uncached areas.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-SEN-026.3` (Educational Operation & Trade-Offs Details in `EditDeviceDialog`). Targets `strings_devices.xml` (all 9 locales).
2. *Historical Origin & Commit Trace*: Sprint 2026-45.1 (`ATT-5073`, commit `59a1be36`). Builds on `ATT-4859` (`REQ-SEN-025`, Sprint 2026-44.2). Amended in Sprint 2026-45.2 (`ATT-5198`) to explicitly require internet connectivity notice for uncached areas.
3. *Root Reason for Existing Formulation*: `ATT-5073` created the educational cards to contrast barometric altimetry against topographic DEM, focusing on spatial terrain limitations (GPS noise, cliffs) but omitting network dependencies for uncached DEM queries.
4. *Preservation of Core Invariants*: Existing pros (weather immunity, absolute altitude) and cons (GPS dependency, bridges/cliffs) are strictly preserved; literal `\n` two-line format and M3 semantic styling remain invariant.

---

## 2. Test Specification (TST-SEN-026)

### Test Case 1: 9-Locale Content & Formatting Audit (`TST-SEN-026.4`)
* **Scope**: Unit Test (`app/src/test/.../ui/devices/SpecializedAltitudeDevicesLocalizationTest.kt`)
* **Action**: Parse `strings_devices.xml` across all 9 locales. Verify `device_altitude_position_pros_cons` has 2 lines starting with `+ ` and `\n- `, and assert the presence of internet and cache keywords in line 2.
* **Expected Result**: All 9 locales contain explicit notice of active internet requirement for uncached areas.

### Test Case 2: Translation Parity & Resource Compilation (`TST-SEN-026.4` & `REQ-PRO-001`)
* **Scope**: Unit Test (`TranslationParityTest`) & AAPT2 Resource Processing (`./gradlew processDebugResources`)
* **Action**: Verify no missing keys, format string specifier parity, and valid XML entity syntax.
* **Expected Result**: Zero missing keys, successful AAPT2 compilation, 100% test pass.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SEN-026.1` | Unit | `SpecializedAltitudeDevicesLocalizationTest.kt` | `REQ-SEN-026.1` | Implemented |
| `TST-SEN-026.2` | Unit | `DevicesDatabaseManagerTest.java` | `REQ-SEN-026.2` | Implemented |
| `TST-SEN-026.3` | Compose | `EditDeviceDialogTest.kt` | `REQ-SEN-026.3` | Implemented |
| `TST-SEN-026.4` | Unit / Parity | `SpecializedAltitudeDevicesLocalizationTest.kt` | `REQ-SEN-026.3`, `REQ-SEN-026.4` | Specified |
| `TST-SEN-026.5` | Suite | Clean-Room Regression Pass | `REQ-PRO-001` | Implemented |
