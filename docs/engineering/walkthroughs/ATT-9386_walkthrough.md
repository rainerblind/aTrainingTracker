# Stage 5: Walkthrough & Verification - ATT-9386 (2026-KW47 Multi-OS & 9-Language Emulator Sweep)

**Ticket**: [ATT-9386](https://rainerblind.atlassian.net/browse/ATT-9386) (`2 SP`) | **Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) | **Target Release**: `V5.0.0` (`Rule 8.3`)  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467) | **Branch**: `feature/ATT-9386` (Gate 4 SHA: `100b1a33`, `ATT-9430` PASS)  
**Requirement / Test Mapping**: `REQ-PRO-135` (`REQ-PRO-135.1`–`.5` `[Verified]`) / `TST-PRO-135` (`TST-PRO-135.1`–`.5` `[Verified]`), `REQ-TST-004`  
**Author**: Lane C Flash (`--as agent1`) | **Date**: 2026-11-21

---

## 1. Executive Summary & Rule 7.5 Doc-Only Fast Path
* **Operational Sweep Archived (`100b1a33`)**: Executed `tools/run_multi_os_emulator_sweep.py` serially on `emulator-5554` (`66020DLCR002FL` strictly excluded per `REQ-TST-004`) across 3 AVDs (`Pixel_5_Android_12`, `Pixel_8_Android_15`, `Pixel_7_Android_36`) and 9 locales (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) using freshly assembled `:app:assembleDebug` + `:app:assembleDebugAndroidTest` APKs (`MultiLocaleUiSweepAndroidTest`).
* **Rule 7.5 Zero-Gradle Doc-Only Fast Path**: Zero production, test, or sweep script changes exist since Gate 4 (`ATT-9430` @ `100b1a33`); Gate 4 build/sweep evidence and `Rule 8.5` visual PNG inspections are reused directly without redundant Gradle or emulator runs.

## 2. Operational Sweep Matrix & Governance Evidence (`4/12 PASS`, `8/12 FAIL`)

| Tier / Scope | Target / Locales | Outcome | Evidence Artifact |
| :--- | :--- | :--- | :--- |
| **Tier-1 Multi-OS (`TST-PRO-135.1`, `.2`)** | `Pixel_8_Android_15` (`de`), `Pixel_5_Android_12` (`en`), `Pixel_7_Android_36` (`fr`) | **1/3 PASS** (`Pixel_8_Android_15` PASS; Android 12 & 36 FAIL) | `docs/engineering/emulator_sweeps/2026-11-21_sweep.md` |
| **Tier-2 9-Locale (`TST-PRO-135.5`)** | `Pixel_8_Android_15`: `es`, `ja`, `pt` PASS; `en`, `de`, `fr`, `it`, `nl`, `pl` FAIL | **3/9 PASS** (`logcat=0`, `bounds=0` on all 4 captured PNGs) | `docs/engineering/emulator_sweeps/img/2026-11-21/*.png` |
| **`Rule 8.5` Visual Inspection (`read_file`)** | `Pixel_8_Android_15_de.png`, `Pixel_8_Android_15_loc_es.png`, `Pixel_8_Android_15_loc_ja.png`, `Pixel_8_Android_15_loc_pt.png` | **4/4 Visually Audited** (clean dialog geometry, contrast, single-line CTAs) | `2026-11-21_sweep.md` §4 |
| **Governance (`TST-PRO-135.3`, `.4`)** | `verify_requirement_governance.py --repo-root . --base-ref 33a060a1 --check-verified-evidence` | **PASS (exit `0`)** | Clean-checkout `/tmp/empty_check_9386` |

## 3. Discovered Genuine Defects (For Coordinator / PO Backlog Follow-Up per `Rule 5.1`)
1. **Tracking FGS / Rapid-Stop Race (`ForegroundServiceDidNotStartInTimeException`)**: `7/12` runs (`Pixel_5_Android_12` `en`; `Pixel_8_Android_15` `en`, `de`, `fr`, `it`, `nl`, `pl`) crashed in `MultiLocaleUiSweepAndroidTest.kt:103-132` because `TrainingApplication.isTracking()` flips `true` before `TrackerService.performStartForeground()` executes, so `REQUEST_STOP_TRACKING` (`TrainingApplication.stopTracking():2026` -> `stopService(TrackerService)`) ~200 ms later triggers Android 12+ `ForegroundServiceDidNotStartInTimeException` and leaves an interrupted workout triggering the `"Training unterbrochen"` modal visible in all 4 PNGs.
2. **Persisted Tracking Tab Localization Lag**: In `Pixel_8_Android_15_loc_{es,ja,pt}.png`, Tab 1 localizes (`Inicio`, `スタート`, `Iniciar`), while Tabs 2–4 remain German (`"Standard"`, `"Karte"`, `"Runden"`) persisted in SQLite from the initial `de` DB initialization.
3. **Android 16 (API 36) `uiautomator dump` Path (`Pixel_7_Android_36`)**: Instrumentation passed, but `cat /sdcard/att_sweep_dump.xml` returned `No such file or directory` on API 36 (recommend `/data/local/tmp/att_sweep_dump.xml` in `run_multi_os_emulator_sweep.py`).

## 4. Invariants & Handover (`Rule 3.3`)
* `66020DLCR002FL` untouched (`REQ-TST-004`); `0` edits under `app/src/main/` or `strings*.xml` (`Localization Parity: N/A`); `feature/ATT-9386` preserved intact for Stage 6 Cold Review.
