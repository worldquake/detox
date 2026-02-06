package hu.detox.utils;

import hu.detox.Main;
import hu.detox.parsers.AmountCalculator;
import org.apache.commons.lang3.ArrayUtils;
import org.jscience.physics.amount.Amount;

import javax.measure.quantity.Duration;
import javax.measure.unit.SI;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class Time {
    public static final Timestamp ZERO = new Timestamp(-2182381200000L);
    public static final Timestamp DETOX_EPOCH = Timestamp.valueOf("1917-07-25 00:00:00");
    private static long start;

    public static Date convertToDate(final Object epoch) {
        if (epoch instanceof Date || epoch == null) {
            return (Date) epoch;
        }
        return Timestamp.from(Instant.ofEpochSecond(((Number) epoch).longValue()));
    }

    public static Object convertToTimezone(Object source, final ZoneId sourceZoneId, final ZoneId targetZoneId) {
        if (source instanceof Timestamp) {
            source = Timestamp.from(((Timestamp) source).toInstant().atZone(targetZoneId).toLocalDateTime().atZone(sourceZoneId).toInstant());
        } else if (source instanceof Date) {
            source = Date.from(((Date) source).toInstant().atZone(targetZoneId).toLocalDateTime().atZone(sourceZoneId).toInstant());
        } else if (source instanceof ZonedDateTime) {
            source = ((ZonedDateTime) source).withZoneSameInstant(targetZoneId);
        } else if (source instanceof Calendar) {
            ((Calendar) source).setTimeZone(TimeZone.getTimeZone(targetZoneId.getId()));
        }
        return source;
    }

    public static Long convertToUnixTime(final Date timestamp) {
        return timestamp != null ? timestamp.toInstant().getEpochSecond() : null;
    }

    public static long toMillis(final String to) {
        final Amount<Duration> dur = AmountCalculator.INSTANCE.calc(to);
        final long durm = (long) dur.to(SI.MILLI(SI.SECOND)).getMaximumValue();
        return durm;
    }

    public static long whatIsOldTime(String to) {
        if (hu.detox.utils.StringUtils.isEmpty(to)) {
            to = Main.prop("cache_old");
        }
        return Time.time() - toMillis(to);
    }

    public static Date date() {
        return new Date(Time.time());
    }

    public static Instant instant() {
        return Instant.ofEpochMilli(Time.time());
    }

    public static boolean isTimeMatching(final String days) {
        if (StringUtils.isBlank(days)) {
            return true;
        }
        String[] da = days.split(";");
        SimpleDateFormat sdf;
        if (da.length == 1) {
            sdf = new SimpleDateFormat("E", Locale.ENGLISH);
        } else {
            sdf = new SimpleDateFormat(da[1], Locale.ENGLISH);
        }
        da = da[0].split(",");
        final String f = sdf.format(Time.date()).toLowerCase(Locale.ENGLISH);
        return ArrayUtils.indexOf(da, f) != ArrayUtils.INDEX_NOT_FOUND;
    }

    public static long startTime() {
        return Time.start;
    }

    public static long time() {
        return System.currentTimeMillis();
    }

    private Time() {
        // Util class
    }
}
