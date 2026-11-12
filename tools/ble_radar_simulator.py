#!/usr/bin/env python3
"""
BLE Cycling Rearview Radar Simulator for aTrainingTracker
Emulates a Garmin Varia Radar BLE peripheral (UUID 6a4e3200-667b-11e3-949a-0800200c9a66) with:
- High-Resolution 2D V2 Target Stream (UUID 6a4e3204-667b-11e3-949a-0800200c9a66)
- Legacy V1 Target Stream (UUID 6a4e3203-667b-11e3-949a-0800200c9a66)
- Bidirectional Tail Light Control (UUID 6a4e3202-667b-11e3-949a-0800200c9a66)
- Battery Service (UUID 0x180F) with Battery Level (0x2A19)
- Device Information (UUID 0x180A) with Model Number (0x2A24) & Manufacturer Name (0x2A29)

Requirement: REQ-SIM-002 (REQ-TST-006.1..4)
Tests: TST-SIM-002 (TST-TST-006.1..6), tools/test_ble_radar_simulator.py

Usage:
    python3 tools/ble_radar_simulator.py                     # Default: V2 Stream, RTL515, 100% Battery
    python3 tools/ble_radar_simulator.py --v1                # Prefer Legacy V1 characteristic
    python3 tools/ble_radar_simulator.py --stvzo             # Emulate RTL516 (StVZO model, no flashing modes)
    python3 tools/ble_radar_simulator.py --battery 80        # Custom initial battery level
"""

import sys
import os
import argparse
import math
import random
import struct
import threading
import time
import dbus
import dbus.exceptions
import dbus.mainloop.glib
import dbus.service
from gi.repository import GLib

# -----------------------------------------------------------------------------
# D-Bus & BlueZ Interfaces & UUIDs
# -----------------------------------------------------------------------------
BLUEZ_SERVICE_NAME = 'org.bluez'
DBUS_OM_IFACE = 'org.freedesktop.DBus.ObjectManager'
DBUS_PROP_IFACE = 'org.freedesktop.DBus.Properties'

GATT_MANAGER_IFACE = 'org.bluez.GattManager1'
GATT_SERVICE_IFACE = 'org.bluez.GattService1'
GATT_CHRC_IFACE = 'org.bluez.GattCharacteristic1'
GATT_DESC_IFACE = 'org.bluez.GattDescriptor1'

LE_ADVERTISING_MANAGER_IFACE = 'org.bluez.LEAdvertisingManager1'
LE_ADVERTISEMENT_IFACE = 'org.bluez.LEAdvertisement1'

# Garmin Varia Primary Radar Service & Characteristics
RADAR_SERVICE_UUID = '6a4e3200-667b-11e3-949a-0800200c9a66'
RADAR_LIGHT_CONTROL_UUID = '6a4e3202-667b-11e3-949a-0800200c9a66'
RADAR_V1_CHARACTERISTIC_UUID = '6a4e3203-667b-11e3-949a-0800200c9a66'
RADAR_V2_CHARACTERISTIC_UUID = '6a4e3204-667b-11e3-949a-0800200c9a66'

# Standard BLE Services
BATTERY_SERVICE_UUID = '0000180f-0000-1000-8000-00805f9b34fb'
BATTERY_LEVEL_CHARACTERISTIC_UUID = '00002a19-0000-1000-8000-00805f9b34fb'

DEVICE_INFO_SERVICE_UUID = '0000180a-0000-1000-8000-00805f9b34fb'
MODEL_NUMBER_CHARACTERISTIC_UUID = '00002a24-0000-1000-8000-00805f9b34fb'
MANUFACTURER_NAME_CHARACTERISTIC_UUID = '00002a29-0000-1000-8000-00805f9b34fb'

# Protocol Constants
PACKET_TYPE_THREAT = 0x00
PACKET_TYPE_V2_THREAT = 0x01


# -----------------------------------------------------------------------------
# Protocol Data Types & Enumerations
# -----------------------------------------------------------------------------
class ThreatLevel:
    NONE = 0
    APPROACHING = 1
    FAST_APPROACHING = 2


class VehicleSizeCategory:
    UNKNOWN_OR_LIGHT = 0
    CAR = 1
    TRUCK_OR_BUS = 2


class LaneState:
    UNKNOWN = 0
    IN_RIDER_LANE = 1
    CHANGING_LANE = 2
    PASSING_LANE = 3


class RadarLightMode:
    OFF = 0x00
    SOLID = 0x01
    PELOTON = 0x05
    NIGHT_FLASH = 0x06
    DAY_FLASH = 0x07

    NAMES = {
        0x00: "OFF",
        0x01: "SOLID",
        0x05: "PELOTON",
        0x06: "NIGHT_FLASH",
        0x07: "DAY_FLASH",
    }

    @classmethod
    def name_of(cls, mode_byte):
        return cls.NAMES.get(mode_byte, f"UNKNOWN(0x{mode_byte:02X})")

    @classmethod
    def is_valid(cls, mode_byte):
        return mode_byte in cls.NAMES


class RadarTargetState:
    """Represents a simulated kinematic radar target."""
    def __init__(self, target_id, range_m, speed_mps, lateral_m=-1.8,
                 threat_level=ThreatLevel.APPROACHING,
                 size_category=VehicleSizeCategory.CAR,
                 lane_state=LaneState.PASSING_LANE):
        self.target_id = int(target_id)
        self.range_m = float(range_m)
        self.speed_mps = float(speed_mps)
        self.lateral_m = float(lateral_m)
        self.threat_level = int(threat_level)
        self.size_category = int(size_category)
        self.lane_state = int(lane_state)

    def copy(self):
        return RadarTargetState(
            self.target_id, self.range_m, self.speed_mps, self.lateral_m,
            self.threat_level, self.size_category, self.lane_state
        )


# -----------------------------------------------------------------------------
# Pure Protocol Serializers & Calculations (TST-PRO-005.6 stdlib)
# -----------------------------------------------------------------------------
def compute_advertising_pdu_size(local_name: str, service_uuids: list) -> int:
    """
    Computes total legacy Bluetooth LE advertisement PDU length in bytes.
    Enforces the 31-byte legacy advertisement PDU constraint.
    """
    # Flags structure: 1 byte len + 1 byte type (0x01) + 1 byte flags = 3 bytes
    flags_len = 3

    # Service UUIDs structure: 1 byte len + 1 byte type + sum(uuid_bytes)
    uuid_data_len = 0
    for u in service_uuids:
        clean = u.replace('-', '')
        if len(clean) == 4:
            uuid_data_len += 2
        elif len(clean) == 32:
            uuid_data_len += 16
        else:
            uuid_data_len += 16
    uuid_ad_len = (1 + 1 + uuid_data_len) if uuid_data_len > 0 else 0

    # Local Name structure: 1 byte len + 1 byte type (0x09) + len(name.encode('utf-8'))
    name_bytes = local_name.encode('utf-8')
    name_ad_len = 1 + 1 + len(name_bytes)

    return flags_len + uuid_ad_len + name_ad_len


class RadarPayloadBuilder:
    """
    Binary packet serializer for Garmin Varia BLE Radar protocols:
    - High-Resolution V2 (6a4e3204): Header 0x01 + N * 8 bytes
    - Legacy V1 (6a4e3203): Header 0x00 + N * 3 bytes
    """

    @staticmethod
    def build_v2_payload(targets):
        """
        Builds a V2 threat stream frame (matching BTLERadarDevice.parseV2ThreatPacket).
        Header byte: 0x01 (PACKET_TYPE_V2_THREAT)
        Stride per target (8 bytes):
          +0: targetId (uint8)
          +1..2: rangeRaw (uint16 LE, 0.1m precision)
          +3..4: speedRaw (int16 LE signed, 0.1 m/s precision)
          +5..6: lateralRaw (int16 LE signed, 0.1m precision, negative=left)
          +7: sizeAndThreat (bits 0..1 threat, bits 2..3 size, bits 4..5 lane)
        """
        payload = [PACKET_TYPE_V2_THREAT]
        for t in targets:
            tid = int(t.target_id) & 0xFF
            range_raw = max(0, min(65535, int(round(t.range_m * 10.0))))
            range_lo = range_raw & 0xFF
            range_hi = (range_raw >> 8) & 0xFF

            speed_raw = max(-32768, min(32767, int(round(t.speed_mps * 10.0))))
            speed_bytes = struct.pack('<h', speed_raw)

            lat_raw = max(-32768, min(32767, int(round(t.lateral_m * 10.0))))
            lat_bytes = struct.pack('<h', lat_raw)

            threat_bits = int(t.threat_level) & 0x03
            size_bits = (int(t.size_category) & 0x03) << 2
            lane_bits = (int(t.lane_state) & 0x03) << 4
            size_and_threat = threat_bits | size_bits | lane_bits

            payload.extend([
                tid,
                range_lo, range_hi,
                speed_bytes[0], speed_bytes[1],
                lat_bytes[0], lat_bytes[1],
                size_and_threat
            ])
        return payload

    @staticmethod
    def build_v1_payload(targets):
        """
        Builds a legacy V1 threat frame (matching BTLERadarDevice.parseV1ThreatPacket).
        Header byte: 0x00 (PACKET_TYPE_THREAT)
        Stride per target (3 bytes):
          +0: targetId (uint8)
          +1: rangeMeters (uint8, 0..255m)
          +2: (threatBits << 4) | (speedMps & 0x0F)
        """
        payload = [PACKET_TYPE_THREAT]
        for t in targets:
            tid = int(t.target_id) & 0xFF
            range_m = max(0, min(255, int(round(t.range_m))))
            threat_bits = (int(t.threat_level) & 0x03) << 4
            speed_mps = max(0, min(15, int(round(t.speed_mps)))) & 0x0F
            speed_and_threat = threat_bits | speed_mps
            payload.extend([tid, range_m, speed_and_threat])
        return payload


# -----------------------------------------------------------------------------
# Kinematics Engine & Presets
# -----------------------------------------------------------------------------
class RadarKinematicsEngine:
    """
    Manages kinematic radar targets at 5 Hz (200 ms interval),
    interactive presets, tail light state, and watchdog starvation.
    """
    def __init__(self, tick_rate_hz=5.0, is_stvzo=False):
        self.tick_rate_hz = float(tick_rate_hz)
        self.dt = 1.0 / self.tick_rate_hz
        self.is_stvzo = bool(is_stvzo)
        self.targets = []
        self.light_mode = RadarLightMode.SOLID
        self.starve_ticks_remaining = 0
        self.country_loop_active = False
        self._country_loop_cooldown_ticks = 0

    def is_starving(self):
        return self.starve_ticks_remaining > 0

    def trigger_watchdog_starvation(self, duration_sec=4.5):
        self.starve_ticks_remaining = int(math.ceil(duration_sec * self.tick_rate_hz))
        print(f"[WATCHDOG] Starvation active: Suppressing radar notifications for {duration_sec:.1f}s "
              f"({self.starve_ticks_remaining} ticks) to trigger client 3500ms timeout...")

    def set_light_mode(self, mode_byte):
        if not RadarLightMode.is_valid(mode_byte):
            print(f"[LIGHT ERROR] Invalid light mode byte: 0x{mode_byte:02X}")
            return False
        if self.is_stvzo and mode_byte in (RadarLightMode.PELOTON, RadarLightMode.NIGHT_FLASH, RadarLightMode.DAY_FLASH):
            print(f"[LIGHT WARNING] StVZO mode active: Flashing mode {RadarLightMode.name_of(mode_byte)} "
                  f"restricted -> setting SOLID (0x01)")
            mode_byte = RadarLightMode.SOLID
        self.light_mode = mode_byte
        print(f"[LIGHT] Phone switched Radar Tail Light -> {RadarLightMode.name_of(mode_byte)} (0x{mode_byte:02X})")
        return True

    def spawn_preset(self, key):
        key = key.lower()
        if key == 'c':
            # Single car
            self._upsert_target(RadarTargetState(
                target_id=1, range_m=140.0, speed_mps=12.0, lateral_m=-1.8,
                threat_level=ThreatLevel.APPROACHING, size_category=VehicleSizeCategory.CAR,
                lane_state=LaneState.PASSING_LANE
            ))
            print("[TRAFFIC] Spawned Single Car (ID=1, Range=140m, Speed=12m/s, Lateral=-1.8m)")
        elif key == 'f':
            # Fast car
            self._upsert_target(RadarTargetState(
                target_id=2, range_m=160.0, speed_mps=22.0, lateral_m=-2.0,
                threat_level=ThreatLevel.FAST_APPROACHING, size_category=VehicleSizeCategory.CAR,
                lane_state=LaneState.PASSING_LANE
            ))
            print("[TRAFFIC] Spawned Fast Car (ID=2, Range=160m, Speed=22m/s, FAST_APPROACHING)")
        elif key == 't':
            # Heavy truck/bus
            self._upsert_target(RadarTargetState(
                target_id=3, range_m=150.0, speed_mps=15.0, lateral_m=-2.2,
                threat_level=ThreatLevel.APPROACHING, size_category=VehicleSizeCategory.TRUCK_OR_BUS,
                lane_state=LaneState.PASSING_LANE
            ))
            print("[TRAFFIC] Spawned Truck/Bus (ID=3, Range=150m, Speed=15m/s, TRUCK_OR_BUS)")
        elif key == 'p':
            # Close pass (< 1.5m passing distance)
            self._upsert_target(RadarTargetState(
                target_id=4, range_m=120.0, speed_mps=14.0, lateral_m=-1.1,
                threat_level=ThreatLevel.APPROACHING, size_category=VehicleSizeCategory.CAR,
                lane_state=LaneState.CHANGING_LANE
            ))
            print("[TRAFFIC] Spawned Close Pass (<1.5m) (ID=4, Range=120m, Speed=14m/s, Lateral=-1.1m)")
        elif key == 'm':
            # 3-car convoy
            self._upsert_target(RadarTargetState(
                target_id=11, range_m=100.0, speed_mps=14.0, lateral_m=-1.8,
                threat_level=ThreatLevel.APPROACHING, size_category=VehicleSizeCategory.CAR,
                lane_state=LaneState.PASSING_LANE
            ))
            self._upsert_target(RadarTargetState(
                target_id=12, range_m=130.0, speed_mps=14.0, lateral_m=-1.8,
                threat_level=ThreatLevel.APPROACHING, size_category=VehicleSizeCategory.CAR,
                lane_state=LaneState.PASSING_LANE
            ))
            self._upsert_target(RadarTargetState(
                target_id=13, range_m=160.0, speed_mps=14.0, lateral_m=-1.8,
                threat_level=ThreatLevel.APPROACHING, size_category=VehicleSizeCategory.CAR,
                lane_state=LaneState.PASSING_LANE
            ))
            print("[TRAFFIC] Spawned 3-Car Convoy (IDs=11,12,13 at 100m, 130m, 160m)")
        elif key == 'l':
            self.country_loop_active = not self.country_loop_active
            status = "AKTIV" if self.country_loop_active else "DEAKTIVIERT"
            print(f"[TRAFFIC] Country Loop: {status}")
        elif key == '0':
            self.clear_targets()
            print("[TRAFFIC] All-Clear: Alle Ziele entfernt.")

    def _upsert_target(self, target):
        for idx, existing in enumerate(self.targets):
            if existing.target_id == target.target_id:
                self.targets[idx] = target
                return
        self.targets.append(target)

    def clear_targets(self):
        self.targets.clear()

    def tick(self, dt=None):
        if dt is None:
            dt = self.dt

        # Watchdog starvation check
        if self.starve_ticks_remaining > 0:
            self.starve_ticks_remaining -= 1
            if self.starve_ticks_remaining == 0:
                print("[WATCHDOG] Starvation ended: Resumed radar threat notifications.")
            return None

        # Country loop traffic generation
        if self.country_loop_active:
            if self._country_loop_cooldown_ticks > 0:
                self._country_loop_cooldown_ticks -= 1
            elif len(self.targets) < 3:
                is_truck = random.random() < 0.25
                size = VehicleSizeCategory.TRUCK_OR_BUS if is_truck else VehicleSizeCategory.CAR
                speed = random.uniform(12.0, 20.0)
                threat = ThreatLevel.FAST_APPROACHING if speed > 18.0 else ThreatLevel.APPROACHING
                tid = random.randint(20, 99)
                self._upsert_target(RadarTargetState(
                    target_id=tid, range_m=random.uniform(130.0, 160.0), speed_mps=speed,
                    lateral_m=random.uniform(-2.2, -1.6), threat_level=threat,
                    size_category=size, lane_state=LaneState.PASSING_LANE
                ))
                self._country_loop_cooldown_ticks = int(random.uniform(6.0, 12.0) * self.tick_rate_hz)

        # Update target positions
        active = []
        for t in self.targets:
            t.range_m -= t.speed_mps * dt
            if t.range_m > 0.0:
                active.append(t)
            else:
                print(f"[TRAFFIC] Target {t.target_id} passed rider (overtake completed, removed)")

        self.targets = active
        return list(self.targets)


# -----------------------------------------------------------------------------
# BlueZ D-Bus GATT Server Infrastructure
# -----------------------------------------------------------------------------
class InvalidArgsException(dbus.exceptions.DBusException):
    _dbus_error_name = 'org.bluez.Error.InvalidArguments'


class NotSupportedException(dbus.exceptions.DBusException):
    _dbus_error_name = 'org.bluez.Error.NotSupported'


class Advertisement(dbus.service.Object):
    PATH_BASE = '/org/bluez/example/advertisement'

    def __init__(self, bus, index, advertising_type):
        self.path = self.PATH_BASE + str(index)
        self.bus = bus
        self.ad_type = advertising_type
        self.service_uuids = None
        self.manufacturer_data = None
        self.solicit_uuids = None
        self.service_data = None
        self.local_name = 'VariaSim'
        self.include_tx_power = False
        self.data = None
        dbus.service.Object.__init__(self, bus, self.path)

    def get_properties(self):
        properties = dict()
        properties['Type'] = self.ad_type
        if self.service_uuids is not None:
            properties['ServiceUUIDs'] = dbus.Array(self.service_uuids, signature='s')
        if self.solicit_uuids is not None:
            properties['SolicitUUIDs'] = dbus.Array(self.solicit_uuids, signature='s')
        if self.manufacturer_data is not None:
            properties['ManufacturerData'] = dbus.Dictionary(self.manufacturer_data, signature='qv')
        if self.service_data is not None:
            properties['ServiceData'] = dbus.Dictionary(self.service_data, signature='sv')
        if self.local_name is not None:
            properties['LocalName'] = dbus.String(self.local_name)
        if self.include_tx_power:
            properties['IncludeTxPower'] = dbus.Boolean(self.include_tx_power)
        if self.data is not None:
            properties['Data'] = dbus.Dictionary(self.data, signature='yv')
        return {LE_ADVERTISEMENT_IFACE: properties}

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method(DBUS_PROP_IFACE, in_signature='s', out_signature='a{sv}')
    def GetAll(self, interface):
        if interface != LE_ADVERTISEMENT_IFACE:
            raise InvalidArgsException()
        return self.get_properties()[LE_ADVERTISEMENT_IFACE]

    @dbus.service.method(LE_ADVERTISEMENT_IFACE, in_signature='', out_signature='')
    def Release(self):
        print(f'{self.path}: Advertisement Released')


class RadarAdvertisement(Advertisement):
    def __init__(self, bus, index):
        super().__init__(bus, index, 'peripheral')
        self.service_uuids = [RADAR_SERVICE_UUID]
        self.local_name = 'VariaSim'
        self.include_tx_power = False


class Application(dbus.service.Object):
    def __init__(self, bus):
        self.path = '/'
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
            chrcs = service.get_characteristics()
            for chrc in chrcs:
                response[chrc.get_path()] = chrc.get_properties()
                descs = chrc.get_descriptors()
                for desc in descs:
                    response[desc.get_path()] = desc.get_properties()
        return response


class Service(dbus.service.Object):
    PATH_BASE = '/org/bluez/example/service'

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
            raise InvalidArgsException()
        return self.get_properties()[GATT_SERVICE_IFACE]


class Characteristic(dbus.service.Object):
    PATH_BASE = '/org/bluez/example/characteristic'

    def __init__(self, bus, index, uuid, flags, service):
        self.path = self.PATH_BASE + str(index)
        self.bus = bus
        self.uuid = uuid
        self.flags = flags
        self.service = service
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
            raise InvalidArgsException()
        return self.get_properties()[GATT_CHRC_IFACE]

    @dbus.service.method(GATT_CHRC_IFACE, in_signature='a{sv}', out_signature='ay')
    def ReadValue(self, options):
        raise NotSupportedException()

    @dbus.service.method(GATT_CHRC_IFACE, in_signature='aya{sv}')
    def WriteValue(self, value, options):
        raise NotSupportedException()

    @dbus.service.method(GATT_CHRC_IFACE)
    def StartNotify(self):
        raise NotSupportedException()

    @dbus.service.method(GATT_CHRC_IFACE)
    def StopNotify(self):
        raise NotSupportedException()

    @dbus.service.signal(DBUS_PROP_IFACE, signature='sa{sv}as')
    def PropertiesChanged(self, interface, changed, invalidated):
        pass


# -----------------------------------------------------------------------------
# Garmin Varia Radar GATT Characteristics
# -----------------------------------------------------------------------------
class RadarV2Characteristic(Characteristic):
    """Garmin Varia V2 High-Resolution 2D Target Stream (6a4e3204)."""
    def __init__(self, bus, index, service):
        super().__init__(bus, index, RADAR_V2_CHARACTERISTIC_UUID, ['notify'], service)
        self.notifying = False

    def StartNotify(self):
        if not self.notifying:
            self.notifying = True
            print("[RADAR V2] Client subscribed to V2 2D Target Stream notifications.")

    def StopNotify(self):
        if self.notifying:
            self.notifying = False
            print("[RADAR V2] Client unsubscribed from V2 Target Stream.")

    def send_targets(self, targets):
        if self.notifying:
            payload = RadarPayloadBuilder.build_v2_payload(targets)
            self.PropertiesChanged(
                GATT_CHRC_IFACE,
                {'Value': dbus.ByteArray(payload)},
                []
            )


class RadarV1Characteristic(Characteristic):
    """Garmin Varia V1 Legacy Target Stream (6a4e3203)."""
    def __init__(self, bus, index, service):
        super().__init__(bus, index, RADAR_V1_CHARACTERISTIC_UUID, ['notify'], service)
        self.notifying = False

    def StartNotify(self):
        if not self.notifying:
            self.notifying = True
            print("[RADAR V1] Client subscribed to V1 Legacy Target Stream notifications.")

    def StopNotify(self):
        if self.notifying:
            self.notifying = False
            print("[RADAR V1] Client unsubscribed from V1 Target Stream.")

    def send_targets(self, targets):
        if self.notifying:
            payload = RadarPayloadBuilder.build_v1_payload(targets)
            self.PropertiesChanged(
                GATT_CHRC_IFACE,
                {'Value': dbus.ByteArray(payload)},
                []
            )


class RadarLightControlCharacteristic(Characteristic):
    """Garmin Varia Tail Light Control (6a4e3202). Supports Read & Write."""
    def __init__(self, bus, index, service, engine):
        super().__init__(bus, index, RADAR_LIGHT_CONTROL_UUID, ['read', 'write', 'notify'], service)
        self.engine = engine
        self.notifying = False

    def ReadValue(self, options):
        return dbus.ByteArray([self.engine.light_mode])

    def WriteValue(self, value, options):
        if not value:
            raise InvalidArgsException("Empty write payload")
        mode_byte = int(value[0])
        success = self.engine.set_light_mode(mode_byte)
        if not success:
            raise InvalidArgsException(f"Invalid light mode byte: {mode_byte}")
        if self.notifying:
            self.PropertiesChanged(
                GATT_CHRC_IFACE,
                {'Value': dbus.ByteArray([self.engine.light_mode])},
                []
            )

    def StartNotify(self):
        self.notifying = True

    def StopNotify(self):
        self.notifying = False


class RadarService(Service):
    def __init__(self, bus, index, engine):
        super().__init__(bus, index, RADAR_SERVICE_UUID, True)
        self.engine = engine
        self.v2_char = RadarV2Characteristic(bus, 0, self)
        self.v1_char = RadarV1Characteristic(bus, 1, self)
        self.light_char = RadarLightControlCharacteristic(bus, 2, self, engine)
        self.add_characteristic(self.v2_char)
        self.add_characteristic(self.v1_char)
        self.add_characteristic(self.light_char)


# -----------------------------------------------------------------------------
# Battery & Device Information Services
# -----------------------------------------------------------------------------
class BatteryLevelCharacteristic(Characteristic):
    def __init__(self, bus, index, service, initial_battery=100):
        super().__init__(bus, index, BATTERY_LEVEL_CHARACTERISTIC_UUID, ['read', 'notify'], service)
        self.battery_level = initial_battery
        self.notifying = False

    def ReadValue(self, options):
        return dbus.ByteArray([self.battery_level])

    def StartNotify(self):
        self.notifying = True

    def StopNotify(self):
        self.notifying = False

    def set_battery(self, level):
        self.battery_level = max(0, min(100, int(level)))
        if self.notifying:
            self.PropertiesChanged(
                GATT_CHRC_IFACE,
                {'Value': dbus.ByteArray([self.battery_level])},
                []
            )


class BatteryService(Service):
    def __init__(self, bus, index, initial_battery=100):
        super().__init__(bus, index, BATTERY_SERVICE_UUID, True)
        self.battery_char = BatteryLevelCharacteristic(bus, 0, self, initial_battery)
        self.add_characteristic(self.battery_char)


class ModelNumberCharacteristic(Characteristic):
    def __init__(self, bus, index, service, model_number="RTL515"):
        super().__init__(bus, index, MODEL_NUMBER_CHARACTERISTIC_UUID, ['read'], service)
        self.model_number = model_number

    def ReadValue(self, options):
        return dbus.ByteArray(self.model_number.encode('utf-8'))


class ManufacturerNameCharacteristic(Characteristic):
    def __init__(self, bus, index, service):
        super().__init__(bus, index, MANUFACTURER_NAME_CHARACTERISTIC_UUID, ['read'], service)

    def ReadValue(self, options):
        return dbus.ByteArray(b"Garmin")


class DeviceInfoService(Service):
    def __init__(self, bus, index, model_number="RTL515"):
        super().__init__(bus, index, DEVICE_INFO_SERVICE_UUID, True)
        self.add_characteristic(ModelNumberCharacteristic(bus, 0, self, model_number))
        self.add_characteristic(ManufacturerNameCharacteristic(bus, 1, self))


# -----------------------------------------------------------------------------
# Adapter Helper & CLI Loop
# -----------------------------------------------------------------------------
def find_adapter(bus):
    remote_om = dbus.Interface(bus.get_object(BLUEZ_SERVICE_NAME, '/'), DBUS_OM_IFACE)
    objects = remote_om.GetManagedObjects()
    for o, props in objects.items():
        if GATT_MANAGER_IFACE in props and LE_ADVERTISING_MANAGER_IFACE in props:
            return o
    return None


def keyboard_input_loop(engine, battery_char, loop):
    print("\n--- Interaktive Tastenbefehle ---")
    print(" c : Einzelnes Auto (140m -> 0m @ 12m/s, APPROACHING)")
    print(" f : Schnelles Auto (160m -> 0m @ 22m/s, FAST_APPROACHING)")
    print(" t : LKW / Bus (150m -> 0m @ 15m/s, TRUCK_OR_BUS)")
    print(" p : Enges Überholmanöver / Close Pass (<1.5m lateral)")
    print(" m : 3er-Konvoi (100m, 130m, 160m)")
    print(" l : Zufällige Landstraßen-Verkehrsschleife an/aus")
    print(" 0 : Alle Ziele sofort löschen (All-Clear)")
    print(" w : Watchdog-Starvation (4.5s Pause -> 3500ms Client Timeout)")
    print(" b<0-100> : Batterie-Level ändern (z.B. b20)")
    print(" q : Beenden")
    print("---------------------------------\n")

    while True:
        try:
            line = sys.stdin.readline()
            if not line:
                break
            line = line.strip().lower()
            if not line:
                continue

            if line in ('c', 'f', 't', 'p', 'm', 'l', '0'):
                engine.spawn_preset(line)
            elif line == 'w':
                engine.trigger_watchdog_starvation(4.5)
            elif line.startswith('b'):
                try:
                    val = int(line[1:])
                    battery_char.set_battery(val)
                    print(f" -> Batterie auf {battery_char.battery_level}% gesetzt")
                except ValueError:
                    print("Ungültige Batterie-Eingabe (z.B. b15)")
            elif line == 'q':
                print("Beende Simulator...")
                loop.quit()
                break
            else:
                print("Unbekannter Befehl (c, f, t, p, m, l, 0, w, b<Zahl>, q)")
        except (EOFError, KeyboardInterrupt):
            loop.quit()
            break


def main():
    parser = argparse.ArgumentParser(description="BLE Garmin Varia Cycling Radar Simulator for aTrainingTracker")
    parser.add_argument("--v1", action="store_true", help="Prefer legacy V1 characteristic stream")
    parser.add_argument("--v2", action="store_true", help="Prefer high-resolution V2 stream (default)")
    parser.add_argument("--stvzo", action="store_true", help="Emulate RTL516 StVZO model (disables flashing modes)")
    parser.add_argument("--model", type=str, default=None, help="Custom DIS Model Number string")
    parser.add_argument("--battery", type=int, default=100, help="Initial battery percentage (0-100, default: 100)")
    args = parser.parse_args()

    model_number = args.model if args.model else ("RTL516" if args.stvzo else "RTL515")

    pdu_size = compute_advertising_pdu_size('VariaSim', [RADAR_SERVICE_UUID])
    if pdu_size > 31:
        print(f"[FEHLER] Advertising PDU ({pdu_size} Bytes) überschreitet BLE-Limit (31 Bytes)!")
        sys.exit(1)

    dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
    bus = dbus.SystemBus()

    adapter = find_adapter(bus)
    if not adapter:
        print("[FEHLER] Kein Bluetooth-Adapter mit BLE-GATT-Unterstützung gefunden!")
        sys.exit(1)

    adapter_props = dbus.Interface(bus.get_object(BLUEZ_SERVICE_NAME, adapter), DBUS_PROP_IFACE)
    adapter_props.Set('org.bluez.Adapter1', 'Powered', dbus.Boolean(True))

    service_manager = dbus.Interface(bus.get_object(BLUEZ_SERVICE_NAME, adapter), GATT_MANAGER_IFACE)
    ad_manager = dbus.Interface(bus.get_object(BLUEZ_SERVICE_NAME, adapter), LE_ADVERTISING_MANAGER_IFACE)

    engine = RadarKinematicsEngine(tick_rate_hz=5.0, is_stvzo=args.stvzo)

    app = Application(bus)
    radar_service = RadarService(bus, 0, engine)
    battery_service = BatteryService(bus, 1, initial_battery=args.battery)
    device_info_service = DeviceInfoService(bus, 2, model_number=model_number)

    app.add_service(radar_service)
    app.add_service(battery_service)
    app.add_service(device_info_service)

    ad = RadarAdvertisement(bus, 0)

    loop = GLib.MainLoop()

    # 5 Hz Radar notification tick timer (200 ms)
    def on_radar_tick():
        targets = engine.tick()
        if targets is not None:
            radar_service.v2_char.send_targets(targets)
            radar_service.v1_char.send_targets(targets)
        return True

    GLib.timeout_add(200, on_radar_tick)

    print("[*] Starte Garmin Varia BLE Radar Simulator...")
    print(f"[*] Modell: {model_number} (StVZO: {args.stvzo}), Batterie: {battery_service.battery_char.battery_level}%")
    print(f"[*] Advertising PDU: {pdu_size} Bytes (Limit: 31 Bytes)")
    print(f"[*] Radar Services: V2 (0x3204), V1 (0x3203), Light Control (0x3202)")
    print("[*] Registriere GATT Application...")
    service_manager.RegisterApplication(
        app.get_path(), {},
        reply_handler=lambda: print("[OK] GATT Services registriert."),
        error_handler=lambda e: print(f"[FEHLER] GATT Fehler: {e}")
    )

    print(f"[*] Starte BLE Advertising als '{ad.local_name}' mit Radar UUID {RADAR_SERVICE_UUID}...")
    ad_manager.RegisterAdvertisement(
        ad.get_path(), {},
        reply_handler=lambda: print("[OK] Advertising aktiv. Garmin Varia Radar ist jetzt sichtbar!"),
        error_handler=lambda e: print(f"[FEHLER] Advertising Fehler: {e}")
    )

    input_thread = threading.Thread(
        target=keyboard_input_loop,
        args=(engine, battery_service.battery_char, loop),
        daemon=True
    )
    input_thread.start()

    try:
        loop.run()
    except KeyboardInterrupt:
        pass
    finally:
        try:
            ad_manager.UnregisterAdvertisement(ad.get_path())
        except Exception:
            pass


if __name__ == '__main__':
    main()
