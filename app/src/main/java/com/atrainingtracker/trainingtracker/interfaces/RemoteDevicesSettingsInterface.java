package com.atrainingtracker.trainingtracker.interfaces;

import com.atrainingtracker.banalservice.Protocol;

public interface RemoteDevicesSettingsInterface {
    void startPairing(Protocol protocol);
    // public void showMyRemoteDevices(Protocol protocol);
    // public void startNewSearch();

    void enableBluetoothRequest();
}
