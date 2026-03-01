package com.kalkitech.scheduled.util;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class TimeAlign {
    private TimeAlign() {}

    /**
     * Aligns the given timestamp down to the nearest 5-minute boundary.
     */
    public static ZonedDateTime alignedTo5Min(ZonedDateTime dt) {
        ZonedDateTime base = dt.withSecond(0).withNano(0);
        int m = base.getMinute();
        int aligned = (m / 5) * 5;
        return base.withMinute(aligned);
    }

    /**
     * Aligns the given timestamp down to the nearest 30-minute boundary.
     */
    public static ZonedDateTime alignedTo30Min(ZonedDateTime dt) {
        ZonedDateTime base = dt.withSecond(0).withNano(0);
        int m = base.getMinute();
        int aligned = (m / 30) * 30;
        return base.withMinute(aligned);
    }

    public static ZonedDateTime nowAlignedTo5Min(ZoneId zone) {
        return alignedTo5Min(ZonedDateTime.now(zone));
    }

    public static ZonedDateTime nowAlignedTo30Min(ZoneId zone) {
        return alignedTo30Min(ZonedDateTime.now(zone));
    }
}
