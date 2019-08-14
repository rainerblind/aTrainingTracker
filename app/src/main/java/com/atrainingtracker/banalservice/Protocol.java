/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (C) 2011 - 2019 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

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

