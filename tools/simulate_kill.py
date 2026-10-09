#!/usr/bin/env python3
"""
tools/simulate_kill.py - Process Kill, Whitelist & Permission Simulation Tool (ATT-2469 / ATT-2079)
===================================================================================================
A standalone developer and desk testing tool to inspect, simulate, and verify Android process
termination scenarios, battery optimization whitelists, and runtime permission revocations.

Features:
- Option A: Fast interactive terminal dashboard with single-keypress hotkeys (no Enter required).
- Option B: Sleek, zero-dependency Web GUI (--gui) with real-time status cards and click triggers.
- Option C: Scriptable CLI commands (--kill, --whitelist, --permission, --launch, --reset).

Enables desk verification of:
- ATT-2079: Forensic crash & kill analysis via ApplicationExitInfo (Battery Kill, LMK, Permission Revoked)
- Progressive Escalation Ladder (Stage 1 -> Stage 2 -> Stage 3)
- Battery Optimization Settings shortcut & recovery dialogs
- Runtime permission revocation behavior

Usage:
    python3 tools/simulate_kill.py                     # Interactive single-key terminal menu
    python3 tools/simulate_kill.py --gui               # Launch sleek browser GUI dashboard
    python3 tools/simulate_kill.py --status            # Show live status dashboard
    python3 tools/simulate_kill.py --kill battery      # Un-whitelist + kill (simulates battery saver kill)
    python3 tools/simulate_kill.py --kill permission   # Revoke location permission (native OS kill)
    python3 tools/simulate_kill.py --kill lmk          # Low memory / background kill
    python3 tools/simulate_kill.py --kill sigkill      # Direct SIGKILL (kill -9)
    python3 tools/simulate_kill.py --whitelist off     # Remove from battery whitelist (subject to kills)
    python3 tools/simulate_kill.py --whitelist on      # Add to battery whitelist (exempt)
    python3 tools/simulate_kill.py --whitelist toggle  # Toggle battery whitelist state
    python3 tools/simulate_kill.py --permission grant  # Grant location and notification permissions
    python3 tools/simulate_kill.py --permission revoke # Revoke location permission
    python3 tools/simulate_kill.py --launch            # Launch MainActivity on device
    python3 tools/simulate_kill.py --reset             # Reset kill counter, restore whitelist and permissions
"""

import sys
import os
import re
import json
import time
import select
import argparse
import subprocess
import webbrowser
import http.server
import socketserver
import xml.etree.ElementTree as ET
from dataclasses import dataclass, asdict
from typing import List, Optional, Tuple, Dict, Any

try:
    import termios
    import tty
    HAS_TERMIOS = True
except ImportError:
    HAS_TERMIOS = False

TARGET_PACKAGES = ["com.atrainingtracker.debug", "com.atrainingtracker"]
MAIN_ACTIVITY = ".activities.MainActivityWithNavigation"
LOCATION_PERMISSION = "android.permission.ACCESS_FINE_LOCATION"
COARSE_LOCATION_PERMISSION = "android.permission.ACCESS_COARSE_LOCATION"
NOTIFICATION_PERMISSION = "android.permission.POST_NOTIFICATIONS"


@dataclass
class ExitInfoRecord:
    index: int
    timestamp_str: str
    pid: Optional[int]
    reason_code: int
    reason_name: str
    subreason: str
    description: str


@dataclass
class AppProcessStatus:
    package_name: str
    device_serial: str
    pids: List[int]
    is_whitelisted: bool
    location_granted: bool
    notification_granted: bool
    battery_kill_count: int
    last_evaluated_timestamp: int
    latest_exit: Optional[ExitInfoRecord] = None


class TerminalKeyReader:
    """Non-blocking single-keypress reader for interactive terminal sessions."""

    def __init__(self):
        self.old_settings = None
        if HAS_TERMIOS and sys.stdin.isatty():
            try:
                self.old_settings = termios.tcgetattr(sys.stdin)
                tty.setcbreak(sys.stdin.fileno())
            except Exception:
                self.old_settings = None

    def restore(self):
        if self.old_settings is not None:
            try:
                termios.tcsetattr(sys.stdin, termios.TCSADRAIN, self.old_settings)
            except Exception:
                pass
            self.old_settings = None

    def poll_key(self) -> Optional[str]:
        """Returns key pressed or None if no input waiting."""
        if not HAS_TERMIOS or not sys.stdin.isatty():
            return None
        try:
            rlist, _, _ = select.select([sys.stdin], [], [], 0)
            if rlist:
                return sys.stdin.read(1)
        except Exception:
            return None
        return None


class KillSimulatorController:
    """Manages device interactions, process kills, whitelists, and permissions over ADB."""

    def __init__(self, device_serial: Optional[str] = None, package_name: Optional[str] = None):
        self.device_serial = device_serial or self.detect_device()
        self.package_name = package_name or self.detect_package()

    def detect_device(self) -> str:
        """Finds first attached ADB device."""
        try:
            res = subprocess.run(["adb", "devices"], capture_output=True, text=True, check=True)
            lines = res.stdout.strip().splitlines()
            devices = []
            for line in lines[1:]:
                parts = line.split()
                if len(parts) >= 2 and parts[1] == "device":
                    devices.append(parts[0])
            if not devices:
                raise RuntimeError("No connected Android device found via ADB. Please connect your device via USB.")
            return devices[0]
        except Exception as e:
            raise RuntimeError(f"Failed to query ADB devices: {e}")

    def detect_package(self) -> str:
        """Finds target package installed on device."""
        res = self.run_adb(["shell", "pm", "list", "packages"], check=False)
        output = res.stdout or ""
        for pkg in TARGET_PACKAGES:
            if f"package:{pkg}" in output:
                return pkg
        return TARGET_PACKAGES[0]

    def run_adb(self, cmd_args: List[str], check: bool = True) -> subprocess.CompletedProcess:
        """Executes adb command on target device."""
        full_cmd = ["adb", "-s", self.device_serial] + cmd_args
        return subprocess.run(full_cmd, capture_output=True, text=True, check=check)

    def get_running_pids(self) -> List[int]:
        """Queries active process IDs for the target package."""
        res = self.run_adb(["shell", "pidof", self.package_name], check=False)
        output = (res.stdout or "").strip()
        if not output:
            return []
        pids = []
        for part in output.split():
            try:
                pids.append(int(part))
            except ValueError:
                pass
        return pids

    def is_battery_whitelisted(self) -> bool:
        """Returns True if the package is exempt from battery optimizations."""
        res = self.run_adb(["shell", "dumpsys", "deviceidle", "whitelist"], check=False)
        output = res.stdout or ""
        for line in output.splitlines():
            if self.package_name in line:
                return True
        return False

    def set_battery_whitelist(self, enable: bool) -> bool:
        """Adds (+) or removes (-) the package from the battery optimization whitelist."""
        op = "+" if enable else "-"
        self.run_adb(["shell", "dumpsys", "deviceidle", "whitelist", f"{op}{self.package_name}"], check=False)
        time.sleep(0.3)
        return self.is_battery_whitelisted()

    def toggle_battery_whitelist(self) -> bool:
        """Toggles battery whitelist status."""
        current = self.is_battery_whitelisted()
        return self.set_battery_whitelist(not current)

    def is_permission_granted(self, permission: str) -> bool:
        """Checks if a runtime permission is granted."""
        res = self.run_adb(["shell", f"dumpsys package {self.package_name} | grep '{permission}: granted'"], check=False)
        output = res.stdout or ""
        return "granted=true" in output

    def grant_permissions(self):
        """Grants location and notification permissions."""
        self.run_adb(["shell", "pm", "grant", self.package_name, LOCATION_PERMISSION], check=False)
        self.run_adb(["shell", "pm", "grant", self.package_name, COARSE_LOCATION_PERMISSION], check=False)
        self.run_adb(["shell", "pm", "grant", self.package_name, NOTIFICATION_PERMISSION], check=False)

    def revoke_location_permission(self):
        """Revokes location permission (triggering immediate OS process termination)."""
        self.run_adb(["shell", "pm", "revoke", self.package_name, LOCATION_PERMISSION], check=False)
        self.run_adb(["shell", "pm", "revoke", self.package_name, COARSE_LOCATION_PERMISSION], check=False)

    def get_latest_exit_info(self) -> Optional[ExitInfoRecord]:
        """Parses the most recent ApplicationExitInfo from ActivityManager."""
        res = self.run_adb(["shell", "dumpsys", "activity", "exit-info", self.package_name], check=False)
        output = res.stdout or ""
        if "ApplicationExitInfo #" not in output:
            return None

        match = re.search(
            r"ApplicationExitInfo #0:\s*\n"
            r"\s*timestamp=([\d\-\s\.\:]+)\s+pid=(\d+).*?\n"
            r"\s*process=\S+\s+reason=(\d+)\s*\(([^)]+)\)\s+subreason=(\d+)\s*\(([^)]*)\).*?\n"
            r"(?:\s*importance=.*?\n)?"
            r"\s*description=(.*?)\n",
            output,
            re.DOTALL
        )
        if match:
            timestamp_str = match.group(1).strip()
            pid = int(match.group(2))
            reason_code = int(match.group(3))
            reason_name = match.group(4).strip()
            subreason = f"{match.group(5)} ({match.group(6)})"
            description = match.group(7).strip()
            return ExitInfoRecord(
                index=0,
                timestamp_str=timestamp_str,
                pid=pid,
                reason_code=reason_code,
                reason_name=reason_name,
                subreason=subreason,
                description=description
            )
        return None

    def read_preferences(self) -> Dict[str, Any]:
        """Reads SharedPreferences via run-as."""
        prefs: Dict[str, Any] = {
            "pref_battery_kill_count": 0,
            "pref_last_evaluated_exit_timestamp": 0
        }
        res = self.run_adb(
            ["shell", f"run-as {self.package_name} cat /data/data/{self.package_name}/shared_prefs/{self.package_name}_preferences.xml 2>/dev/null"],
            check=False
        )
        output = res.stdout or ""
        if not output or "<map>" not in output:
            return prefs

        try:
            root = ET.fromstring(output)
            for child in root:
                name = child.get("name")
                if name == "pref_battery_kill_count":
                    val = child.get("value")
                    if val is not None:
                        prefs["pref_battery_kill_count"] = int(val)
                elif name == "pref_last_evaluated_exit_timestamp":
                    val = child.get("value")
                    if val is not None:
                        prefs["pref_last_evaluated_exit_timestamp"] = int(val)
        except Exception:
            pass

        return prefs

    def reset_kill_counter(self):
        """Resets battery kill count and last evaluated timestamp in SharedPreferences."""
        res = self.run_adb(
            ["shell", f"run-as {self.package_name} cat /data/data/{self.package_name}/shared_prefs/{self.package_name}_preferences.xml 2>/dev/null"],
            check=False
        )
        output = res.stdout or ""
        if not output or "<map>" not in output:
            return

        try:
            root = ET.fromstring(output)
            for elem in list(root):
                if elem.get("name") in ("pref_battery_kill_count", "pref_last_evaluated_exit_timestamp"):
                    root.remove(elem)

            new_item = ET.SubElement(root, "int")
            new_item.set("name", "pref_battery_kill_count")
            new_item.set("value", "0")

            xml_str = ET.tostring(root, encoding="utf-8").decode("utf-8")
            escaped_xml = xml_str.replace('"', '\\"').replace('$', '\\$')
            self.run_adb(
                ["shell", f"run-as {self.package_name} sh -c 'echo \"{escaped_xml}\" > /data/data/{self.package_name}/shared_prefs/{self.package_name}_preferences.xml'"],
                check=False
            )
        except Exception:
            pass

    def set_kill_count(self, count: int):
        """Sets pref_battery_kill_count directly for quick stage testing."""
        res = self.run_adb(
            ["shell", f"run-as {self.package_name} cat /data/data/{self.package_name}/shared_prefs/{self.package_name}_preferences.xml 2>/dev/null"],
            check=False
        )
        output = res.stdout or ""
        if not output or "<map>" not in output:
            return

        try:
            root = ET.fromstring(output)
            for elem in list(root):
                if elem.get("name") == "pref_battery_kill_count":
                    root.remove(elem)

            new_item = ET.SubElement(root, "int")
            new_item.set("name", "pref_battery_kill_count")
            new_item.set("value", str(count))

            xml_str = ET.tostring(root, encoding="utf-8").decode("utf-8")
            escaped_xml = xml_str.replace('"', '\\"').replace('$', '\\$')
            self.run_adb(
                ["shell", f"run-as {self.package_name} sh -c 'echo \"{escaped_xml}\" > /data/data/{self.package_name}/shared_prefs/{self.package_name}_preferences.xml'"],
                check=False
            )
        except Exception:
            pass

    def kill_process_sigkill(self) -> List[int]:
        """Kills all active PIDs with SIGKILL (9)."""
        pids = self.get_running_pids()
        if pids:
            for pid in pids:
                self.run_adb(["shell", "kill", "-9", str(pid)], check=False)
                self.run_adb(["shell", f"run-as {self.package_name} kill -9 {pid} 2>/dev/null"], check=False)
        return pids

    def kill_battery_simulation(self) -> Tuple[List[int], bool]:
        """
        Executes complete battery-kill simulation:
        1. Removes package from battery whitelist (!isIgnoringBatteryOptimizations).
        2. Kills running process via SIGKILL.
        """
        self.set_battery_whitelist(False)
        time.sleep(0.2)
        killed_pids = self.kill_process_sigkill()
        return killed_pids, False

    def kill_permission_simulation(self) -> Tuple[bool, str]:
        """
        Executes permission-change simulation:
        Revokes ACCESS_FINE_LOCATION, triggering native Android OS REASON_PERMISSION_CHANGE kill.
        """
        self.revoke_location_permission()
        time.sleep(0.5)
        pids = self.get_running_pids()
        return len(pids) == 0, LOCATION_PERMISSION

    def kill_lmk_simulation(self) -> List[int]:
        """Simulates low memory / background trim kill via am kill."""
        pids = self.get_running_pids()
        self.run_adb(["shell", "am", "send-trim-memory", self.package_name, "RUNNING_CRITICAL"], check=False)
        self.run_adb(["shell", "am", "kill", self.package_name], check=False)
        time.sleep(0.3)
        remaining = self.get_running_pids()
        if remaining:
            self.kill_process_sigkill()
        return pids

    def launch_app(self):
        """Launches the MainActivity on the device."""
        component = f"{self.package_name}/{MAIN_ACTIVITY}"
        self.run_adb(["shell", "am", "start", "-n", component], check=False)

    def get_full_status(self) -> AppProcessStatus:
        """Gathers full live diagnostic state."""
        pids = self.get_running_pids()
        is_whitelisted = self.is_battery_whitelisted()
        loc_granted = self.is_permission_granted(LOCATION_PERMISSION)
        notif_granted = self.is_permission_granted(NOTIFICATION_PERMISSION)
        prefs = self.read_preferences()
        latest_exit = self.get_latest_exit_info()

        return AppProcessStatus(
            package_name=self.package_name,
            device_serial=self.device_serial,
            pids=pids,
            is_whitelisted=is_whitelisted,
            location_granted=loc_granted,
            notification_granted=notif_granted,
            battery_kill_count=prefs["pref_battery_kill_count"],
            last_evaluated_timestamp=prefs["pref_last_evaluated_exit_timestamp"],
            latest_exit=latest_exit
        )


# --- Formatting & CLI Displays ---

def print_status_banner(status: AppProcessStatus):
    """Renders ANSI formatted diagnostic dashboard."""
    proc_str = (
        f"\033[1;32mRUNNING (PID: {', '.join(map(str, status.pids))})\033[0m"
        if status.pids else "\033[1;31mSTOPPED\033[0m"
    )
    whitelist_str = (
        "\033[1;32m[WHITELISTED]\033[0m (Exempt from battery optimizations)"
        if status.is_whitelisted else
        "\033[1;33m[OPTIMIZED]\033[0m (Subject to OS battery kill / sleep)"
    )
    loc_str = "\033[1;32mGRANTED\033[0m" if status.location_granted else "\033[1;31mREVOKED\033[0m"
    notif_str = "\033[1;32mGRANTED\033[0m" if status.notification_granted else "\033[1;31mDENIED\033[0m"

    if status.battery_kill_count >= 3:
        stage_desc = "\033[1;31mStage 3 (Max Escalation)\033[0m"
    elif status.battery_kill_count == 2:
        stage_desc = "\033[1;33mStage 2 (Elevated Urgency)\033[0m"
    elif status.battery_kill_count == 1:
        stage_desc = "\033[1;36mStage 1 (Initial Empathetic Alert)\033[0m"
    else:
        stage_desc = "\033[1;32mClean (Count = 0)\033[0m"

    print("\n" + "=" * 70)
    print(f"  \033[1;35mPROCESS KILL & EXIT REASON SIMULATOR\033[0m  (ATT-2469 / ATT-2079)")
    print("=" * 70)
    print(f"  Device:         \033[1m{status.device_serial}\033[0m")
    print(f"  Package:        \033[1m{status.package_name}\033[0m")
    print(f"  Process State:  {proc_str}")
    print(f"  Battery Status: {whitelist_str}")
    print(f"  Location Perm:  {loc_str}")
    print(f"  Notification:   {notif_str}")
    print(f"  Kill Counter:   count = {status.battery_kill_count} -> {stage_desc}")

    if status.latest_exit:
        e = status.latest_exit
        print(f"  Last OS Exit:   \033[1;36mreason={e.reason_code} ({e.reason_name})\033[0m at {e.timestamp_str}")
        print(f"                  subreason={e.subreason}, desc='{e.description}'")
    else:
        print("  Last OS Exit:   No ApplicationExitInfo records available.")
    print("-" * 70)


def run_interactive_menu(ctrl: KillSimulatorController):
    """Runs interactive terminal loop with non-blocking hotkeys."""
    reader = TerminalKeyReader()
    try:
        last_refresh = 0.0
        cached_status = None
        action_msg = ""

        while True:
            now = time.time()
            if cached_status is None or (now - last_refresh) > 3.0:
                cached_status = ctrl.get_full_status()
                last_refresh = now
                os.system("clear" if os.name == "posix" else "cls")
                print_status_banner(cached_status)
                if action_msg:
                    print(f"  \033[1;36m>> {action_msg}\033[0m\n  " + "-" * 66)
                    action_msg = ""

                wl_action = "REMOVE from Whitelist" if cached_status.is_whitelisted else "ADD to Whitelist"
                perm_action = "REVOKE Location" if cached_status.location_granted else "GRANT Location"

                print("  \033[1mHotkeys (Single keypress):\033[0m")
                print("   [1] Simulate BATTERY KILL (Un-whitelist + SIGKILL)")
                print("   [2] Simulate PERMISSION KILL (Revoke Location -> Native OS kill)")
                print("   [3] Simulate LOW MEMORY KILL (Trim memory + am kill)")
                print("   [4] Simulate DIRECT SIGKILL (kill -9)")
                print(f"   [5] Toggle Battery Whitelist ({wl_action})")
                print(f"   [6] Toggle Location Permission ({perm_action})")
                print("   [7] Set Escalation Count (Cycle 0 -> 1 -> 2 -> 3)")
                print("   [8] Reset All to Clean State (Whitelist ON, Perms ON, Count 0)")
                print("   [9] Launch App (MainActivity on device)")
                print("   [G] Open Web GUI Control Center")
                print("   [R] Refresh Status Immediately")
                print("   [Q] Quit")
                print("-" * 70)

            key = reader.poll_key() if HAS_TERMIOS else None
            if key is None and not HAS_TERMIOS:
                try:
                    choice = input("Select option [1-9, G, R, Q]: ").strip().upper()
                    key = choice[:1] if choice else None
                except (KeyboardInterrupt, EOFError):
                    break

            if key:
                k = key.upper()
                if k == "1":
                    pids, _ = ctrl.kill_battery_simulation()
                    action_msg = f"Battery Kill executed. Un-whitelisted & killed PIDs: {pids}."
                    cached_status = None
                elif k == "2":
                    ctrl.kill_permission_simulation()
                    action_msg = "Permission Kill executed. Location revoked."
                    cached_status = None
                elif k == "3":
                    pids = ctrl.kill_lmk_simulation()
                    action_msg = f"LMK Kill executed. Trimmed memory & killed PIDs: {pids}."
                    cached_status = None
                elif k == "4":
                    pids = ctrl.kill_process_sigkill()
                    action_msg = f"SIGKILL executed on PIDs: {pids}."
                    cached_status = None
                elif k == "5":
                    st = ctrl.toggle_battery_whitelist()
                    action_msg = f"Whitelist toggled: {'ENABLED (Exempt)' if st else 'DISABLED (Optimized)'}."
                    cached_status = None
                elif k == "6":
                    if cached_status and cached_status.location_granted:
                        ctrl.revoke_location_permission()
                        action_msg = "Location permission REVOKED."
                    else:
                        ctrl.grant_permissions()
                        action_msg = "Location & Notification permissions GRANTED."
                    cached_status = None
                elif k == "7":
                    curr = cached_status.battery_kill_count if cached_status else 0
                    nxt = (curr + 1) % 4
                    ctrl.set_kill_count(nxt)
                    action_msg = f"Escalation count set to {nxt} (Stage {min(nxt+1, 3)})."
                    cached_status = None
                elif k == "8":
                    ctrl.set_battery_whitelist(True)
                    ctrl.grant_permissions()
                    ctrl.reset_kill_counter()
                    action_msg = "Environment reset: Whitelist ON, Perms ON, Kill Count 0."
                    cached_status = None
                elif k == "9":
                    ctrl.launch_app()
                    action_msg = f"Launched {ctrl.package_name}/{MAIN_ACTIVITY}."
                    cached_status = None
                elif k == "G":
                    reader.restore()
                    print("\n\033[1;36m==> Launching Web GUI on http://127.0.0.1:8088...\033[0m")
                    start_web_gui(ctrl, port=8088, auto_open=True)
                    return
                elif k == "R":
                    cached_status = None
                elif k == "Q":
                    print("\nExiting simulator.")
                    break
            time.sleep(0.1)
    finally:
        reader.restore()


# --- Sleek Web GUI Control Center ---

WEB_GUI_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Process Kill & Exit Simulator (ATT-2469 / ATT-2079)</title>
  <style>
    :root {
      --bg: #0e1117;
      --card-bg: #161b22;
      --card-border: #30363d;
      --text: #f0f6fc;
      --text-muted: #8b949e;
      --primary: #58a6ff;
      --primary-hover: #1f6feb;
      --success: #238636;
      --success-hover: #2ea043;
      --danger: #da3633;
      --danger-hover: #b62324;
      --warning: #d29922;
      --radius: 12px;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
    body { background-color: var(--bg); color: var(--text); padding: 24px; }
    .container { max-width: 960px; margin: 0 auto; }
    header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; padding-bottom: 16px; border-bottom: 1px solid var(--card-border); }
    h1 { font-size: 20px; font-weight: 700; color: #fff; }
    .badge { display: inline-flex; align-items: center; padding: 4px 10px; border-radius: 20px; font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; }
    .badge-running { background: rgba(35, 134, 54, 0.2); color: #3fb950; border: 1px solid rgba(63, 185, 80, 0.4); }
    .badge-stopped { background: rgba(218, 54, 51, 0.2); color: #f85149; border: 1px solid rgba(248, 81, 73, 0.4); }
    .badge-exempt { background: rgba(88, 166, 255, 0.2); color: #58a6ff; border: 1px solid rgba(88, 166, 255, 0.4); }
    .badge-optimized { background: rgba(210, 153, 34, 0.2); color: #d29922; border: 1px solid rgba(210, 153, 34, 0.4); }

    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 16px; margin-bottom: 24px; }
    .card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: var(--radius); padding: 18px; box-shadow: 0 4px 12px rgba(0,0,0,0.25); }
    .card h2 { font-size: 14px; font-weight: 600; text-transform: uppercase; color: var(--text-muted); margin-bottom: 12px; letter-spacing: 0.5px; }
    .stat-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; font-size: 14px; }
    .stat-row:last-child { margin-bottom: 0; }
    .stat-label { color: var(--text-muted); }
    .stat-value { font-weight: 600; }

    .btn-group { display: flex; flex-direction: column; gap: 10px; }
    button { background: var(--card-border); color: #fff; border: 1px solid transparent; padding: 12px 16px; border-radius: 8px; font-size: 14px; font-weight: 600; cursor: pointer; transition: all 0.15s ease; display: flex; align-items: center; justify-content: center; gap: 8px; }
    button:hover { filter: brightness(1.15); }
    button:active { transform: scale(0.98); }
    .btn-danger { background: var(--danger); }
    .btn-danger:hover { background: var(--danger-hover); }
    .btn-success { background: var(--success); }
    .btn-success:hover { background: var(--success-hover); }
    .btn-primary { background: var(--primary); color: #0e1117; }
    .btn-primary:hover { background: var(--primary-hover); color: #fff; }
    .btn-warning { background: var(--warning); color: #0e1117; }
    .btn-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }

    .toast { position: fixed; bottom: 24px; right: 24px; background: #238636; color: #fff; padding: 12px 20px; border-radius: 8px; font-weight: 600; font-size: 14px; box-shadow: 0 6px 16px rgba(0,0,0,0.4); opacity: 0; transition: opacity 0.2s ease; pointer-events: none; }
    .toast.show { opacity: 1; }
  </style>
</head>
<body>
  <div class="container">
    <header>
      <div>
        <h1>⚡ Process Kill & Exit Simulator</h1>
        <div style="font-size: 12px; color: var(--text-muted); margin-top: 4px;">ATT-2469 / ATT-2079 Desk Testing Control Center</div>
      </div>
      <div>
        <span id="header-proc-badge" class="badge badge-stopped">POLLING...</span>
      </div>
    </header>

    <div class="grid">
      <!-- Live Device & App State -->
      <div class="card">
        <h2>Target Environment</h2>
        <div class="stat-row">
          <span class="stat-label">Device Serial</span>
          <span id="val-device" class="stat-value">--</span>
        </div>
        <div class="stat-row">
          <span class="stat-label">Package Name</span>
          <span id="val-package" class="stat-value">--</span>
        </div>
        <div class="stat-row">
          <span class="stat-label">Process State</span>
          <span id="val-proc" class="stat-value">--</span>
        </div>
        <div class="stat-row">
          <span class="stat-label">Battery Whitelist</span>
          <span id="val-whitelist" class="stat-value">--</span>
        </div>
        <div class="stat-row">
          <span class="stat-label">Location Perm</span>
          <span id="val-location" class="stat-value">--</span>
        </div>
        <div class="stat-row">
          <span class="stat-label">Notification</span>
          <span id="val-notif" class="stat-value">--</span>
        </div>
        <div class="stat-row">
          <span class="stat-label">Escalation Count</span>
          <span id="val-killcount" class="stat-value">--</span>
        </div>
      </div>

      <!-- Last OS Exit Info -->
      <div class="card">
        <h2>Latest ApplicationExitInfo</h2>
        <div id="exit-info-box">
          <div class="stat-row">
            <span class="stat-label">Reason Code</span>
            <span id="val-exit-reason" class="stat-value">--</span>
          </div>
          <div class="stat-row">
            <span class="stat-label">Timestamp</span>
            <span id="val-exit-time" class="stat-value">--</span>
          </div>
          <div class="stat-row">
            <span class="stat-label">Description</span>
            <span id="val-exit-desc" class="stat-value" style="font-size: 12px; text-align: right; max-width: 60%;">--</span>
          </div>
        </div>
        <div style="margin-top: 18px; border-top: 1px solid var(--card-border); padding-top: 14px;">
          <h2 style="margin-bottom: 8px;">Quick Launch & Reset</h2>
          <div style="display: flex; gap: 8px;">
            <button class="btn-primary" style="flex: 1;" onclick="triggerAction('launch')">🚀 Launch App</button>
            <button class="btn-success" style="flex: 1;" onclick="triggerAction('reset')">🔄 Clean Reset</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Actions Section -->
    <div class="grid">
      <!-- Simulated Kills -->
      <div class="card">
        <h2>Simulate Process Kill</h2>
        <div class="btn-group">
          <button class="btn-danger" onclick="triggerAction('kill_battery')">
            🔋 Battery Kill (Un-whitelist + SIGKILL)
          </button>
          <button class="btn-warning" onclick="triggerAction('kill_permission')">
            🔒 Permission Kill (Revoke Location -> OS Kill)
          </button>
          <button onclick="triggerAction('kill_lmk')">
            📉 Low Memory Kill (Trim Memory + am kill)
          </button>
          <button onclick="triggerAction('kill_sigkill')">
            🛑 Direct SIGKILL (kill -9)
          </button>
        </div>
      </div>

      <!-- Environment Toggles -->
      <div class="card">
        <h2>Environment & Stage Overrides</h2>
        <div class="btn-group" style="margin-bottom: 16px;">
          <button id="btn-toggle-whitelist" onclick="triggerAction('toggle_whitelist')">
            Toggle Battery Whitelist
          </button>
          <button id="btn-toggle-permission" onclick="triggerAction('toggle_permission')">
            Toggle Location Permission
          </button>
        </div>
        <h2 style="font-size: 12px; margin-bottom: 8px;">Set Escalation Stage Directly:</h2>
        <div class="btn-row">
          <button onclick="setKillCount(0)">Clean (0)</button>
          <button onclick="setKillCount(1)">Stage 1</button>
          <button onclick="setKillCount(2)">Stage 2</button>
          <button onclick="setKillCount(3)">Stage 3</button>
        </div>
      </div>
    </div>
  </div>

  <div id="toast" class="toast">Action Executed</div>

  <script>
    function showToast(msg) {
      const toast = document.getElementById('toast');
      toast.innerText = msg;
      toast.classList.add('show');
      setTimeout(() => toast.classList.remove('show'), 2500);
    }

    async function fetchStatus() {
      try {
        const res = await fetch('/api/status');
        const data = await res.json();
        renderStatus(data);
      } catch (e) {
        console.error('Failed to poll status', e);
      }
    }

    function renderStatus(s) {
      document.getElementById('val-device').innerText = s.device_serial;
      document.getElementById('val-package').innerText = s.package_name;

      const pids = s.pids || [];
      const procBadge = document.getElementById('header-proc-badge');
      if (pids.length > 0) {
        procBadge.className = 'badge badge-running';
        procBadge.innerText = 'RUNNING (PID ' + pids.join(', ') + ')';
        document.getElementById('val-proc').innerHTML = '<span style="color:#3fb950; font-weight:bold;">Running (' + pids.join(', ') + ')</span>';
      } else {
        procBadge.className = 'badge badge-stopped';
        procBadge.innerText = 'STOPPED';
        document.getElementById('val-proc').innerHTML = '<span style="color:#f85149; font-weight:bold;">Stopped</span>';
      }

      if (s.is_whitelisted) {
        document.getElementById('val-whitelist').innerHTML = '<span class="badge badge-exempt">Exempt (Whitelisted)</span>';
        document.getElementById('btn-toggle-whitelist').innerText = '🔋 Remove from Whitelist (Make Optimized)';
      } else {
        document.getElementById('val-whitelist').innerHTML = '<span class="badge badge-optimized">Optimized (Kill Eligible)</span>';
        document.getElementById('btn-toggle-whitelist').innerText = '🔋 Add to Whitelist (Grant Exemption)';
      }

      document.getElementById('val-location').innerHTML = s.location_granted
        ? '<span style="color:#3fb950;">Granted</span>'
        : '<span style="color:#f85149;">Revoked</span>';
      document.getElementById('btn-toggle-permission').innerText = s.location_granted
        ? '📍 Revoke Location Permission'
        : '📍 Grant Location Permission';

      document.getElementById('val-notif').innerHTML = s.notification_granted
        ? '<span style="color:#3fb950;">Granted</span>'
        : '<span style="color:#f85149;">Denied</span>';

      const cnt = s.battery_kill_count;
      const stageName = cnt >= 3 ? 'Stage 3 (Max)' : (cnt === 2 ? 'Stage 2 (Elevated)' : (cnt === 1 ? 'Stage 1 (Alert)' : 'Clean (0)'));
      document.getElementById('val-killcount').innerText = cnt + ' (' + stageName + ')';

      if (s.latest_exit) {
        document.getElementById('val-exit-reason').innerText = s.latest_exit.reason_code + ' (' + s.latest_exit.reason_name + ')';
        document.getElementById('val-exit-time').innerText = s.latest_exit.timestamp_str;
        document.getElementById('val-exit-desc').innerText = s.latest_exit.description || '--';
      } else {
        document.getElementById('val-exit-reason').innerText = 'None';
        document.getElementById('val-exit-time').innerText = '--';
        document.getElementById('val-exit-desc').innerText = '--';
      }
    }

    async function triggerAction(actionName) {
      try {
        const res = await fetch('/api/action', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ action: actionName })
        });
        const resp = await res.json();
        showToast(resp.message || 'Action completed');
        fetchStatus();
      } catch (e) {
        showToast('Error executing action: ' + e);
      }
    }

    async function setKillCount(count) {
      try {
        const res = await fetch('/api/action', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ action: 'set_count', count: count })
        });
        const resp = await res.json();
        showToast(resp.message || 'Updated kill count');
        fetchStatus();
      } catch (e) {
        showToast('Error updating count: ' + e);
      }
    }

    fetchStatus();
    setInterval(fetchStatus, 2000);
  </script>
</body>
</html>
"""


def start_web_gui(ctrl: KillSimulatorController, port: int = 8088, auto_open: bool = True):
    """Starts embedded HTTP server hosting the GUI dashboard."""
    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path in ("/", "/index.html"):
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.end_headers()
                self.wfile.write(WEB_GUI_HTML.encode("utf-8"))
            elif self.path == "/api/status":
                status = ctrl.get_full_status()
                data = asdict(status)
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps(data).encode("utf-8"))
            else:
                self.send_response(404)
                self.end_headers()

        def do_POST(self):
            if self.path == "/api/action":
                length = int(self.headers.get("Content-Length", 0))
                body = self.rfile.read(length)
                try:
                    payload = json.loads(body.decode("utf-8"))
                    action = payload.get("action")
                    msg = "Executed action"

                    if action == "kill_battery":
                        pids, _ = ctrl.kill_battery_simulation()
                        msg = f"Battery Kill: un-whitelisted & terminated PIDs {pids}"
                    elif action == "kill_permission":
                        ctrl.kill_permission_simulation()
                        msg = "Permission Kill: location permission revoked"
                    elif action == "kill_lmk":
                        pids = ctrl.kill_lmk_simulation()
                        msg = f"LMK Kill: trimmed memory & terminated PIDs {pids}"
                    elif action == "kill_sigkill":
                        pids = ctrl.kill_process_sigkill()
                        msg = f"SIGKILL terminated PIDs {pids}"
                    elif action == "toggle_whitelist":
                        st = ctrl.toggle_battery_whitelist()
                        msg = f"Whitelist toggled: {'ENABLED' if st else 'DISABLED'}"
                    elif action == "toggle_permission":
                        st = ctrl.get_full_status()
                        if st.location_granted:
                            ctrl.revoke_location_permission()
                            msg = "Location permission REVOKED"
                        else:
                            ctrl.grant_permissions()
                            msg = "Location permission GRANTED"
                    elif action == "set_count":
                        count = int(payload.get("count", 0))
                        ctrl.set_kill_count(count)
                        msg = f"Set pref_battery_kill_count to {count}"
                    elif action == "reset":
                        ctrl.set_battery_whitelist(True)
                        ctrl.grant_permissions()
                        ctrl.reset_kill_counter()
                        msg = "Reset all: Whitelist ON, Perms ON, Kill Count 0"
                    elif action == "launch":
                        ctrl.launch_app()
                        msg = f"Launched {ctrl.package_name}/{MAIN_ACTIVITY}"

                    self.send_response(200)
                    self.send_header("Content-Type", "application/json")
                    self.end_headers()
                    self.wfile.write(json.dumps({"success": True, "message": msg}).encode("utf-8"))
                except Exception as e:
                    self.send_response(500)
                    self.send_header("Content-Type", "application/json")
                    self.end_headers()
                    self.wfile.write(json.dumps({"success": False, "error": str(e)}).encode("utf-8"))
            else:
                self.send_response(404)
                self.end_headers()

        def log_message(self, format, *args):
            pass  # Suppress default request logging to keep console clean

    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("127.0.0.1", port), Handler) as httpd:
        url = f"http://127.0.0.1:{port}"
        print(f"\n\033[1;32m==> Web GUI Control Center active at: {url}\033[0m")
        print("    Press Ctrl+C in this terminal to stop the web server.\n")
        if auto_open:
            webbrowser.open(url)
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nShutting down Web GUI server.")


def main():
    parser = argparse.ArgumentParser(
        description="Process Kill & Exit Reason Simulator for Desk Testing (ATT-2469 / ATT-2079)"
    )
    parser.add_argument("--device", "-s", help="Specific ADB device serial (optional)")
    parser.add_argument("--package", "-p", help="Target package name (default: auto-detected com.atrainingtracker.debug)")
    parser.add_argument("--gui", action="store_true", help="Launch sleek Web GUI dashboard in browser")
    parser.add_argument("--port", type=int, default=8088, help="Port for Web GUI (default: 8088)")
    parser.add_argument("--status", action="store_true", help="Print live diagnostic status and exit")
    parser.add_argument("--kill", choices=["battery", "permission", "lmk", "sigkill"], help="Execute specific simulated process kill")
    parser.add_argument("--whitelist", choices=["on", "off", "toggle"], help="Set or toggle battery optimization whitelist")
    parser.add_argument("--permission", choices=["grant", "revoke"], help="Grant or revoke location/notification permissions")
    parser.add_argument("--set-count", type=int, help="Directly set pref_battery_kill_count (0, 1, 2, 3)")
    parser.add_argument("--launch", action="store_true", help="Launch MainActivity on device")
    parser.add_argument("--reset", action="store_true", help="Reset all to clean defaults (whitelist on, perms granted, count 0)")

    args = parser.parse_args()

    try:
        ctrl = KillSimulatorController(device_serial=args.device, package_name=args.package)
    except Exception as e:
        print(f"\033[1;31mError initializing ADB controller:\033[0m {e}", file=sys.stderr)
        sys.exit(1)

    if args.gui:
        start_web_gui(ctrl, port=args.port, auto_open=True)
        return

    # CLI one-shot execution
    has_action = False

    if args.status:
        has_action = True
        status = ctrl.get_full_status()
        print_status_banner(status)

    if args.whitelist:
        has_action = True
        if args.whitelist == "on":
            ctrl.set_battery_whitelist(True)
            print("Battery Whitelist: ENABLED (Exempt from optimizations)")
        elif args.whitelist == "off":
            ctrl.set_battery_whitelist(False)
            print("Battery Whitelist: DISABLED (Subject to battery kills)")
        elif args.whitelist == "toggle":
            st = ctrl.toggle_battery_whitelist()
            print(f"Battery Whitelist toggled: {'ENABLED' if st else 'DISABLED'}")

    if args.permission:
        has_action = True
        if args.permission == "grant":
            ctrl.grant_permissions()
            print("Permissions: GRANTED (Location + Notifications)")
        elif args.permission == "revoke":
            ctrl.revoke_location_permission()
            print("Location Permission: REVOKED")

    if args.set_count is not None:
        has_action = True
        ctrl.set_kill_count(args.set_count)
        print(f"pref_battery_kill_count set to {args.set_count}")

    if args.kill:
        has_action = True
        if args.kill == "battery":
            pids, _ = ctrl.kill_battery_simulation()
            print(f"Executed Battery Kill. Un-whitelisted and killed PIDs: {pids}")
        elif args.kill == "permission":
            ctrl.kill_permission_simulation()
            print("Executed Permission Kill. Location permission revoked.")
        elif args.kill == "lmk":
            pids = ctrl.kill_lmk_simulation()
            print(f"Executed LMK / Memory Trim Kill on PIDs: {pids}")
        elif args.kill == "sigkill":
            pids = ctrl.kill_process_sigkill()
            print(f"Executed SIGKILL on PIDs: {pids}")

    if args.reset:
        has_action = True
        ctrl.set_battery_whitelist(True)
        ctrl.grant_permissions()
        ctrl.reset_kill_counter()
        print("Reset Complete: Whitelist ENABLED, Permissions GRANTED, Kill count 0.")

    if args.launch:
        has_action = True
        ctrl.launch_app()
        print(f"Launched {ctrl.package_name}/{MAIN_ACTIVITY}")

    if not has_action:
        run_interactive_menu(ctrl)


if __name__ == "__main__":
    main()
