package hu.detox.utils;

import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import hu.detox.parsers.AmountCalculator;
import hu.detox.utils.strings.StringConverter;
import hu.detox.utils.strings.StringUtils;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.ArrayUtils;
import org.jscience.physics.amount.Amount;
import org.jspecify.annotations.NonNull;
import org.springframework.util.CollectionUtils;

import javax.measure.quantity.Duration;
import javax.measure.unit.SI;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

import static hu.detox.spring.DetoxConfig.prop;

public class TimeUtils {
    public static final Timestamp DETOX_EPOCH = Timestamp.valueOf("1900-10-05 00:00:00");
    private static final List<TimeZoneMapping> ZONEMAPPINGS = new ArrayList<TimeZoneMapping>();
    private static final List<TimeZoneWithDisplayNames> TIME_ZONES = new ArrayList<TimeZoneWithDisplayNames>();
    private static long start;

    public static final class TimeZoneMapping {
        private final String windowsStandardName;
        private final String olsonName;
        private final String windowsDisplayName;

        private TimeZoneMapping(final String windowsStandardName, final String olsonName, final String windowsDisplayName) {
            this.windowsStandardName = windowsStandardName;
            this.olsonName = olsonName;
            this.windowsDisplayName = windowsDisplayName;
        }

        public String getOlsonName() {
            return this.olsonName;
        }

        public String getWindowsDisplayName() {
            return this.windowsDisplayName;
        }

        public String getWindowsStandardName() {
            return this.windowsStandardName;
        }
    }

    public static final class TimeZoneWithDisplayNames {
        private final TimeZone timeZone;
        private final String displayName;
        private final String standardDisplayName;

        private TimeZoneWithDisplayNames(final TimeZone timeZone, final String displayName, final String standardDisplayName) {
            this.timeZone = timeZone;
            this.displayName = displayName;
            this.standardDisplayName = standardDisplayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public String getStandardDisplayName() {
            return this.standardDisplayName;
        }

        public TimeZone getTimeZone() {
            return this.timeZone;
        }
    }


    static { // NOCH This is imported class
        ZONEMAPPINGS.add(new TimeZoneMapping("Afghanistan Standard Time", "Asia/Kabul", "(GMT +04:30) Kabul"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Alaskan Standard Time", "America/Anchorage", "(GMT -09:00) Alaska"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Arab Standard Time", "Asia/Riyadh", "(GMT +03:00) Kuwait, Riyadh"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Arabian Standard Time", "Asia/Dubai", "(GMT +04:00) Abu Dhabi, Muscat"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Arabic Standard Time", "Asia/Baghdad", "(GMT +03:00) Baghdad"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Argentina Standard Time", "America/Buenos_Aires", "(GMT -03:00) Buenos Aires"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Atlantic Standard Time", "America/Halifax", "(GMT -04:00) Atlantic Time (Canada)"));
        ZONEMAPPINGS.add(new TimeZoneMapping("AUS Central Standard Time", "Australia/Darwin", "(GMT +09:30) Darwin"));
        ZONEMAPPINGS.add(new TimeZoneMapping("AUS Eastern Standard Time", "Australia/Sydney", "(GMT +10:00) Canberra, Melbourne, Sydney"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Azerbaijan Standard Time", "Asia/Baku", "(GMT +04:00) Baku"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Azores Standard Time", "Atlantic/Azores", "(GMT -01:00) Azores"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Bangladesh Standard Time", "Asia/Dhaka", "(GMT +06:00) Dhaka"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Canada Central Standard Time", "America/Regina", "(GMT -06:00) Saskatchewan"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Cape Verde Standard Time", "Atlantic/Cape_Verde", "(GMT -01:00) Cape Verde Is."));
        ZONEMAPPINGS.add(new TimeZoneMapping("Caucasus Standard Time", "Asia/Yerevan", "(GMT +04:00) Yerevan"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Cen. Australia Standard Time", "Australia/Adelaide", "(GMT +09:30) Adelaide"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Central America Standard Time", "America/Guatemala", "(GMT -06:00) Central America"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Central Asia Standard Time", "Asia/Almaty", "(GMT +06:00) Astana"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Central Brazilian Standard Time", "America/Cuiaba", "(GMT -04:00) Cuiaba"));
        ZONEMAPPINGS
                .add(new TimeZoneMapping("Central Europe Standard Time", "Europe/Budapest", "(GMT +01:00) Belgrade, Bratislava, Budapest, Ljubljana, Prague"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Central European Standard Time", "Europe/Warsaw", "(GMT +01:00) Sarajevo, Skopje, Warsaw, Zagreb"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Central Pacific Standard Time", "Pacific/Guadalcanal", "(GMT +11:00) Solomon Is., New Caledonia"));
        ZONEMAPPINGS
                .add(new TimeZoneMapping("Central Standard Time (Mexico)", "America/Mexico_City", "(GMT -06:00) Guadalajara, Mexico City, Monterrey"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Central Standard Time", "America/Chicago", "(GMT -06:00) Central Time (US & Canada)"));
        ZONEMAPPINGS.add(new TimeZoneMapping("China Standard Time", "Asia/Shanghai", "(GMT +08:00) Beijing, Chongqing, Hong Kong, Urumqi"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Dateline Standard Time", "Etc/GMT+12", "(GMT -12:00) International Date Line West"));
        ZONEMAPPINGS.add(new TimeZoneMapping("E. Africa Standard Time", "Africa/Nairobi", "(GMT +03:00) Nairobi"));
        ZONEMAPPINGS.add(new TimeZoneMapping("E. Australia Standard Time", "Australia/Brisbane", "(GMT +10:00) Brisbane"));
        ZONEMAPPINGS.add(new TimeZoneMapping("E. Europe Standard Time", "Europe/Minsk", "(GMT +02:00) Minsk"));
        ZONEMAPPINGS.add(new TimeZoneMapping("E. South America Standard Time", "America/Sao_Paulo", "(GMT -03:00) Brasilia"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Eastern Standard Time", "America/New_York", "(GMT -05:00) Eastern Time (US & Canada)"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Egypt Standard Time", "Africa/Cairo", "(GMT +02:00) Cairo"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Ekaterinburg Standard Time", "Asia/Yekaterinburg", "(GMT +05:00) Ekaterinburg"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Fiji Standard Time", "Pacific/Fiji", "(GMT +12:00) Fiji, Marshall Is."));
        ZONEMAPPINGS.add(new TimeZoneMapping("FLE Standard Time", "Europe/Kiev", "(GMT +02:00) Helsinki, Kyiv, Riga, Sofia, Tallinn, Vilnius"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Georgian Standard Time", "Asia/Tbilisi", "(GMT +04:00) Tbilisi"));
        ZONEMAPPINGS.add(new TimeZoneMapping("GMT Standard Time", "Europe/London", "(GMT) Dublin, Edinburgh, Lisbon, London"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Greenland Standard Time", "America/Godthab", "(GMT -03:00) Greenland"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Greenwich Standard Time", "Atlantic/Reykjavik", "(GMT) Monrovia, Reykjavik"));
        ZONEMAPPINGS.add(new TimeZoneMapping("GTB Standard Time", "Europe/Istanbul", "(GMT +02:00) Athens, Bucharest, Istanbul"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Hawaiian Standard Time", "Pacific/Honolulu", "(GMT -10:00) Hawaii"));
        ZONEMAPPINGS.add(new TimeZoneMapping("India Standard Time", "Asia/Calcutta", "(GMT +05:30) Chennai, Kolkata, Mumbai, New Delhi"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Iran Standard Time", "Asia/Tehran", "(GMT +03:30) Tehran"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Israel Standard Time", "Asia/Jerusalem", "(GMT +02:00) Jerusalem"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Jordan Standard Time", "Asia/Amman", "(GMT +02:00) Amman"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Kamchatka Standard Time", "Asia/Kamchatka", "(GMT +12:00) Petropavlovsk-Kamchatsky - Old"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Korea Standard Time", "Asia/Seoul", "(GMT +09:00) Seoul"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Magadan Standard Time", "Asia/Magadan", "(GMT +11:00) Magadan"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Mauritius Standard Time", "Indian/Mauritius", "(GMT +04:00) Port Louis"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Mid-Atlantic Standard Time", "Etc/GMT+2", "(GMT -02:00) Mid-Atlantic"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Middle East Standard Time", "Asia/Beirut", "(GMT +02:00) Beirut"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Montevideo Standard Time", "America/Montevideo", "(GMT -03:00) Montevideo"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Morocco Standard Time", "Africa/Casablanca", "(GMT) Casablanca"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Mountain Standard Time (Mexico)", "America/Chihuahua", "(GMT -07:00) Chihuahua, La Paz, Mazatlan"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Mountain Standard Time", "America/Denver", "(GMT -07:00) Mountain Time (US & Canada)"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Myanmar Standard Time", "Asia/Rangoon", "(GMT +06:30) Yangon (Rangoon)"));
        ZONEMAPPINGS.add(new TimeZoneMapping("N. Central Asia Standard Time", "Asia/Novosibirsk", "(GMT +06:00) Novosibirsk"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Namibia Standard Time", "Africa/Windhoek", "(GMT +02:00) Windhoek"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Nepal Standard Time", "Asia/Katmandu", "(GMT +05:45) Kathmandu"));
        ZONEMAPPINGS.add(new TimeZoneMapping("New Zealand Standard Time", "Pacific/Auckland", "(GMT +12:00) Auckland, Wellington"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Newfoundland Standard Time", "America/St_Johns", "(GMT -03:30) Newfoundland"));
        ZONEMAPPINGS.add(new TimeZoneMapping("North Asia East Standard Time", "Asia/Irkutsk", "(GMT +08:00) Irkutsk"));
        ZONEMAPPINGS.add(new TimeZoneMapping("North Asia Standard Time", "Asia/Krasnoyarsk", "(GMT +07:00) Krasnoyarsk"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Pacific SA Standard Time", "America/Santiago", "(GMT -04:00) Santiago"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Pacific Standard Time (Mexico)", "America/Tijuana", "(GMT -08:00) Baja California"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Pacific Standard Time", "America/Los_Angeles", "(GMT -08:00) Pacific Time (US & Canada)"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Pakistan Standard Time", "Asia/Karachi", "(GMT +05:00) Islamabad, Karachi"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Paraguay Standard Time", "America/Asuncion", "(GMT -04:00) Asuncion"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Romance Standard Time", "Europe/Paris", "(GMT +01:00) Brussels, Copenhagen, Madrid, Paris"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Russian Standard Time", "Europe/Moscow", "(GMT +03:00) Moscow, St. Petersburg, Volgograd"));
        ZONEMAPPINGS.add(new TimeZoneMapping("SA Eastern Standard Time", "America/Cayenne", "(GMT -03:00) Cayenne, Fortaleza"));
        ZONEMAPPINGS.add(new TimeZoneMapping("SA Pacific Standard Time", "America/Bogota", "(GMT -05:00) Bogota, Lima, Quito"));
        ZONEMAPPINGS.add(new TimeZoneMapping("SA Western Standard Time", "America/La_Paz", "(GMT -04:00) Georgetown, La Paz, Manaus, San Juan"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Samoa Standard Time", "Pacific/Apia", "(GMT -11:00) Samoa"));
        ZONEMAPPINGS.add(new TimeZoneMapping("SE Asia Standard Time", "Asia/Bangkok", "(GMT +07:00) Bangkok, Hanoi, Jakarta"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Singapore Standard Time", "Asia/Singapore", "(GMT +08:00) Kuala Lumpur, Singapore"));
        ZONEMAPPINGS.add(new TimeZoneMapping("South Africa Standard Time", "Africa/Johannesburg", "(GMT +02:00) Harare, Pretoria"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Sri Lanka Standard Time", "Asia/Colombo", "(GMT +05:30) Sri Jayawardenepura"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Syria Standard Time", "Asia/Damascus", "(GMT +02:00) Damascus"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Taipei Standard Time", "Asia/Taipei", "(GMT +08:00) Taipei"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Tasmania Standard Time", "Australia/Hobart", "(GMT +10:00) Hobart"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Tokyo Standard Time", "Asia/Tokyo", "(GMT +09:00) Osaka, Sapporo, Tokyo"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Tonga Standard Time", "Pacific/Tongatapu", "(GMT +13:00) Nuku'alofa"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Ulaanbaatar Standard Time", "Asia/Ulaanbaatar", "(GMT +08:00) Ulaanbaatar"));
        ZONEMAPPINGS.add(new TimeZoneMapping("US Eastern Standard Time", "America/Indianapolis", "(GMT -05:00) Indiana (East)"));
        ZONEMAPPINGS.add(new TimeZoneMapping("US Mountain Standard Time", "America/Phoenix", "(GMT -07:00) Arizona"));
        ZONEMAPPINGS.add(new TimeZoneMapping("GMT ", "Etc/GMT", "(GMT) Coordinated Universal Time"));
        ZONEMAPPINGS.add(new TimeZoneMapping("GMT +12", "Etc/GMT-12", "(GMT +12:00) Coordinated Universal Time+12"));
        ZONEMAPPINGS.add(new TimeZoneMapping("GMT -02", "Etc/GMT+2", "(GMT -02:00) Coordinated Universal Time-02"));
        ZONEMAPPINGS.add(new TimeZoneMapping("GMT -11", "Etc/GMT+11", "(GMT -11:00) Coordinated Universal Time-11"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Venezuela Standard Time", "America/Caracas", "(GMT -04:30) Caracas"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Vladivostok Standard Time", "Asia/Vladivostok", "(GMT +10:00) Vladivostok"));
        ZONEMAPPINGS.add(new TimeZoneMapping("W. Australia Standard Time", "Australia/Perth", "(GMT +08:00) Perth"));
        ZONEMAPPINGS.add(new TimeZoneMapping("W. Central Africa Standard Time", "Africa/Lagos", "(GMT +01:00) West Central Africa"));
        ZONEMAPPINGS
                .add(new TimeZoneMapping("W. Europe Standard Time", "Europe/Berlin", "(GMT +01:00) Amsterdam, Berlin, Bern, Rome, Stockholm, Vienna"));
        ZONEMAPPINGS.add(new TimeZoneMapping("West Asia Standard Time", "Asia/Tashkent", "(GMT +05:00) Tashkent"));
        ZONEMAPPINGS.add(new TimeZoneMapping("West Pacific Standard Time", "Pacific/Port_Moresby", "(GMT +10:00) Guam, Port Moresby"));
        ZONEMAPPINGS.add(new TimeZoneMapping("Yakutsk Standard Time", "Asia/Yakutsk", "(GMT +09:00) Yakutsk"));
    }

    public static @NotNull TimeZone toJavaTimezone(final String tz) {
        TimeZone ret = null;
        for (final TimeZoneMapping map : ZONEMAPPINGS) {
            if (map.getWindowsStandardName().equalsIgnoreCase(tz) || map.getWindowsDisplayName().equalsIgnoreCase(tz)) {
                ret = TimeZone.getTimeZone(map.getOlsonName());
            }
        }
        if (ret == null) ret = TimeZone.getTimeZone(tz);
        return ret;
    }

    static {
        final HashSet<String> availableIdsSet = new HashSet<>(Arrays.asList(TimeZone.getAvailableIDs()));
        for (final TimeZoneMapping zoneMapping : ZONEMAPPINGS) {
            final String id = zoneMapping.getOlsonName();
            if (!availableIdsSet.contains(id)) {
                throw new IllegalStateException("Unknown ID [" + id + "]");
            }
            final TimeZone timeZone = TimeZone.getTimeZone(id);
            TIME_ZONES.add(new TimeZoneWithDisplayNames(timeZone, zoneMapping.getWindowsDisplayName(), zoneMapping.getWindowsStandardName()));
        }
        TIME_ZONES.sort((a, b) -> {
            final int diff = a.getTimeZone().getRawOffset() - b.getTimeZone().getRawOffset();
            if (diff < 0) {
                return -1;
            } else if (diff > 0) {
                return 1;
            } else {
                return a.getDisplayName().compareTo(b.getDisplayName());
            }
        });
        StringConverter.STRINGER.put(TimeZone.class, new StringConverter.Stringer<TimeZone>() {
            @Override
            public TimeZone fromString(final String val) {
                TimeZone ret = TimeZone.getTimeZone(val);
                if (val != null) {
                    ret = toJavaTimezone(val);
                }
                if (ret == null) {
                    ret = TimeZone.getTimeZone(val);
                }
                return ret;
            }

            @Override
            public String toString(final @NonNull TimeZone parObj) {
                return parObj.getID();
            }
        });
        final StringConverter.Stringer<Date> ds = new StringConverter.Stringer<>() {
            private final Parser dateParser = new Parser();

            @Override
            public Date fromString(final String val) {
                Date ret = null;
                if (StringUtils.isEmpty(val)) {
                    return ret;
                }
                try {
                    final long v = Long.parseLong(val);
                    return new Date(v);
                } catch (final NumberFormatException nfe) {
                    try {
                        final LocalDateTime dt = LocalDateTime.parse(val);
                        ret = Date.from(dt.atZone(ZoneId.systemDefault()).toInstant());
                    } catch (final DateTimeParseException ia) {
                        for (int i = 1; i <= 6; i++) {
                            try {
                                ret = StringUtils.parse(i, val);
                            } catch (final java.text.ParseException e1) {
                                // All ok, try the next one
                            }
                        }
                    }
                }
                if (ret == null) {
                    List<DateGroup> groups = dateParser.parse(val);
                    if (CollectionUtils.isEmpty(groups))
                        throw new IllegalArgumentException("I do not get it: '" + val + "'");
                    ret = groups.get(0).getDates().get(0);
                }
                return ret;
            }

            @Override
            public String toString(final @NonNull Date parObj) {
                return new Timestamp(parObj.getTime()).toString();
            }
        };
        StringConverter.STRINGER.put(Calendar.class, new StringConverter.Stringer<Calendar>() {
            @Override
            public Calendar fromString(final String val) {
                final Date d = ds.fromString(val);
                Calendar ret = null;
                if (d != null) {
                    ret = Calendar.getInstance();
                    ret.setTime(d);
                }
                return ret;
            }

            @Override
            public String toString(final @NonNull Calendar parObj) {
                return new Timestamp(parObj.getTimeInMillis()).toString();
            }
        });
        StringConverter.STRINGER.put(Timestamp.class, new StringConverter.Stringer<Timestamp>() {
            @Override
            public Timestamp fromString(final String val) {
                final Date d = ds.fromString(val);
                Timestamp ret = null;
                if (d != null) {
                    ret = new Timestamp(d.getTime());
                }
                return ret;
            }

            @Override
            public String toString(final @NonNull Timestamp parObj) {
                return new Timestamp(parObj.getTime()).toString();
            }
        });
        StringConverter.STRINGER.put(java.sql.Date.class, new StringConverter.Stringer<java.sql.Date>() {
            @Override
            public java.sql.Date fromString(final String val) {
                final Date d = ds.fromString(val);
                java.sql.Date ret = null;
                if (d != null) {
                    ret = new java.sql.Date(d.getTime());
                }
                return ret;
            }

            @Override
            public String toString(final java.sql.@NonNull Date parObj) {
                return ds.toString(parObj);
            }
        });
        StringConverter.STRINGER.put(LocalDate.class, new StringConverter.Stringer<LocalDate>() {
            @Override
            public LocalDate fromString(final String val) {
                final Date d = ds.fromString(val);
                LocalDate ret = null;
                if (d != null) {
                    ret = Instant.ofEpochMilli(d.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
                }
                return ret;
            }

            @Override
            public String toString(final @NonNull LocalDate parObj) {
                return parObj.toString();
            }
        });
        StringConverter.STRINGER.put(LocalDateTime.class, new StringConverter.Stringer<LocalDateTime>() {
            @Override
            public LocalDateTime fromString(final String val) {
                final Date d = ds.fromString(val);
                LocalDateTime ret = null;
                if (d != null) {
                    ret = Instant.ofEpochMilli(d.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
                }
                return ret;
            }

            @Override
            public String toString(final @NonNull LocalDateTime parObj) {
                return parObj.toString();
            }
        });
        StringConverter.STRINGER.put(Date.class, ds);
    }

    private static Date cvtToGmt(final Date date) {
        final TimeZone tz = TimeZone.getDefault();
        Date ret = new Date(date.getTime() - tz.getRawOffset());
        // if we are now in DST, back off by the delta.  Note that we are checking the GMT date, this is the KEY.
        if (tz.inDaylightTime(ret)) {
            final Date dstDate = new Date(ret.getTime() - tz.getDSTSavings());
            // check to make sure we have not crossed back into standard time
            // this happens when we are on the cusp of DST (7pm the day before the change for PDT)
            if (tz.inDaylightTime(dstDate)) {
                ret = dstDate;
            }
        }
        return ret;
    }

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

    public static long toMillis(final String duration) {
        final Amount<Duration> dur = AmountCalculator.INSTANCE.calc(duration);
        final long durm = (long) dur.to(SI.MILLI(SI.SECOND)).getMaximumValue();
        return durm;
    }

    public static long whatIsOldTime(String to) {
        if (StringUtils.isEmpty(to)) {
            to = prop("cache_old");
        }
        return TimeUtils.time() - toMillis(to);
    }

    public static Date date() {
        return new Date(TimeUtils.time());
    }

    public static Instant instant() {
        return Instant.ofEpochMilli(TimeUtils.time());
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
        final String f = sdf.format(TimeUtils.date()).toLowerCase(Locale.ENGLISH);
        return ArrayUtils.indexOf(da, f) != ArrayUtils.INDEX_NOT_FOUND;
    }

    public static long testTime() {
        if (start == 0) start = DETOX_EPOCH.getTime();
        return TimeUtils.start;
    }

    public static long time() {
        if (start > 0) return start++;
        return System.currentTimeMillis();
    }

    private TimeUtils() {
        // Util class
    }
}
