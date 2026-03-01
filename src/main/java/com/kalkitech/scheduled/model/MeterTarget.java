package com.kalkitech.scheduled.model;

/**
 * A single meter target (device) for which commands should be sent.
 */
public record MeterTarget(String meterNumber, String meterMake, String ip) {
}
