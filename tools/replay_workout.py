#!/usr/bin/env python3
"""
tools/replay_workout.py - Synchronized TCX Workout Replay Tool
=============================================================
Replays recorded Garmin / Strava TCX activities with simultaneous
BLE Cycling Power (0x1818), BLE Heart Rate (0x180D), and ADB mock GPS
simulation on connected Android test devices.

Features:
- PyQt6 Desktop GUI with modern obsidian dark theme, transport controls,
  interactive timeline scrubber, high-glanceability digital gauges, and
  connection status badges.
- Headless CLI mode (--headless / --cli) for display-less CI runners.
- BLE GATT peripheral simulation over BlueZ D-Bus.
- ADB mock location provider injection across gps, network, and fused.

Usage:
    python3 tools/replay_workout.py [workout.tcx]
    python3 tools/replay_workout.py --headless [workout.tcx]
    python3 tools/replay_workout.py --no-ble --no-gps
"""

import sys
import os
import math
import time
import signal
import atexit
import argparse
import subprocess
import threading
import queue
from dataclasses import dataclass
from typing import List, Tuple, Optional, Callable
import xml.etree.ElementTree as ET

# --- Non-blocking terminal input for CLI ---
try:
    import select
    import termios
    import tty
    HAS_TERMIOS = True
except ImportError:
    HAS_TERMIOS = False

# --- BlueZ DBus GATT optional imports ---
try:
    import dbus
    import dbus.exceptions
    import dbus.mainloop.glib
    import dbus.service
    from gi.repository import GLib
    HAS_DBUS = True
except ImportError:
    HAS_DBUS = False

# --- PyQt6 optional imports ---
try:
    from PyQt6.QtWidgets import (
        QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
        QGridLayout, QPushButton, QLabel, QSlider, QComboBox, QFileDialog,
        QFrame
    )
    from PyQt6.QtCore import Qt, QTimer, pyqtSignal, QObject
    from PyQt6.QtGui import QFont, QPalette, QColor
    HAS_PYQT6 = True
except ImportError:
    HAS_PYQT6 = False


EARTH_RADIUS_METERS = 6371000.0


# ==============================================================================
# 1. Geodesic Math & Helpers
# ==============================================================================

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


def format_duration(seconds: float) -> str:
    """Formats seconds into HH:MM:SS."""
    sec = int(max(0.0, seconds))
    h = sec // 3600
    m = (sec % 3600) // 60
    s = sec % 60
    return f"{h:02d}:{m:02d}:{s:02d}"


# ==============================================================================
# 2. TCX Data Structures & Parsing Engine
# ==============================================================================

@dataclass
class TcxPoint:
    time_iso: str
    time_sec: float
    lat: float
    lon: float
    ele: float = 0.0
    dist: float = 0.0
    hr: int = 0
    cadence: int = 0
    watts: int = 0
    speed_mps: float = 0.0
    bearing: float = 0.0


class TcxParser:
    """Parses Garmin/Strava TCX activities extracting timestamped trackpoints."""

    @staticmethod
    def parse_file(file_path: str) -> List[TcxPoint]:
        if not os.path.isfile(file_path):
            raise FileNotFoundError(f"TCX file not found: {file_path}")

        tree = ET.parse(file_path)
        root = tree.getroot()

        # Handle wildcard namespaces
        points: List[TcxPoint] = []
        start_epoch: Optional[float] = None

        # Trackpoints may appear under Activity/Lap/Track/Trackpoint or Course/Track/Trackpoint
        for trkpt in root.iter():
            if not trkpt.tag.endswith("Trackpoint"):
                continue

            time_elem = None
            pos_elem = None
            ele_elem = None
            dist_elem = None
            hr_elem = None
            cad_elem = None
            ext_elem = None

            for child in trkpt:
                tag = child.tag.split("}")[-1] if "}" in child.tag else child.tag
                if tag == "Time":
                    time_elem = child
                elif tag == "Position":
                    pos_elem = child
                elif tag == "AltitudeMeters":
                    ele_elem = child
                elif tag == "DistanceMeters":
                    dist_elem = child
                elif tag == "HeartRateBpm":
                    hr_elem = child
                elif tag == "Cadence":
                    cad_elem = child
                elif tag == "Extensions":
                    ext_elem = child

            # Coordinates are mandatory for replay
            if pos_elem is None:
                continue

            lat_elem = None
            lon_elem = None
            for pchild in pos_elem:
                ptag = pchild.tag.split("}")[-1] if "}" in pchild.tag else pchild.tag
                if ptag == "LatitudeDegrees":
                    lat_elem = pchild
                elif ptag == "LongitudeDegrees":
                    lon_elem = pchild

            if lat_elem is None or lon_elem is None or not lat_elem.text or not lon_elem.text:
                continue

            try:
                lat = float(lat_elem.text)
                lon = float(lon_elem.text)
            except ValueError:
                continue

            # Time parsing
            time_iso = time_elem.text if time_elem is not None and time_elem.text else ""
            time_sec = 0.0
            if time_iso:
                # Parse ISO-8601 timestamp e.g. 2026-10-10T10:00:00Z
                try:
                    iso_clean = time_iso.replace("Z", "+00:00")
                    # Python 3.11+ supports fromisoformat
                    import datetime
                    dt = datetime.datetime.fromisoformat(iso_clean)
                    epoch = dt.timestamp()
                    if start_epoch is None:
                        start_epoch = epoch
                    time_sec = max(0.0, epoch - start_epoch)
                except Exception:
                    time_sec = float(len(points))
            else:
                time_sec = float(len(points))

            # Altitude
            ele = 0.0
            if ele_elem is not None and ele_elem.text:
                try:
                    ele = float(ele_elem.text)
                except ValueError:
                    ele = 0.0

            # Distance
            dist = 0.0
            if dist_elem is not None and dist_elem.text:
                try:
                    dist = float(dist_elem.text)
                except ValueError:
                    dist = 0.0

            # Heart Rate
            hr = 0
            if hr_elem is not None:
                for val in hr_elem:
                    if val.tag.endswith("Value") and val.text:
                        try:
                            hr = int(round(float(val.text)))
                        except ValueError:
                            hr = 0

            # Cadence
            cadence = 0
            if cad_elem is not None and cad_elem.text:
                try:
                    cadence = int(round(float(cad_elem.text)))
                except ValueError:
                    cadence = 0

            # Extensions (Watts, Speed)
            watts = 0
            speed_mps = 0.0
            if ext_elem is not None:
                for ext in ext_elem.iter():
                    etag = ext.tag.split("}")[-1] if "}" in ext.tag else ext.tag
                    if etag == "Watts" and ext.text:
                        try:
                            watts = int(round(float(ext.text)))
                        except ValueError:
                            watts = 0
                    elif etag == "Speed" and ext.text:
                        try:
                            speed_mps = float(ext.text)
                        except ValueError:
                            speed_mps = 0.0

            points.append(TcxPoint(
                time_iso=time_iso,
                time_sec=time_sec,
                lat=lat,
                lon=lon,
                ele=ele,
                dist=dist,
                hr=hr,
                cadence=cadence,
                watts=watts,
                speed_mps=speed_mps,
                bearing=0.0
            ))

        if not points:
            raise ValueError(f"No valid trackpoints found in TCX file: {file_path}")

        # Compute cumulative distance and bearings if missing
        cum_dist = 0.0
        for i in range(len(points)):
            if i > 0:
                step_d = haversine_distance(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon)
                cum_dist += step_d
                # Calculate forward bearing
                if step_d > 0.05:
                    points[i].bearing = calculate_bearing(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon)
                else:
                    points[i].bearing = points[i - 1].bearing
            else:
                if len(points) > 1:
                    points[0].bearing = calculate_bearing(points[0].lat, points[0].lon, points[1].lat, points[1].lon)

            if points[i].dist == 0.0 and i > 0:
                points[i].dist = cum_dist

        return points


# ==============================================================================
# 3. BLE GATT Payloads & Broadcaster
# ==============================================================================

def build_power_payload(watts: int, cadence: int, cumulative_revs: int = 0, event_time_1024: int = 0) -> List[int]:
    """Generates 8-byte BLE Cycling Power Measurement payload (UUID 0x2A63)."""
    flags = 0x0020  # Bit 5: Crank Revolution Data Present
    flags_bytes = list(flags.to_bytes(2, byteorder='little'))
    power_clamped = max(-32768, min(32767, int(watts)))
    power_bytes = list(power_clamped.to_bytes(2, byteorder='little', signed=True))
    crank_rev_bytes = list((cumulative_revs % 65536).to_bytes(2, byteorder='little'))
    crank_time_bytes = list((event_time_1024 % 65536).to_bytes(2, byteorder='little'))
    return flags_bytes + power_bytes + crank_rev_bytes + crank_time_bytes

def build_hr_payload(bpm: int) -> List[int]:
    """Generates 2-byte BLE Heart Rate Measurement payload (UUID 0x2A37)."""
    flags = 0x00  # UINT8 format
    bpm_clamped = max(0, min(255, int(bpm)))
    return [flags, bpm_clamped]


# ==============================================================================
# 3. Rule 30 Host Pre-Flight Self-Diagnostics
# ==============================================================================

BLUEZ_SERVICE_NAME = 'org.bluez'
DBUS_OM_IFACE = 'org.freedesktop.DBus.ObjectManager'
DBUS_PROP_IFACE = 'org.freedesktop.DBus.Properties'
GATT_MANAGER_IFACE = 'org.bluez.GattManager1'
GATT_SERVICE_IFACE = 'org.bluez.GattService1'
GATT_CHRC_IFACE = 'org.bluez.GattCharacteristic1'
GATT_DESC_IFACE = 'org.bluez.GattDescriptor1'
LE_ADVERTISING_MANAGER_IFACE = 'org.bluez.LEAdvertisingManager1'
LE_ADVERTISEMENT_IFACE = 'org.bluez.LEAdvertisement1'

CYCLING_POWER_SERVICE_UUID = '00001818-0000-1000-8000-00805f9b34fb'
CYCLING_POWER_MEASUREMENT_UUID = '00002a63-0000-1000-8000-00805f9b34fb'
CYCLING_POWER_FEATURE_UUID = '00002a65-0000-1000-8000-00805f9b34fb'
SENSOR_LOCATION_UUID = '00002a5d-0000-1000-8000-00805f9b34fb'

HEART_RATE_SERVICE_UUID = '0000180d-0000-1000-8000-00805f9b34fb'
HEART_RATE_MEASUREMENT_UUID = '00002a37-0000-1000-8000-00805f9b34fb'
BODY_SENSOR_LOCATION_UUID = '00002a38-0000-1000-8000-00805f9b34fb'

DEVICE_INFO_SERVICE_UUID = '0000180a-0000-1000-8000-00805f9b34fb'
MANUFACTURER_NAME_UUID = '00002a29-0000-1000-8000-00805f9b34fb'


@dataclass
class PreFlightReport:
    bt_ok: bool
    bt_message: str
    bt_advice: List[str]
    adb_ok: bool
    adb_message: str
    adb_advice: List[str]
    device_serial: Optional[str] = None


class HostPreFlightDiagnostics:
    """Pre-flight self-diagnostics for host Bluetooth and ADB test devices (Rule 30)."""

    @staticmethod
    def check_bluetooth(bus=None) -> Tuple[bool, str, List[str]]:
        if not HAS_DBUS:
            return False, "Python dbus/GLib libraries missing", ["Install dependencies: sudo apt install python3-dbus python3-gi"]
        try:
            if bus is None:
                dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
                bus = dbus.SystemBus()
            remote_om = dbus.Interface(bus.get_object(BLUEZ_SERVICE_NAME, '/'), DBUS_OM_IFACE)
            objects = remote_om.GetManagedObjects()

            found_adapter = None
            for o, props in objects.items():
                if GATT_MANAGER_IFACE in props and LE_ADVERTISING_MANAGER_IFACE in props:
                    found_adapter = o
                    adapter_props = dbus.Interface(bus.get_object(BLUEZ_SERVICE_NAME, o), DBUS_PROP_IFACE)
                    powered = bool(adapter_props.Get('org.bluez.Adapter1', 'Powered'))
                    if not powered:
                        return False, f"Bluetooth adapter {o} is powered OFF", [
                            f"Power on adapter: sudo bluetoothctl power on",
                            f"Or check rfkill: sudo rfkill unblock bluetooth"
                        ]
                    return True, f"BlueZ ready on {o} (GATT + LE Advertising)", []

            return False, "No Bluetooth adapter with LE Advertising support found", [
                "Verify Bluetooth service: sudo systemctl status bluetooth",
                "Ensure Bluetooth controller supports LE Peripheral Mode"
            ]
        except Exception as e:
            return False, f"BlueZ D-Bus query failed: {e}", [
                "Start Bluetooth daemon: sudo systemctl start bluetooth",
                "Check user permissions or D-Bus policy"
            ]

    @staticmethod
    def check_adb(device_serial: Optional[str] = None) -> Tuple[bool, str, List[str], Optional[str]]:
        try:
            res = subprocess.run(["adb", "devices"], capture_output=True, text=True, check=True)
            lines = res.stdout.strip().splitlines()
            online_devices = []
            unauthorized = []
            for line in lines[1:]:
                parts = line.split()
                if len(parts) >= 2:
                    if parts[1] == "device":
                        online_devices.append(parts[0])
                    elif parts[1] == "unauthorized":
                        unauthorized.append(parts[0])

            if unauthorized and not online_devices:
                return False, f"Device {unauthorized[0]} is UNAUTHORIZED", [
                    "Unlock your phone screen and accept 'Allow USB debugging' prompt",
                    "Check USB debugging authorization in Developer Options"
                ], None

            if not online_devices:
                return False, "No connected Android device found via ADB", [
                    "Connect your Android test device (e.g. Pixel 10) via USB",
                    "Enable USB Debugging in Developer Options",
                    "Verify with: adb devices"
                ], None

            target = device_serial if (device_serial and device_serial in online_devices) else online_devices[0]

            # Verify mock location appops permission
            appops_res = subprocess.run(
                ["adb", "-s", target, "shell", "cmd", "appops", "get", "2000", "android:mock_location"],
                capture_output=True, text=True
            )
            out = appops_res.stdout.strip()
            if "allow" not in out:
                return False, f"Device {target} mock location NOT allowed for shell", [
                    f"Grant mock location permission: adb -s {target} shell appops set 2000 android:mock_location allow",
                    "Or set mock location app in Developer Options"
                ], target

            return True, f"ADB device {target} ready (mock location allowed)", [], target
        except FileNotFoundError:
            return False, "ADB binary not found in PATH", ["Install Android platform-tools: sudo apt install adb"], None
        except Exception as e:
            return False, f"ADB query failed: {e}", ["Ensure adb server is running: adb start-server"], None

    @classmethod
    def run_all(cls, bus=None, target_device: Optional[str] = None) -> PreFlightReport:
        bt_ok, bt_msg, bt_adv = cls.check_bluetooth(bus)
        adb_ok, adb_msg, adb_adv, dev = cls.check_adb(target_device)
        return PreFlightReport(
            bt_ok=bt_ok, bt_message=bt_msg, bt_advice=bt_adv,
            adb_ok=adb_ok, adb_message=adb_msg, adb_advice=adb_adv,
            device_serial=dev
        )


# ==============================================================================
# 4. BlueZ D-Bus GATT Application, Services & Advertisements
# ==============================================================================

if HAS_DBUS:
    class DBusInvalidArgsException(dbus.exceptions.DBusException):
        _dbus_error_name = 'org.bluez.Error.InvalidArguments'

    class DBusNotSupportedException(dbus.exceptions.DBusException):
        _dbus_error_name = 'org.bluez.Error.NotSupported'

    class DBusAdvertisement(dbus.service.Object):
        PATH_BASE = '/org/bluez/att/replay/adv'

        def __init__(self, bus, index, advertising_type, service_uuids, local_name):
            self.path = self.PATH_BASE + str(index)
            self.bus = bus
            self.ad_type = advertising_type
            self.service_uuids = service_uuids
            self.local_name = local_name
            dbus.service.Object.__init__(self, bus, self.path)

        def get_properties(self):
            props = {'Type': self.ad_type}
            if self.service_uuids:
                props['ServiceUUIDs'] = dbus.Array(self.service_uuids, signature='s')
            if self.local_name:
                props['LocalName'] = dbus.String(self.local_name)
            return {LE_ADVERTISEMENT_IFACE: props}

        def get_path(self):
            return dbus.ObjectPath(self.path)

        @dbus.service.method(DBUS_PROP_IFACE, in_signature='s', out_signature='a{sv}')
        def GetAll(self, interface):
            if interface != LE_ADVERTISEMENT_IFACE:
                raise DBusInvalidArgsException()
            return self.get_properties()[LE_ADVERTISEMENT_IFACE]

        @dbus.service.method(LE_ADVERTISEMENT_IFACE, in_signature='', out_signature='')
        def Release(self):
            pass

    class DBusApplication(dbus.service.Object):
        def __init__(self, bus):
            self.path = '/org/bluez/att/replay'
            self.services = []
            dbus.service.Object.__init__(self, bus, self.path)

        def get_path(self):
            return dbus.ObjectPath(self.path)

        def add_service(self, service):
            self.services.append(service)

        @dbus.service.method(DBUS_OM_IFACE, out_signature='a{oa{sa{sv}}}')
        def GetManagedObjects(self):
            response = {}
            for service in self.services:
                response[service.get_path()] = service.get_properties()
                for chrc in service.get_characteristics():
                    response[chrc.get_path()] = chrc.get_properties()
                    for desc in chrc.get_descriptors():
                        response[desc.get_path()] = desc.get_properties()
            return response

    class DBusService(dbus.service.Object):
        PATH_BASE = '/org/bluez/att/replay/service'

        def __init__(self, bus, index, uuid, primary):
            self.path = self.PATH_BASE + str(index)
            self.bus = bus
            self.uuid = uuid
            self.primary = primary
            self.characteristics = []
            dbus.service.Object.__init__(self, bus, self.path)

        def get_properties(self):
            return {
                GATT_SERVICE_IFACE: {
                    'UUID': self.uuid,
                    'Primary': self.primary,
                    'Characteristics': dbus.Array(
                        [chrc.get_path() for chrc in self.characteristics],
                        signature='o'
                    )
                }
            }

        def get_path(self):
            return dbus.ObjectPath(self.path)

        def add_characteristic(self, characteristic):
            self.characteristics.append(characteristic)

        def get_characteristics(self):
            return self.characteristics

        @dbus.service.method(DBUS_PROP_IFACE, in_signature='s', out_signature='a{sv}')
        def GetAll(self, interface):
            if interface != GATT_SERVICE_IFACE:
                raise DBusInvalidArgsException()
            return self.get_properties()[GATT_SERVICE_IFACE]

    class DBusCharacteristic(dbus.service.Object):
        def __init__(self, bus, index, uuid, flags, service):
            self.path = service.path + '/char' + str(index)
            self.bus = bus
            self.uuid = uuid
            self.service = service
            self.flags = flags
            self.descriptors = []
            dbus.service.Object.__init__(self, bus, self.path)

        def get_properties(self):
            return {
                GATT_CHRC_IFACE: {
                    'Service': self.service.get_path(),
                    'UUID': self.uuid,
                    'Flags': self.flags,
                    'Descriptors': dbus.Array(
                        [desc.get_path() for desc in self.descriptors],
                        signature='o'
                    )
                }
            }

        def get_path(self):
            return dbus.ObjectPath(self.path)

        def add_descriptor(self, descriptor):
            self.descriptors.append(descriptor)

        def get_descriptors(self):
            return self.descriptors

        @dbus.service.method(DBUS_PROP_IFACE, in_signature='s', out_signature='a{sv}')
        def GetAll(self, interface):
            if interface != GATT_CHRC_IFACE:
                raise DBusInvalidArgsException()
            return self.get_properties()[GATT_CHRC_IFACE]

        @dbus.service.method(GATT_CHRC_IFACE, in_signature='a{sv}', out_signature='ay')
        def ReadValue(self, options):
            raise DBusNotSupportedException()

        @dbus.service.method(GATT_CHRC_IFACE, in_signature='aya{sv}')
        def WriteValue(self, value, options):
            raise DBusNotSupportedException()

        @dbus.service.method(GATT_CHRC_IFACE)
        def StartNotify(self):
            raise DBusNotSupportedException()

        @dbus.service.method(GATT_CHRC_IFACE)
        def StopNotify(self):
            raise DBusNotSupportedException()

        @dbus.service.signal(DBUS_PROP_IFACE, signature='sa{sv}as')
        def PropertiesChanged(self, interface, changed, invalidated):
            pass

    class CyclingPowerMeasurementCharacteristic(DBusCharacteristic):
        def __init__(self, bus, index, service):
            super().__init__(bus, index, CYCLING_POWER_MEASUREMENT_UUID, ['notify'], service)
            self.notifying = False
            self.power = 0
            self.cadence = 0
            self.cumulative_revs = 0
            self.event_time_1024 = 0

        def update_values(self, power: int, cadence: int, revs: int, time_1024: int):
            self.power = power
            self.cadence = cadence
            self.cumulative_revs = revs
            self.event_time_1024 = time_1024
            if self.notifying:
                payload = build_power_payload(self.power, self.cadence, self.cumulative_revs, self.event_time_1024)
                val = dbus.Array([dbus.Byte(b) for b in payload], signature='y')
                self.PropertiesChanged(GATT_CHRC_IFACE, {'Value': val}, [])

        @dbus.service.method(GATT_CHRC_IFACE)
        def StartNotify(self):
            self.notifying = True
            payload = build_power_payload(self.power, self.cadence, self.cumulative_revs, self.event_time_1024)
            val = dbus.Array([dbus.Byte(b) for b in payload], signature='y')
            self.PropertiesChanged(GATT_CHRC_IFACE, {'Value': val}, [])

        @dbus.service.method(GATT_CHRC_IFACE)
        def StopNotify(self):
            self.notifying = False

    class CyclingPowerFeatureCharacteristic(DBusCharacteristic):
        def __init__(self, bus, index, service):
            super().__init__(bus, index, CYCLING_POWER_FEATURE_UUID, ['read'], service)
            self.value = [0x08, 0x00, 0x00, 0x00]  # Bit 3: Crank revs supported

        @dbus.service.method(GATT_CHRC_IFACE, in_signature='a{sv}', out_signature='ay')
        def ReadValue(self, options):
            return dbus.Array([dbus.Byte(b) for b in self.value], signature='y')

    class SensorLocationCharacteristic(DBusCharacteristic):
        def __init__(self, bus, index, service):
            super().__init__(bus, index, SENSOR_LOCATION_UUID, ['read'], service)
            self.value = [13]  # Pedals

        @dbus.service.method(GATT_CHRC_IFACE, in_signature='a{sv}', out_signature='ay')
        def ReadValue(self, options):
            return dbus.Array([dbus.Byte(b) for b in self.value], signature='y')

    class HeartRateMeasurementCharacteristic(DBusCharacteristic):
        def __init__(self, bus, index, service):
            super().__init__(bus, index, HEART_RATE_MEASUREMENT_UUID, ['notify'], service)
            self.notifying = False
            self.bpm = 0

        def update_values(self, bpm: int):
            self.bpm = bpm
            if self.notifying:
                payload = build_hr_payload(self.bpm)
                val = dbus.Array([dbus.Byte(b) for b in payload], signature='y')
                self.PropertiesChanged(GATT_CHRC_IFACE, {'Value': val}, [])

        @dbus.service.method(GATT_CHRC_IFACE)
        def StartNotify(self):
            self.notifying = True
            payload = build_hr_payload(self.bpm)
            val = dbus.Array([dbus.Byte(b) for b in payload], signature='y')
            self.PropertiesChanged(GATT_CHRC_IFACE, {'Value': val}, [])

        @dbus.service.method(GATT_CHRC_IFACE)
        def StopNotify(self):
            self.notifying = False

    class BodySensorLocationCharacteristic(DBusCharacteristic):
        def __init__(self, bus, index, service):
            super().__init__(bus, index, BODY_SENSOR_LOCATION_UUID, ['read'], service)
            self.value = [0x01]  # Chest

        @dbus.service.method(GATT_CHRC_IFACE, in_signature='a{sv}', out_signature='ay')
        def ReadValue(self, options):
            return dbus.Array([dbus.Byte(b) for b in self.value], signature='y')

    class ManufacturerNameCharacteristic(DBusCharacteristic):
        def __init__(self, bus, index, service):
            super().__init__(bus, index, MANUFACTURER_NAME_UUID, ['read'], service)
            self.value = [ord(c) for c in "ATT-Replay"]

        @dbus.service.method(GATT_CHRC_IFACE, in_signature='a{sv}', out_signature='ay')
        def ReadValue(self, options):
            return dbus.Array([dbus.Byte(b) for b in self.value], signature='y')

    class CyclingPowerService(DBusService):
        def __init__(self, bus, index):
            super().__init__(bus, index, CYCLING_POWER_SERVICE_UUID, True)
            self.meas_char = CyclingPowerMeasurementCharacteristic(bus, 0, self)
            self.add_characteristic(self.meas_char)
            self.add_characteristic(CyclingPowerFeatureCharacteristic(bus, 1, self))
            self.add_characteristic(SensorLocationCharacteristic(bus, 2, self))

    class HeartRateService(DBusService):
        def __init__(self, bus, index):
            super().__init__(bus, index, HEART_RATE_SERVICE_UUID, True)
            self.meas_char = HeartRateMeasurementCharacteristic(bus, 0, self)
            self.add_characteristic(self.meas_char)
            self.add_characteristic(BodySensorLocationCharacteristic(bus, 1, self))

    class DeviceInfoService(DBusService):
        def __init__(self, bus, index):
            super().__init__(bus, index, DEVICE_INFO_SERVICE_UUID, True)
            self.add_characteristic(ManufacturerNameCharacteristic(bus, 0, self))


class BleReplayServer:
    """Manages combined BlueZ GATT Peripheral and dual advertising for Cycling Power and Heart Rate."""

    def __init__(self, enabled: bool = True):
        self.enabled = enabled and HAS_DBUS
        self.is_advertising = False
        self.current_watts = 0
        self.current_cadence = 0
        self.current_hr = 0
        self.cumulative_crank_revs = 0
        self.last_crank_event_time = 0

        self.bus = None
        self.adapter = None
        self.app = None
        self.cp_service = None
        self.hr_service = None
        self.ad_pwr = None
        self.ad_hrm = None
        self.ad_manager = None
        self.service_manager = None
        self.loop = None
        self.loop_thread = None

    def start(self):
        """Starts advertising BLE services over BlueZ D-Bus."""
        if not self.enabled:
            return
        try:
            dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
            self.bus = dbus.SystemBus()

            remote_om = dbus.Interface(self.bus.get_object(BLUEZ_SERVICE_NAME, '/'), DBUS_OM_IFACE)
            objects = remote_om.GetManagedObjects()
            for o, props in objects.items():
                if GATT_MANAGER_IFACE in props and LE_ADVERTISING_MANAGER_IFACE in props:
                    self.adapter = o
                    break

            if not self.adapter:
                print("[!] Warning: No Bluetooth adapter with LE Advertising support found. BLE inactive.")
                self.is_advertising = False
                return

            # Ensure adapter powered
            adapter_props = dbus.Interface(self.bus.get_object(BLUEZ_SERVICE_NAME, self.adapter), DBUS_PROP_IFACE)
            adapter_props.Set('org.bluez.Adapter1', 'Powered', dbus.Boolean(True))

            self.service_manager = dbus.Interface(self.bus.get_object(BLUEZ_SERVICE_NAME, self.adapter), GATT_MANAGER_IFACE)
            self.ad_manager = dbus.Interface(self.bus.get_object(BLUEZ_SERVICE_NAME, self.adapter), LE_ADVERTISING_MANAGER_IFACE)

            # Create GATT application
            self.app = DBusApplication(self.bus)
            self.cp_service = CyclingPowerService(self.bus, 0)
            self.hr_service = HeartRateService(self.bus, 1)
            self.app.add_service(self.cp_service)
            self.app.add_service(self.hr_service)
            self.app.add_service(DeviceInfoService(self.bus, 2))

            # Create Advertisements: ATT-Pwr (1818) and ATT-HRM (180d)
            self.ad_pwr = DBusAdvertisement(self.bus, 0, 'peripheral', ['1818'], 'ATT-Pwr')
            self.ad_hrm = DBusAdvertisement(self.bus, 1, 'peripheral', ['180d'], 'ATT-HRM')

            # Register Application
            self.service_manager.RegisterApplication(
                self.app.get_path(), {},
                reply_handler=lambda: None,
                error_handler=lambda e: print(f"[!] Warning: BlueZ GATT RegisterApplication error: {e}")
            )

            # Register Advertisements
            self.ad_manager.RegisterAdvertisement(
                self.ad_pwr.get_path(), {},
                reply_handler=lambda: None,
                error_handler=lambda e: print(f"[!] Warning: BlueZ RegisterAdvertisement (ATT-Pwr) error: {e}")
            )
            self.ad_manager.RegisterAdvertisement(
                self.ad_hrm.get_path(), {},
                reply_handler=lambda: None,
                error_handler=lambda e: print(f"[!] Warning: BlueZ RegisterAdvertisement (ATT-HRM) error: {e}")
            )

            self.is_advertising = True

            # Start GLib main loop in background thread with dedicated isolated context
            def glib_worker():
                ctx = GLib.MainContext.new()
                ctx.push_thread_default()
                self.loop = GLib.MainLoop(ctx)
                try:
                    self.loop.run()
                finally:
                    try:
                        ctx.pop_thread_default()
                    except Exception:
                        pass

            self.loop_thread = threading.Thread(target=glib_worker, daemon=True)
            self.loop_thread.start()
            print("[*] BLE GATT server active: Advertising 'ATT-Pwr' (0x1818) and 'ATT-HRM' (0x180D).")

        except Exception as e:
            print(f"[!] Warning: BlueZ DBus error ({e}). Continuing in mock BLE mode.")
            self.is_advertising = False

    def update_telemetry(self, watts: int, cadence: int, hr_bpm: int):
        """Updates internal telemetry values and dispatches BLE notifications."""
        self.current_watts = watts
        self.current_cadence = cadence
        self.current_hr = hr_bpm

        if cadence > 0:
            revs_per_sec = cadence / 60.0
            self.cumulative_crank_revs = (self.cumulative_crank_revs + int(round(revs_per_sec))) % 65536
            delta_1024 = int(round(1024.0 / revs_per_sec)) if revs_per_sec > 0 else 0
            self.last_crank_event_time = (self.last_crank_event_time + delta_1024) % 65536

        if self.cp_service and self.cp_service.meas_char:
            self.cp_service.meas_char.update_values(
                self.current_watts, self.current_cadence,
                self.cumulative_crank_revs, self.last_crank_event_time
            )

        if self.hr_service and self.hr_service.meas_char:
            self.hr_service.meas_char.update_values(self.current_hr)

    def stop(self):
        """Unregisters advertisements and releases BlueZ GATT resources."""
        if self.ad_manager:
            if self.ad_pwr:
                try:
                    self.ad_manager.UnregisterAdvertisement(self.ad_pwr.get_path())
                except Exception:
                    pass
            if self.ad_hrm:
                try:
                    self.ad_manager.UnregisterAdvertisement(self.ad_hrm.get_path())
                except Exception:
                    pass
        if self.loop:
            try:
                self.loop.quit()
            except Exception:
                pass
        self.is_advertising = False


# ==============================================================================
# 5. ADB Mock GPS Location Injector (Android 14+ Syntax)
# ==============================================================================

class AdbGpsInjector:
    """Injects simulated GPS fixes via ADB cmd location providers strictly adhering to Android 14+."""

    DEFAULT_PROVIDERS = ("gps", "network", "fused")

    def __init__(self, enabled: bool = True, device_serial: Optional[str] = None):
        self.enabled = enabled
        self.device_serial = device_serial
        self.is_registered = False
        self._queue = queue.Queue(maxsize=5)
        self._worker_thread = None
        self._stop_event = threading.Event()

    def detect_device(self) -> Optional[str]:
        if not self.enabled:
            return None
        try:
            res = subprocess.run(["adb", "devices"], capture_output=True, text=True, check=True)
            lines = res.stdout.strip().splitlines()
            for line in lines[1:]:
                parts = line.split()
                if len(parts) >= 2 and parts[1] == "device":
                    return parts[0]
            return None
        except Exception:
            return None

    def setup(self):
        if not self.enabled:
            return
        if not self.device_serial:
            self.device_serial = self.detect_device()
        if not self.device_serial:
            self.enabled = False
            return

        try:
            # Grant mock location to Android shell (UID 2000)
            self._run_adb(["shell", "appops", "set", "2000", "android:mock_location", "allow"], check=False)
            cmds = []
            for p in self.DEFAULT_PROVIDERS:
                cmds.append(
                    f"cmd location providers add-test-provider {p} --supportsAltitude --supportsSpeed --supportsBearing 2>/dev/null ; "
                    f"cmd location providers set-test-provider-enabled {p} true"
                )
            self._run_adb(["shell", " ; ".join(cmds)], check=False)
            self.is_registered = True

            # Start background async injection worker thread
            if not self._worker_thread or not self._worker_thread.is_alive():
                self._stop_event.clear()
                self._worker_thread = threading.Thread(target=self._worker_loop, daemon=True)
                self._worker_thread.start()
        except Exception as e:
            print(f"[!] Warning: Failed to setup ADB test providers: {e}")
            self.enabled = False

    def _worker_loop(self):
        while not self._stop_event.is_set():
            try:
                item = self._queue.get(timeout=0.1)
            except queue.Empty:
                continue
            if item is None:
                break
            lat, lon = item
            now_ms = int(time.time() * 1000)
            cmds = [self.build_inject_cmd(p, lat, lon, now_ms, accuracy=2.5) for p in self.DEFAULT_PROVIDERS]
            self._run_adb(["shell", " && ".join(cmds)], check=False)
            self._queue.task_done()

    def _run_adb(self, cmd_args: List[str], check: bool = False):
        if not self.device_serial:
            return None
        cmd = ["adb", "-s", self.device_serial] + cmd_args
        return subprocess.run(cmd, capture_output=True, text=True, check=check)

    @classmethod
    def build_inject_cmd(cls, provider: str, lat: float, lon: float, time_ms: int, accuracy: float = 2.5) -> str:
        """Constructs Android 14+ compatible location injection shell command string."""
        return f"cmd location providers set-test-provider-location {provider} --location {lat:.6f},{lon:.6f} --accuracy {accuracy:.1f} --time {time_ms}"

    def inject_location(self, lat: float, lon: float, ele: float, speed_mps: float, bearing_deg: float):
        if not self.enabled or not self.is_registered:
            return
        if self._queue.full():
            try:
                self._queue.get_nowait()
            except queue.Empty:
                pass
        try:
            self._queue.put_nowait((lat, lon))
        except queue.Full:
            pass

    def teardown(self):
        self._stop_event.set()
        try:
            self._queue.put_nowait(None)
        except Exception:
            pass
        if self.is_registered and self.device_serial:
            try:
                cmds = [f"cmd location providers remove-test-provider {p} 2>/dev/null" for p in self.DEFAULT_PROVIDERS]
                self._run_adb(["shell", " ; ".join(cmds)], check=False)
                self.is_registered = False
            except Exception:
                pass


# ==============================================================================
# 5. Core Replay Engine
# ==============================================================================

class ReplayEngine:
    """Manages timeline progression, transport states, and telemetry dispatch."""

    def __init__(self, points: List[TcxPoint], on_update: Optional[Callable[[TcxPoint], None]] = None):
        if not points:
            raise ValueError("ReplayEngine requires at least one trackpoint.")
        self.points = points
        self.on_update = on_update
        self.status = "STOPPED"
        self.current_index = 0
        self.speed_multiplier = 1.0
        self.total_duration_sec = points[-1].time_sec if points[-1].time_sec > 0 else float(len(points))
        self.total_distance_m = points[-1].dist

    @property
    def current_point(self) -> TcxPoint:
        return self.points[self.current_index]

    @property
    def progress_ratio(self) -> float:
        if len(self.points) <= 1:
            return 1.0
        return self.current_index / (len(self.points) - 1)

    def play(self):
        self.status = "PLAYING"

    def pause(self):
        self.status = "PAUSED"

    def stop(self):
        self.status = "STOPPED"
        self.current_index = 0
        if self.on_update:
            self.on_update(self.current_point)

    def set_speed(self, multiplier: float):
        self.speed_multiplier = max(0.1, min(20.0, multiplier))

    def seek_ratio(self, ratio: float):
        """Seeks timeline to given ratio [0.0, 1.0]."""
        ratio = max(0.0, min(1.0, ratio))
        self.current_index = int(round(ratio * (len(self.points) - 1)))
        if self.on_update:
            self.on_update(self.current_point)

    def seek_time(self, delta_sec: float):
        """Seeks relative to current point by delta seconds."""
        cur_time = self.current_point.time_sec
        target_time = max(0.0, min(self.total_duration_sec, cur_time + delta_sec))
        # Find closest point
        best_idx = 0
        min_diff = float("inf")
        for i, pt in enumerate(self.points):
            diff = abs(pt.time_sec - target_time)
            if diff < min_diff:
                min_diff = diff
                best_idx = i
        self.current_index = best_idx
        if self.on_update:
            self.on_update(self.current_point)

    def step(self):
        """Advances replay by one step based on speed multiplier."""
        if self.status != "PLAYING":
            return
        # Advance index proportionally to speed multiplier
        step_increment = max(1, int(round(self.speed_multiplier)))
        if self.current_index + step_increment < len(self.points):
            self.current_index += step_increment
        else:
            self.current_index = len(self.points) - 1
            self.status = "STOPPED"

        if self.on_update:
            self.on_update(self.current_point)


# ==============================================================================
# 6. PyQt6 Modern Dark Desktop GUI
# ==============================================================================

if HAS_PYQT6:
    class ReplayGuiWindow(QMainWindow):
        """Modern Obsidian Dark Desktop GUI for TCX Replay & Telemetry Monitoring."""

        def __init__(self, file_path: str, engine: ReplayEngine, ble: BleReplayServer, gps: AdbGpsInjector):
            super().__init__()
            self.file_path = file_path
            self.engine = engine
            self.ble = ble
            self.gps = gps

            self.setWindowTitle("aTrainingTracker - TCX Workout Replay & Telemetry Simulator")
            self.resize(880, 560)
            self.setup_ui()
            self.apply_theme()

            # Wire engine update callback
            self.engine.on_update = self.on_engine_update

            # Playback ticker timer (runs at 10 Hz)
            self.timer = QTimer(self)
            self.timer.timeout.connect(self.on_timer_tick)
            self.timer.start(100)
            self.tick_counter = 0

            # Initial display refresh
            self.on_engine_update(self.engine.current_point)

        def setup_ui(self):
            central = QWidget(self)
            self.setCentralWidget(central)
            main_layout = QVBoxLayout(central)
            main_layout.setSpacing(14)
            main_layout.setContentsMargins(18, 18, 18, 18)

            # --- 1. Header Bar ---
            header_layout = QHBoxLayout()
            self.lbl_file = QLabel(f"Activity: {os.path.basename(self.file_path)}")
            self.lbl_file.setFont(QFont("Inter", 13, QFont.Weight.Bold))
            self.lbl_file.setStyleSheet("color: #FFFFFF;")

            self.btn_open = QPushButton("Open TCX...")
            self.btn_open.setFixedHeight(32)
            self.btn_open.clicked.connect(self.choose_file)

            header_layout.addWidget(self.lbl_file)
            header_layout.addStretch()
            header_layout.addWidget(self.btn_open)
            main_layout.addLayout(header_layout)

            # --- 2. Telemetry Gauges Grid ---
            grid_frame = QFrame()
            grid_frame.setStyleSheet("""
                QFrame {
                    background-color: #1A1A1A;
                    border: 1px solid #2D2D2D;
                    border-radius: 10px;
                }
            """)
            grid_layout = QGridLayout(grid_frame)
            grid_layout.setSpacing(12)
            grid_layout.setContentsMargins(14, 14, 14, 14)

            self.card_power, self.val_power = self._create_card("POWER", "0", "W", "#BB86FC")
            self.card_cadence, self.val_cadence = self._create_card("CADENCE", "0", "RPM", "#03DAC6")
            self.card_hr, self.val_hr = self._create_card("HEART RATE", "0", "BPM", "#CF6679")
            self.card_speed, self.val_speed = self._create_card("SPEED", "0.0", "km/h", "#03DAC6")
            self.card_dist, self.val_dist = self._create_card("DISTANCE", "0.00", "km", "#BB86FC")
            self.card_ele, self.val_ele = self._create_card("ALTITUDE", "0", "m", "#E0E0E0")

            grid_layout.addWidget(self.card_power, 0, 0)
            grid_layout.addWidget(self.card_cadence, 0, 1)
            grid_layout.addWidget(self.card_hr, 0, 2)
            grid_layout.addWidget(self.card_speed, 1, 0)
            grid_layout.addWidget(self.card_dist, 1, 1)
            grid_layout.addWidget(self.card_ele, 1, 2)
            main_layout.addWidget(grid_frame)

            # --- 3. Timeline Scrubber ---
            timeline_box = QVBoxLayout()
            time_labels_box = QHBoxLayout()
            self.lbl_time_cur = QLabel("00:00:00")
            self.lbl_time_cur.setFont(QFont("Monospace", 10, QFont.Weight.Bold))
            self.lbl_time_cur.setStyleSheet("color: #BB86FC;")
            self.lbl_time_total = QLabel(format_duration(self.engine.total_duration_sec))
            self.lbl_time_total.setFont(QFont("Monospace", 10))
            self.lbl_time_total.setStyleSheet("color: #888888;")

            time_labels_box.addWidget(self.lbl_time_cur)
            time_labels_box.addStretch()
            time_labels_box.addWidget(self.lbl_time_total)
            timeline_box.addLayout(time_labels_box)

            self.slider = QSlider(Qt.Orientation.Horizontal)
            self.slider.setRange(0, 1000)
            self.slider.setValue(0)
            self.slider.sliderMoved.connect(self.on_slider_moved)
            timeline_box.addWidget(self.slider)
            main_layout.addLayout(timeline_box)

            # --- 4. Transport Controls Bar ---
            transport_layout = QHBoxLayout()
            self.btn_step_back = QPushButton("-10s")
            self.btn_step_back.setFixedHeight(38)
            self.btn_step_back.clicked.connect(lambda: self.engine.seek_time(-10))

            self.btn_play_pause = QPushButton("Play")
            self.btn_play_pause.setFixedHeight(38)
            self.btn_play_pause.setFixedWidth(100)
            self.btn_play_pause.setFont(QFont("Inter", 10, QFont.Weight.Bold))
            self.btn_play_pause.setStyleSheet("background-color: #BB86FC; color: #000000; border-radius: 6px;")
            self.btn_play_pause.clicked.connect(self.toggle_play_pause)

            self.btn_stop = QPushButton("Stop")
            self.btn_stop.setFixedHeight(38)
            self.btn_stop.clicked.connect(self.engine.stop)

            self.btn_step_fwd = QPushButton("+10s")
            self.btn_step_fwd.setFixedHeight(38)
            self.btn_step_fwd.clicked.connect(lambda: self.engine.seek_time(10))

            self.combo_speed = QComboBox()
            self.combo_speed.setFixedHeight(38)
            self.combo_speed.addItems(["1x Speed", "2x Speed", "5x Speed", "10x Speed"])
            self.combo_speed.currentIndexChanged.connect(self.on_speed_changed)

            transport_layout.addWidget(self.btn_step_back)
            transport_layout.addWidget(self.btn_play_pause)
            transport_layout.addWidget(self.btn_stop)
            transport_layout.addWidget(self.btn_step_fwd)
            transport_layout.addStretch()
            transport_layout.addWidget(QLabel("Speed:"))
            transport_layout.addWidget(self.combo_speed)
            main_layout.addLayout(transport_layout)

            # --- 5. Status Footer ---
            status_layout = QHBoxLayout()
            adb_text = f"ADB: {self.gps.device_serial}" if self.gps.enabled and self.gps.device_serial else "ADB: Inactive"
            ble_text = "BLE: Advertising (ATT-Pwr + ATT-HRM)" if self.ble.is_advertising else "BLE: Standby"

            self.lbl_status_adb = QLabel(f"● {adb_text}")
            self.lbl_status_adb.setStyleSheet("color: #03DAC6; font-size: 11px;")
            self.lbl_status_ble = QLabel(f"● {ble_text}")
            self.lbl_status_ble.setStyleSheet("color: #BB86FC; font-size: 11px;")

            status_layout.addWidget(self.lbl_status_adb)
            status_layout.addSpacing(20)
            status_layout.addWidget(self.lbl_status_ble)
            status_layout.addStretch()
            main_layout.addLayout(status_layout)

        def _create_card(self, title: str, init_val: str, unit: str, color: str) -> Tuple[QWidget, QLabel]:
            card = QWidget()
            card.setStyleSheet("""
                QWidget {
                    background-color: #222222;
                    border: 1px solid #333333;
                    border-radius: 8px;
                }
            """)
            layout = QVBoxLayout(card)
            layout.setContentsMargins(12, 10, 12, 10)
            layout.setSpacing(4)

            lbl_title = QLabel(title)
            lbl_title.setFont(QFont("Inter", 9, QFont.Weight.Bold))
            lbl_title.setStyleSheet("color: #999999; border: none; background: transparent;")

            val_box = QHBoxLayout()
            lbl_val = QLabel(init_val)
            lbl_val.setFont(QFont("Monospace", 22, QFont.Weight.Bold))
            lbl_val.setStyleSheet(f"color: {color}; border: none; background: transparent;")

            lbl_unit = QLabel(unit)
            lbl_unit.setFont(QFont("Inter", 10))
            lbl_unit.setStyleSheet("color: #777777; border: none; background: transparent; padding-top: 8px;")

            val_box.addWidget(lbl_val)
            val_box.addWidget(lbl_unit)
            val_box.addStretch()

            layout.addWidget(lbl_title)
            layout.addLayout(val_box)
            return card, lbl_val

        def apply_theme(self):
            self.setStyleSheet("""
                QMainWindow {
                    background-color: #121212;
                }
                QLabel {
                    color: #E0E0E0;
                }
                QPushButton {
                    background-color: #2A2A2A;
                    color: #FFFFFF;
                    border: 1px solid #444444;
                    border-radius: 6px;
                    padding: 6px 14px;
                    font-weight: 600;
                }
                QPushButton:hover {
                    background-color: #383838;
                    border-color: #555555;
                }
                QComboBox {
                    background-color: #2A2A2A;
                    color: #FFFFFF;
                    border: 1px solid #444444;
                    border-radius: 6px;
                    padding: 4px 10px;
                }
                QSlider::groove:horizontal {
                    height: 6px;
                    background: #2D2D2D;
                    border-radius: 3px;
                }
                QSlider::sub-page:horizontal {
                    background: #BB86FC;
                    border-radius: 3px;
                }
                QSlider::handle:horizontal {
                    background: #FFFFFF;
                    width: 16px;
                    margin-top: -5px;
                    margin-bottom: -5px;
                    border-radius: 8px;
                }
            """)

        def choose_file(self):
            fname, _ = QFileDialog.getOpenFileName(self, "Open TCX Workout", os.path.dirname(self.file_path), "TCX Files (*.tcx)")
            if fname:
                try:
                    points = TcxParser.parse_file(fname)
                    self.file_path = fname
                    self.lbl_file.setText(f"Activity: {os.path.basename(fname)}")
                    self.engine = ReplayEngine(points, on_update=self.on_engine_update)
                    self.lbl_time_total.setText(format_duration(self.engine.total_duration_sec))
                    self.on_engine_update(self.engine.current_point)
                except Exception as e:
                    self.lbl_file.setText(f"Error: {e}")

        def toggle_play_pause(self):
            if self.engine.status == "PLAYING":
                self.engine.pause()
                self.btn_play_pause.setText("Play")
                self.btn_play_pause.setStyleSheet("background-color: #BB86FC; color: #000000; border-radius: 6px;")
            else:
                self.engine.play()
                self.btn_play_pause.setText("Pause")
                self.btn_play_pause.setStyleSheet("background-color: #03DAC6; color: #000000; border-radius: 6px;")

        def on_speed_changed(self, idx: int):
            multipliers = [1.0, 2.0, 5.0, 10.0]
            if 0 <= idx < len(multipliers):
                self.engine.set_speed(multipliers[idx])

        def on_slider_moved(self, val: int):
            ratio = val / 1000.0
            self.engine.seek_ratio(ratio)

        def on_timer_tick(self):
            self.tick_counter += 1
            # Step engine at 1 Hz intervals (or scaled by multiplier)
            if self.engine.status == "PLAYING" and self.tick_counter % 10 == 0:
                self.engine.step()
                if self.engine.status != "PLAYING":
                    self.btn_play_pause.setText("Play")
                    self.btn_play_pause.setStyleSheet("background-color: #BB86FC; color: #000000; border-radius: 6px;")

        def on_engine_update(self, pt: TcxPoint):
            # Update gauges
            self.val_power.setText(str(pt.watts))
            self.val_cadence.setText(str(pt.cadence))
            self.val_hr.setText(str(pt.hr))
            speed_kmh = pt.speed_mps * 3.6
            self.val_speed.setText(f"{speed_kmh:.1f}")
            self.val_dist.setText(f"{pt.dist / 1000.0:.2f}")
            self.val_ele.setText(f"{int(round(pt.ele))}")

            # Update timeline
            self.lbl_time_cur.setText(format_duration(pt.time_sec))
            ratio = self.engine.progress_ratio
            self.slider.blockSignals(True)
            self.slider.setValue(int(ratio * 1000))
            self.slider.blockSignals(False)

            # Dispatch to BLE and ADB
            self.ble.update_telemetry(pt.watts, pt.cadence, pt.hr)
            self.gps.inject_location(pt.lat, pt.lon, pt.ele, pt.speed_mps, pt.bearing)


# ==============================================================================
# 7. Headless CLI Runner
# ==============================================================================

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
        if not HAS_TERMIOS or not sys.stdin.isatty():
            return None
        try:
            rlist, _, _ = select.select([sys.stdin], [], [], 0)
            if rlist:
                return sys.stdin.read(1)
        except Exception:
            return None
        return None


def run_headless_cli(engine: ReplayEngine, ble: BleReplayServer, gps: AdbGpsInjector):
    """Runs interactive terminal dashboard for headless execution."""
    key_reader = TerminalKeyReader()
    atexit.register(key_reader.restore)

    print("\n" + "=" * 70)
    print("  aTrainingTracker - Headless TCX Workout Replay Simulator")
    print("=" * 70)
    print(f"Points: {len(engine.points)} | Duration: {format_duration(engine.total_duration_sec)} | Distance: {engine.total_distance_m / 1000.0:.2f} km")
    print("Hotkeys: [Space] Play/Pause | [s] Stop | [1,2,5,0] Speed (1x,2x,5x,10x) | [q] Quit\n")

    def on_update(pt: TcxPoint):
        ble.update_telemetry(pt.watts, pt.cadence, pt.hr)
        gps.inject_location(pt.lat, pt.lon, pt.ele, pt.speed_mps, pt.bearing)

    engine.on_update = on_update
    engine.play()

    try:
        while True:
            k = key_reader.poll_key()
            if k:
                if k == ' ':
                    if engine.status == "PLAYING":
                        engine.pause()
                    else:
                        engine.play()
                elif k == 's':
                    engine.stop()
                elif k == '1':
                    engine.set_speed(1.0)
                elif k == '2':
                    engine.set_speed(2.0)
                elif k == '5':
                    engine.set_speed(5.0)
                elif k == '0':
                    engine.set_speed(10.0)
                elif k in ('q', 'Q', '\x03'):
                    break

            if engine.status == "PLAYING":
                engine.step()

            pt = engine.current_point
            bar_len = 20
            filled = int(round(engine.progress_ratio * bar_len))
            bar = "[" + "#" * filled + "-" * (bar_len - filled) + "]"
            sys.stdout.write(
                f"\r{bar} {format_duration(pt.time_sec)} | "
                f"Pwr: {pt.watts:3d}W | Cad: {pt.cadence:2d} | HR: {pt.hr:3d} | "
                f"Spd: {pt.speed_mps * 3.6:4.1f}km/h | {engine.status} ({engine.speed_multiplier:.0f}x)   "
            )
            sys.stdout.flush()

            time.sleep(1.0 / engine.speed_multiplier)
    finally:
        key_reader.restore()
        print("\n\nSimulation stopped cleanly.")


# ==============================================================================
# 8. Main Entrypoint & Argument Dispatcher
# ==============================================================================

def main():
    parser = argparse.ArgumentParser(description="Synchronized TCX Workout Replay & Telemetry Simulator")
    parser.add_argument("tcx_file", nargs="?", default="tools/sample_workout.tcx", help="Path to TCX workout activity")
    parser.add_argument("--headless", "--cli", action="store_true", help="Run in headless terminal mode without GUI")
    parser.add_argument("--no-ble", action="store_true", help="Disable BlueZ BLE GATT peripheral advertising")
    parser.add_argument("--no-gps", action="store_true", help="Disable ADB mock GPS coordinate injection")
    parser.add_argument("--device", help="Target ADB device serial number")
    parser.add_argument("--speed", type=float, default=1.0, help="Initial playback speed multiplier")
    args = parser.parse_args()

    # Determine if running GUI or Headless mode
    is_gui = not (args.headless or not HAS_PYQT6 or not os.environ.get("DISPLAY"))

    # When running in GUI mode, initialize QApplication as the root desktop action
    # to ensure platform windowing/GTK initialization happens before background threads.
    app = None
    if is_gui:
        app = QApplication(sys.argv)

    # Resolve TCX file with tilde expansion
    tcx_path = os.path.abspath(os.path.expanduser(args.tcx_file))

    if not os.path.isfile(tcx_path):
        print(f"Error: Specified TCX file not found: {tcx_path}", file=sys.stderr)
        sys.exit(1)

    print(f"[*] Parsing TCX activity: {tcx_path}...")
    points = TcxParser.parse_file(tcx_path)
    print(f"[*] Loaded {len(points)} trackpoints ({points[-1].dist / 1000.0:.2f} km, {format_duration(points[-1].time_sec)}).")

    # Rule 30 Host Pre-Flight Self-Diagnostics
    print("[*] Running Rule 30 Host Pre-Flight Diagnostics...")
    report = HostPreFlightDiagnostics.run_all(target_device=args.device)
    if report.bt_ok:
        print(f"  [OK] Bluetooth: {report.bt_message}")
    else:
        print(f"  [!] Bluetooth Warning: {report.bt_message}")
        for adv in report.bt_advice:
            print(f"      -> {adv}")

    if report.adb_ok:
        print(f"  [OK] ADB Location: {report.adb_message}")
    else:
        print(f"  [!] ADB Warning: {report.adb_message}")
        for adv in report.adb_advice:
            print(f"      -> {adv}")

    target_device = args.device or report.device_serial

    # Initialize subsystems
    ble_server = BleReplayServer(enabled=not args.no_ble)
    ble_server.start()

    gps_injector = AdbGpsInjector(enabled=not args.no_gps, device_serial=target_device)
    gps_injector.setup()

    # Clean exit teardown registration
    def cleanup():
        print("\n[*] Releasing test providers and terminating BLE GATT server...")
        gps_injector.teardown()
        ble_server.stop()

    atexit.register(cleanup)
    signal.signal(signal.SIGINT, lambda s, f: sys.exit(0))
    signal.signal(signal.SIGTERM, lambda s, f: sys.exit(0))

    engine = ReplayEngine(points)
    engine.set_speed(args.speed)

    # Dispatch to GUI or Headless
    if is_gui:
        window = ReplayGuiWindow(tcx_path, engine, ble_server, gps_injector)
        window.show()
        sys.exit(app.exec())
    else:
        if not args.headless and not HAS_PYQT6:
            print("[*] PyQt6 not found in environment. Defaulting to headless CLI mode.")
        run_headless_cli(engine, ble_server, gps_injector)


if __name__ == "__main__":
    main()
