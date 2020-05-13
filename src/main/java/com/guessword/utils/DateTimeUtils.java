package com.guessword.utils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class DateTimeUtils {

    public static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return LocalDateTime.ofInstant(timestamp.toInstant(), ZoneId.of("UTC"));
    }
}
