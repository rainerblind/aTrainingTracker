package com.atrainingtracker.banalservice.Sensor.formater;

public interface MyFormater<T> {
    // TODO: add units???
    String format(T value);
}