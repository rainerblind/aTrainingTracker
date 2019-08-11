package com.atrainingtracker.banalservice;

import com.atrainingtracker.R;

public enum Protocol {
    ANT_PLUS(R.drawable.ant_logo),
    BLUETOOTH_LE(R.drawable.logo_protocol_bluetooth),

    ALL(R.drawable.logo),

    SMARTPHONE(R.drawable.ic_phone_android_black_48dp);

    private final int iconId;

    Protocol(int iconId) {
        this.iconId = iconId;
    }

    public int getIconId() {
        return iconId;
    }
}

