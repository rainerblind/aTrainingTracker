package com.atrainingtracker.banalservice.Sensor.formater;

// import java.text.NumberFormat;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.trainingtracker.TrainingApplication;


public class DistanceFormater implements MyFormater<Number> {

    @Override
    public String format(Number distance_m) {

        if (distance_m == null) {
            return "--";
        }

        switch (TrainingApplication.getUnit()) {
            case METRIC:
                return String.format("%.2f", distance_m.doubleValue() / 1000);
            // return NumberFormat.getInstance().format(distance_m.doubleValue()/1000);
            case IMPERIAL:
                return String.format("%.2f", distance_m.doubleValue() / BANALService.METER_PER_MILE);
            // return NumberFormat.getInstance().format(distance_m.doubleValue()/BANALService.METER_PER_MILE);
            default:
                return "--";
        }
    }

    public String format_with_units(Number distance_m) {

        if (distance_m == null) {
            return "--";
        }

        switch (TrainingApplication.getUnit()) {
            case METRIC:
                if (distance_m.intValue() < 1000) {
                    return String.format("%d m", distance_m.intValue());
                } else {
                    return String.format("%.2f km", distance_m.doubleValue() / 1000);
                }
                // return NumberFormat.getInstance().format(distance_m.doubleValue()/1000);
            case IMPERIAL:
                return String.format("%.2f mile", distance_m.doubleValue() / BANALService.METER_PER_MILE);
            // return NumberFormat.getInstance().format(distance_m.doubleValue()/BANALService.METER_PER_MILE);
            default:
                return "--";
        }
    }

    public String format_3(Number distance_m) {

        if (distance_m == null) {
            return "--";
        }

        switch (TrainingApplication.getUnit()) {
            case METRIC:
                return String.format("%.3f", distance_m.doubleValue() / 1000);
            // return NumberFormat.getInstance().format(distance_m.doubleValue()/1000);
            case IMPERIAL:
                return String.format("%.3f", distance_m.doubleValue() / BANALService.METER_PER_MILE);
            // return NumberFormat.getInstance().format(distance_m.doubleValue()/BANALService.METER_PER_MILE);
            default:
                return "--";
        }
    }
}
