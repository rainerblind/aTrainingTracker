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
from typing import Callable, Dict, Iterable, List, Optional, Sequence, Set, Tuple
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
UI_DUMP_DEVICE_PATH = "/data/local/tmp/att_sweep_dump.xml"
LAUNCHER_PACKAGE_TOKENS: Tuple[str, ...] = ("nexuslauncher", "launcher3", "android.launcher", ".launcher")
INSTRUMENTATION_FAIL_MARKERS: Tuple[str, ...] = (
    "FAILURES!!!",
    "INSTRUMENTATION_FAILED",
    "INSTRUMENTATION_ABORTED",
    "Process crashed",
    "Unable to find instrumentation info",
)

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
_LOGCAT_TIME_OR_BRIEF_PID_RE = re.compile(
    r"^(?:\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3}\s+)?[VDIWEFA]/[^:\r\n]*?\(\s*(\d+)\s*\):"
)
_LOGCAT_THREADTIME_PID_RE = re.compile(
    r"^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3}\s+(\d+)\s+\d+\s+[VDIWEFA]\s+"
)
_FATAL_CONTINUATION_RE = re.compile(
    r"^(?:Process:|PID:|at\s|Caused by:|Suppressed:|\.\.\.\s+\d+\s+more\b|\*\*\*|pid:\s*\d+|Build fingerprint:|ABI:|Abort message:|backtrace:|\s+at\s)"
)
_EXPLICIT_PROCESS_LINE_RE = re.compile(r"\bProcess:\s*([A-Za-z0-9_.:-]+)")


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


def _compile_package_pattern(package: str) -> re.Pattern:
    """Matches exact `package` (or `<package>.test` / `<package>:<subproc>`) without prefix/suffix collisions."""
    return re.compile(
        rf"(?<![A-Za-z0-9_.]){re.escape(package)}(?:\.test)?(?::[A-Za-z0-9_.-]+)?(?![A-Za-z0-9_.])"
    )


def extract_logcat_pid(line: str) -> Optional[str]:
    """Extracts normalized numeric PID from a logcat line (`-v time`, `brief`, or `threadtime`)."""
    stripped = (line or "").strip()
    if not stripped:
        return None
    match = _LOGCAT_TIME_OR_BRIEF_PID_RE.match(stripped) or _LOGCAT_THREADTIME_PID_RE.match(stripped)
    if not match:
        return None
    return str(int(match.group(1)))


def discover_app_pids_from_logcat(logcat_text: str, package: str) -> Set[str]:
    """Extracts PIDs explicitly bound to `package` from ActivityManager/AndroidRuntime/DEBUG logcat lines."""
    if not logcat_text or not package:
        return set()
    pkg_pat = rf"(?<![A-Za-z0-9_.]){re.escape(package)}(?:\.test)?(?::[A-Za-z0-9_.-]+)?"
    patterns = (
        re.compile(rf"\bStart proc\s+(\d+):{pkg_pat}(?![A-Za-z0-9_.])"),
        re.compile(rf"\bProcess:\s*{pkg_pat}(?![A-Za-z0-9_.]),\s*PID:\s*(\d+)\b"),
        re.compile(rf"\bProcess\s+{pkg_pat}(?![A-Za-z0-9_.])\s*\(pid\s+(\d+)\)"),
        re.compile(rf"\bpid:\s*(\d+),\s*tid:\s*\d+.*?>>>\s*{pkg_pat}(?![A-Za-z0-9_.])\s*<<<"),
    )
    pids: Set[str] = set()
    for line in logcat_text.splitlines():
        for pat in patterns:
            for match in pat.finditer(line):
                pids.add(str(int(match.group(1))))
    return pids


def _collect_fatal_block(
    lines: Sequence[str],
    start_idx: int,
    start_pid: Optional[str],
) -> List[Tuple[int, str]]:
    """Collects lines belonging to a `FATAL EXCEPTION` block starting at `start_idx`."""
    block: List[Tuple[int, str]] = [(start_idx, lines[start_idx].strip())]
    limit = min(len(lines), start_idx + 60)
    fatal_pat = LOGCAT_DEFECT_PATTERNS[0][1]
    for idx in range(start_idx + 1, limit):
        stripped = lines[idx].strip()
        if not stripped:
            if start_pid is None:
                break
            continue
        line_pid = extract_logcat_pid(lines[idx])
        if fatal_pat.search(stripped) and (start_pid is None or line_pid == start_pid):
            break
        if start_pid is not None:
            if line_pid == start_pid:
                block.append((idx, stripped))
            elif line_pid is None and _FATAL_CONTINUATION_RE.search(stripped):
                block.append((idx, stripped))
        else:
            if line_pid is not None:
                break
            if any(pat.search(stripped) for tag, pat in LOGCAT_DEFECT_PATTERNS if tag != "FORMAT_EXCEPTION"):
                break
            block.append((idx, stripped))
    return block


def _is_fatal_block_for_app(
    block: Sequence[Tuple[int, str]],
    start_pid: Optional[str],
    known_app_pids: Set[str],
    package_re: Optional[re.Pattern],
) -> bool:
    """Determines whether a `FATAL EXCEPTION` block belongs to the target app process or package."""
    if start_pid is not None and start_pid in known_app_pids:
        return True
    if package_re is None:
        return False
    for _, line in block:
        proc_match = _EXPLICIT_PROCESS_LINE_RE.search(line)
        if proc_match:
            return bool(package_re.search(proc_match.group(1)))
    return any(package_re.search(line) for _, line in block)


def scan_logcat_defects(
    logcat_text: str,
    app_pids: Optional[Iterable[str]] = None,
    package: Optional[str] = None,
) -> List[str]:
    """
    Scans adb logcat output for FATAL EXCEPTION, ANR, StrictMode, and format crashes.

    When `app_pids` or `package` is provided, restricts detection strictly to the target
    app's process PID(s) (including PIDs discovered from logcat lifecycle/crash lines) and
    crash/ANR blocks referencing `package`, ignoring unrelated system process noise.
    """
    defects: List[str] = []
    lines = (logcat_text or "").splitlines()
    filtering_enabled = app_pids is not None or bool(package)

    known_app_pids: Set[str] = set()
    if app_pids is not None:
        for pid in app_pids:
            pid_str = str(pid).strip()
            if pid_str.isdigit():
                known_app_pids.add(str(int(pid_str)))
    if package:
        known_app_pids.update(discover_app_pids_from_logcat(logcat_text or "", package))

    package_re = _compile_package_pattern(package) if package else None
    app_fatal_line_indices: Set[int] = set()

    for idx, line in enumerate(lines):
        stripped = line.strip()
        if not stripped:
            continue
        matched_tag: Optional[str] = None
        for tag, pattern in LOGCAT_DEFECT_PATTERNS:
            if pattern.search(stripped):
                matched_tag = tag
                break
        if matched_tag is None:
            continue

        if not filtering_enabled:
            defects.append(f"[{matched_tag}] {stripped[:240]}")
            continue

        line_pid = extract_logcat_pid(stripped)
        if matched_tag == "FATAL_EXCEPTION":
            block = _collect_fatal_block(lines, idx, line_pid)
            if _is_fatal_block_for_app(block, line_pid, known_app_pids, package_re):
                app_fatal_line_indices.update(b_idx for b_idx, _ in block)
                defects.append(f"[{matched_tag}] {stripped[:240]}")
            continue

        if matched_tag == "STRICT_MODE":
            if line_pid is not None:
                is_app = line_pid in known_app_pids
            else:
                is_app = bool(package_re and package_re.search(stripped))
            if is_app:
                defects.append(f"[{matched_tag}] {stripped[:240]}")
            continue

        if matched_tag == "ANR":
            is_app = (
                (line_pid is not None and line_pid in known_app_pids)
                or bool(package_re and package_re.search(stripped))
            )
            if is_app:
                defects.append(f"[{matched_tag}] {stripped[:240]}")
            continue

        is_app = (
            (line_pid is not None and line_pid in known_app_pids)
            or (idx in app_fatal_line_indices)
            or bool(package_re and package_re.search(stripped))
        )
        if is_app:
            defects.append(f"[{matched_tag}] {stripped[:240]}")

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

    def _walk(
        node: ET.Element,
        parent_clickable: bool = False,
        parent_positive_bounds: bool = False,
    ) -> None:
        leaf_boxes: List[Tuple[str, Tuple[int, int, int, int]]] = []
        for child in node:
            text = (child.attrib.get("text") or "").strip()
            bounds = parse_bounds(child.attrib.get("bounds", ""))
            has_children = len(list(child)) > 0
            child_clickable = (child.attrib.get("clickable") or "").lower() == "true"
            child_positive = bounds is not None and bounds[2] > bounds[0] and bounds[3] > bounds[1]

            if text and bounds is not None:
                x1, y1, x2, y2 = bounds
                is_api31_merged_zero_child = (
                    bounds == (0, 0, 0, 0)
                    and parent_clickable
                    and parent_positive_bounds
                    and not child_clickable
                )
                if (x2 <= x1 or y2 <= y1) and not is_api31_merged_zero_child:
                    defects.append(
                        BoundsDefect(
                            kind="ZERO_OR_INVERTED_BOUNDS",
                            text=text,
                            bounds=bounds,
                            detail=f"Text '{text}' rendered with zero/negative dimensions {bounds}",
                        )
                    )
                elif (x2 > x1 and y2 > y1) and (x1 < 0 or y1 < 0 or x2 > screen_width or y2 > screen_height):
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

            _walk(
                child,
                parent_clickable=child_clickable,
                parent_positive_bounds=child_positive,
            )

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
    check: bool = False,
) -> subprocess.CompletedProcess:
    assert_emulator_only_serial(serial)
    res = runner([adb_bin, "-s", serial] + list(args), capture_output=True, text=True, timeout=timeout)
    if check and getattr(res, "returncode", 0) != 0:
        detail = ((getattr(res, "stderr", "") or "") or (getattr(res, "stdout", "") or "")).strip()
        raise RuntimeError(f"ADB command failed ({' '.join(args)}, rc={res.returncode}): {detail[:300]}")
    return res


def verify_instrumentation_output(returncode: int, stdout: str, stderr: str = "") -> None:
    """REQ-PRO-135.1: Fails closed unless `am instrument` exited 0, reported `OK (`, and had no failure markers."""
    out_text = stdout or ""
    err_text = stderr or ""
    combined = f"{out_text}\n{err_text}"
    if returncode != 0:
        raise RuntimeError(f"am instrument exited with code {returncode}: {combined.strip()[:400]}")
    for marker in INSTRUMENTATION_FAIL_MARKERS:
        if marker in combined:
            raise RuntimeError(f"am instrument reported '{marker}': {combined.strip()[:400]}")
    if "OK (" not in out_text:
        raise RuntimeError(f"am instrument did not report 'OK (' success marker: {out_text.strip()[:400]}")


def validate_captured_ui_xml(xml_text: str, package: str) -> None:
    """REQ-PRO-135.1: Rejects empty/malformed XML, launcher UIs, or dumps missing target `package` nodes."""
    if not xml_text or not xml_text.strip():
        raise RuntimeError("uiautomator dump returned empty XML.")
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError as e:
        raise RuntimeError(f"uiautomator dump returned malformed XML: {e}") from e

    packages = {
        (elem.attrib.get("package") or "").strip()
        for elem in root.iter()
        if (elem.attrib.get("package") or "").strip()
    }
    launcher_hits = sorted(
        pkg for pkg in packages if any(tok in pkg.lower() for tok in LAUNCHER_PACKAGE_TOKENS)
    )
    if launcher_hits and package not in packages:
        raise RuntimeError(
            f"uiautomator captured Android Launcher {launcher_hits} instead of target app '{package}'."
        )
    if package not in packages:
        raise RuntimeError(
            f"uiautomator XML contains no nodes for target package '{package}' (found: {sorted(packages)})."
        )


def install_apk(
    serial: str,
    apk_path: str,
    adb_bin: str = "adb",
    runner: Callable = subprocess.run,
) -> None:
    """Installs an APK (`--apk` or `--test-apk`) via ADB and fails closed on non-zero or Failure output."""
    res = _run_adb(adb_bin, serial, ["install", "-r", "-d", apk_path], timeout=120, runner=runner, check=True)
    combined = f"{getattr(res, 'stdout', '') or ''}\n{getattr(res, 'stderr', '') or ''}"
    if "Failure" in combined:
        raise RuntimeError(f"adb install failed for '{apk_path}': {combined.strip()[:300]}")


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


def _locale_tag_matches(actual: str, target: str) -> bool:
    act = (actual or "").strip().lower().replace("_", "-")
    tgt = (target or "").strip().lower().replace("_", "-")
    if not act or not tgt:
        return False
    return act == tgt or act.split("-")[0] == tgt.split("-")[0]


def verify_get_app_locales_output(stdout: str, target_locale: str, sdk: int = 33) -> List[str]:
    """
    Parses the bracketed locale tag list `[...]` from `adb shell cmd locale get-app-locales`
    and verifies fail-closed that at least one tag matches `target_locale` via `_locale_tag_matches`.
    Avoids substring false positives such as `"de"` in `"debug"` or `"es"` in `"locales"`.
    """
    out = (stdout or "").strip()
    match = re.search(r"\[([^\]]*)\]", out)
    if match is None:
        raise RuntimeError(
            f"Android {sdk} locale verification failed for '{target_locale}': "
            f"missing bracketed locale list in get-app-locales output '{out}'"
        )
    tags = [tok.strip() for tok in match.group(1).split(",") if tok.strip()]
    if not tags or not any(_locale_tag_matches(tag, target_locale) for tag in tags):
        raise RuntimeError(
            f"Android {sdk} locale verification failed for '{target_locale}': "
            f"get-app-locales returned {tags} from '{out}'"
        )
    return tags


def get_device_sdk_int(
    serial: str,
    adb_bin: str = "adb",
    runner: Callable = subprocess.run,
) -> int:
    """Queries `ro.build.version.sdk` on the target emulator serial."""
    res = _run_adb(adb_bin, serial, ["shell", "getprop", "ro.build.version.sdk"], runner=runner, check=True)
    raw = (getattr(res, "stdout", "") or "").strip()
    if not raw.isdigit():
        raise RuntimeError(f"Unable to parse Android SDK version from getprop ro.build.version.sdk: '{raw}'")
    return int(raw)


def set_app_locale(
    serial: str,
    package: str,
    locale: str,
    adb_bin: str = "adb",
    runner: Callable = subprocess.run,
    boot_check_fn: Callable = agent_emulator.is_boot_completed,
    tune_fn: Callable = agent_emulator.tune_emulator_settings,
    sleep_fn: Callable = time.sleep,
    timeout_sec: int = 120,
) -> None:
    """
    REQ-PRO-135.1: Switches runtime locale API-safely.
    - API >= 33: `cmd locale set-app-locales` + verified `get-app-locales`.
    - API < 33 (Android 12): verifies `adb root` (`uid=0`), applies `persist.sys.locale` +
      `system_locales` + runtime restart when locale differs + `am force-stop <package>`.
    """
    sdk = get_device_sdk_int(serial, adb_bin=adb_bin, runner=runner)
    if sdk >= 33:
        _run_adb(
            adb_bin,
            serial,
            ["shell", "cmd", "locale", "set-app-locales", package, "--locales", locale],
            runner=runner,
            check=True,
        )
        verify_res = _run_adb(
            adb_bin,
            serial,
            ["shell", "cmd", "locale", "get-app-locales", package],
            runner=runner,
            check=True,
        )
        verify_get_app_locales_output(getattr(verify_res, "stdout", "") or "", locale, sdk=sdk)
        return

    # API < 33 fallback (e.g. Android 12 API 31)
    cur_res = _run_adb(adb_bin, serial, ["shell", "getprop", "persist.sys.locale"], runner=runner)
    cur_loc = (getattr(cur_res, "stdout", "") or "").strip()
    if not cur_loc:
        prod_res = _run_adb(adb_bin, serial, ["shell", "getprop", "ro.product.locale"], runner=runner)
        cur_loc = (getattr(prod_res, "stdout", "") or "").strip()

    id_res = _run_adb(adb_bin, serial, ["shell", "id"], runner=runner)
    if "uid=0" not in (getattr(id_res, "stdout", "") or ""):
        _run_adb(adb_bin, serial, ["root"], timeout=20, runner=runner, check=True)
        _run_adb(adb_bin, serial, ["wait-for-device"], timeout=30, runner=runner, check=True)
        id_after = _run_adb(adb_bin, serial, ["shell", "id"], runner=runner, check=True)
        if "uid=0" not in (getattr(id_after, "stdout", "") or ""):
            raise RuntimeError(
                f"API < 33 (SDK {sdk}) locale fallback requires rootable emulator (uid=0); "
                f"got '{(getattr(id_after, 'stdout', '') or '').strip()}'. Non-rootable Play Store API < 33 images are unsupported."
            )

    if not _locale_tag_matches(cur_loc, locale):
        _run_adb(adb_bin, serial, ["shell", "setprop", "persist.sys.locale", locale], runner=runner, check=True)
        _run_adb(
            adb_bin,
            serial,
            ["shell", "settings", "put", "system", "system_locales", locale],
            runner=runner,
            check=True,
        )
        _run_adb(adb_bin, serial, ["shell", "am", "force-stop", package], runner=runner)
        _run_adb(adb_bin, serial, ["shell", "setprop", "sys.boot_completed", "0"], runner=runner, check=True)
        _run_adb(adb_bin, serial, ["shell", "stop"], runner=runner, check=True)
        _run_adb(adb_bin, serial, ["shell", "start"], runner=runner, check=True)
        _run_adb(adb_bin, serial, ["wait-for-device"], timeout=30, runner=runner, check=True)

        deadline = time.time() + timeout_sec
        booted = False
        while time.time() < deadline:
            if boot_check_fn(serial, adb_bin):
                svc_res = _run_adb(adb_bin, serial, ["shell", "service", "check", "activity"], runner=runner)
                svc_out = (getattr(svc_res, "stdout", "") or "").lower()
                if "found" in svc_out and "not found" not in svc_out:
                    booted = True
                    break
            sleep_fn(2)
        if not booted:
            raise TimeoutError(f"API {sdk} runtime failed to reboot after locale change to '{locale}' within {timeout_sec}s.")

        tune_fn(serial, adb_bin)
        verify_prop = _run_adb(adb_bin, serial, ["shell", "getprop", "persist.sys.locale"], runner=runner, check=True)
        actual_prop = (getattr(verify_prop, "stdout", "") or "").strip()
        if not _locale_tag_matches(actual_prop, locale):
            raise RuntimeError(
                f"API {sdk} persist.sys.locale verification failed: expected '{locale}', got '{actual_prop}'"
            )
    else:
        _run_adb(adb_bin, serial, ["shell", "am", "force-stop", package], runner=runner)


def get_package_pids(
    serial: str,
    package: str,
    adb_bin: str = "adb",
    runner: Callable = subprocess.run,
) -> Set[str]:
    """Queries running PID(s) for `package` via `adb shell pidof <package>`."""
    res = _run_adb(adb_bin, serial, ["shell", "pidof", package], timeout=15, runner=runner, check=False)
    if getattr(res, "returncode", 1) != 0:
        return set()
    stdout = (getattr(res, "stdout", "") or "").strip()
    return {str(int(tok)) for tok in stdout.split() if tok.isdigit()}


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
    sleep_fn: Callable = time.sleep,
) -> Tuple[List[str], List[BoundsDefect], Optional[str]]:
    """Clears logcat, injects mock GPS, runs instrumentation, launches foreground App UI, and verifies dump + PNG."""
    _run_adb(adb_bin, serial, ["logcat", "-c"], runner=runner, check=True)
    _run_adb(adb_bin, serial, ["emu", "geo", "fix", str(geo_fix[0]), str(geo_fix[1])], runner=runner, check=True)
    app_pids: Set[str] = set()

    if instrumentation_class:
        inst_res = _run_adb(
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
            timeout=180,
            runner=runner,
        )
        verify_instrumentation_output(
            getattr(inst_res, "returncode", 1),
            getattr(inst_res, "stdout", "") or "",
            getattr(inst_res, "stderr", "") or "",
        )
        app_pids.update(get_package_pids(serial, package, adb_bin=adb_bin, runner=runner))

    # Always bring MainActivityWithNavigation to foreground after instrumentation finishes
    start_res = _run_adb(
        adb_bin,
        serial,
        ["shell", "am", "start", "-W", "-n", f"{package}/{DEFAULT_MAIN_ACTIVITY}"],
        timeout=30,
        runner=runner,
        check=True,
    )
    start_out = f"{getattr(start_res, 'stdout', '') or ''}\n{getattr(start_res, 'stderr', '') or ''}"
    if "Error:" in start_out:
        raise RuntimeError(f"am start -W failed: {start_out.strip()[:300]}")
    sleep_fn(2.0)
    app_pids.update(get_package_pids(serial, package, adb_bin=adb_bin, runner=runner))

    # Remove any prior dump so stale XML cannot cause a false pass.
    # Retry uiautomator dump up to 6 times on transient rc=0 `ERROR:` (UiTestAutomationBridge
    # right after am instrument) and up to 3 times when Android's transient starting window
    # (`packages == {"android"}`) is still dismissing after `am start -W`.
    # Launcher, empty, malformed XML, or `cat` read failures still fail closed immediately.
    _run_adb(adb_bin, serial, ["shell", "rm", "-f", UI_DUMP_DEVICE_PATH], runner=runner)
    xml_text = ""
    try:
        for ui_attempt in range(3):
            dump_ok = False
            last_dump_err: Optional[Exception] = None
            for attempt in range(6):
                dump_res = _run_adb(
                    adb_bin,
                    serial,
                    ["shell", "uiautomator", "dump", UI_DUMP_DEVICE_PATH],
                    timeout=30,
                    runner=runner,
                    check=False,
                )
                dump_out = f"{getattr(dump_res, 'stdout', '') or ''}\n{getattr(dump_res, 'stderr', '') or ''}"
                if getattr(dump_res, "returncode", 1) == 0 and "ERROR:" not in dump_out:
                    dump_ok = True
                    break
                last_dump_err = RuntimeError(
                    f"uiautomator dump failed (rc={getattr(dump_res, 'returncode', 1)}): {dump_out.strip()[:300]}"
                )
                if attempt < 5:
                    sleep_fn(1.5)
            if not dump_ok and last_dump_err is not None:
                raise last_dump_err
            xml_res = _run_adb(
                adb_bin,
                serial,
                ["shell", "cat", UI_DUMP_DEVICE_PATH],
                runner=runner,
                check=True,
            )
            xml_text = getattr(xml_res, "stdout", "") or ""
            if ui_attempt < 2 and xml_text.strip():
                try:
                    _root = ET.fromstring(xml_text)
                    _pkgs = {
                        (e.attrib.get("package") or "").strip()
                        for e in _root.iter()
                        if (e.attrib.get("package") or "").strip()
                    }
                    if _pkgs == {"android"}:
                        sleep_fn(2.0)
                        continue
                except ET.ParseError:
                    pass
            break
    finally:
        try:
            _run_adb(adb_bin, serial, ["shell", "rm", "-f", UI_DUMP_DEVICE_PATH], runner=runner)
        except Exception:
            pass
    validate_captured_ui_xml(xml_text, package)

    app_pids.update(get_package_pids(serial, package, adb_bin=adb_bin, runner=runner))
    log_res = _run_adb(adb_bin, serial, ["logcat", "-d", "-v", "time"], runner=runner)

    os.makedirs(screenshots_dir, exist_ok=True)
    screenshot_path = os.path.join(screenshots_dir, f"{avd_name}_{label}.png")
    assert_emulator_only_serial(serial)
    shot_res = runner([adb_bin, "-s", serial, "exec-out", "screencap", "-p"], capture_output=True, timeout=20)
    png_bytes = getattr(shot_res, "stdout", b"") or b""
    if getattr(shot_res, "returncode", 1) != 0 or not isinstance(png_bytes, (bytes, bytearray)) or not png_bytes.startswith(b"\x89PNG"):
        raise RuntimeError(
            f"screencap failed or returned invalid PNG (rc={getattr(shot_res, 'returncode', 1)}, bytes={len(png_bytes)})"
        )
    with open(screenshot_path, "wb") as f:
        f.write(png_bytes)

    log_defects = scan_logcat_defects(
        getattr(log_res, "stdout", "") or "",
        app_pids=app_pids,
        package=package,
    )
    bounds_defects = analyze_uiautomator_bounds(xml_text)
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

    diag_lines: List[str] = []
    for r in os_results:
        if r.error:
            diag_lines.append(f"- OS `{r.avd_name}` (`{r.locale}`): error={r.error}")
        for d in r.logcat_defects:
            diag_lines.append(f"- OS `{r.avd_name}` (`{r.locale}`) logcat: {d}")
        for b in r.bounds_defects:
            diag_lines.append(f"- OS `{r.avd_name}` (`{r.locale}`) bounds [{b.kind}]: {b.detail}")
    for lr in locale_results:
        if lr.error:
            diag_lines.append(f"- Locale `{lr.locale}` (`{lr.avd_name}`): error={lr.error}")
        for d in lr.logcat_defects:
            diag_lines.append(f"- Locale `{lr.locale}` (`{lr.avd_name}`) logcat: {d}")
        for b in lr.bounds_defects:
            diag_lines.append(f"- Locale `{lr.locale}` (`{lr.avd_name}`) bounds [{b.kind}]: {b.detail}")
    if diag_lines:
        lines.extend(["", "## 3. Failure & Defect Diagnostics", ""] + diag_lines)

    with open(report_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    return report_path


def run_sweep(
    requested_avds: Optional[Sequence[str]] = None,
    ref_avd: Optional[str] = None,
    apk_path: Optional[str] = None,
    test_apk_path: Optional[str] = None,
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
                install_apk(serial, apk_path, adb_bin=adb_bin, runner=runner)
            if test_apk_path:
                install_apk(serial, test_apk_path, adb_bin=adb_bin, runner=runner)
            configure_fn(serial, package=package, adb_bin=adb_bin)
            sleep_fn(15.0)
            set_app_locale(
                serial,
                package,
                rotating_locale,
                adb_bin=adb_bin,
                runner=runner,
                boot_check_fn=boot_check_fn,
                tune_fn=tune_fn,
                sleep_fn=sleep_fn,
                timeout_sec=timeout_sec,
            )

            log_defects, bounds_defects, shot = run_ui_probe(
                serial=serial,
                package=package,
                avd_name=avd,
                label=rotating_locale,
                screenshots_dir=screenshots_dir,
                adb_bin=adb_bin,
                runner=runner,
                instrumentation_class=instrumentation_class,
                sleep_fn=sleep_fn,
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

            # Tier 2: Execute language UI sweep on reference AVD before tearing it down
            if avd == chosen_ref_avd:
                for loc in locales:
                    try:
                        set_app_locale(
                            serial,
                            package,
                            loc,
                            adb_bin=adb_bin,
                            runner=runner,
                            boot_check_fn=boot_check_fn,
                            tune_fn=tune_fn,
                            sleep_fn=sleep_fn,
                            timeout_sec=timeout_sec,
                        )
                        l_log, l_bounds, l_shot = run_ui_probe(
                            serial=serial,
                            package=package,
                            avd_name=avd,
                            label=f"loc_{loc}",
                            screenshots_dir=screenshots_dir,
                            adb_bin=adb_bin,
                            runner=runner,
                            instrumentation_class=instrumentation_class,
                            sleep_fn=sleep_fn,
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
                    except Exception as loc_err:
                        locale_results.append(
                            LocaleSweepResult(
                                locale=loc,
                                avd_name=avd,
                                serial=serial,
                                passed=False,
                                error=str(loc_err),
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
    parser.add_argument("--ref-avd", default=None, help="Reference AVD for language UI sweep")
    parser.add_argument("--apk", default=None, help="Path to debug APK to install per AVD")
    parser.add_argument("--test-apk", default=None, help="Path to debug androidTest APK to install per AVD")
    parser.add_argument("--locales", nargs="+", default=None, help="Locales for UI sweep (default: all 9 supported)")
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
        test_apk_path=args.test_apk,
        package=args.package,
        locales=args.locales if args.locales is not None else SUPPORTED_LOCALES,
        out_dir=args.out_dir,
        port=args.port,
        timeout_sec=args.timeout,
        allow_fewer_avds=args.allow_fewer_avds,
    )
    print(f"Emulator sweep report written to {report_path} (OS={len(os_res)}, Locales={len(loc_res)}, PASS={all_ok})")
    for r in os_res:
        if not r.passed:
            print(f"[FAIL] AVD={r.avd_name} locale={r.locale} error={r.error} logcat={len(r.logcat_defects)} bounds={len(r.bounds_defects)}", file=sys.stderr)
    for lr in loc_res:
        if not lr.passed:
            print(f"[FAIL] Locale={lr.locale} avd={lr.avd_name} error={lr.error} logcat={len(lr.logcat_defects)} bounds={len(lr.bounds_defects)}", file=sys.stderr)
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())
