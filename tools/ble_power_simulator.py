#!/usr/bin/env python3
"""
BLE Cycling Power Simulator for aTrainingTracker
Emulates a Bluetooth Low Energy Cycling Power Service (UUID 0x1818) with:
- Cycling Power Measurement (UUID 0x2A63)
- Cycling Power Feature (UUID 0x2A65)
- Cycling Power Sensor Location (UUID 0x2A5D)

Usage:
    python3 tools/ble_power_simulator.py
"""

import sys
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
        self.local_name = 'ATT Power Sim'
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


CYCLING_POWER_SERVICE_UUID = '00001818-0000-1000-8000-00805f9b34fb'
CYCLING_POWER_MEASUREMENT_UUID = '00002a63-0000-1000-8000-00805f9b34fb'
CYCLING_POWER_FEATURE_UUID = '00002a65-0000-1000-8000-00805f9b34fb'
SENSOR_LOCATION_UUID = '00002a5d-0000-1000-8000-00805f9b34fb'


class CyclingPowerAdvertisement(Advertisement):
    def __init__(self, bus, index):
        super().__init__(bus, index, 'peripheral')
        # Standard 16-bit BLE UUID so Android's ScanFilter matches exactly like a physical sensor
        self.service_uuids = ['1818']
        self.local_name = 'ATT-Pwr'
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


class CyclingPowerFeatureCharacteristic(Characteristic):
    # UUID 0x2A65: Cycling Power Feature
    # Bit 3: Crank Revolution Data Supported (0x08)
    def __init__(self, bus, index, service):
        super().__init__(bus, index, CYCLING_POWER_FEATURE_UUID, ['read'], service)
        # 32-bit uint: bit 3 = crank revolution data supported
        self.value = [0x08, 0x00, 0x00, 0x00]

    @dbus.service.method(GATT_CHRC_IFACE, in_signature='a{sv}', out_signature='ay')
    def ReadValue(self, options):
        print(f"[*] Read CyclingPowerFeature (0x2A65) -> Crank Revolution Data Supported")
        return dbus.Array([dbus.Byte(b) for b in self.value], signature='y')


class SensorLocationCharacteristic(Characteristic):
    # UUID 0x2A5D: Sensor Location (13 = Left and Right Pedals, 4 = Left Crank, 5 = Right Crank)
    def __init__(self, bus, index, service):
        super().__init__(bus, index, SENSOR_LOCATION_UUID, ['read'], service)
        self.value = [13]  # Left and Right Pedals

    @dbus.service.method(GATT_CHRC_IFACE, in_signature='a{sv}', out_signature='ay')
    def ReadValue(self, options):
        print(f"[*] Read SensorLocation (0x2A5D) -> Pedals")
        return dbus.Array([dbus.Byte(b) for b in self.value], signature='y')


class CyclingPowerMeasurementCharacteristic(Characteristic):
    # UUID 0x2A63: Cycling Power Measurement
    def __init__(self, bus, index, service):
        super().__init__(bus, index, CYCLING_POWER_MEASUREMENT_UUID, ['notify'], service)
        self.notifying = False
        self.power = 200  # Watts
        self.cadence = 85  # RPM
        self.cumulative_crank_revs = 0
        self.last_crank_event_time = 0  # 1/1024 seconds
        self.timer_id = None

    def build_payload(self):
        # Flags: 16-bit uint (0x0020 = Crank Revolution Data Present)
        flags = 0x0020
        flags_bytes = list(flags.to_bytes(2, byteorder='little'))

        # Instantaneous Power: 16-bit signed integer
        power_clamped = max(-32768, min(32767, int(self.power)))
        power_bytes = list(power_clamped.to_bytes(2, byteorder='little', signed=True))

        # Crank revolution calculation
        if self.cadence > 0:
            revs_per_sec = self.cadence / 60.0
            self.cumulative_crank_revs = (self.cumulative_crank_revs + int(round(revs_per_sec))) % 65536
            # Event time in 1/1024s
            time_delta_1024 = int(round(1024.0 / revs_per_sec)) if revs_per_sec > 0 else 0
            self.last_crank_event_time = (self.last_crank_event_time + time_delta_1024) % 65536

        crank_rev_bytes = list(self.cumulative_crank_revs.to_bytes(2, byteorder='little'))
        crank_time_bytes = list(self.last_crank_event_time.to_bytes(2, byteorder='little'))

        payload = flags_bytes + power_bytes + crank_rev_bytes + crank_time_bytes
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
        print(f"[*] Client connected & subscribed to Cycling Power notifications! (Sende {self.power}W, {self.cadence}RPM)")
        self.timer_id = GLib.timeout_add(1000, self.send_notification)

    @dbus.service.method(GATT_CHRC_IFACE)
    def StopNotify(self):
        if not self.notifying:
            return
        self.notifying = False
        print(f"[*] Client unsubscribed from notifications.")
        if self.timer_id:
            GLib.source_remove(self.timer_id)
            self.timer_id = None


class CyclingPowerService(Service):
    def __init__(self, bus, index):
        super().__init__(bus, index, CYCLING_POWER_SERVICE_UUID, True)
        self.measurement_char = CyclingPowerMeasurementCharacteristic(bus, 0, self)
        self.add_characteristic(self.measurement_char)
        self.add_characteristic(CyclingPowerFeatureCharacteristic(bus, 1, self))
        self.add_characteristic(SensorLocationCharacteristic(bus, 2, self))


DEVICE_INFO_SERVICE_UUID = '0000180a-0000-1000-8000-00805f9b34fb'
MANUFACTURER_NAME_UUID = '00002a29-0000-1000-8000-00805f9b34fb'


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
        if GATT_MANAGER_IFACE in props.keys() and LE_ADVERTISING_MANAGER_IFACE in props.keys():
            return o
    return None


def keyboard_input_loop(meas_char, loop):
    print("\n" + "=" * 60)
    print("  ATT BLE Cycling Power Simulator RUNNING")
    print("=" * 60)
    print("  Steuerung im Terminal:")
    print("    [+] / [-]     : Leistung um 10 W erhöhen / verringern")
    print("    [w<Zahl>]     : Exakte Wattzahl (z.B. w250 für 250 W)")
    print("    [c<Zahl>]     : Trittfrequenz (z.B. c90 für 90 RPM)")
    print("    [q]           : Beenden")
    print("=" * 60)
    print(f"  Aktuell: {meas_char.power} W | {meas_char.cadence} RPM\n")

    while True:
        try:
            line = input().strip()
            if not line:
                continue
            if line == '+':
                meas_char.power += 10
            elif line == '-':
                meas_char.power = max(0, meas_char.power - 10)
            elif line.startswith('w'):
                try:
                    meas_char.power = int(line[1:])
                except ValueError:
                    print("Ungültige Watt-Eingabe (z.B. w250)")
            elif line.startswith('c'):
                try:
                    meas_char.cadence = int(line[1:])
                except ValueError:
                    print("Ungültige Cadence-Eingabe (z.B. c90)")
            elif line == 'q':
                print("Beende Simulator...")
                loop.quit()
                break
            else:
                print("Unbekannter Befehl (+, -, w<Zahl>, c<Zahl>, q)")
            print(f" -> Leistung: {meas_char.power} W | Kadenz: {meas_char.cadence} RPM")
        except (EOFError, KeyboardInterrupt):
            loop.quit()
            break


def main():
    dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
    bus = dbus.SystemBus()

    adapter = find_adapter(bus)
    if not adapter:
        print("[FEHLER] Kein Bluetooth-Adapter mit BLE-GATT-Unterstützung gefunden!")
        print("Stelle sicher, dass Bluetooth aktiviert ist (z.B. 'sudo systemctl start bluetooth').")
        sys.exit(1)

    adapter_props = dbus.Interface(bus.get_object(BLUEZ_SERVICE_NAME, adapter), DBUS_PROP_IFACE)
    adapter_props.Set('org.bluez.Adapter1', 'Powered', dbus.Boolean(True))

    service_manager = dbus.Interface(bus.get_object(BLUEZ_SERVICE_NAME, adapter), GATT_MANAGER_IFACE)
    ad_manager = dbus.Interface(bus.get_object(BLUEZ_SERVICE_NAME, adapter), LE_ADVERTISING_MANAGER_IFACE)

    app = Application(bus)
    cp_service = CyclingPowerService(bus, 0)
    app.add_service(cp_service)
    app.add_service(DeviceInfoService(bus, 1))

    ad = CyclingPowerAdvertisement(bus, 0)

    loop = GLib.MainLoop()

    print("[*] Starte BLE Cycling Power Simulator (Build 20:04 Uhr)...")
    print("[*] Registriere GATT Application (Cycling Power 0x1818 & Device Info 0x180A)...")
    service_manager.RegisterApplication(app.get_path(), {},
                                        reply_handler=lambda: print("[OK] GATT Service registriert."),
                                        error_handler=lambda e: print(f"[FEHLER] GATT Fehler: {e}"))

    print(f"[*] Starte BLE Advertising als '{ad.local_name}' mit UUID {ad.service_uuids}...")
    ad_manager.RegisterAdvertisement(ad.get_path(), {},
                                     reply_handler=lambda: print("[OK] Advertising aktiv. Gerät ist jetzt sichtbar!"),
                                     error_handler=lambda e: print(f"[FEHLER] Advertising Fehler: {e}"))

    # Starte interaktive Tastatureingabe in separatem Thread
    input_thread = threading.Thread(target=keyboard_input_loop, args=(cp_service.measurement_char, loop), daemon=True)
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
