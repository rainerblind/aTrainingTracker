package com.atrainingtracker.banalservice.Sensor.formater;

// import java.text.NumberFormat;

public class DefaultStringFormater implements MyFormater<String> {
    @Override
    public String format(String value) {
        return value;
    }

}
