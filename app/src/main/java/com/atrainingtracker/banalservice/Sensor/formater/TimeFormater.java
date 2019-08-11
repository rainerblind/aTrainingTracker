package com.atrainingtracker.banalservice.Sensor.formater;

import java.util.concurrent.TimeUnit;

public class TimeFormater implements MyFormater<Number> {
    @Override
    public String format(Number value) {
        if (value == null) {
            return "--:--:--";
        } else {
            // return seconds2StringFormat.format(new Date(value.intValue() * 1000));
            int s = value.intValue();
            return String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60));
        }
    }

    public String format_with_units(Number value) {
        if (value == null) {
            return "--:--:--";
        } else {
            long seconds = value.longValue();
            if (seconds < 60) {
                return String.format("%02d sec", seconds);
            } else if (seconds < 60 * 60) {
                long minutes = TimeUnit.SECONDS.toMinutes(seconds);
                seconds = seconds - TimeUnit.MINUTES.toSeconds(minutes);

                return String.format("%02d:%02d min", minutes, seconds);
            } else {
                long hours = TimeUnit.SECONDS.toHours(seconds);
                long minutes = TimeUnit.SECONDS.toMinutes(seconds) - TimeUnit.HOURS.toMinutes(hours);
                seconds = seconds - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.MINUTES.toSeconds(minutes);

                return String.format("02d:%02d:%02d h", hours, minutes, seconds);
            }
        }
    }
}
