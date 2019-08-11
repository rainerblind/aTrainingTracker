package com.atrainingtracker.banalservice.Sensor.formater;

public class CadenceFormater implements MyFormater<Number> {
    @Override
    public String format(Number value) {
        if (value == null) {
            return "--";
        } else {
            // return String.format("%.0f", value);
            return Integer.toString(value.intValue());
        }
    }
}
