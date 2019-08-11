package com.atrainingtracker.banalservice.Devices.bluetooth_le;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Devices.DeviceType;
import com.atrainingtracker.banalservice.Devices.MyRemoteDevice;
import com.atrainingtracker.banalservice.Protocol;
import com.atrainingtracker.banalservice.Sensor.MySensorManager;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;

import java.util.LinkedList;
import java.util.Queue;

//import de.rainerblind.MyAntPlusApp;

// TODO: where/when to register and unregister the sensors

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public abstract class MyBTLEDevice extends MyRemoteDevice {
    private static final boolean DEBUG = BANALService.DEBUG & false;
    private static final int SEARCH_PERIOD = 10 * 1000; // 10 seconds (same as for ANT+)
    private static final int READ_BATTERY_PERCENTAGE_PERIOD = 5 * 60 * 1000; // 5 minutes
    protected String mAddress; // the MAC Address
    // private static final int READ_BATTERY_PERCENTAGE_PERIOD = 10 * 1000; // 10 seconds (just for testing)
    protected BluetoothGatt mBluetoothGatt;
    protected Queue<BluetoothGattCharacteristic> mReadCharacteristicQueue = new LinkedList<BluetoothGattCharacteristic>();
    protected State mState = State.DISCONNECTED;
    Handler mHandler;
    private String TAG = "MyBTLEDevice";
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (DEBUG)
                Log.i(TAG, "onConnectionStateChange: status=" + status + ", newState=" + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (DEBUG) Log.i(TAG, "Connected to GATT server");

                mState = State.CONNECTED_TO_GATT;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBluetoothGatt.discoverServices();
                        notifyStopSearching(true);  // we found the device
                    }
                });
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                mState = State.DISCONNECTED;
                unregisterSensors();

                disconnectFromGatt(); // correct?

                startSearching();

                // startSearching(); // Missing in action!
            } else {
                Log.d(TAG, "unhandled state: " + newState);
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            Log.i(TAG, "onServiceDiscovered");

            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    BluetoothGattService btGattService;
                    BluetoothGattCharacteristic btGattChar;

                    btGattService = gatt.getService(BluetoothConstants.UUID_SERVICE_BATTERY);
                    if (btGattService != null) { // ok, the battery service is available
                        btGattChar = btGattService.getCharacteristic(BluetoothConstants.UUID_CHARACTERISTIC_BATTERY_LEVEL);
                        if (btGattChar != null) {
                            Log.i(TAG, "go battery service, request to read battery characteristic");
                            mReadCharacteristicQueue.add(btGattChar);
                        } else {
                            Log.d(TAG, "WTF: btGattChar == null for battery characteristic");
                        }
                    }
                    readNextCharacteristic();
                }
            });
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (DEBUG) Log.i(TAG, "onCharacteristicRead: " + characteristic.getUuid());

            characteristicUpdate(gatt, characteristic);
            readNextCharacteristic();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            characteristicUpdate(gatt, characteristic);
        }
    };

    /**
     * constructor
     */
    public MyBTLEDevice(Context context, MySensorManager mySensorManager, DeviceType deviceType, long deviceId, String address) {
        super(context, mySensorManager, deviceType, deviceId);

        mAddress = address;
        mHandler = new Handler(mContext.getMainLooper());
    }

    @Override
    public Protocol getProtocol() {
        return Protocol.BLUETOOTH_LE;
    }


    @Override
    public void startSearching() {
        if (DEBUG) Log.i(TAG, "startSearching()");
        notifyStartSearching();

        mState = State.SEARCHING;

        //stop searching after some time
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (DEBUG) Log.i(TAG, "search time out");
                if (!isReceivingData()) {
                    if (DEBUG) Log.i(TAG, "device is not receiving data, so this search failed");
                    disconnectFromGatt();
                    notifyStopSearching(false);  // we did not find the device
                }
            }
        }, SEARCH_PERIOD);

        final BluetoothDevice device = ((BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter().getRemoteDevice(mAddress);

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (DEBUG) Log.i(TAG, "trying to connect gatt");
                mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback);
            }
        });
    }

    private void disconnectFromGatt() {
        if (DEBUG) Log.i(TAG, "disconnectFromGatt()");

        if (mBluetoothGatt != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mBluetoothGatt.disconnect();
                    mBluetoothGatt.close();
                }
            });
        }
    }

    @Override
    public boolean isReceivingData() {
        return mState == State.CONNECTED_WITH_SERVICE;
    }


    @Override
    public void shutDown() {
        if (DEBUG) Log.i(TAG, "shutDown()");

        mHandler.removeCallbacksAndMessages(null);

        disconnectFromGatt();

        super.shutDown();
    }

    protected abstract void measurementCharacteristicUpdate(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);

    private void characteristicUpdate(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
        if (DEBUG) Log.i(TAG, "characteristicUpdate");

        if (BluetoothConstants.getCharacteristicUUID(getDeviceType()).equals(characteristic.getUuid())) {

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    measurementCharacteristicUpdate(gatt, characteristic);
                }
            });
        }
        if (BluetoothConstants.UUID_CHARACTERISTIC_BATTERY_LEVEL.equals(characteristic.getUuid())) {
            int batteryPercentage = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            if (DEBUG) Log.i(TAG, "got battery percentage: " + batteryPercentage);
            DevicesDatabaseManager.setBatteryPercentage(getDeviceId(), batteryPercentage);

            //reread after some time
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mReadCharacteristicQueue.add(characteristic);
                    readNextCharacteristic();
                }
            }, READ_BATTERY_PERCENTAGE_PERIOD);
        }
        // else {
        //	Log.d(TAG, "TODO: implement characteristicUpdate for other services");
        // }
    }

    protected void readNextCharacteristic() {
        if (DEBUG) Log.i(TAG, "readNextCharacteristic");

        if (!mReadCharacteristicQueue.isEmpty()) {
            if (DEBUG) Log.i(TAG, "queue is not empty, so we read the next characteristic");

            final BluetoothGattCharacteristic gattChar = mReadCharacteristicQueue.poll();
            if (gattChar == null) Log.d(TAG, "WTF: gattChar == null");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mBluetoothGatt.readCharacteristic(gattChar);
                }
            });
        } else { // queue is empty
            if (!sensorsRegistered()) {  // but sensors are not yet registered
                mState = State.CONNECTED_WITH_SERVICE;
                setLastActive();
                registerSensors();

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        BluetoothGattService btGattService = mBluetoothGatt.getService(BluetoothConstants.getServiceUUID(getDeviceType()));
                        if (btGattService == null) {
                            Log.d(TAG, "WTF, we expected a service here!");
                            return;
                        }
                        BluetoothGattCharacteristic btGattChar = btGattService.getCharacteristic(BluetoothConstants.getCharacteristicUUID(getDeviceType()));
                        mBluetoothGatt.setCharacteristicNotification(btGattChar, true);

                        BluetoothGattDescriptor descriptor = btGattChar.getDescriptor(BluetoothConstants.UUID_CHARACTERISTIC_CLIENT_CONFIG);
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        mBluetoothGatt.writeDescriptor(descriptor);
                    }
                });
            }
        }
    }

    protected enum State {DISCONNECTED, SEARCHING, CONNECTED_TO_GATT, CONNECTED_WITH_SERVICE}
}
