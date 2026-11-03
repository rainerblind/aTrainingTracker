# Stage 2: Requirement & Test Specification - ATT-5073: Specialized Altitude Devices Naming & Explanations

**Ticket**: [ATT-5073](https://rainerblind.atlassian.net/browse/ATT-5073)  
**Sub-task**: [[ATT-5165]](https://rainerblind.atlassian.net/browse/ATT-5165) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-SEN-026` (*Specialized Altitude Devices Naming Parity & Explanations*)  
**Test Spec ID**: `TST-SEN-026`  
**Branch**: `feature/ATT-5073`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-SEN-026)

### 1.1 Problem Statement & Rationale
`ALTITUDE_FROM_PRESSURE` remained labeled as "Luftdruck" / "Air Pressure", creating naming asymmetry with `ALTITUDE_FROM_POSITION` ("Topografische Höhe (DEM)") and obscuring its altitude output. In `EditDeviceDialog.kt`, athletes have no educational context explaining how either altitude device measures elevation or their operational trade-offs (barometer response vs. weather drift; DEM weather immunity vs. grid resolution).

### 1.2 Functional & Architectural Requirements
1. `devices_altitude_from_pressure` SHALL be renamed in all 9 supported locales to reflect altitude output ("Höhe aus Luftdruck" in DE, "Altitude from Pressure" in EN).
2. `DevicesDatabaseManager` SHALL execute an idempotent migration updating legacy default names in `Devices.db` for `ALTITUDE_FROM_PRESSURE` while strictly preserving custom user renames.
3. `EditDeviceDialog.kt` SHALL render an `OutlinedCard` for `ALTITUDE_FROM_PRESSURE` and `ALTITUDE_FROM_POSITION` with structured "How it works" and "Pros & Cons" sections.
4. All string resources SHALL maintain 100% 9-language translation parity with literal `\n` linebreaks.

---

## 2. 9-Language Localization Specification

| Locale | `devices_altitude_from_pressure` | Title / Headers | `ALTITUDE_FROM_PRESSURE` (How / Pros & Cons) | `ALTITUDE_FROM_POSITION` (How / Pros & Cons) |
| :--- | :--- | :--- | :--- | :--- |
| **EN** | Altitude from Pressure | About this Sensor / How it works / Pros & Cons | Barometric formula + calibration / + Responsive, smooth, 0 GPS jitter - Weather drift | GPS matched to DEM cache / + Weather immune, absolute height - GPS accuracy dep. |
| **DE** | Höhe aus Luftdruck | Über diesen Sensor / Funktionsweise / Vor- & Nachteile | Barometrische Höhenformel + Kalibrierung / + Reaktionsschnell, glatt, kein GPS-Rauschen - Höhendrift | GPS-Abgleich mit DEM-Höhenmodell / + Wetterunabhängig, absolute Höhe - GPS-abhängig |
| **ES** | Altitud por presión | Acerca de este sensor / Cómo funciona / Ventajas y desventajas | Fórmula barométrica + calibración / + Reactivo, fluido, sin ruido GPS - Deriva por clima | Posición GPS frente a DEM / + Inmune al clima, altura absoluta - Depende de GPS |
| **FR** | Altitude par pression | À propos de ce capteur / Fonctionnement / Avantages et inconvénients | Formule barométrique + calibrage / + Réactif, fluide, sans bruit GPS - Dérive météo | Position GPS comparée au MNT / + Insensible météo, hauteur absolue - Dépend du GPS |
| **IT** | Altitudine da pressione | Info su questo sensore / Come funziona / Pro e contro | Formula barometrica + calibrazione / + Reattivo, fluido, zero rumore GPS - Deriva meteo | Posizione GPS su modello DEM / + Immune a meteo, quota assoluta - Dipende da GPS |
| **JA** | 気圧からの高度 | このセンサーについて / 仕組み / メリットとデメリット | 気圧計算式 + 校正 / + 高精度で滑らか、GPSノイズなし - 気圧変動ドリフト | GPS位置を標高DEMと照合 / + 天候不変、絶対標高 - GPS精度に依存 |
| **NL** | Hoogte uit luchtdruk | Over deze sensor / Hoe het werkt / Voor- & nadelen | Barometrische formule + kalibratie / + Responsief, stabiel, geen GPS-ruis - Weerdrift | GPS-positie gekoppeld aan DEM / + Weeronafhankelijk, absolute hoogte - GPS-afhankelijk |
| **PL** | Wysokość z ciśnienia | O tym czujniku / Jak to działa / Zalety i wady | Wzór barometryczny + kalibracja / + Płynna, czuła, bez szumu GPS - Dryf pogodowy | Pozycja GPS z modelem DEM / + Odporna na pogodę, bezwzględna - Zależy od GPS |
| **PT** | Altitude por pressão | Sobre este sensor / Como funciona / Prós e contras | Fórmula barométrica + calibração / + Responsivo, suave, sem ruído GPS - Deriva climática | Posição GPS com modelo DEM / + Imune ao clima, altitude absoluta - Depende do GPS |

---

## 3. Test Specification & Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SEN-026.1` | Unit / Parity | `SpecializedAltitudeDevicesLocalizationTest` (9 locales) | `REQ-SEN-026.1` | Specified |
| `TST-SEN-026.2` | DB Migration | `DevicesDatabaseManagerTest` (idempotent name migration) | `REQ-SEN-026.2` | Specified |
| `TST-SEN-026.3` | Compose UI | `EditDeviceDialogTest` (`AltitudeExplanationSection` rendering) | `REQ-SEN-026.3` | Specified |
| `TST-SEN-026.4` | Localization | `TranslationParityTest` (all altitude explanation keys) | `REQ-SEN-026.4` | Specified |
| `TST-SEN-026.5` | Regression | `./gradlew testDebugUnitTest --tests "...devices.*"` | `REQ-PRO-001` | Specified |
