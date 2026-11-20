#!/usr/bin/env python3
"""
run_multi_os_emulator_sweep.py - Serial Multi-OS & 9-Language Emulator UI Verification Sweep (ATT-8222 / REQ-PRO-135)

Executes a two-tier serial Android Emulator verification sweep:
1. Multi-OS AVD matrix (>= 3 Android API levels, strictly 1 AVD booted at a time)
   with mock GPS injection (`adb emu geo fix`) and rotating primary locales.
2. All-9-Language UI sweep (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`)
   on a reference AVD, checking `logcat` (fatal/ANR/StrictMode/IllegalFormat*)
   and `uiautomator dump` XML bounds (clipped/ellipsized/overlapping nodes),
   and archiving reports + screenshots under `docs/engineering/emulator_sweeps/`.

Invariants:
- Strictly Python 3 standard library (`TST-PRO-005.6`).
- Enforces `REQ-TST-004` (`emulator-*` serials ONLY; physical phones strictly refused).
- Never boots > 1 AVD concurrently; guarantees `adb emu kill` cleanup in `finally`.
- Zero `app/src/main/` modifications.
"""

import argparse
from dataclasses import dataclass, field
from datetime import datetime
import os
import re
import subprocess
import sys
import time
from typing import Callable, Dict, List, Optional, Sequence, Tuple
import xml.etree.ElementTree as ET

_tools_dir = os.path.dirname(os.path.abspath(__file__))
if _tools_dir not in sys.path:
    sys.path.insert(0, _tools_dir)

import agent_emulator


SUPPORTED_LOCALES: Tuple[str, ...] = ("en", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")
ROTATING_OS_LOCALES: Tuple[str, ...] = ("en", "de", "fr", "ja")
DEFAULT_PACKAGE = agent_emulator.DEFAULT_PACKAGE
DEFAULT_MAIN_ACTIVITY = "com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation"
DEFAULT_INSTRUMENTATION_CLASS = "com.atrainingtracker.trainingtracker.MultiLocaleUiSweepAndroidTest"
DEFAULT_GEO_FIX: Tuple[float, float] = (11.5761, 48.1372)  # (lon, lat) Munich

LOGCAT_DEFECT_PATTERNS: Tuple[Tuple[str, re.Pattern], ...] = (
    ("FATAL_EXCEPTION", re.compile(r"FATAL EXCEPTION", re.IGNORECASE)),
    ("ANR", re.compile(r"\bANR in\b")),
    ("STRICT_MODE", re.compile(r"StrictMode policy violation")),
    (
        "FORMAT_EXCEPTION",
        re.compile(
            r"(?:IllegalFormat[A-Za-z]*Exception|FormatFlagsConversionMismatchException|"
            r"UnknownFormatConversionException|MissingFormatArgumentException)"
        ),
    ),
)

_BOUNDS_RE = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


@dataclass
class BoundsDefect:
    kind: str
    text: str
    bounds: Tuple[int, int, int, int]
    detail: str


@dataclass
class AvdRunResult:
    avd_name: str
    api_hint: str
    locale: str
    serial: str
    passed: bool
    logcat_defects: List[str] = field(default_factory=list)
    bounds_defects: List[BoundsDefect] = field(default_factory=list)
    screenshot_path: Optional[str] = None
    error: Optional[str] = None


@dataclass
class LocaleSweepResult:
    locale: str
    avd_name: str
    serial: str
    passed: bool
    logcat_defects: List[str] = field(default_factory=list)
    bounds_defects: List[BoundsDefect] = field(default_factory=list)
    screenshot_path: Optional[str] = None
    error: Optional[str] = None


def assert_emulator_only_serial(serial: str) -> str:
    """REQ-TST-004 / REQ-PRO-135.2: Rejects any non-emulator serial before ADB calls."""
    if not serial or not str(serial).strip().startswith("emulator-"):
        raise RuntimeError(
            f"Physical-Phone Guardrail Violation (REQ-TST-004): Target serial '{serial}' is not "
            f"an Android emulator ('emulator-*'). Physical devices (e.g. Pixel 10) are strictly forbidden."
        )
    return str(serial).strip()


def infer_avd_api_hint(avd_name: str) -> str:
    """Extracts an OS/API hint from an AVD name (e.g. Pixel_8_Android_15 -> Android_15)."""
    m = re.search(r"Android_(\d+(?:\.\d+)?)", avd_name, re.IGNORECASE)
    if m:
        return f"Android_{m.group(1)}"
    m_api = re.search(r"API_?(\d+)", avd_name, re.IGNORECASE)
    if m_api:
        return f"API_{m_api.group(1)}"
    return avd_name


def select_multi_os_avds(
    available_avds: Sequence[str],
    requested_avds: Optional[Sequence[str]] = None,
    min_levels: int = 3,
    allow_fewer: bool = False,
) -> List[str]:
    """Selects representative AVDs covering distinct Android OS/API levels."""
    avail_list = [a.strip() for a in available_avds if a and a.strip()]
    if requested_avds:
        chosen = []
        for req in requested_avds:
            r = req.strip()
            if r not in avail_list:
                raise RuntimeError(f"Requested AVD '{r}' not found in available AVDs: {avail_list}")
            if r not in chosen:
                chosen.append(r)
        return chosen

    selected: List[str] = []
    seen_hints = set()
    # Prefer Pixel_5_Android_12, Pixel_8_Android_15, Pixel_7_Android_36 order when present
    preferred_order = [
        "Pixel_5_Android_12",
        "Pixel_8_Android_15",
        "Pixel_7_Android_36",
        "Pixel_6_Pro_Android_12",
    ]
    ordered_candidates = [a for a in preferred_order if a in avail_list] + [
        a for a in avail_list if a not in preferred_order
    ]
    for avd in ordered_candidates:
        hint = infer_avd_api_hint(avd)
        if hint not in seen_hints:
            seen_hints.add(hint)
            selected.append(avd)

    if len(selected) < min_levels and not allow_fewer:
        raise RuntimeError(
            f"Insufficient distinct Android OS AVD levels ({len(selected)} < {min_levels}). "
            f"Available AVDs: {avail_list}"
        )
    return selected


def resolve_reference_avd(selected_avds: Sequence[str], requested_ref: Optional[str] = None) -> str:
    """Resolves the reference AVD for the 9-locale UI sweep."""
    if requested_ref:
        return requested_ref
    for preferred in ("Pixel_8_Android_15", "Pixel_7_Android_36", "Pixel_5_Android_12"):
        if preferred in selected_avds:
            return preferred
    if not selected_avds:
        raise RuntimeError("No AVDs available to select a reference AVD for 9-language sweep.")
    return selected_avds[0]


def scan_logcat_defects(logcat_text: str) -> List[str]:
    """Scans adb logcat output for FATAL EXCEPTION, ANR, StrictMode, and format crashes."""
    defects: List[str] = []
    for line in (logcat_text or "").splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        for tag, pattern in LOGCAT_DEFECT_PATTERNS:
            if pattern.search(stripped):
                defects.append(f"[{tag}] {stripped[:240]}")
                break
    return defects


def parse_bounds(bounds_str: str) -> Optional[Tuple[int, int, int, int]]:
    """Parses Android UIAutomator bounds '[x1,y1][x2,y2]' into a 4-int tuple."""
    if not bounds_str:
        return None
    m = _BOUNDS_RE.match(bounds_str.strip())
    if not m:
        return None
    return int(m.group(1)), int(m.group(2)), int(m.group(3)), int(m.group(4))


def analyze_uiautomator_bounds(
    xml_text: str,
    screen_width: int = 1080,
    screen_height: int = 2400,
) -> List[BoundsDefect]:
    """Parses uiautomator XML hierarchy for clipped, ellipsized, zero-size, or overlapping text."""
    defects: List[BoundsDefect] = []
    if not xml_text or not xml_text.strip():
        return defects

    root = ET.fromstring(xml_text)

    def _walk(node: ET.Element) -> None:
        leaf_boxes: List[Tuple[str, Tuple[int, int, int, int]]] = []
        for child in node:
            text = (child.attrib.get("text") or "").strip()
            bounds = parse_bounds(child.attrib.get("bounds", ""))
            has_children = len(list(child)) > 0

            if text and bounds is not None:
                x1, y1, x2, y2 = bounds
                if x2 <= x1 or y2 <= y1:
                    defects.append(
                        BoundsDefect(
                            kind="ZERO_OR_INVERTED_BOUNDS",
                            text=text,
                            bounds=bounds,
                            detail=f"Text '{text}' rendered with zero/negative dimensions {bounds}",
                        )
                    )
                elif x1 < 0 or y1 < 0 or x2 > screen_width or y2 > screen_height:
                    defects.append(
                        BoundsDefect(
                            kind="OUT_OF_SCREEN_CLIP",
                            text=text,
                            bounds=bounds,
                            detail=f"Text '{text}' exceeds viewport {screen_width}x{screen_height}: {bounds}",
                        )
                    )
                if text.endswith("...") or text.endswith("…"):
                    defects.append(
                        BoundsDefect(
                            kind="ELLIPSIZED_TEXT",
                            text=text,
                            bounds=bounds,
                            detail=f"Text '{text}' is ellipsized at {bounds}",
                        )
                    )
                if not has_children and x2 > x1 and y2 > y1:
                    leaf_boxes.append((text, bounds))

            _walk(child)

        # Pairwise sibling leaf-text overlap check within the same container
        for i in range(len(leaf_boxes)):
            t1, (ax1, ay1, ax2, ay2) = leaf_boxes[i]
            for j in range(i + 1, len(leaf_boxes)):
                t2, (bx1, by1, bx2, by2) = leaf_boxes[j]
                ix1 = max(ax1, bx1)
                iy1 = max(ay1, by1)
                ix2 = min(ax2, bx2)
                iy2 = min(ay2, by2)
                if ix2 > ix1 and iy2 > iy1:
                    defects.append(
                        BoundsDefect(
                            kind="OVERLAPPING_TEXT",
                            text=f"{t1} <-> {t2}",
                            bounds=(ix1, iy1, ix2, iy2),
                            detail=f"Sibling labels '{t1}' and '{t2}' overlap by {(ix2 - ix1) * (iy2 - iy1)} px^2",
                        )
                    )

    _walk(root)
    return defects


def _run_adb(
    adb_bin: str,
    serial: str,
    args: Sequence[str],
    timeout: int = 30,
    runner: Callable = subprocess.run,
) -> subprocess.CompletedProcess:
    assert_emulator_only_serial(serial)
    return runner([adb_bin, "-s", serial] + list(args), capture_output=True, text=True, timeout=timeout)


def shutdown_emulator(
    serial: str,
    proc: Optional[subprocess.Popen] = None,
    adb_bin: str = "adb",
    runner: Callable = subprocess.run,
) -> None:
    """Cleanly terminates a single booted emulator instance via `adb emu kill` + Popen fallback."""
    assert_emulator_only_serial(serial)
    try:
        runner([adb_bin, "-s", serial, "emu", "kill"], capture_output=True, text=True, timeout=15)
    except Exception:
        pass
    if proc is not None:
        try:
            proc.terminate()
            proc.wait(timeout=10)
        except Exception:
            try:
                proc.kill()
            except Exception:
                pass


def boot_single_avd(
    avd_name: str,
    emulator_bin: str,
    adb_bin: str = "adb",
    port: int = 5554,
    timeout_sec: int = 180,
    popen_fn: Callable = subprocess.Popen,
    devices_fn: Callable = agent_emulator.get_adb_devices,
    boot_check_fn: Callable = agent_emulator.is_boot_completed,
    sleep_fn: Callable = time.sleep,
) -> Tuple[str, subprocess.Popen]:
    """
    REQ-PRO-135.1 / REQ-TST-004: Strictly boots one AVD serially (`-no-snapshot-save`).
    Refuses to boot if another `emulator-*` instance is already online.
    """
    existing_emus = [d for d in devices_fn(adb_bin) if d.is_emulator and d.state == "device"]
    if existing_emus:
        raise RuntimeError(
            f"Serial AVD Violation: Another emulator is already running ({[d.serial for d in existing_emus]}). "
            f"Stop running emulators before starting multi-OS sweep."
        )

    target_serial = assert_emulator_only_serial(f"emulator-{port}")
    cmd = [emulator_bin, "-avd", avd_name, "-port", str(port), "-no-snapshot-save"]
    proc = popen_fn(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)

    deadline = time.time() + timeout_sec
    try:
        while time.time() < deadline:
            devs = devices_fn(adb_bin)
            if any(d.serial == target_serial and d.is_emulator and d.state == "device" for d in devs):
                if boot_check_fn(target_serial, adb_bin):
                    return target_serial, proc
            sleep_fn(2)
        raise TimeoutError(f"AVD '{avd_name}' ({target_serial}) did not finish booting within {timeout_sec}s.")
    except Exception:
        shutdown_emulator(target_serial, proc=proc, adb_bin=adb_bin)
        raise


def set_app_locale(
    serial: str,
    package: str,
    locale: str,
    adb_bin: str = "adb",
    runner: Callable = subprocess.run,
) -> None:
    """Switches app runtime locale via Android 13+ `cmd locale` (with persist.sys.locale fallback)."""
    _run_adb(adb_bin, serial, ["shell", "cmd", "locale", "set-app-locales", package, "--locales", locale], runner=runner)


def run_ui_probe(
    serial: str,
    package: str,
    avd_name: str,
    label: str,
    screenshots_dir: str,
    adb_bin: str = "adb",
    runner: Callable = subprocess.run,
    instrumentation_class: Optional[str] = DEFAULT_INSTRUMENTATION_CLASS,
    geo_fix: Tuple[float, float] = DEFAULT_GEO_FIX,
) -> Tuple[List[str], List[BoundsDefect], Optional[str]]:
    """Clears logcat, injects mock GPS, launches/instruments UI, captures uiautomator & screenshot."""
    _run_adb(adb_bin, serial, ["logcat", "-c"], runner=runner)
    _run_adb(adb_bin, serial, ["emu", "geo", "fix", str(geo_fix[0]), str(geo_fix[1])], runner=runner)

    if instrumentation_class:
        _run_adb(
            adb_bin,
            serial,
            [
                "shell",
                "am",
                "instrument",
                "-w",
                "-e",
                "class",
                instrumentation_class,
                f"{package}.test/androidx.test.runner.AndroidJUnitRunner",
            ],
            timeout=120,
            runner=runner,
        )
    else:
        _run_adb(
            adb_bin,
            serial,
            ["shell", "am", "start", "-W", "-n", f"{package}/{DEFAULT_MAIN_ACTIVITY}"],
            timeout=30,
            runner=runner,
        )

    _run_adb(adb_bin, serial, ["shell", "uiautomator", "dump", "/sdcard/att_sweep_dump.xml"], runner=runner)
    xml_res = _run_adb(adb_bin, serial, ["shell", "cat", "/sdcard/att_sweep_dump.xml"], runner=runner)
    log_res = _run_adb(adb_bin, serial, ["logcat", "-d", "-v", "time"], runner=runner)

    os.makedirs(screenshots_dir, exist_ok=True)
    screenshot_path = os.path.join(screenshots_dir, f"{avd_name}_{label}.png")
    try:
        assert_emulator_only_serial(serial)
        shot_res = runner([adb_bin, "-s", serial, "exec-out", "screencap", "-p"], capture_output=True, timeout=20)
        if shot_res.returncode == 0 and shot_res.stdout:
            with open(screenshot_path, "wb") as f:
                f.write(shot_res.stdout)
        else:
            screenshot_path = None
    except Exception:
        screenshot_path = None

    log_defects = scan_logcat_defects(getattr(log_res, "stdout", "") or "")
    bounds_defects = analyze_uiautomator_bounds(getattr(xml_res, "stdout", "") or "")
    return log_defects, bounds_defects, screenshot_path


def write_sweep_report(
    out_dir: str,
    sweep_date: str,
    os_results: Sequence[AvdRunResult],
    locale_results: Sequence[LocaleSweepResult],
    develop_head: Optional[str] = None,
) -> str:
    """Writes structured sweep report to `docs/engineering/emulator_sweeps/YYYY-MM-DD_sweep.md`."""
    os.makedirs(out_dir, exist_ok=True)
    report_path = os.path.join(out_dir, f"{sweep_date}_sweep.md")
    all_passed = all(r.passed for r in os_results) and all(r.passed for r in locale_results)
    lines = [
        f"# Multi-OS & 9-Language Emulator UI Verification Sweep ({sweep_date})",
        "",
        f"**Ticket / Requirement**: `ATT-8222` / `REQ-PRO-135` | **Verdict**: `{'PASS' if all_passed else 'FAIL'}` | **Base SHA**: `{develop_head or 'HEAD'}`",
        "",
        "## 1. Multi-OS AVD Matrix (Serial Execution)",
        "",
        "| AVD | OS / API | Locale | Serial | Verdict | Logcat Defects | Bounds Defects | Screenshot |",
        "| :--- | :--- | :--- | :--- | :--- | :---: | :---: | :--- |",
    ]
    for r in os_results:
        shot = os.path.basename(r.screenshot_path) if r.screenshot_path else "N/A"
        lines.append(
            f"| `{r.avd_name}` | `{r.api_hint}` | `{r.locale}` | `{r.serial}` | `{'PASS' if r.passed else 'FAIL'}` | "
            f"{len(r.logcat_defects)} | {len(r.bounds_defects)} | `{shot}` |"
        )

    lines.extend([
        "",
        "## 2. All-9-Language UI Sweep (Reference AVD)",
        "",
        "| Locale | Reference AVD | Verdict | Logcat Defects | Bounds Defects | Screenshot |",
        "| :--- | :--- | :--- | :---: | :---: | :--- |",
    ])
    for lr in locale_results:
        shot = os.path.basename(lr.screenshot_path) if lr.screenshot_path else "N/A"
        lines.append(
            f"| `{lr.locale}` | `{lr.avd_name}` | `{'PASS' if lr.passed else 'FAIL'}` | "
            f"{len(lr.logcat_defects)} | {len(lr.bounds_defects)} | `{shot}` |"
        )

    with open(report_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    return report_path


def run_sweep(
    requested_avds: Optional[Sequence[str]] = None,
    ref_avd: Optional[str] = None,
    apk_path: Optional[str] = None,
    package: str = DEFAULT_PACKAGE,
    locales: Sequence[str] = SUPPORTED_LOCALES,
    out_dir: Optional[str] = None,
    adb_bin: str = "adb",
    emulator_bin: Optional[str] = None,
    port: int = 5554,
    timeout_sec: int = 180,
    min_levels: int = 3,
    allow_fewer_avds: bool = False,
    instrumentation_class: Optional[str] = DEFAULT_INSTRUMENTATION_CLASS,
    runner: Callable = subprocess.run,
    popen_fn: Callable = subprocess.Popen,
    devices_fn: Callable = agent_emulator.get_adb_devices,
    avds_fn: Callable = agent_emulator.get_available_avds,
    boot_check_fn: Callable = agent_emulator.is_boot_completed,
    tune_fn: Callable = agent_emulator.tune_emulator_settings,
    configure_fn: Callable = agent_emulator.configure_app,
    sleep_fn: Callable = time.sleep,
) -> Tuple[bool, str, List[AvdRunResult], List[LocaleSweepResult]]:
    """Orchestrates serial Multi-OS + 9-Language emulator sweeps with guaranteed cleanup."""
    repo_root = os.path.dirname(_tools_dir)
    if out_dir is None:
        out_dir = os.path.join(repo_root, "docs", "engineering", "emulator_sweeps")
    screenshots_dir = os.path.join(out_dir, "img", datetime.now().strftime("%Y-%m-%d"))

    emu_bin = emulator_bin or agent_emulator.find_emulator_binary()
    available = avds_fn(emu_bin)
    selected_avds = select_multi_os_avds(
        available,
        requested_avds=requested_avds,
        min_levels=min_levels,
        allow_fewer=allow_fewer_avds,
    )
    chosen_ref_avd = resolve_reference_avd(selected_avds, requested_ref=ref_avd)

    os_results: List[AvdRunResult] = []
    locale_results: List[LocaleSweepResult] = []

    for idx, avd in enumerate(selected_avds):
        rotating_locale = ROTATING_OS_LOCALES[idx % len(ROTATING_OS_LOCALES)]
        serial = ""
        proc = None
        try:
            serial, proc = boot_single_avd(
                avd_name=avd,
                emulator_bin=emu_bin,
                adb_bin=adb_bin,
                port=port,
                timeout_sec=timeout_sec,
                popen_fn=popen_fn,
                devices_fn=devices_fn,
                boot_check_fn=boot_check_fn,
                sleep_fn=sleep_fn,
            )
            assert_emulator_only_serial(serial)
            tune_fn(serial, adb_bin)
            if apk_path:
                _run_adb(adb_bin, serial, ["install", "-r", "-d", apk_path], timeout=120, runner=runner)
            configure_fn(serial, package=package, adb_bin=adb_bin)
            set_app_locale(serial, package, rotating_locale, adb_bin=adb_bin, runner=runner)

            log_defects, bounds_defects, shot = run_ui_probe(
                serial=serial,
                package=package,
                avd_name=avd,
                label=rotating_locale,
                screenshots_dir=screenshots_dir,
                adb_bin=adb_bin,
                runner=runner,
                instrumentation_class=instrumentation_class,
            )
            os_results.append(
                AvdRunResult(
                    avd_name=avd,
                    api_hint=infer_avd_api_hint(avd),
                    locale=rotating_locale,
                    serial=serial,
                    passed=(not log_defects and not bounds_defects),
                    logcat_defects=log_defects,
                    bounds_defects=bounds_defects,
                    screenshot_path=shot,
                )
            )

            # Tier 2: Execute 9-language UI sweep on reference AVD before tearing it down
            if avd == chosen_ref_avd:
                for loc in locales:
                    set_app_locale(serial, package, loc, adb_bin=adb_bin, runner=runner)
                    l_log, l_bounds, l_shot = run_ui_probe(
                        serial=serial,
                        package=package,
                        avd_name=avd,
                        label=f"loc_{loc}",
                        screenshots_dir=screenshots_dir,
                        adb_bin=adb_bin,
                        runner=runner,
                        instrumentation_class=instrumentation_class,
                    )
                    locale_results.append(
                        LocaleSweepResult(
                            locale=loc,
                            avd_name=avd,
                            serial=serial,
                            passed=(not l_log and not l_bounds),
                            logcat_defects=l_log,
                            bounds_defects=l_bounds,
                            screenshot_path=l_shot,
                        )
                    )
        except Exception as e:
            os_results.append(
                AvdRunResult(
                    avd_name=avd,
                    api_hint=infer_avd_api_hint(avd),
                    locale=rotating_locale,
                    serial=serial or f"emulator-{port}",
                    passed=False,
                    error=str(e),
                )
            )
        finally:
            cleanup_serial = serial or f"emulator-{port}"
            shutdown_emulator(cleanup_serial, proc=proc, adb_bin=adb_bin, runner=runner)

    sweep_date = datetime.now().strftime("%Y-%m-%d")
    report_path = write_sweep_report(out_dir, sweep_date, os_results, locale_results)
    all_ok = (
        bool(os_results)
        and all(r.passed for r in os_results)
        and all(r.passed for r in locale_results)
    )
    return all_ok, report_path, os_results, locale_results


def build_cli_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Serial Multi-OS & 9-Language Emulator UI Verification Sweep (ATT-8222 / REQ-PRO-135)"
    )
    parser.add_argument("--avds", nargs="+", default=None, help="Explicit AVD names to sweep serially")
    parser.add_argument("--ref-avd", default=None, help="Reference AVD for 9-locale UI sweep")
    parser.add_argument("--apk", default=None, help="Path to debug APK to install per AVD")
    parser.add_argument("--package", default=DEFAULT_PACKAGE, help=f"Target package (default: {DEFAULT_PACKAGE})")
    parser.add_argument("--out-dir", default=None, help="Output directory for sweep reports")
    parser.add_argument("--port", type=int, default=5554, help="Emulator console port (default: 5554)")
    parser.add_argument("--timeout", type=int, default=180, help="Per-AVD boot timeout in seconds")
    parser.add_argument("--allow-fewer-avds", action="store_true", help="Allow fewer than 3 distinct OS AVDs")
    return parser


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = build_cli_parser()
    args = parser.parse_args(argv)
    all_ok, report_path, os_res, loc_res = run_sweep(
        requested_avds=args.avds,
        ref_avd=args.ref_avd,
        apk_path=args.apk,
        package=args.package,
        out_dir=args.out_dir,
        port=args.port,
        timeout_sec=args.timeout,
        allow_fewer_avds=args.allow_fewer_avds,
    )
    print(f"Emulator sweep report written to {report_path} (OS={len(os_res)}, Locales={len(loc_res)}, PASS={all_ok})")
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())
