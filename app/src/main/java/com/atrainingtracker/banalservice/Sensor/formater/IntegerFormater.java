package com.atrainingtracker.banalservice.Sensor.formater;

public class IntegerFormater implements MyFormater<Number> {

    @Override
    public String format(Number value) {
        if (value == null) {
            return "--";
        } else {
            return value.intValue() + "";
        }
    }
}
