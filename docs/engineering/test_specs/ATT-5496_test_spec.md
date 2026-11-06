# Stage 2: Requirement & Test Specification - ATT-5496: Supporter Emblems & Starter Icons (B5, S4-A, G6, Variant C & PRO/PRO+ Naming)

**Ticket**: [ATT-5496](https://rainerblind.atlassian.net/browse/ATT-5496)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5496`  
**Requirement Mapping**: `REQ-UI-445` (*Supporter Badges & Launcher Icons*) | **Test Spec**: `TST-UI-445`  

---

## 1. Requirement Specification (REQ-UI-445)
* **Emblem Progression (`REQ-UI-445.1`)**: Deploy distinct emblems: Bronze B5 Participant Rosette (`cx=12, cy=10`, 12 pleats, two ribbon tails, no star), Silver S4-A Single-Rim Coins, Gold G6 Bar + Coin, Diamond 3D Faceted Diamond, Pioneer Star.
* **Variant C Starter Icons (`REQ-UI-445.2`)**: Centered compact text stadium flanked on `#FFFFFF` ground by full-color tier emblems outside the text block, keeping all 28 Cervélo P3C/stopwatch base paths 100% untouched within safe zone ($33.0 \le r \le 34.5\text{dp}$).
* **Verbal Hierarchy (`REQ-UI-445.3`)**: Supporter tier verbal progression across Bronze (`SUPPORTER`), Silver (`PRO SUPPORTER`), Gold (`PRO+ SUPPORTER`), and Diamond (`SPONSOR`).
* **Supporter Screen (`REQ-UI-445.4`)**: `SupporterPlanCard` displays tier emblem at 44dp (`Color.Unspecified`), `ActiveTierBanner` & `PioneerStatusBanner` display tier emblem at 36dp (`Color.Unspecified`).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-445.1`, `REQ-UI-445.3`, `REQ-SET-086`.
* **Historical Origin & Commit Trace**: `cae1b332` (`ATT-1406`), refined in `46247b3d`, `e0c5b1c5`, `8baf42c8` (`ATT-5397`).
* **Root Reason for Existing Formulation**: Bottom ribbon embedded small monochrome lozenges/stars to keep safe-zone margins, but colored background caused contrast clash and lacked visual differentiation.
* **Preservation of Core Invariants**: Variant C moves emblems outside the text capsule onto the `#FFFFFF` canvas, preserving safe-zone containment ($33.0 \le r \le 34.5\text{dp}$) and all 28 base logo paths.

---

## 2. 9-Language Localization Parity Matrix (TST-LOC-001)

| Locale | `supporter_tier_silver` | `supporter_tier_gold` | `drawer_tier_silver` | `drawer_tier_gold` | `launcher_icon_silver` | `launcher_icon_gold` | `garage_detector_dialog_message_gold` |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **values** (EN) | Pro Supporter | Pro+ Supporter | ★ SILVER PRO SUPPORTER | ★ GOLD PRO+ SUPPORTER | Silver Pro Supporter | Gold Pro+ Supporter | "... Gold Pro+ Supporter!" |
| **values-de** | Pro-Unterstützer | Pro+-Unterstützer | ★ SILBER PRO SUPPORTER | ★ GOLD PRO+ SUPPORTER | Silber Pro Supporter | Gold Pro+ Supporter | "... Gold Pro+ Supporter!" |
| **values-es** | Pro Supporter Plata | Pro+ Supporter Oro | ★ PRO SUPPORTER PLATA | ★ PRO+ SUPPORTER ORO | Pro Supporter Plata | Pro+ Supporter Oro | "... Partidario Oro Pro+!" |
| **values-fr** | Pro Supporter Argent | Pro+ Supporter Or | ★ PRO SUPPORTER ARGENT | ★ PRO+ SUPPORTER OR | Pro Supporter Argent | Pro+ Supporter Or | "... Supporter Or Pro+ !" |
| **values-it** | Pro Sostenitore Argento | Pro+ Sostenitore Oro | ★ PRO SUPPORTER ARGENTO | ★ PRO+ SUPPORTER ORO | Pro Supporter Argento | Pro+ Supporter Oro | "... Sostenitore Oro Pro+!" |
| **values-ja** | シルバーProサポーター | ゴールドPro+サポーター | ★ シルバー PROサポーター | ★ ゴールド PRO+サポーター | シルバーProサポーター | ゴールドPro+サポーター | "... ゴールドPro+サポーター..." |
| **values-nl** | Zilver Pro Supporter | Goud Pro+ Supporter | ★ ZILVER PRO SUPPORTER | ★ GOUD PRO+ SUPPORTER | Zilver Pro Supporter | Goud Pro+ Supporter | "... Goud Pro+ Supporter!" |
| **values-pl** | Pro Wspierający Srebrny | Pro+ Wspierający Złoty | ★ SREBRNY PRO SUPPORTER | ★ ZŁOTY PRO+ SUPPORTER | Pro Wspierający Srebrny | Pro+ Wspierający Złoty | "... Złoty Wspierający Pro+!" |
| **values-pt** | Pro Apoiador Prata | Pro+ Apoiador Ouro | ★ PRO SUPPORTER PRATA | ★ PRO+ SUPPORTER OURO | Pro Supporter Prata | Pro+ Supporter Ouro | "... Apoiante Ouro Pro+!" |

---

## 3. Test Specification & Traceability Matrix

| Test ID | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-445.1` | Unit | `SupporterBadgeContractTest`: Variant C safe zone ($33.0 \le r \le 34.5\text{dp}$), 28 base paths preserved, cubic Béziers | `REQ-UI-445.1`, `REQ-UI-445.2` | Specified |
| `TST-UI-445.2` | Unit | `SupporterBadgeContractTest`: 24dp standalone vector validity (`ic_supporter_emblem_{bronze,silver,gold}.xml`) | `REQ-UI-445.1` | Specified |
| `TST-UI-445.3` | Unit/Compose | `SupporterComponentsTest`: `SupporterPlanCard` (44dp), `ActiveTierBanner` (36dp), `PioneerStatusBanner` (36dp) | `REQ-UI-445.4` | Specified |
| `TST-UI-445.4` | Contract | `LauncherIconManagerTest` & `AppNavigationDrawerTest`: Automatic tier synchronization, drawer branding | `REQ-UI-445.2`, `REQ-UI-445.4` | Specified |
| `TST-UI-445.5` | Localization | `TranslationParityTest`: 9-language parity across all 7 modified keys with zero missing entries or specifier drift | `REQ-UI-445.4`, `TST-LOC-001` | Specified |
| `TST-UI-445.6` | Regression | Full unit test suite + `verify_requirement_governance.py` | `REQ-PRO-001` | Specified |
