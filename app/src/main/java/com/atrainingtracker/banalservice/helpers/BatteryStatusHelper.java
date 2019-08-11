package com.atrainingtracker.banalservice.helpers;

import com.atrainingtracker.R;
import com.dsi.ant.plugins.antplus.pcc.defines.BatteryStatus;

public class BatteryStatusHelper {
    @Deprecated
    public static int getBatterStatusImageId(BatteryStatus batteryStatus) {
        switch (batteryStatus) {
            case NEW:
                return R.drawable.stat_sys_battery_80;  // TODO: better one?
            case GOOD:
                return R.drawable.stat_sys_battery_60;
            case OK:
                return R.drawable.stat_sys_battery_40;
            case LOW:
                return R.drawable.stat_sys_battery_20;
            case CRITICAL:
                return R.drawable.stat_sys_battery_10;
            default:
                return R.drawable.stat_sys_battery_unknown;
        }
    }

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
