#!/usr/bin/env python3
"""
BLE Heart Rate Monitor Simulator for aTrainingTracker
Emulates a standard Bluetooth Low Energy Heart Rate Service (UUID 0x180D) with:
- Heart Rate Measurement (UUID 0x2A37)
- Body Sensor Location (UUID 0x2A38)
- Battery Service (UUID 0x180F) with Battery Level (0x2A19)
- Device Information (UUID 0x180A) with Manufacturer Name (0x2A29)

Usage:
    python3 tools/ble_hr_simulator.py                     # Default: 135 BPM, 100% Battery
    python3 tools/ble_hr_simulator.py --battery 15        # Start with critical low battery (15%)
    python3 tools/ble_hr_simulator.py --bpm 140 --battery 85
"""

import sys
import os
import argparse
import dbus
import dbus.exceptions
import dbus.mainloop.glib
import dbus.service
import threading
import time
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


class HeartRateMeasurementCharacteristic(Characteristic):
    # UUID 0x2A37: Heart Rate Measurement
    def __init__(self, bus, index, service, initial_bpm=135):
        super().__init__(bus, index, HEART_RATE_MEASUREMENT_UUID, ['notify'], service)
        self.notifying = False
        self.bpm = max(30, min(240, int(initial_bpm)))
        self.timer_id = None

    def build_payload(self):
        # Flags: uint8 (0x00 = UINT8 heart rate format, sensor contact not supported)
        flags = 0x00
        bpm_val = max(30, min(240, int(self.bpm)))
        payload = [flags, bpm_val]
        return dbus.Array([dbus.Byte(b) for b in payload], signature='y')

    def send_notification(self):
        if not self.notifying:
            return False
        val = self.build_payload()
        self.PropertiesChanged(GATT_CHRC_IFACE, {'Value': val}, [])
        return True

    @dbus.service.method(GATT_CHRC_IFACE)
    def StartNotify(self):
        if self.notifying:
            return
        self.notifying = True
        print(f"[*] Client verbunden & Herzfrequenz abonniert! (Sende {self.bpm} BPM)")
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
    def __init__(self, bus, index, initial_bpm=135):
        super().__init__(bus, index, HEART_RATE_SERVICE_UUID, True)
        self.measurement_char = HeartRateMeasurementCharacteristic(bus, 0, self, initial_bpm)
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
    print("\n" + "=" * 60)
    print("  ATT BLE Herzfrequenz- & Batterie-Simulator LÄUFT")
    print("=" * 60)
    print("  Steuerung im Terminal:")
    print("    [+] / [-]     : Puls um 5 BPM erhöhen / verringern")
    print("    [h<Zahl>]     : Exakter Puls (z.B. h145 für 145 BPM)")
    print("    [b<Zahl>]     : Batteriestand ändern (z.B. b15 für 15%, b85 für 85%)")
    print("    [b+] / [b-]   : Batteriestand um 5% erhöhen / verringern")
    print("    [q]           : Beenden")
    print("=" * 60)
    print(f"  Aktuell: {meas_char.bpm} BPM | Batterie: {battery_char.battery_level}%\n")

    while True:
        try:
            line = input().strip()
            if not line:
                continue
            if line == '+':
                meas_char.bpm = min(240, meas_char.bpm + 5)
                print(f" -> Herzfrequenz: {meas_char.bpm} BPM")
            elif line == '-':
                meas_char.bpm = max(30, meas_char.bpm - 5)
                print(f" -> Herzfrequenz: {meas_char.bpm} BPM")
            elif line == 'b+':
                battery_char.set_battery(battery_char.battery_level + 5)
            elif line == 'b-':
                battery_char.set_battery(battery_char.battery_level - 5)
            elif line.startswith('h'):
                try:
                    meas_char.bpm = int(line[1:])
                    print(f" -> Herzfrequenz: {meas_char.bpm} BPM")
                except ValueError:
                    print("Ungültige BPM-Eingabe (z.B. h140)")
            elif line.startswith('b'):
                try:
                    battery_char.set_battery(int(line[1:]))
                except ValueError:
                    print("Ungültige Batterie-Eingabe (z.B. b15)")
            elif line == 'q':
                print("Beende Simulator...")
                loop.quit()
                break
            else:
                print("Unbekannter Befehl (+, -, h<Zahl>, b<Zahl>, b+, b-, q)")
        except (EOFError, KeyboardInterrupt):
            loop.quit()
            break


def main():
    parser = argparse.ArgumentParser(description="BLE Heart Rate & Battery Simulator for aTrainingTracker")
    parser.add_argument("--bpm", type=int, default=135, help="Initial heart rate in BPM (default: 135)")
    parser.add_argument("--battery", type=int, default=100, help="Initial battery percentage (0-100, default: 100)")
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
    hr_service = HeartRateService(bus, 0, initial_bpm=args.bpm)
    battery_service = BatteryService(bus, 1, initial_battery=args.battery)
    app.add_service(hr_service)
    app.add_service(battery_service)
    app.add_service(DeviceInfoService(bus, 2))

    ad = HeartRateAdvertisement(bus, 0)

    loop = GLib.MainLoop()

    print("[*] Starte BLE Herzfrequenz & Batterie Simulator...")
    print(f"[*] Initiale Werte: {hr_service.measurement_char.bpm} BPM, {battery_service.battery_char.battery_level}% Batterie")
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
