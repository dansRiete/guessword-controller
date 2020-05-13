package com.oleksii.kuzko.dao;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * @author The Weather Company, An IBM Business
 */
public class DateTimeUtils {

    public static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return LocalDateTime.ofInstant(timestamp.toInstant(), ZoneId.of("UTC"));
    }
}
