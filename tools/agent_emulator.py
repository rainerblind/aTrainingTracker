#!/usr/bin/env python3
"""
agent_emulator.py - Android Emulator Lifecycle & Guardrail Manager (ATT-6506 / REQ-TST-004)

Manages Android emulator (emulator-5554) auto-boot, app permission configuration,
mock-location enablement, and golden database seeding for autonomous agent testing,
while enforcing a strict physical-phone guardrail in Phase 2.

Invariants:
- Strictly Python 3 standard library (no pip packages per TST-PRO-005.6).
- Zero production code modifications (app/src/main/ untouched).
- Prevents autonomous agents from modifying connected physical USB devices during Phase 2.
"""

import argparse
from contextlib import contextmanager
import fcntl
import os
import re
import shutil
import subprocess
import sys
import time
from typing import Iterator, List, Optional, Sequence, Tuple


DEFAULT_PACKAGE = "com.atrainingtracker.debug"
DEFAULT_AVD = "Pixel_8_Android_15"
FALLBACK_AVD = "Pixel_7_Android_36"
TARGET_SERIAL = "emulator-5554"
DEFAULT_GOLDEN_DIR = os.path.join(os.path.dirname(__file__), "golden_db")
DEFAULT_EMULATOR_LOCK = "/tmp/att_emulator.lock"
DEFAULT_LOCK_TIMEOUT_SEC = 1800.0
EMULATOR_LOCK_ENV_VAR = "ATT_EMULATOR_LOCK_HELD"
SPRINT_LOCK_ENV_VAR = "ATT_SPRINT_LOCK_HELD"


@contextmanager
def emulator_session_lock(
    lock_path: str = DEFAULT_EMULATOR_LOCK,
    timeout_sec: float = DEFAULT_LOCK_TIMEOUT_SEC,
    poll_interval_sec: float = 0.25,
) -> Iterator[None]:
    """
    Exclusive POSIX flock serializing all emulator-5554 lifecycle and UI sessions
    (installDebug + am start + uiautomator + input tap + screencap + logcat).

    Deadlock-prevention invariant (canonical lock hierarchy):
      /tmp/att_emulator.lock MUST always be acquired OUTSIDE /tmp/att_build_merge.lock.
      Nesting emulator_session_lock inside parallel_sprint_lock.py (ATT_SPRINT_LOCK_HELD=1)
      is rejected immediately to prevent AB-BA deadlocks across parallel lanes.
    """
    if os.environ.get(EMULATOR_LOCK_ENV_VAR) == "1" and lock_path == DEFAULT_EMULATOR_LOCK:
        yield
        return

    if os.environ.get(SPRINT_LOCK_ENV_VAR) == "1" and lock_path == DEFAULT_EMULATOR_LOCK:
        raise RuntimeError(
            "Lock-order inversion detected: ATT_SPRINT_LOCK_HELD=1 (/tmp/att_build_merge.lock) is already held. "
            "To prevent AB-BA deadlocks, always wrap emulator sessions with "
            "'python3 tools/agent_emulator.py exec --ensure -- ...' as the OUTER wrapper "
            "(never nest agent_emulator.py inside parallel_sprint_lock.py)."
        )

    lock_dir = os.path.dirname(os.path.abspath(lock_path))
    if lock_dir and not os.path.exists(lock_dir):
        os.makedirs(lock_dir, exist_ok=True)

    fd = os.open(lock_path, os.O_CREAT | os.O_RDWR, 0o666)
    try:
        os.chmod(lock_path, 0o666)
    except OSError:
        pass

    start_time = time.monotonic()
    warned = False
    prev_env = os.environ.get(EMULATOR_LOCK_ENV_VAR)
    try:
        while True:
            try:
                fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
                if lock_path == DEFAULT_EMULATOR_LOCK:
                    os.environ[EMULATOR_LOCK_ENV_VAR] = "1"
                break
            except (BlockingIOError, IOError, OSError):
                elapsed = time.monotonic() - start_time
                if elapsed >= timeout_sec:
                    raise TimeoutError(
                        f"Timeout ({timeout_sec:.1f}s) exceeded waiting for emulator session lock on {lock_path}"
                    )
                if not warned and elapsed >= 1.0:
                    print(
                        f"[emulator-lock] Waiting for exclusive emulator session lock on {lock_path} "
                        f"(held by another lane/Stage-6 reviewer)...",
                        file=sys.stderr,
                        flush=True,
                    )
                    warned = True
                time.sleep(poll_interval_sec)
        yield
    finally:
        if lock_path == DEFAULT_EMULATOR_LOCK:
            if prev_env is None:
                os.environ.pop(EMULATOR_LOCK_ENV_VAR, None)
            else:
                os.environ[EMULATOR_LOCK_ENV_VAR] = prev_env
        try:
            fcntl.flock(fd, fcntl.LOCK_UN)
        finally:
            os.close(fd)


def exec_locked_session(
    cmd: Sequence[str],
    ensure: bool = False,
    seed: bool = False,
    preferred_avd: Optional[str] = None,
    boot_timeout_sec: int = 120,
    lock_timeout_sec: float = DEFAULT_LOCK_TIMEOUT_SEC,
    lock_path: str = DEFAULT_EMULATOR_LOCK,
    target_serial: str = TARGET_SERIAL,
    package: str = DEFAULT_PACKAGE,
    adb_bin: str = "adb",
) -> int:
    """
    Executes a complete emulator verification session (e.g. installDebug + am start +
    uiautomator + taps + screencap + logcat) atomically under /tmp/att_emulator.lock.
    """
    if not cmd:
        raise ValueError("exec requires a non-empty command after '--'.")

    with emulator_session_lock(lock_path=lock_path, timeout_sec=lock_timeout_sec):
        active_serial = target_serial
        if ensure:
            active_serial = ensure_emulator(
                preferred_avd=preferred_avd,
                timeout_sec=boot_timeout_sec,
                target_serial=target_serial,
                adb_bin=adb_bin,
                seed=seed,
                package=package,
            )
        child_env = os.environ.copy()
        child_env["ANDROID_SERIAL"] = active_serial
        child_env["ATT_AGENT_MODE"] = "1"
        if lock_path == DEFAULT_EMULATOR_LOCK:
            child_env[EMULATOR_LOCK_ENV_VAR] = "1"
        proc = subprocess.run(list(cmd), env=child_env)
        return proc.returncode

CORE_DATABASES = [
    "WorkoutSummaries.db",
    "RouteClusters.db",
    "Climbs.db",
    "PeriodSummaries.db",
    "HrvReadiness.db",
    "Devices.db",
    "Equipment.db",
    "SportType.db",
]

CORE_PERMISSIONS = [
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.BLUETOOTH_SCAN",
    "android.permission.BLUETOOTH_CONNECT",
]


class AdbDevice:
    """Represents an attached ADB device."""

    def __init__(self, serial: str, state: str, is_emulator: bool, model: str = "", product: str = ""):
        self.serial = serial
        self.state = state
        self.is_emulator = is_emulator
        self.model = model
        self.product = product

    def __repr__(self) -> str:
        kind = "EMULATOR" if self.is_emulator else "PHYSICAL"
        return f"<AdbDevice {self.serial} ({kind}) state={self.state} model={self.model}>"


def parse_adb_devices(output: str) -> List[AdbDevice]:
    """Parses output of 'adb devices -l' into structured AdbDevice objects."""
    devices = []
    lines = output.strip().splitlines()
    for line in lines:
        line = line.strip()
        if not line or line.startswith("List of devices"):
            continue

        parts = line.split()
        if len(parts) < 2:
            continue

        serial = parts[0]
        state = parts[1]
        is_emulator = serial.startswith("emulator-")

        model = ""
        product = ""
        for token in parts[2:]:
            if token.startswith("model:"):
                model = token.split(":", 1)[1]
            elif token.startswith("product:"):
                product = token.split(":", 1)[1]

        devices.append(AdbDevice(serial, state, is_emulator, model, product))
    return devices


def get_adb_devices(adb_bin: str = "adb") -> List[AdbDevice]:
    """Runs 'adb devices -l' and returns parsed AdbDevice list."""
    try:
        res = subprocess.run([adb_bin, "devices", "-l"], capture_output=True, text=True, check=True)
        return parse_adb_devices(res.stdout)
    except Exception as e:
        raise RuntimeError(f"Failed to query ADB devices via '{adb_bin} devices -l': {e}")


def resolve_target_device(
    device_serial: Optional[str] = None,
    agent_mode: Optional[bool] = None,
    adb_bin: str = "adb",
) -> str:
    """
    Resolves the target ADB device serial respecting the Two-Tier Device Policy:
    1. If agent_mode is active (--agent or ATT_AGENT_MODE=1), strictly prohibits targeting physical USB phones.
    2. When multiple devices are attached, prioritizes 'emulator-5554' / running emulators.
    """
    if agent_mode is None:
        agent_mode = os.environ.get("ATT_AGENT_MODE") == "1"

    if device_serial is None:
        device_serial = os.environ.get("ANDROID_SERIAL")

    devices = get_adb_devices(adb_bin)

    if device_serial:
        serial = device_serial.strip()
        is_emu = serial.startswith("emulator-")
        if agent_mode and not is_emu:
            raise RuntimeError(
                f"Physical-Phone Guardrail Violation: Agent mode is active (ATT_AGENT_MODE=1 or --agent), "
                f"but target device '{serial}' is a physical USB device. Autonomous agents must target "
                f"an Android emulator (e.g. emulator-5554) during Stages 1–5!"
            )
        return serial

    # No serial specified: check connected emulators
    online_emulators = [d for d in devices if d.is_emulator and d.state == "device"]
    if online_emulators:
        for emu in online_emulators:
            if emu.serial == TARGET_SERIAL:
                return emu.serial
        return online_emulators[0].serial

    if agent_mode:
        raise RuntimeError(
            "Physical-Phone Guardrail Violation: Agent mode is active (ATT_AGENT_MODE=1 or --agent), "
            "but no Android emulator is running. Run 'python3 tools/agent_emulator.py ensure' to boot an emulator!"
        )

    # Non-agent mode: return physical device if present
    online_physical = [d for d in devices if not d.is_emulator and d.state == "device"]
    if online_physical:
        return online_physical[0].serial

    if devices:
        return devices[0].serial

    raise RuntimeError("No connected Android device found via ADB. Connect a device or run 'agent_emulator.py ensure'.")


def find_emulator_binary() -> str:
    """Locates the Android SDK emulator executable."""
    home_sdk = os.path.expanduser("~/Android/Sdk/emulator/emulator")
    if os.path.isfile(home_sdk) and os.access(home_sdk, os.X_OK):
        return home_sdk

    for env_var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk_root = os.environ.get(env_var)
        if sdk_root:
            bin_path = os.path.join(sdk_root, "emulator", "emulator")
            if os.path.isfile(bin_path) and os.access(bin_path, os.X_OK):
                return bin_path

    which_path = shutil.which("emulator")
    if which_path:
        return which_path

    raise FileNotFoundError(
        "Could not find Android SDK 'emulator' executable in ~/Android/Sdk/emulator, "
        "$ANDROID_HOME, $ANDROID_SDK_ROOT, or PATH."
    )


def get_available_avds(emulator_bin: Optional[str] = None) -> List[str]:
    """Returns list of installed Android Virtual Devices (AVDs)."""
    bin_path = emulator_bin or find_emulator_binary()
    try:
        res = subprocess.run([bin_path, "-list-avds"], capture_output=True, text=True, check=True)
        return [line.strip() for line in res.stdout.strip().splitlines() if line.strip()]
    except Exception as e:
        raise RuntimeError(f"Failed to query available AVDs via '{bin_path} -list-avds': {e}")


def is_boot_completed(serial: str, adb_bin: str = "adb") -> bool:
    """Checks if target device has completed sys.boot_completed."""
    try:
        res = subprocess.run(
            [adb_bin, "-s", serial, "shell", "getprop", "sys.boot_completed"],
            capture_output=True,
            text=True,
            timeout=5,
        )
        return res.stdout.strip() == "1"
    except Exception:
        return False


def tune_emulator_settings(serial: str, adb_bin: str = "adb") -> None:
    """Wakes keyguard and disables animation scales for deterministic Compose UI execution."""
    cmds = [
        ["shell", "input", "keyevent", "82"],  # Unlock / Menu
        ["shell", "settings", "put", "global", "window_animation_scale", "0.0"],
        ["shell", "settings", "put", "global", "transition_animation_scale", "0.0"],
        ["shell", "settings", "put", "global", "animator_duration_scale", "0.0"],
    ]
    for cmd in cmds:
        try:
            subprocess.run([adb_bin, "-s", serial] + cmd, capture_output=True, text=True, timeout=10)
        except Exception:
            pass


def ensure_emulator(
    preferred_avd: Optional[str] = None,
    timeout_sec: int = 120,
    target_serial: str = TARGET_SERIAL,
    adb_bin: str = "adb",
    seed: bool = False,
    package: str = DEFAULT_PACKAGE,
) -> str:
    """
    Ensures an emulator instance is running and fully booted.
    If not running, boots preferred AVD, waits for sys.boot_completed,
    tunes animation scales, optionally seeds databases, and returns serial.
    """
    devices = get_adb_devices(adb_bin)
    for d in devices:
        if d.is_emulator and d.state == "device":
            if is_boot_completed(d.serial, adb_bin):
                tune_emulator_settings(d.serial, adb_bin)
                if seed:
                    configure_app(d.serial, package=package, adb_bin=adb_bin)
                    seed_db(target_serial=d.serial, package=package, adb_bin=adb_bin)
                return d.serial

    # Boot emulator
    emu_bin = find_emulator_binary()
    avds = get_available_avds(emu_bin)
    if not avds:
        raise RuntimeError("No AVDs found. Please create an AVD such as Pixel_8_Android_15.")

    chosen_avd = preferred_avd
    if not chosen_avd:
        for cand in [DEFAULT_AVD, FALLBACK_AVD]:
            if cand in avds:
                chosen_avd = cand
                break
    if not chosen_avd:
        chosen_avd = avds[0]

    boot_cmd = [emu_bin, "-avd", chosen_avd]
    # Launch in background detached session
    subprocess.Popen(boot_cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)

    start_time = time.time()
    active_serial = None
    while time.time() - start_time < timeout_sec:
        devs = get_adb_devices(adb_bin)
        for d in devs:
            if d.is_emulator and d.state == "device":
                if is_boot_completed(d.serial, adb_bin):
                    active_serial = d.serial
                    break
        if active_serial:
            break
        time.sleep(2)

    if not active_serial:
        raise TimeoutError(f"Emulator failed to complete boot within {timeout_sec} seconds.")

    tune_emulator_settings(active_serial, adb_bin)

    if seed:
        configure_app(active_serial, package=package, adb_bin=adb_bin)
        seed_db(target_serial=active_serial, package=package, adb_bin=adb_bin)

    return active_serial


def configure_app(
    serial: str = TARGET_SERIAL,
    package: str = DEFAULT_PACKAGE,
    adb_bin: str = "adb",
) -> None:
    """Grants runtime permissions and sets mock location appops on emulator."""
    for perm in CORE_PERMISSIONS:
        subprocess.run(
            [adb_bin, "-s", serial, "shell", "pm", "grant", package, perm],
            capture_output=True,
            text=True,
            timeout=10,
        )

    # Set mock location appops for app and Android shell UID (2000)
    subprocess.run(
        [adb_bin, "-s", serial, "shell", "appops", "set", package, "android:mock_location", "allow"],
        capture_output=True,
        text=True,
        timeout=10,
    )
    subprocess.run(
        [adb_bin, "-s", serial, "shell", "appops", "set", "2000", "android:mock_location", "allow"],
        capture_output=True,
        text=True,
        timeout=10,
    )


def sync_from_physical(
    source_serial: Optional[str] = None,
    golden_dir: str = DEFAULT_GOLDEN_DIR,
    package: str = DEFAULT_PACKAGE,
    adb_bin: str = "adb",
) -> List[str]:
    """Pulls core SQLite databases non-destructively from physical device via run-as."""
    if not source_serial:
        devices = get_adb_devices(adb_bin)
        physical = [d for d in devices if not d.is_emulator and d.state == "device"]
        if not physical:
            raise RuntimeError("No physical Android device connected to sync databases from.")
        source_serial = physical[0].serial

    os.makedirs(golden_dir, exist_ok=True)
    synced_files = []

    for db_name in CORE_DATABASES:
        local_path = os.path.join(golden_dir, db_name)
        # Stream database non-destructively via exec-out run-as
        cmd = [adb_bin, "-s", source_serial, "exec-out", "run-as", package, "cat", f"databases/{db_name}"]
        try:
            res = subprocess.run(cmd, capture_output=True, timeout=15)
            if res.returncode == 0 and len(res.stdout) > 0 and not res.stdout.startswith(b"cat:"):
                with open(local_path, "wb") as f:
                    f.write(res.stdout)
                synced_files.append(db_name)
        except Exception:
            pass

    return synced_files


def seed_db(
    target_serial: str = TARGET_SERIAL,
    golden_dir: str = DEFAULT_GOLDEN_DIR,
    package: str = DEFAULT_PACKAGE,
    adb_bin: str = "adb",
) -> List[str]:
    """Pushes golden databases to the target emulator via run-as."""
    if not os.path.isdir(golden_dir):
        # Auto-sync from physical device if connected
        try:
            sync_from_physical(golden_dir=golden_dir, package=package, adb_bin=adb_bin)
        except Exception:
            return []

    # Ensure target app databases directory exists
    subprocess.run(
        [adb_bin, "-s", target_serial, "shell", "run-as", package, "mkdir", "-p", "databases"],
        capture_output=True,
        text=True,
        timeout=10,
    )

    seeded = []
    for db_name in CORE_DATABASES:
        local_path = os.path.join(golden_dir, db_name)
        if not os.path.isfile(local_path) or os.path.getsize(local_path) == 0:
            continue

        remote_tmp = f"/data/local/tmp/{db_name}"
        # Push to tmp
        push_res = subprocess.run([adb_bin, "-s", target_serial, "push", local_path, remote_tmp], capture_output=True)
        if push_res.returncode != 0:
            continue

        # Copy into package database sandbox and adjust permissions
        install_cmd = (
            f"run-as {package} cp {remote_tmp} databases/{db_name} && "
            f"run-as {package} chmod 660 databases/{db_name} && "
            f"rm -f {remote_tmp}"
        )
        sub_res = subprocess.run(
            [adb_bin, "-s", target_serial, "shell", install_cmd],
            capture_output=True,
            text=True,
            timeout=10,
        )
        if sub_res.returncode == 0:
            seeded.append(db_name)

    return seeded


def build_cli_parser() -> argparse.ArgumentParser:
    """Builds CLI argument parser for agent_emulator."""
    parser = argparse.ArgumentParser(
        description="Android Emulator Lifecycle & Physical-Phone Guardrail Utility (ATT-6506 / REQ-TST-004)"
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    # ensure
    p_ensure = subparsers.add_parser("ensure", help="Ensure emulator is booted and ready")
    p_ensure.add_argument("--avd", default=None, help=f"Preferred AVD name (default: {DEFAULT_AVD})")
    p_ensure.add_argument("--timeout", type=int, default=120, help="Boot timeout in seconds (default: 120)")
    p_ensure.add_argument("--serial", default=TARGET_SERIAL, help="Target serial (default: emulator-5554)")
    p_ensure.add_argument("--seed", action="store_true", help="Configure app permissions and seed databases")
    p_ensure.add_argument("--package", default=DEFAULT_PACKAGE, help=f"Target package (default: {DEFAULT_PACKAGE})")

    # configure-app
    p_config = subparsers.add_parser("configure-app", help="Configure app permissions and mock location")
    p_config.add_argument("--serial", default=TARGET_SERIAL, help="Target emulator serial")
    p_config.add_argument("--package", default=DEFAULT_PACKAGE, help=f"Target package (default: {DEFAULT_PACKAGE})")

    # seed-db
    p_seed = subparsers.add_parser("seed-db", help="Seed debug databases into emulator")
    p_seed.add_argument("--target", default=TARGET_SERIAL, help="Target emulator serial")
    p_seed.add_argument("--golden-dir", default=DEFAULT_GOLDEN_DIR, help="Golden database directory")
    p_seed.add_argument("--package", default=DEFAULT_PACKAGE, help="Target package name")

    # sync-from-physical
    p_sync = subparsers.add_parser("sync-from-physical", help="Extract databases from connected physical phone")
    p_sync.add_argument("--source", default=None, help="Physical source device serial")
    p_sync.add_argument("--golden-dir", default=DEFAULT_GOLDEN_DIR, help="Directory to store extracted snapshots")
    p_sync.add_argument("--package", default=DEFAULT_PACKAGE, help="Target package name")

    # devices
    subparsers.add_parser("devices", help="List attached ADB devices categorized")

    # resolve-target
    p_res = subparsers.add_parser("resolve-target", help="Resolve target ADB device enforcing policy")
    p_res.add_argument("--serial", default=None, help="Explicit target serial")
    p_res.add_argument("--agent", action="store_true", help="Enforce agent mode physical-phone guardrail")

    # exec (exclusive locked emulator session)
    p_exec = subparsers.add_parser(
        "exec",
        help="Run a complete emulator verification session exclusively under /tmp/att_emulator.lock",
    )
    p_exec.add_argument("--ensure", action="store_true", help="Ensure emulator is booted and tuned before running command")
    p_exec.add_argument("--seed", action="store_true", help="With --ensure, also configure permissions and seed golden DBs")
    p_exec.add_argument("--avd", default=None, help=f"Preferred AVD name when --ensure is set (default: {DEFAULT_AVD})")
    p_exec.add_argument("--boot-timeout", type=int, default=120, help="Emulator boot timeout in seconds (default: 120)")
    p_exec.add_argument("--timeout", type=float, default=DEFAULT_LOCK_TIMEOUT_SEC, help="Lock wait timeout in seconds (default: 1800)")
    p_exec.add_argument("--lock-file", default=DEFAULT_EMULATOR_LOCK, help=f"Lock file path (default: {DEFAULT_EMULATOR_LOCK})")
    p_exec.add_argument("--serial", default=TARGET_SERIAL, help="Target emulator serial (default: emulator-5554)")
    p_exec.add_argument("--package", default=DEFAULT_PACKAGE, help=f"Target package (default: {DEFAULT_PACKAGE})")
    p_exec.add_argument("cmd", nargs=argparse.REMAINDER, help="Command to execute under the emulator session lock (prefix with --)")

    return parser


def main() -> None:
    parser = build_cli_parser()
    args = parser.parse_args()

    try:
        if args.command == "ensure":
            with emulator_session_lock():
                serial = ensure_emulator(
                    preferred_avd=args.avd,
                    timeout_sec=args.timeout,
                    target_serial=args.serial,
                    seed=args.seed,
                    package=args.package,
                )
            print(serial)

        elif args.command == "configure-app":
            with emulator_session_lock():
                configure_app(serial=args.serial, package=args.package)
            print(f"Configured permissions and mock location for {args.package} on {args.serial}.")

        elif args.command == "seed-db":
            with emulator_session_lock():
                seeded = seed_db(target_serial=args.target, golden_dir=args.golden_dir, package=args.package)
            print(f"Seeded {len(seeded)} database(s) onto {args.target}: {', '.join(seeded)}")

        elif args.command == "sync-from-physical":
            synced = sync_from_physical(source_serial=args.source, golden_dir=args.golden_dir, package=args.package)
            print(f"Successfully extracted {len(synced)} database(s) from physical device to {args.golden_dir}.")

        elif args.command == "devices":
            devs = get_adb_devices()
            print(f"{'SERIAL':<22} {'TYPE':<10} {'STATE':<10} {'MODEL':<20} {'PRODUCT'}")
            print("-" * 75)
            for d in devs:
                kind = "EMULATOR" if d.is_emulator else "PHYSICAL"
                print(f"{d.serial:<22} {kind:<10} {d.state:<10} {d.model:<20} {d.product}")

        elif args.command == "resolve-target":
            target = resolve_target_device(device_serial=args.serial, agent_mode=args.agent)
            print(target)

        elif args.command == "exec":
            cmd = list(args.cmd)
            if cmd and cmd[0] == "--":
                cmd = cmd[1:]
            if not cmd:
                parser.error("exec requires a command (e.g. agent_emulator.py exec --ensure -- bash -c '...')")
            rc = exec_locked_session(
                cmd=cmd,
                ensure=args.ensure,
                seed=args.seed,
                preferred_avd=args.avd,
                boot_timeout_sec=args.boot_timeout,
                lock_timeout_sec=args.timeout,
                lock_path=args.lock_file,
                target_serial=args.serial,
                package=args.package,
            )
            sys.exit(rc)

    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
