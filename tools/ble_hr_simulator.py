#!/usr/bin/env python3
"""
BLE Heart Rate Monitor Simulator for aTrainingTracker
Emulates a standard Bluetooth Low Energy Heart Rate Service (UUID 0x180D) with:
- Heart Rate Measurement (UUID 0x2A37) with Beat-to-Beat RR-Interval packing (Bit 4 0x10)
- Body Sensor Location (UUID 0x2A38)
- Battery Service (UUID 0x180F) with Battery Level (0x2A19)
- Device Information (UUID 0x180A) with Manufacturer Name (0x2A29)

Requirement: REQ-SIM-001 (REQ-TST-005.1..4)
Tests: TST-SIM-001 (TST-TST-005.1..5), tools/test_ble_hr_simulator.py

Usage:
    python3 tools/ble_hr_simulator.py                     # Default: 135 BPM, 100% Battery, RR enabled
    python3 tools/ble_hr_simulator.py --no-rr             # Legacy 8-bit BPM only (flags = 0x00)
    python3 tools/ble_hr_simulator.py --bpm 140 --battery 85 --rmssd 45.0 --dfa-regime threshold
"""

import sys
import os
import argparse
import math
import random
import threading
import time
import dbus
import dbus.exceptions
import dbus.mainloop.glib
import dbus.service
from gi.repository import GLib

BLUEZ_SERVICE_NAME = 'org.bluez'
DBUS_OM_IFACE = 'org.freedesktop.DBus.ObjectManager'
DBUS_PROP_IFACE = 'org.freedesktop.DBus.Properties'

GATT_MANAGER_IFACE = 'org.bluez.GattManager1'
GATT_SERVICE_IFACE = 'org.bluez.GattService1'
GATT_CHRC_IFACE = 'org.bluez.GattCharacteristic1'
GATT_DESC_IFACE = 'org.bluez.GattDescriptor1'

LE_ADVERTISING_MANAGER_IFACE = 'org.bluez.LEAdvertisingManager1'
LE_ADVERTISEMENT_IFACE = 'org.bluez.LEAdvertisement1'


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
        self.local_name = 'ATT-HRM'
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


HEART_RATE_SERVICE_UUID = '0000180d-0000-1000-8000-00805f9b34fb'
HEART_RATE_MEASUREMENT_UUID = '00002a37-0000-1000-8000-00805f9b34fb'
BODY_SENSOR_LOCATION_UUID = '00002a38-0000-1000-8000-00805f9b34fb'

BATTERY_SERVICE_UUID = '0000180f-0000-1000-8000-00805f9b34fb'
BATTERY_LEVEL_CHARACTERISTIC_UUID = '00002a19-0000-1000-8000-00805f9b34fb'

DEVICE_INFO_SERVICE_UUID = '0000180a-0000-1000-8000-00805f9b34fb'
MANUFACTURER_NAME_UUID = '00002a29-0000-1000-8000-00805f9b34fb'


class HeartRateAdvertisement(Advertisement):
    def __init__(self, bus, index):
        super().__init__(bus, index, 'peripheral')
        # Standard 16-bit BLE UUIDs: 0x180D (Heart Rate), 0x180F (Battery Service)
        self.service_uuids = ['180d', '180f']
        self.local_name = 'ATT-HRM'
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


class RrSynthesizer:
    """
    Pure Python 3 standard library Beat-to-Beat RR series synthesizer (REQ-SIM-001.3).
    Supports selectable DFA alpha-1 regimes via AR(1) pink/white noise,
    exact rMSSD innovation scaling, phase-locked 1 Hz beat accumulation with error diffusion,
    and interactive physiological artifact and RF gap injection (REQ-SIM-001.4).
    Zero external pip dependencies (TST-PRO-005.6).
    """
    REGIMES = {
        'aerobic': {'phi': 0.70, 'target_a1': 1.00},
        'threshold': {'phi': 0.35, 'target_a1': 0.70},
        'anaerobic': {'phi': -0.20, 'target_a1': 0.45},
    }

    def __init__(self, bpm=135, rmssd=35.0, regime='aerobic', phi=None):
        self.bpm = max(30, min(240, int(bpm)))
        self.rmssd = max(0.0, float(rmssd))
        self.regime_name = regime if regime in self.REGIMES else 'aerobic'
        self.phi = float(phi) if phi is not None else self.REGIMES[self.regime_name]['phi']
        self.delta_prev = 0.0
        self.phase_ms = 0.0
        self.quant_error_ms = 0.0
        self.artifact_queue = []
        self.gap_ticks_remaining = 0

    def set_bpm(self, bpm):
        self.bpm = max(30, min(240, int(bpm)))

    def set_rmssd(self, rmssd_ms):
        self.rmssd = max(0.0, float(rmssd_ms))

    def set_regime(self, name):
        if name in self.REGIMES:
            self.regime_name = name
            self.phi = self.REGIMES[name]['phi']

    def inject_artifact(self, kind):
        """
        Injects physiological artifacts or RF packet dropout (REQ-SIM-001.4).
        - 'ectopic' / 'e': PVC pair (shortened beat ~0.70x followed by compensatory pause ~1.30x)
        - 'missed' / 'm': Missed beat (single interval ~2.0x)
        - 'extra' / 'x': Extra beat (two intervals ~0.50x each)
        - 'gap' / 'g': RF dropout (> 2.5s notification suppression)
        """
        k = kind.lower().strip()
        if k in ('e', 'ectopic'):
            self.artifact_queue.append(('ectopic', 0.70))
            self.artifact_queue.append(('ectopic', 1.30))
        elif k in ('m', 'missed'):
            self.artifact_queue.append(('missed', 2.00))
        elif k in ('x', 'extra'):
            self.artifact_queue.append(('extra', 0.50))
            self.artifact_queue.append(('extra', 0.50))
        elif k in ('g', 'gap'):
            self.gap_ticks_remaining = 3  # 3 seconds suppression (> 2.5s)

    def generate_next_rr_ms(self):
        """
        Generates the next beat interval in milliseconds.
        """
        rr_mean = 60000.0 / self.bpm
        if self.artifact_queue:
            art_type, factor = self.artifact_queue.pop(0)
            self.delta_prev = 0.0
            return max(200.0, min(3000.0, rr_mean * factor))

        if self.rmssd <= 1e-6:
            return rr_mean

        sigma = self.rmssd * math.sqrt(max(0.001, (1.0 + self.phi) / 2.0))
        eps = random.gauss(0.0, 1.0)
        delta = self.phi * self.delta_prev + sigma * eps
        rr = max(250.0, min(2200.0, rr_mean + delta))
        self.delta_prev = rr - rr_mean
        return rr

    def tick_1hz(self, dt_ms=1000.0):
        """
        Advances the phase accumulator across a 1 Hz tick (dt_ms).
        Returns a list of completed RR intervals encoded in 1/1024s uint16 raw units.
        Guarantees 60s cumulative drift <= 50 ms (REQ-SIM-001.2).
        """
        if self.gap_ticks_remaining > 0:
            self.gap_ticks_remaining -= 1
            self.phase_ms += dt_ms
            return []

        self.phase_ms += dt_ms
        rr_mean = 60000.0 / self.bpm
        emitted_raw_1024 = []

        while self.phase_ms >= rr_mean - 1e-6:
            rr_ms = self.generate_next_rr_ms()
            target_ms = rr_ms + self.quant_error_ms
            raw = int(round(target_ms * 1024.0 / 1000.0))
            raw = max(1, min(65535, raw))
            eff_ms = raw * 1000.0 / 1024.0
            self.quant_error_ms = target_ms - eff_ms
            self.phase_ms -= rr_mean
            emitted_raw_1024.append(raw)

        if abs(self.phase_ms) < 1e-6:
            self.phase_ms = 0.0

        return emitted_raw_1024


def build_hr_payload(bpm, rr_raw_list=None, enable_rr=True):
    """
    Constructs BLE 0x2A37 Heart Rate Measurement payload bytes (REQ-SIM-001.1).
    When enable_rr is True and rr_raw_list is non-empty, sets Bit 4 (0x10) and appends
    little-endian uint16 values (1/1024s units). When enable_rr is False or list is empty,
    emits flags=0x00 and 8-bit BPM only.
    """
    bpm_val = max(30, min(240, int(bpm)))
    if not enable_rr or not rr_raw_list:
        return [0x00, bpm_val]

    payload = [0x10, bpm_val]
    for raw in rr_raw_list:
        raw_clamped = max(0, min(65535, int(raw)))
        payload.append(raw_clamped & 0xFF)
        payload.append((raw_clamped >> 8) & 0xFF)
    return payload


class HeartRateMeasurementCharacteristic(Characteristic):
    # UUID 0x2A37: Heart Rate Measurement
    def __init__(self, bus, index, service, initial_bpm=135, enable_rr=True, rmssd=35.0,
                 regime='aerobic', phi=None):
        super().__init__(bus, index, HEART_RATE_MEASUREMENT_UUID, ['notify'], service)
        self.notifying = False
        self.bpm = max(30, min(240, int(initial_bpm)))
        self.enable_rr = bool(enable_rr)
        self.synthesizer = RrSynthesizer(bpm=self.bpm, rmssd=rmssd, regime=regime, phi=phi)
        self.timer_id = None

    def set_bpm(self, bpm):
        self.bpm = max(30, min(240, int(bpm)))
        self.synthesizer.set_bpm(self.bpm)

    def build_payload(self):
        bpm_val = max(30, min(240, int(self.bpm)))
        self.synthesizer.set_bpm(bpm_val)
        if not self.enable_rr:
            payload = build_hr_payload(bpm_val, enable_rr=False)
        else:
            rr_raw_list = self.synthesizer.tick_1hz(1000.0)
            payload = build_hr_payload(bpm_val, rr_raw_list, enable_rr=True)
        return dbus.Array([dbus.Byte(b) for b in payload], signature='y')

    def send_notification(self):
        if not self.notifying:
            return False
        # If RF gap active, advance simulator clock but suppress GATT notification
        if self.enable_rr and self.synthesizer.gap_ticks_remaining > 0:
            self.synthesizer.tick_1hz(1000.0)
            return True
        val = self.build_payload()
        self.PropertiesChanged(GATT_CHRC_IFACE, {'Value': val}, [])
        return True

    @dbus.service.method(GATT_CHRC_IFACE)
    def StartNotify(self):
        if self.notifying:
            return
        self.notifying = True
        mode_str = f"RR-Intervals (Bit 4 0x10, regime={self.synthesizer.regime_name})" if self.enable_rr else "Legacy BPM only (--no-rr)"
        print(f"[*] Client verbunden & Herzfrequenz abonniert! (Sende {self.bpm} BPM | {mode_str})")
        self.timer_id = GLib.timeout_add(1000, self.send_notification)

    @dbus.service.method(GATT_CHRC_IFACE)
    def StopNotify(self):
        if not self.notifying:
            return
        self.notifying = False
        print("[*] Client hat Herzfrequenz abbestellt.")
        if self.timer_id:
            GLib.source_remove(self.timer_id)
            self.timer_id = None


class BodySensorLocationCharacteristic(Characteristic):
    # UUID 0x2A38: Body Sensor Location (1 = Chest)
    def __init__(self, bus, index, service):
        super().__init__(bus, index, BODY_SENSOR_LOCATION_UUID, ['read'], service)
        self.value = [0x01]  # Chest

    @dbus.service.method(GATT_CHRC_IFACE, in_signature='a{sv}', out_signature='ay')
    def ReadValue(self, options):
        print("[*] Read BodySensorLocation (0x2A38) -> Chest (0x01)")
        return dbus.Array([dbus.Byte(b) for b in self.value], signature='y')


class HeartRateService(Service):
    def __init__(self, bus, index, initial_bpm=135, enable_rr=True, rmssd=35.0,
                 regime='aerobic', phi=None):
        super().__init__(bus, index, HEART_RATE_SERVICE_UUID, True)
        self.measurement_char = HeartRateMeasurementCharacteristic(
            bus, 0, self, initial_bpm=initial_bpm, enable_rr=enable_rr,
            rmssd=rmssd, regime=regime, phi=phi
        )
        self.add_characteristic(self.measurement_char)
        self.add_characteristic(BodySensorLocationCharacteristic(bus, 1, self))


class BatteryLevelCharacteristic(Characteristic):
    # UUID 0x2A19: Battery Level (0 - 100%)
    def __init__(self, bus, index, service, initial_battery=100):
        super().__init__(bus, index, BATTERY_LEVEL_CHARACTERISTIC_UUID, ['read', 'notify'], service)
        self.battery_level = max(0, min(100, int(initial_battery)))
        self.notifying = False

    def build_payload(self):
        return dbus.Array([dbus.Byte(self.battery_level)], signature='y')

    def send_notification(self):
        if not self.notifying:
            return
        val = self.build_payload()
        self.PropertiesChanged(GATT_CHRC_IFACE, {'Value': val}, [])

    def set_battery(self, level):
        self.battery_level = max(0, min(100, int(level)))
        print(f"[*] Batteriestand aktualisiert auf: {self.battery_level}%")
        self.send_notification()

    @dbus.service.method(GATT_CHRC_IFACE, in_signature='a{sv}', out_signature='ay')
    def ReadValue(self, options):
        print(f"[*] Read BatteryLevel (0x2A19) -> {self.battery_level}%")
        return self.build_payload()

    @dbus.service.method(GATT_CHRC_IFACE)
    def StartNotify(self):
        if self.notifying:
            return
        self.notifying = True
        print(f"[*] Client verbunden & Batteriestand abonniert! (Aktuell {self.battery_level}%)")
        self.send_notification()

    @dbus.service.method(GATT_CHRC_IFACE)
    def StopNotify(self):
        if not self.notifying:
            return
        self.notifying = False
        print("[*] Client hat Batteriestand abbestellt.")


class BatteryService(Service):
    def __init__(self, bus, index, initial_battery=100):
        super().__init__(bus, index, BATTERY_SERVICE_UUID, True)
        self.battery_char = BatteryLevelCharacteristic(bus, 0, self, initial_battery)
        self.add_characteristic(self.battery_char)


class ManufacturerNameCharacteristic(Characteristic):
    # UUID 0x2A29: Manufacturer Name String
    def __init__(self, bus, index, service):
        super().__init__(bus, index, MANUFACTURER_NAME_UUID, ['read'], service)
        self.value = [ord(c) for c in "ATT-Simulator"]

    @dbus.service.method(GATT_CHRC_IFACE, in_signature='a{sv}', out_signature='ay')
    def ReadValue(self, options):
        print("[*] Read ManufacturerName (0x2A29) -> 'ATT-Simulator'")
        return dbus.Array([dbus.Byte(b) for b in self.value], signature='y')


class DeviceInfoService(Service):
    def __init__(self, bus, index):
        super().__init__(bus, index, DEVICE_INFO_SERVICE_UUID, True)
        self.add_characteristic(ManufacturerNameCharacteristic(bus, 0, self))


def find_adapter(bus):
    remote_om = dbus.Interface(bus.get_object(BLUEZ_SERVICE_NAME, '/'), DBUS_OM_IFACE)
    objects = remote_om.GetManagedObjects()
    for o, props in objects.items():
        if GATT_MANAGER_IFACE in props.keys():
            return o
    return None


def keyboard_input_loop(meas_char, battery_char, loop):
    time.sleep(0.5)
    print("\n" + "=" * 65)
    print("  ATT BLE Herzfrequenz-, HRV- & Batterie-Simulator LÄUFT")
    print("=" * 65)
    print("  Steuerung im Terminal:")
    print("    [+] / [-]         : Puls um 5 BPM erhöhen / verringern")
    print("    [h<Zahl>]         : Exakter Puls (z.B. h145 für 145 BPM)")
    print("    [b<Zahl>]         : Batteriestand ändern (z.B. b15 für 15%, b85 für 85%)")
    print("    [b+] / [b-]       : Batteriestand um 5% erhöhen / verringern")
    print("    [a1] / [a2] / [a3]: DFA a1 Zone (aerobic / threshold / anaerobic)")
    print("    [r<Zahl>]         : Target rMSSD in ms (z.B. r65, r35, r15)")
    print("    [e]               : Ektopischer Extraschlag (PVC + Pause)")
    print("    [m]               : Ausgelassener Schlag (Missed Beat: 2x RR)")
    print("    [x]               : Zusätzlicher Schlag (Extra Beat: 2x 0.5 RR)")
    print("    [g]               : RF Dropout / Sensor-Lücke (> 2.5s Pause)")
    print("    [q]               : Beenden")
    print("=" * 65)
    mode_str = f"RR={meas_char.enable_rr} ({meas_char.synthesizer.regime_name}, rMSSD={meas_char.synthesizer.rmssd:.1f}ms)"
    print(f"  Aktuell: {meas_char.bpm} BPM | {mode_str} | Batterie: {battery_char.battery_level}%\n")

    while True:
        try:
            line = input().strip()
            if not line:
                continue
            if line == '+':
                meas_char.set_bpm(min(240, meas_char.bpm + 5))
                print(f" -> Herzfrequenz: {meas_char.bpm} BPM")
            elif line == '-':
                meas_char.set_bpm(max(30, meas_char.bpm - 5))
                print(f" -> Herzfrequenz: {meas_char.bpm} BPM")
            elif line == 'b+':
                battery_char.set_battery(battery_char.battery_level + 5)
            elif line == 'b-':
                battery_char.set_battery(battery_char.battery_level - 5)
            elif line.startswith('h'):
                try:
                    meas_char.set_bpm(int(line[1:]))
                    print(f" -> Herzfrequenz: {meas_char.bpm} BPM")
                except ValueError:
                    print("Ungültige BPM-Eingabe (z.B. h140)")
            elif line.startswith('b') and line[1:].isdigit():
                try:
                    battery_char.set_battery(int(line[1:]))
                except ValueError:
                    print("Ungültige Batterie-Eingabe (z.B. b15)")
            elif line in ('a1', 'aerobic'):
                meas_char.synthesizer.set_regime('aerobic')
                print(f" -> DFA a1 Regime: aerobic (phi=0.70, target a1=1.00)")
            elif line in ('a2', 'threshold'):
                meas_char.synthesizer.set_regime('threshold')
                print(f" -> DFA a1 Regime: threshold (phi=0.35, target a1=0.70)")
            elif line in ('a3', 'anaerobic'):
                meas_char.synthesizer.set_regime('anaerobic')
                print(f" -> DFA a1 Regime: anaerobic (phi=-0.20, target a1=0.45)")
            elif line.startswith('r'):
                try:
                    val = float(line[1:])
                    meas_char.synthesizer.set_rmssd(val)
                    print(f" -> Target rMSSD: {meas_char.synthesizer.rmssd:.1f} ms")
                except ValueError:
                    print("Ungültige rMSSD-Eingabe (z.B. r45)")
            elif line in ('e', 'ectopic'):
                meas_char.synthesizer.inject_artifact('ectopic')
                print(" -> [Artefakt injiziert] Ektopischer Extraschlag (PVC + Pause)")
            elif line in ('m', 'missed'):
                meas_char.synthesizer.inject_artifact('missed')
                print(" -> [Artefakt injiziert] Ausgelassener Herzschlag (2x RR)")
            elif line in ('x', 'extra'):
                meas_char.synthesizer.inject_artifact('extra')
                print(" -> [Artefakt injiziert] Zusätzlicher Extraschlag (2x 0.5 RR)")
            elif line in ('g', 'gap'):
                meas_char.synthesizer.inject_artifact('gap')
                print(" -> [Artefakt injiziert] RF Dropout / Paketlücke (3.0s Pause)")
            elif line == 'q':
                print("Beende Simulator...")
                loop.quit()
                break
            else:
                print("Unbekannter Befehl (+, -, h<Zahl>, b<Zahl>, b+, b-, a1, a2, a3, r<ms>, e, m, x, g, q)")
        except (EOFError, KeyboardInterrupt):
            loop.quit()
            break


def main():
    parser = argparse.ArgumentParser(description="BLE Heart Rate, HRV & Battery Simulator for aTrainingTracker")
    parser.add_argument("--bpm", type=int, default=135, help="Initial heart rate in BPM (default: 135)")
    parser.add_argument("--battery", type=int, default=100, help="Initial battery percentage (0-100, default: 100)")
    parser.add_argument("--no-rr", action="store_true", help="Disable RR intervals (legacy 8-bit BPM only)")
    parser.add_argument("--rmssd", type=float, default=35.0, help="Target rMSSD in ms (default: 35.0)")
    parser.add_argument("--dfa-regime", choices=["aerobic", "threshold", "anaerobic"], default="aerobic",
                        help="Initial DFA alpha-1 regime (default: aerobic)")
    parser.add_argument("--dfa-a1", type=float, default=None, help="Custom DFA alpha-1 / AR(1) phi parameter")
    args = parser.parse_args()

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

    app = Application(bus)
    hr_service = HeartRateService(
        bus, 0,
        initial_bpm=args.bpm,
        enable_rr=not args.no_rr,
        rmssd=args.rmssd,
        regime=args.dfa_regime,
        phi=args.dfa_a1
    )
    battery_service = BatteryService(bus, 1, initial_battery=args.battery)
    app.add_service(hr_service)
    app.add_service(battery_service)
    app.add_service(DeviceInfoService(bus, 2))

    ad = HeartRateAdvertisement(bus, 0)

    loop = GLib.MainLoop()

    print("[*] Starte BLE Herzfrequenz, HRV & Batterie Simulator...")
    print(f"[*] Initiale Werte: {hr_service.measurement_char.bpm} BPM, {battery_service.battery_char.battery_level}% Batterie")
    mode_str = f"RR aktiv (Regime: {hr_service.measurement_char.synthesizer.regime_name}, rMSSD: {hr_service.measurement_char.synthesizer.rmssd:.1f}ms)" if not args.no_rr else "Legacy BPM-only (Bit 4 deaktiviert)"
    print(f"[*] HRV Modus: {mode_str}")
    print("[*] Registriere GATT Application (Heart Rate 0x180D, Battery 0x180F, Device Info 0x180A)...")
    service_manager.RegisterApplication(app.get_path(), {},
                                        reply_handler=lambda: print("[OK] GATT Services registriert."),
                                        error_handler=lambda e: print(f"[FEHLER] GATT Fehler: {e}"))

    print(f"[*] Starte BLE Advertising als '{ad.local_name}' mit UUIDs {ad.service_uuids}...")
    ad_manager.RegisterAdvertisement(ad.get_path(), {},
                                     reply_handler=lambda: print("[OK] Advertising aktiv. Herzfrequenz- & Batterie-Sensor ist jetzt sichtbar!"),
                                     error_handler=lambda e: print(f"[FEHLER] Advertising Fehler: {e}"))

    input_thread = threading.Thread(
        target=keyboard_input_loop,
        args=(hr_service.measurement_char, battery_service.battery_char, loop),
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
