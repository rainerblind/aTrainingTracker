package com.atrainingtracker.banalservice.Sensor.formater;

// import java.text.NumberFormat;

import com.atrainingtracker.trainingtracker.TrainingApplication;

public class SpeedFormater implements MyFormater<Number> {
    @Override
    public String format(Number speed_mps) {
        if (speed_mps == null) {
            return "--";
        }

        double foo = 0;
        switch (TrainingApplication.getUnit()) {
            case METRIC:
                foo = speed_mps.doubleValue() * 3.6;
                break;

            case IMPERIAL:
                foo = speed_mps.doubleValue() * 2.23693629;
                break;
        }
        return String.format("%.1f", foo);
        // return NumberFormat.getInstance().format(foo);
    }

    public String format_with_units(Number speed_mps) {
        String units = "";
        switch (TrainingApplication.getUnit()) {
            case METRIC:
                units = "km/h";
                break;
            case IMPERIAL:
                units = "mile/h";
                break;
        }

        return format(speed_mps) + " " + units;
    }

}
