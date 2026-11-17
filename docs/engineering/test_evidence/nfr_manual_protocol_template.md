# Non-Functional Requirements — Attested On-Device Manual Verification Protocol

**[MANUAL PROTOCOL — NOT EXECUTED IN JVM]**

**Ticket**: `ATT-8158` (`REQ-PRO-113.2`) | **Standard**: ASPICE 4.0 `SWE.2 BP3`, `SWE.6 BP3`  
**Scope**: Physical-Hardware, Battery, Render Timing, GNSS Field & Play Vitals NFRs (`Group B`: `REQ-NFR-004, 006, 012, 015, 016, 017, 018`)

> **Evidence Honesty Policy**: None of the 7 NFRs below can be measured authentically on a host JVM or Robolectric shadow environment. They MUST remain `[Implemented]` (requirements) / `[Planned]` (test specs) with `Verification Method: On-Device Manual` until a physical Pixel / Play Console run is executed and attested below with real measurements, device serial/model, Android build, date, and operator signature.

---

## Attestation Metadata (Fill During Physical Release / Field Run)

- **Execution Status**: `[MANUAL PROTOCOL — NOT EXECUTED IN JVM]`
- **Reference Device Model**: `<TBD — e.g., Pixel 8>`
- **Android OS / Build ID**: `<TBD — e.g., Android 15 / AP3A...>`
- **App Version / Git Commit**: `<TBD>`
- **Execution Date (UTC)**: `<TBD>`
- **Operator**: `<TBD>`

---

## Group B Manual Protocol & Attestation Ledger

| REQ / TST ID | Budget Target | Verification Procedure & Exact Tooling | Device / Build | Date | Operator | Measured Value | Verdict |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `REQ-NFR-004` / `TST-NFR-004` | `< 5.0%` single-core CPU | 30-min screen-off active GNSS + 2 BLE sensors on Pixel; capture Android Studio CPU Profiler / `simpleperf` & `adb shell dumpsys cpuinfo` | `<TBD>` | `<TBD>` | `<TBD>` | `<NOT MEASURED>` | `PENDING MANUAL` |
| `REQ-NFR-006` / `TST-NFR-006` | `<= 5.0%/h` bg / `<= 12.0%/h` fg | Unplugged >= 90% SoC; `adb shell dumpsys batterystats --reset`; 60-min bg screen-off + 60-min fg cockpit map (50% brightness); Battery Historian | `<TBD>` | `<TBD>` | `<TBD>` | `<NOT MEASURED>` | `PENDING MANUAL` |
| `REQ-NFR-012` / `TST-NFR-012` | `< 16.6 ms` p95 (60 FPS) | 60-sec live `SensorGridScreen` 1-Hz streaming; `adb shell dumpsys gfxinfo com.atrainingtracker.trainingtracker framestats` or Macrobenchmark `FrameTimingMetric` | `<TBD>` | `<TBD>` | `<TBD>` | `<NOT MEASURED>` | `PENDING MANUAL` |
| `REQ-NFR-015` / `TST-NFR-015` | TTID `<= 1200 ms`, TTFD `<= 1500 ms` | Force-stop app; >= 5 cold launches via `adb shell am start -W` / Macrobenchmark `StartupMode.COLD`; record median/p95 | `<TBD>` | `<TBD>` | `<TBD>` | `<NOT MEASURED>` | `PENDING MANUAL` |
| `REQ-NFR-016` / `TST-NFR-016` | p95 `<= 5.0 m`, RMSE `<= 3.0 m` | Open-sky field track on Pixel vs surveyed RTK ground-truth polyline; orthogonal cross-track projection on exported GPX | `<TBD>` | `<TBD>` | `<TBD>` | `<NOT MEASURED>` | `PENDING MANUAL` |
| `REQ-NFR-017` / `TST-NFR-017` | Crash-free `>= 99.5%` (staging `>= 99.0%`) | Export rolling 30-day session crash-free percentage from Google Play Console Android Vitals / Crashlytics | `<TBD>` | `<TBD>` | `<TBD>` | `<NOT MEASURED>` | `PENDING MANUAL` |
| `REQ-NFR-018` / `TST-NFR-018` | ANR `<= 0.05%` (main-thread `< 100 ms`) | Export rolling 30-day user-perceived ANR rate from Play Console Android Vitals + on-device StrictMode/Perfetto check | `<TBD>` | `<TBD>` | `<TBD>` | `<NOT MEASURED>` | `PENDING MANUAL` |
