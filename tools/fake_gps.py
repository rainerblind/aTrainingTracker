#!/usr/bin/env python3
"""
tools/fake_gps.py - Standalone External ADB GPX Replay & Mock GPS Simulation Tool
================================================================================
Simulates realistic GPS movement along a GPX route on a connected physical Android
device (e.g. Google Pixel 10) or emulator via ADB `cmd location providers`.

Enables desk testing and sprint review verification of:
- Turn-by-Turn Navigation Cues (ATT-1450)
- Active Route Chevrons (ATT-1841)
- In-Ride Fork Route Selection & Decision Alerts (ATT-1955)
- Take Me Home return navigation (ATT-2462)

Zero changes to Android application source code (0% production risk).

Usage:
    python3 tools/fake_gps.py --sample munich
    python3 tools/fake_gps.py --sample fork
    python3 tools/fake_gps.py --gpx /path/to/my_route.gpx
    python3 tools/fake_gps.py --device 66020DLCR002FL --speed 30
"""

import sys
import os
import math
import time
import signal
import atexit
import argparse
import subprocess
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from typing import List, Tuple, Optional

# Non-blocking terminal input imports for Linux/macOS
try:
    import select
    import termios
    import tty
    HAS_TERMIOS = True
except ImportError:
    HAS_TERMIOS = False

EARTH_RADIUS_METERS = 6371000.0


@dataclass
class GpxPoint:
    lat: float
    lon: float
    ele: float = 0.0
    time: str = ""


# --- Geodesic Mathematics ---

def haversine_distance(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculates great-circle distance between two coordinates in meters."""
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)

    a = (math.sin(delta_phi / 2.0) ** 2 +
         math.cos(phi1) * math.cos(phi2) * (math.sin(delta_lambda / 2.0) ** 2))
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return EARTH_RADIUS_METERS * c


def calculate_bearing(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculates forward initial azimuth bearing in degrees [0, 360)."""
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_lambda = math.radians(lon2 - lon1)

    y = math.sin(delta_lambda) * math.cos(phi2)
    x = math.cos(phi1) * math.sin(phi2) - math.sin(phi1) * math.cos(phi2) * math.cos(delta_lambda)
    bearing_rad = math.atan2(y, x)
    bearing_deg = math.degrees(bearing_rad)
    return (bearing_deg + 360.0) % 360.0


def compute_destination_point(lat: float, lon: float, distance_m: float, bearing_deg: float) -> Tuple[float, float]:
    """Computes destination coordinate given start, distance, and bearing."""
    if distance_m <= 0.0:
        return lat, lon
    delta = distance_m / EARTH_RADIUS_METERS
    theta = math.radians(bearing_deg)
    phi1 = math.radians(lat)
    lambda1 = math.radians(lon)

    phi2 = math.asin(math.sin(phi1) * math.cos(delta) +
                     math.cos(phi1) * math.sin(delta) * math.cos(theta))
    lambda2 = lambda1 + math.atan2(math.sin(theta) * math.sin(delta) * math.cos(phi1),
                                  math.cos(delta) - math.sin(phi1) * math.sin(phi2))
    return math.degrees(phi2), (math.degrees(lambda2) + 540.0) % 360.0 - 180.0


def compute_off_route_point(lat: float, lon: float, forward_bearing_deg: float, offset_meters: float = 75.0) -> Tuple[float, float]:
    """Computes a point perpendicularly offset (+90 deg) from the travel heading."""
    offset_bearing = (forward_bearing_deg + 90.0) % 360.0
    return compute_destination_point(lat, lon, offset_meters, offset_bearing)


def heading_to_cardinal(bearing_deg: float) -> str:
    """Converts degrees into 8-point compass cardinal direction."""
    cardinals = ["N", "NE", "E", "SE", "S", "SW", "W", "NW", "N"]
    idx = int((bearing_deg + 22.5) / 45.0) % 8
    return cardinals[idx]


# --- GPX Parsing & Sample Routes ---

def parse_gpx_file(file_path: str) -> List[GpxPoint]:
    """Parses standard GPX file extracting <trkpt> trackpoints."""
    if not os.path.isfile(file_path):
        raise FileNotFoundError(f"GPX file not found: {file_path}")

    tree = ET.parse(file_path)
    root = tree.getroot()

    points: List[GpxPoint] = []
    # Handle optional XML namespaces
    ns = ""
    if root.tag.startswith("{"):
        ns = root.tag.split("}")[0] + "}"

    for trkpt in root.iter(f"{ns}trkpt"):
        lat_str = trkpt.get("lat")
        lon_str = trkpt.get("lon")
        if lat_str is None or lon_str is None:
            continue
        try:
            lat = float(lat_str)
            lon = float(lon_str)
        except ValueError:
            continue

        ele = 0.0
        ele_elem = trkpt.find(f"{ns}ele")
        if ele_elem is not None and ele_elem.text:
            try:
                ele = float(ele_elem.text)
            except ValueError:
                ele = 0.0

        time_str = ""
        time_elem = trkpt.find(f"{ns}time")
        if time_elem is not None and time_elem.text:
            time_str = time_elem.text

        points.append(GpxPoint(lat=lat, lon=lon, ele=ele, time=time_str))

    if not points:
        raise ValueError(f"No valid <trkpt> trackpoints found in {file_path}")

    return points


def get_sample_route(sample_name: str) -> Tuple[str, List[GpxPoint]]:
    """Provides built-in realistic test routes without requiring external files."""
    name_lower = sample_name.lower().strip()

    if name_lower == "fork":
        # Realistic fork departure corridor (Munich Isar river trail branching at 1.5 km)
        # Specially crafted to verify ATT-1955 fork alert and decision card!
        points = [
            GpxPoint(48.137154, 11.576124, 520.0),  # Marienplatz Start
            GpxPoint(48.136000, 11.578000, 520.0),
            GpxPoint(48.134500, 11.580500, 519.0),
            GpxPoint(48.132500, 11.583000, 518.0),  # Shared corridor
            GpxPoint(48.130500, 11.585500, 517.0),
            GpxPoint(48.128500, 11.588000, 516.0),
            GpxPoint(48.126500, 11.590500, 515.0),
            GpxPoint(48.125000, 11.592500, 515.0),  # 300m approaching fork
            GpxPoint(48.123500, 11.594000, 514.0),  # 150m approaching fork
            GpxPoint(48.122000, 11.595500, 514.0),  # JCT / FORK POINT!
            # Outbound branch A (East trail toward Flaucher)
            GpxPoint(48.120000, 11.598000, 513.0),
            GpxPoint(48.118000, 11.600500, 512.0),
            GpxPoint(48.116000, 11.603000, 511.0),
            GpxPoint(48.114000, 11.605500, 510.0),
            GpxPoint(48.112000, 11.608000, 509.0),
            GpxPoint(48.110000, 11.610500, 508.0),
        ]
        return "Isar Corridor Fork Test (ATT-1955)", points

    elif name_lower == "straight":
        # Straight 2.5 km sprint corridor along Leopoldstrasse
        points = [
            GpxPoint(48.150000, 11.582000, 515.0),
            GpxPoint(48.155000, 11.583000, 516.0),
            GpxPoint(48.160000, 11.584000, 517.0),
            GpxPoint(48.165000, 11.585000, 518.0),
            GpxPoint(48.170000, 11.586000, 519.0),
            GpxPoint(48.175000, 11.587000, 520.0),
        ]
        return "Leopoldstrasse Straight Sprint", points

    else:
        # Default: Munich Olympic Park Scenic Loop (~4.8 km) with sharp turns and elevation
        points = [
            GpxPoint(48.173200, 11.546300, 510.0),  # Olympiastadion Start
            GpxPoint(48.174500, 11.548000, 512.0),
            GpxPoint(48.175800, 11.551000, 515.0),
            GpxPoint(48.176500, 11.554500, 518.0),
            GpxPoint(48.175500, 11.558000, 523.0),
            GpxPoint(48.173800, 11.560500, 530.0),  # Olympiaberg Climb
            GpxPoint(48.172000, 11.562000, 545.0),  # Olympiaberg Summit
            GpxPoint(48.170500, 11.560000, 535.0),  # Descent
            GpxPoint(48.169000, 11.557000, 522.0),
            GpxPoint(48.168000, 11.553500, 516.0),
            GpxPoint(48.167800, 11.550000, 512.0),
            GpxPoint(48.168500, 11.546500, 510.0),
            GpxPoint(48.170200, 11.544000, 509.0),
            GpxPoint(48.172000, 11.544500, 510.0),
            GpxPoint(48.173200, 11.546300, 510.0),  # Loop Closure
        ]
        return "Munich Olympic Park Scenic Loop", points


class AdbController:
    """Manages Android system test location provider over ADB."""

    DEFAULT_PROVIDERS = ("gps", "network", "fused")

    def __init__(self, device_serial: Optional[str] = None, providers: Optional[List[str]] = None):
        self.device_serial = device_serial or self.detect_device()
        self.providers = list(providers or self.DEFAULT_PROVIDERS)
        self.is_provider_registered = False

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
                raise RuntimeError("No connected Android device found via ADB. Please connect your Pixel 10 via USB.")
            return devices[0]
        except Exception as e:
            raise RuntimeError(f"Failed to query ADB devices: {e}")

    def run_adb(self, cmd_args: List[str], check: bool = True) -> subprocess.CompletedProcess:
        """Executes adb command on target device."""
        full_cmd = ["adb", "-s", self.device_serial] + cmd_args
        return subprocess.run(full_cmd, capture_output=True, text=True, check=check)

    def setup_test_provider(self):
        """Grants mock location permission and registers test providers for gps, network, and fused."""
        # Grant mock_location permission to Android shell (UID 2000)
        self.run_adb(["shell", "appops", "set", "2000", "android:mock_location", "allow"], check=False)
        # Register and enable all target providers in a single shell invocation
        setup_cmds = []
        for p in self.providers:
            setup_cmds.append(
                f"cmd location providers add-test-provider {p} --supportsAltitude --supportsSpeed --supportsBearing 2>/dev/null ; "
                f"cmd location providers set-test-provider-enabled {p} true"
            )
        self.run_adb(["shell", " ; ".join(setup_cmds)], check=False)
        self.is_provider_registered = True

    def remove_test_provider(self):
        """Removes all test providers and restores real hardware GNSS/Network reception."""
        if self.is_provider_registered:
            try:
                teardown_cmds = [f"cmd location providers remove-test-provider {p} 2>/dev/null" for p in self.providers]
                self.run_adb(["shell", " ; ".join(teardown_cmds)], check=False)
                self.is_provider_registered = False
            except Exception:
                pass

    def inject_location(self, lat: float, lon: float, accuracy: float = 2.5):
        """Injects simulated location fix synchronously into all system location providers."""
        now_ms = int(time.time() * 1000)
        loc_str = f"{lat:.6f},{lon:.6f}"
        acc_str = f"{accuracy:.1f}"
        inject_cmds = [
            f"cmd location providers set-test-provider-location {p} --location {loc_str} --accuracy {acc_str} --time {now_ms}"
            for p in self.providers
        ]
        self.run_adb(["shell", " && ".join(inject_cmds)], check=False)


# --- Simulation State Engine ---

class GpxSimulationEngine:
    """Calculates linear geodesic interpolation, bearing, speed scaling, and deviation."""

    def __init__(self, route_name: str, points: List[GpxPoint], base_speed_kmh: float = 25.0):
        if len(points) < 2:
            raise ValueError("Route must contain at least 2 trackpoints.")
        self.route_name = route_name
        self.points = points
        self.base_speed_kmh = max(1.0, base_speed_kmh)
        self.speed_multiplier = 1.0

        # Precompute cumulative distance along vertices
        self.cumulative_distances: List[float] = [0.0]
        total = 0.0
        for i in range(1, len(points)):
            d = haversine_distance(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon)
            total += d
            self.cumulative_distances.append(total)
        self.total_distance = total

        self.current_distance = 0.0
        self.status = "PLAYING"
        self.is_deviating = False
        self.deviation_offset_meters = 75.0

        self.current_lat = points[0].lat
        self.current_lon = points[0].lon
        self.current_bearing = 0.0
        self.current_ele = points[0].ele
        self.elapsed_time_sec = 0.0

    @property
    def effective_speed_kmh(self) -> float:
        return self.base_speed_kmh * self.speed_multiplier

    @property
    def effective_speed_mps(self) -> float:
        return self.effective_speed_kmh / 3.6

    @property
    def progress_ratio(self) -> float:
        if self.total_distance <= 0.0:
            return 0.0
        return min(1.0, max(0.0, self.current_distance / self.total_distance))

    def step(self, delta_sec: float) -> Tuple[float, float, float, float]:
        """Advances simulation by delta_sec, returning (lat, lon, bearing, ele)."""
        if self.status != "PLAYING" or self.total_distance <= 0.0:
            return self.current_lat, self.current_lon, self.current_bearing, self.current_ele

        delta_dist = self.effective_speed_mps * delta_sec
        self.current_distance = min(self.total_distance, self.current_distance + delta_dist)
        self.elapsed_time_sec += delta_sec

        lat, lon, bearing, ele = self.interpolate_at_distance(self.current_distance)

        if self.is_deviating:
            lat, lon = compute_off_route_point(lat, lon, bearing, self.deviation_offset_meters)

        self.current_lat = lat
        self.current_lon = lon
        self.current_bearing = bearing
        self.current_ele = ele

        if self.current_distance >= self.total_distance:
            self.status = "STOPPED"

        return lat, lon, bearing, ele

    def interpolate_at_distance(self, dist_m: float) -> Tuple[float, float, float, float]:
        """Interpolates coordinate, bearing, and altitude at distance offset."""
        if dist_m <= 0.0:
            p0, p1 = self.points[0], self.points[1]
            bearing = calculate_bearing(p0.lat, p0.lon, p1.lat, p1.lon)
            return p0.lat, p0.lon, bearing, p0.ele

        if dist_m >= self.total_distance:
            p_penult, p_last = self.points[-2], self.points[-1]
            bearing = calculate_bearing(p_penult.lat, p_penult.lon, p_last.lat, p_last.lon)
            return p_last.lat, p_last.lon, bearing, p_last.ele

        # Binary search for enclosing segment
        low = 0
        high = len(self.cumulative_distances) - 1
        while low <= high:
            mid = (low + high) // 2
            if self.cumulative_distances[mid] <= dist_m:
                low = mid + 1
            else:
                high = mid - 1

        idx = max(0, high)
        d0 = self.cumulative_distances[idx]
        d1 = self.cumulative_distances[idx + 1]
        p0 = self.points[idx]
        p1 = self.points[idx + 1]

        seg_len = d1 - d0
        t = (dist_m - d0) / seg_len if seg_len > 0.0 else 0.0

        lat = p0.lat + t * (p1.lat - p0.lat)
        lon = p0.lon + t * (p1.lon - p0.lon)
        ele = p0.ele + t * (p1.ele - p0.ele)
        bearing = calculate_bearing(p0.lat, p0.lon, p1.lat, p1.lon)
        return lat, lon, bearing, ele

    def seek_progress(self, ratio: float):
        """Snaps simulation directly to given progress ratio [0.0, 1.0]."""
        ratio = min(1.0, max(0.0, ratio))
        self.current_distance = ratio * self.total_distance
        lat, lon, bearing, ele = self.interpolate_at_distance(self.current_distance)
        if self.is_deviating:
            lat, lon = compute_off_route_point(lat, lon, bearing, self.deviation_offset_meters)
        self.current_lat = lat
        self.current_lon = lon
        self.current_bearing = bearing
        self.current_ele = ele


# --- Terminal UI & Interactive Loop ---

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


def format_progress_bar(ratio: float, width: int = 24) -> str:
    filled = int(round(ratio * width))
    bar = "█" * filled + "░" * (width - filled)
    pct = ratio * 100.0
    return f"[{bar}] {pct:5.1f}%"


def format_seconds(seconds: float) -> str:
    m = int(seconds) // 60
    s = int(seconds) % 60
    return f"{m:02d}:{s:02d}"


def run_interactive_simulation(
    engine: GpxSimulationEngine,
    adb: AdbController,
    tick_rate_sec: float = 1.0
):
    """Main interactive loop streaming mock locations and processing hotkeys."""
    key_reader = TerminalKeyReader()

    def cleanup():
        key_reader.restore()
        print("\n\033[1;33m[!] Tearing down ADB test provider and restoring real GNSS...\033[0m")
        adb.remove_test_provider()
        print("\033[1;32m[✓] Hardware GPS restored cleanly.\033[0m")

    atexit.register(cleanup)
    signal.signal(signal.SIGINT, lambda sig, frame: sys.exit(0))
    signal.signal(signal.SIGTERM, lambda sig, frame: sys.exit(0))

    print(f"\033[1;36m==> Initializing ADB test provider on device: {adb.device_serial}...\033[0m")
    adb.setup_test_provider()
    print("\033[1;32m==> Test provider active. Starting GPX replay...\033[0m\n")

    # Clear terminal screen
    print("\033[2J\033[H", end="")

    try:
        while True:
            # 1. Process keypresses
            ch = key_reader.poll_key()
            if ch:
                if ch in ("q", "Q"):
                    break
                elif ch == " ":
                    if engine.status == "PLAYING":
                        engine.status = "PAUSED"
                    elif engine.status in ("PAUSED", "STOPPED"):
                        if engine.status == "STOPPED":
                            engine.current_distance = 0.0
                        engine.status = "PLAYING"
                elif ch in ("s", "S"):
                    engine.status = "STOPPED"
                    engine.current_distance = 0.0
                elif ch == "1":
                    engine.speed_multiplier = 1.0
                elif ch == "2":
                    engine.speed_multiplier = 2.0
                elif ch == "5":
                    engine.speed_multiplier = 5.0
                elif ch == "0":
                    engine.speed_multiplier = 10.0
                elif ch in ("+", "="):
                    engine.base_speed_kmh += 5.0
                elif ch in ("-", "_"):
                    engine.base_speed_kmh = max(5.0, engine.base_speed_kmh - 5.0)
                elif ch in ("d", "D"):
                    engine.is_deviating = not engine.is_deviating
                elif ch in ("r", "R"):
                    engine.is_deviating = False
                elif ch in (">", "."):
                    engine.seek_progress(engine.progress_ratio + 0.1)
                elif ch in ("<", ","):
                    engine.seek_progress(engine.progress_ratio - 0.1)

            # 2. Advance physics step
            lat, lon, bearing, ele = engine.step(tick_rate_sec)

            # 3. Inject into Android location service
            adb.inject_location(lat, lon, accuracy=2.5)

            # 4. Render Dashboard
            cardinal = heading_to_cardinal(bearing)
            progress_bar = format_progress_bar(engine.progress_ratio, width=22)
            elapsed_str = format_seconds(engine.elapsed_time_sec)
            dist_km = engine.current_distance / 1000.0
            total_km = engine.total_distance / 1000.0

            status_color = "\033[1;32m" if engine.status == "PLAYING" else "\033[1;33m"
            dev_indicator = "\033[1;31m[⚠️ OFF-ROUTE: +75m]\033[0m" if engine.is_deviating else "\033[1;32m[ON ROUTE]\033[0m"

            dashboard = [
                "\033[H",  # Reposition cursor at top
                "\033[1;37;44m                ATT-2467: GPX REPLAY MOCK RIDE SIMULATOR                \033[0m",
                f" \033[1mRoute:\033[0m   {engine.route_name}",
                f" \033[1mDevice:\033[0m  {adb.device_serial}  |  \033[1mStatus:\033[0m {status_color}[{engine.status}]\033[0m  {dev_indicator}",
                "────────────────────────────────────────────────────────────────────────",
                f" \033[1mCoords:\033[0m  {lat:10.6f}, {lon:10.6f}   |  \033[1mBearing:\033[0m  {bearing:5.1f}° ({cardinal:2s})",
                f" \033[1mSpeed:\033[0m   {engine.effective_speed_kmh:5.1f} km/h ({engine.speed_multiplier:.1f}x)     |  \033[1mAltitude:\033[0m {ele:5.1f} m",
                f" \033[1mDistance:\033[0m{dist_km:5.2f} / {total_km:5.2f} km         |  \033[1mElapsed:\033[0m  {elapsed_str}",
                f" \033[1mProgress:\033[0m{progress_bar}",
                "────────────────────────────────────────────────────────────────────────",
                " \033[1mHotkeys:\033[0m [Space] Play/Pause  | [1/2/5/0] 1x/2x/5x/10x  | [+/-] Speed",
                "          [d] Deviate Off-Route | [r] Rejoin Route      | [</>] Scrub",
                "          [s] Stop / Reset      | [q] Quit & Clean Exit",
                "────────────────────────────────────────────────────────────────────────",
            ]
            print("\n".join(dashboard), flush=True)

            time.sleep(tick_rate_sec)

    finally:
        cleanup()


# --- CLI Entry Point ---

def main():
    parser = argparse.ArgumentParser(
        description="ATT-2467 Standalone External ADB GPX Replay & Mock GPS Simulation Tool"
    )
    parser.add_argument(
        "--gpx",
        type=str,
        default=None,
        help="Path to an external .gpx file to replay"
    )
    parser.add_argument(
        "--sample",
        type=str,
        default="munich",
        choices=["munich", "fork", "straight"],
        help="Built-in sample route: 'munich' (Olympic Park), 'fork' (corridor for ATT-1955), 'straight' (2km sprint)"
    )
    parser.add_argument(
        "--speed",
        type=float,
        default=25.0,
        help="Base replay speed in km/h (default: 25.0 km/h)"
    )
    parser.add_argument(
        "--device",
        type=str,
        default=None,
        help="Target ADB device serial (defaults to auto-detected connected device)"
    )
    parser.add_argument(
        "--tick",
        type=float,
        default=1.0,
        help="Simulation update tick rate in seconds (default: 1.0s)"
    )

    args = parser.parse_args()

    # Load points from GPX or sample route
    if args.gpx:
        route_name = os.path.basename(args.gpx)
        points = parse_gpx_file(args.gpx)
    else:
        route_name, points = get_sample_route(args.sample)

    engine = GpxSimulationEngine(route_name, points, base_speed_kmh=args.speed)
    adb = AdbController(device_serial=args.device)

    run_interactive_simulation(engine, adb, tick_rate_sec=args.tick)


if __name__ == "__main__":
    main()
