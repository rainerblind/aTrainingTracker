# Stage 2: Requirement & Test Specification - ATT-1298: Live MMP Curve & Rolling/Peak Metrics

**Ticket**: [ATT-1298](https://rainerblind.atlassian.net/browse/ATT-1298) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-1298`  
**Requirement Mapping**: `REQ-TRK-058` (`docs/requirements/req_tracking_cockpit.md`)  
**Test Spec ID**: `TST-TRK-058` (`docs/tests/tst_tracking_cockpit.md`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-TRK-058)

### 1.1 Problem Statement & Rationale
Athletes need real-time Mean Maximal Power (MMP) and rolling/peak interval feedback (5s, 1m, 5m, 20m) in the tracking cockpit to pace efforts tactically without degrading CPU/battery performance.

### 1.2 Functional & Architectural Requirements
* **REQ-TRK-058.1** (*O(1) Ring-Buffer Accumulators & Warmup*): `CyclingPowerMetricsDevice` SHALL maintain $O(1)$ circular ring-buffer accumulators (`SlidingWindowPowerAccumulator`) for 5s, 60s, 300s, and 1200s. Rolling averages compute immediately over available samples during warmup ($1 \le k < W$) without `null` delay (`Rule 40`).
* **REQ-TRK-058.2** (*Rolling & Session Peak Power Telemetry*): Calculate rolling means (`POWER_5S`, `POWER_1M`, `POWER_5M`, `POWER_20M`) and monotonic session peaks (`POWER_PEAK_5S`, `POWER_PEAK_1M`, `POWER_PEAK_5M`, `POWER_PEAK_20M`) at 1Hz, gated on active power telemetry (`Rule 52`).
* **REQ-TRK-058.3** (*SensorType & 9-Locale Parity*): Register 8 new `SensorType` entries under `POWER_AND_PEDALING` with 100% 9-language localization parity in `app/src/main/res/values*/strings_cockpit.xml`.
* **REQ-TRK-058.4** (*Live Vector MMP Curve Cockpit Component*): Compose vector chart `LiveMmpCurveChart` SHALL render real-time power on a logarithmic duration axis ($1\text{s} \dots 7200\text{s}$) with benchmark callout pins and peak highlights with zero snapshot state writes during composition (`Rule 66`).
* **REQ-TRK-058.5** (*Pause Lifecycle & Reset Parity*): Suspension (`isSuspended`) freezes accumulators; `resetSession()` clears all buffers and peak registers (`Rule 51`).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* incoming 1Hz power samples [200, 250, 300, 350, 400] W, *When* 5s elapse, *Then* `POWER_5S` and `POWER_PEAK_5S` emit 300 W.
* *Given* workout at $t = 3\text{s}$, *When* power is received, *Then* `POWER_5S` emits average of 3 samples (`Rule 40`).
* *Given* paused recording, *When* suspended, *Then* sliding windows freeze without zero-watt pollution.
* *Given* `resetSession()`, *When* called, *Then* all buffers and peak registers clear (`null` emitted).
* *Invariants*: Existing power metrics (`POWER`, `NP`, `IF`, `WORK_KJ`, `TSS`, `W_PRIME_BAL`) 100% regression-free.

---

## 2. 9-Language Localization Plan (`strings_cockpit.xml`)

| Key | EN | DE | ES | FR | IT | JA | NL | PL | PT |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `power_5s` | Power (5s) | Leistung (5s) | Potencia (5s) | Puissance (5s) | Potenza (5s) | パワー (5秒) | Vermogen (5s) | Moc (5s) | Potência (5s) |
| `power_5s_short` | 5s W | 5s W | 5s W | 5s W | 5s W | 5秒 W | 5s W | 5s W | 5s W |
| `power_1m` | Power (1m) | Leistung (1m) | Potencia (1m) | Puissance (1m) | Potenza (1m) | パワー (1分) | Vermogen (1m) | Moc (1m) | Potência (1m) |
| `power_1m_short` | 1m W | 1m W | 1m W | 1m W | 1m W | 1分 W | 1m W | 1m W | 1m W |
| `power_5m` | Power (5m) | Leistung (5m) | Potencia (5m) | Puissance (5m) | Potenza (5m) | パワー (5分) | Vermogen (5m) | Moc (5m) | Potência (5m) |
| `power_5m_short` | 5m W | 5m W | 5m W | 5m W | 5m W | 5分 W | 5m W | 5m W | 5m W |
| `power_20m` | Power (20m) | Leistung (20m) | Potencia (20m) | Puissance (20m) | Potenza (20m) | パワー (20分) | Vermogen (20m) | Moc (20m) | Potência (20m) |
| `power_20m_short` | 20m W | 20m W | 20m W | 20m W | 20m W | 20分 W | 20m W | 20m W | 20m W |
| `power_peak_5s` | Peak Power (5s) | Spitzenleistung (5s) | Potencia pico (5s) | Puissance de crête (5s) | Potenza di picco (5s) | ピークパワー (5秒) | Piekvermogen (5s) | Moc szczytowa (5s) | Potência de pico (5s) |
| `power_peak_5s_short` | Pk 5s | Pk 5s | Pk 5s | Pk 5s | Pk 5s | Pk 5秒 | Pk 5s | Pk 5s | Pk 5s |
| `power_peak_1m` | Peak Power (1m) | Spitzenleistung (1m) | Potencia pico (1m) | Puissance de crête (1m) | Potenza di picco (1m) | ピークパワー (1分) | Piekvermogen (1m) | Moc szczytowa (1m) | Potência de pico (1m) |
| `power_peak_1m_short` | Pk 1m | Pk 1m | Pk 1m | Pk 1m | Pk 1m | Pk 1分 | Pk 1m | Pk 1m | Pk 1m |
| `power_peak_5m` | Peak Power (5m) | Spitzenleistung (5m) | Potencia pico (5m) | Puissance de crête (5m) | Potenza di picco (5m) | ピークパワー (5分) | Piekvermogen (5m) | Moc szczytowa (5m) | Potência de pico (5m) |
| `power_peak_5m_short` | Pk 5m | Pk 5m | Pk 5m | Pk 5m | Pk 5m | Pk 5分 | Pk 5m | Pk 5m | Pk 5m |
| `power_peak_20m` | Peak Power (20m) | Spitzenleistung (20m) | Potencia pico (20m) | Puissance de crête (20m) | Potenza di picco (20m) | ピークパワー (20分) | Piekvermogen (20m) | Moc szczytowa (20m) | Potência de pico (20m) |
| `power_peak_20m_short` | Pk 20m | Pk 20m | Pk 20m | Pk 20m | Pk 20m | Pk 20分 | Pk 20m | Pk 20m | Pk 20m |

---

## 3. Test Specification (TST-TRK-058) & Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-058.1` | Unit | `SlidingWindowPowerAccumulatorTest.kt` | `REQ-TRK-058.1` | Planned |
| `TST-TRK-058.2` | Integration | `CyclingPowerMetricsDeviceMmpTest.kt` | `REQ-TRK-058.2`, `REQ-TRK-058.5` | Planned |
| `TST-TRK-058.3` | Localization | `SensorTypeMmpTest.kt`, `TranslationParityTest.kt` | `REQ-TRK-058.3` | Planned |
| `TST-TRK-058.4` | UI Contract | `LiveMmpCurveChartTest.kt` | `REQ-TRK-058.4` | Planned |
| `TST-TRK-058.5` | Regression | `banalservice.devices.*`, `ui.tracking.*` | `REQ-PRO-001` | Planned |
