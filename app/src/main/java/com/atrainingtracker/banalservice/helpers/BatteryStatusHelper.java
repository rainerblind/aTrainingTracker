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

package com.atrainingtracker.banalservice.helpers;

import com.atrainingtracker.R;
import com.dsi.ant.plugins.antplus.pcc.defines.BatteryStatus;

public class BatteryStatusHelper {

    @Deprecated // use fun within DeviceUiData instead.
    public static int getBatteryStatusImageId(int batteryStatusPercentage) {
        if (batteryStatusPercentage >= 80) {
            return R.drawable.stat_sys_battery_80;
        } else if (batteryStatusPercentage >= 60) {
            return R.drawable.stat_sys_battery_60;
        } else if (batteryStatusPercentage >= 40) {
            return R.drawable.stat_sys_battery_40;
        } else if (batteryStatusPercentage >= 20) {
            return R.drawable.stat_sys_battery_20;
        } else if (batteryStatusPercentage > 0) {
            return R.drawable.stat_sys_battery_10;
        } else {
            return R.drawable.stat_sys_battery_unknown;
        }
    }

    public static int getBatteryStatusNameId(int batteryStatusPercentage) {
        if (batteryStatusPercentage >= 80) {
            return R.string.battery_level_new;
        } else if (batteryStatusPercentage >= 60) {
            return R.string.battery_level_good;
        } else if (batteryStatusPercentage >= 40) {
            return R.string.battery_level_ok;
        } else if (batteryStatusPercentage >= 20) {
            return R.string.battery_level_low;
        } else if (batteryStatusPercentage > 0) {
            return R.string.battery_level_critical;
        } else {
            return R.string.battery_level_unknown;
        }
    }

    public static int getBatterPercentage(BatteryStatus batteryStatus) {
        switch (batteryStatus) {
            case NEW:
                return 80;
            case GOOD:
                return 60;
            case OK:
                return 40;
            case LOW:
                return 20;
            case CRITICAL:
                return 10;
            default:
                return -1;
        }
    }

}
