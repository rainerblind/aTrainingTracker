# Stage 3 Implementation Plan: ATT-9386 - Weekly Multi-OS & 9-Language Emulator UI Verification Sweep (2026-KW47)

**Ticket**: [ATT-9386](https://issue-tracker/ATT-9386) | **Parent Epic**: [ATT-232](https://issue-tracker/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467)  
**Requirement Mapping**: `REQ-PRO-135` (`REQ-PRO-135.1`–`.5`), `REQ-TST-004` | **Test Spec**: `TST-PRO-135` (`TST-PRO-135.1`–`.5`)  
**Branch**: `feature/ATT-9386` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-21

---

## 1. Architectural & Scope Summary
* **Operational Execution Only**: Executes `tools/run_multi_os_emulator_sweep.py` (`ATT-9319` @ `3aaa57a6`) to produce the 2026-KW47 sweep evidence under `docs/engineering/emulator_sweeps/`.
* **SWE.2 / SWE.3 & UI Consistency**: Zero edits to `app/src/main/`, `strings*.xml`, `docs/architecture.md` (`Rule 8.8`), or `docs/architecture/detailed_design/` (`REQ-PRO-114`).

## 2. Atomic Operational Execution Steps

| Step | Action & Target Artifacts | Mapped Req / Test | Verification / Guardrail |
| :--- | :--- | :--- | :--- |
| **1** | **Assemble APKs**: Link `local.properties` & `ANT-Android-SDKs` SDK subdirs temporarily (removed cleanly in `trap` immediately post-build per `Rule 9.2`), then run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -Dkotlin.daemon.jvm.options=-Xmx3072m`. | `REQ-PRO-135.1`, `TST-PRO-135.5` | `flock`-serialized (`Rule 8.6`); APKs present at `app/build/outputs/apk/{debug,androidTest/debug}/`; zero leftover symlinks in `git status`. |
| **2** | **Pre-Flight & Serial Multi-OS + 9-Locale Sweep**: Stop any idle `emulator-5554` (`adb -s emulator-5554 emu kill`, never touching `66020DLCR002FL` per `REQ-TST-004`). Execute `python3 tools/run_multi_os_emulator_sweep.py --apk app/build/outputs/apk/debug/app-debug.apk --test-apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` serially across `Pixel_5_Android_12` (`en`), `Pixel_8_Android_15` (`de` + 9 locales `en,de,es,fr,it,ja,nl,pl,pt`), `Pixel_7_Android_36` (`fr`). | `REQ-PRO-135.1`, `.2`, `.5`; `TST-PRO-135.1`, `.2`, `.5` | Strictly 1 AVD booted at a time; `assert_emulator_only_serial()`; `OK (` instrumentation + non-launcher XML + `finally: adb emu kill`. |
| **3** | **Visual Audit, Archival & Governance**: Inspect ALL captured PNGs under `docs/engineering/emulator_sweeps/img/2026-11-21/` via `read_file` (`Rule 8.5`), review `logcat` & `uiautomator` bounds defects, archive `docs/engineering/emulator_sweeps/2026-11-21_sweep.md` + PNGs, run `python3 tools/verify_requirement_governance.py --repo-root . --base-ref sprint/2026-11-20_03_Nacht`, and report any genuine defects cleanly to Coordinator (`Rule 5.1`, no autonomous `create-issue`). | `REQ-PRO-135.3`, `.4`; `TST-PRO-135.3`, `.4`; `Rule 8.5` | `12/12` PNGs visually verified via `read_file`; governance exits `0`; zero `app/src/main/` modifications. |

## 3. System Invariants & Rollback Safety
* **Device Isolation (`REQ-TST-004`)**: Physical device `66020DLCR002FL` is never targeted or mutated; all ADB actions enforce `emulator-*`.
* **Worktree & Submodule Hygiene (`Rule 9.2`)**: Temporary build symlinks are cleaned up immediately after Step 1 (`trap`) and never staged or committed; only `docs/engineering/emulator_sweeps/` evidence (plus Stage 5 walkthrough) is added in Stage 4/5.
* **Code Lock (`Rule 4.1`)**: Zero build/sweep execution prior to Gate 3 `PASS` (`check-gate` exit `0`).
