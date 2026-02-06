package hu.detox.utils;

import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import hu.detox.Main;
import hu.detox.parsers.AmountCalculator;
import hu.detox.utils.url.URL;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.spi.StandardLevel;
import org.jscience.physics.amount.Amount;
import org.springframework.core.convert.ConversionException;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

public class StringConverter {
    public static class Stringer<T> {
        protected T fromString(final String parStr) {
            return (T) parStr;
        }

        protected String toString(final T parObj) {
            return String.valueOf(parObj);
        }
    }

    public static final NumberFormat FORMAT = new DecimalFormat("#.#");

    /**
     * This instance is not thread safe, a change in the instance will affect every conversion!
     */
    public static final StringConverter INSTANCE = new StringConverter();
    public static final Map<Class<?>, Stringer<?>> STRINGER = new LinkedHashMap<Class<?>, Stringer<?>>();

    static {
        final Stringer<Number> ns = new Stringer<Number>() {
            @Override
            protected Number fromString(final String parStr) {
                if (StringUtils.isEmpty(parStr)) {
                    return null;
                }
                Number ret;
                try {
                    ret = Double.valueOf(parStr);
                } catch (final NumberFormatException ex) {
                    final ParsePosition ppos = new ParsePosition(0);
                    ret = NumberFormat.getInstance().parse(parStr, ppos);
                    if (ppos.getIndex() != parStr.length() || ppos.getErrorIndex() >= 0) {
                        throw new IllegalArgumentException("Can not parse " + parStr + " at " + ppos + " as number");
                    }
                }
                return ret;
            }

            @Override
            protected String toString(final Number parObj) {
                return NumberFormat.getInstance().format(parObj);
            }
        };
        StringConverter.STRINGER.put(Number.class, ns);
        StringConverter.STRINGER.put(Integer.class, new Stringer<Number>() {
            @Override
            protected Integer fromString(final String parStr) {
                final Number n = ns.fromString(parStr);
                return n == null ? null : n.intValue();
            }
        });
        StringConverter.STRINGER.put(Level.class, new Stringer<Level>() {
            @SuppressWarnings("deprecation")
            @Override
            protected Level fromString(final String parStr) {
                try {
                    final String[] levelDetails = parStr.split("|");
                    final int lev = Integer.parseInt(levelDetails[1]);
                    Level l = Level.toLevel(levelDetails[0], null);
                    if (l == null) {
                        for (final StandardLevel p : StandardLevel.values()) {
                            if (p.intLevel() == lev) {
                                l = Level.toLevel(p.name());
                                break;
                            }
                        }
                    }
                    return l;
                } catch (final NumberFormatException ex) {
                    return Level.toLevel(parStr, Level.OFF);
                }
            }
        });
        StringConverter.STRINGER.put(java.net.URL.class, new Stringer<java.net.URL>() {
            @Override
            protected java.net.URL fromString(final String parStr) {
                final URL url = URL.valueOf(parStr);
                url.setEncode(SystemUtils.UTF8CS);
                return url.toURL();
            }
        });
        StringConverter.STRINGER.put(Float.class, new Stringer<Float>() {
            @Override
            protected Float fromString(final String parStr) {
                final Number n = ns.fromString(parStr);
                return n == null ? null : n.floatValue();
            }
        });
        StringConverter.STRINGER.put(Double.class, new Stringer<Double>() {
            @Override
            protected Double fromString(final String parStr) {
                final Number n = ns.fromString(parStr);
                return n == null ? null : n.doubleValue();
            }
        });
        StringConverter.STRINGER.put(Pattern.class, new Stringer<Pattern>() {
            @Override
            protected Pattern fromString(final String parStr) {
                return StringUtils.isExplicitNull(parStr) ? null : Pattern.compile(parStr);
            }

            @Override
            protected String toString(final Pattern parObj) {
                return parObj.pattern();
            }
        });
        StringConverter.STRINGER.put(byte[].class, new Stringer<byte[]>() {
            @Override
            protected byte[] fromString(final String parStr) {
                if (parStr == null) {
                    return null;
                }
                try {
                    return Hex.decodeHex(parStr.toCharArray());
                } catch (final DecoderException e) {
                    throw new IllegalArgumentException("Not a Hex: " + parStr, e);
                }
            }

            @Override
            protected String toString(final byte[] parObj) {
                return Hex.encodeHexString(parObj);
            }
        });
        StringConverter.STRINGER.put(Byte.class, new Stringer<Byte>() {
            @Override
            protected Byte fromString(final String parStr) {
                final Number n = ns.fromString(parStr);
                return n == null ? null : n.byteValue();
            }
        });
        StringConverter.STRINGER.put(Short.class, new Stringer<Short>() {
            @Override
            protected Short fromString(final String parStr) {
                final Number n = ns.fromString(parStr);
                return n == null ? null : n.shortValue();
            }
        });
        StringConverter.STRINGER.put(Long.class, new Stringer<Number>() {
            @Override
            protected Long fromString(final String parStr) {
                final Number n = ns.fromString(parStr);
                return n == null ? null : n.longValue();
            }
        });
        StringConverter.STRINGER.put(BigDecimal.class, new Stringer<BigDecimal>() {
            @Override
            protected BigDecimal fromString(final String parStr) {
                BigDecimal ret;
                if (StringUtils.isEmpty(parStr)) {
                    return null;
                }
                try {
                    ret = new BigDecimal(parStr);
                } catch (final NumberFormatException nfe) {
                    final Number n = ns.fromString(parStr);
                    ret = new BigDecimal(n instanceof Long ? (Long) n : (Double) n);
                }
                return ret;
            }
        });
        final Stringer<Date> ds = new Stringer<>() {
            private final Parser dateParser = new Parser();

            @Override
            protected Date fromString(final String val) {
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
            protected String toString(final Date parObj) {
                return new Timestamp(parObj.getTime()).toString();
            }
        };
        StringConverter.STRINGER.put(Calendar.class, new Stringer<Calendar>() {
            @Override
            protected Calendar fromString(final String val) {
                final Date d = ds.fromString(val);
                Calendar ret = null;
                if (d != null) {
                    ret = Calendar.getInstance();
                    ret.setTime(d);
                }
                return ret;
            }

            @Override
            protected String toString(final Calendar parObj) {
                return new Timestamp(parObj.getTimeInMillis()).toString();
            }
        });
        StringConverter.STRINGER.put(Boolean.class, new Stringer<Boolean>() {
            @Override
            protected Boolean fromString(final String val) {
                if (StringUtils.isEmpty(val)) {
                    return null;
                }
                try {
                    final double d = Double.parseDouble(val);
                    return d != 0;
                } catch (final NumberFormatException nfe) {
                    boolean bret = Boolean.parseBoolean(val) || "t".equalsIgnoreCase(val) || "y".equalsIgnoreCase(val) || "on".equalsIgnoreCase(val)
                            || "yes".equals(val);
                    if (bret) {
                        return bret;
                    }
                    bret = "n".equalsIgnoreCase(val) || "f".equalsIgnoreCase(val) || "no".equalsIgnoreCase(val) || "false".equalsIgnoreCase(val);
                    if (bret) {
                        return !bret;
                    }
                }
                throw new IllegalArgumentException("Unrecognized boolean '" + val + "'");
            }

            @Override
            protected String toString(final Boolean parObj) {
                return Boolean.toString(parObj);
            }
        });
        StringConverter.STRINGER.put(Timestamp.class, new Stringer<Timestamp>() {
            @Override
            protected Timestamp fromString(final String val) {
                final Date d = ds.fromString(val);
                Timestamp ret = null;
                if (d != null) {
                    ret = new Timestamp(d.getTime());
                }
                return ret;
            }

            @Override
            protected String toString(final Timestamp parObj) {
                return new Timestamp(parObj.getTime()).toString();
            }
        });
        StringConverter.STRINGER.put(java.sql.Date.class, new Stringer<java.sql.Date>() {
            @Override
            protected java.sql.Date fromString(final String val) {
                final Date d = ds.fromString(val);
                java.sql.Date ret = null;
                if (d != null) {
                    ret = new java.sql.Date(d.getTime());
                }
                return ret;
            }

            @Override
            protected String toString(final java.sql.Date parObj) {
                return ds.toString(parObj);
            }
        });
        StringConverter.STRINGER.put(LocalDate.class, new Stringer<LocalDate>() {
            @Override
            protected LocalDate fromString(final String val) {
                final Date d = ds.fromString(val);
                LocalDate ret = null;
                if (d != null) {
                    ret = Instant.ofEpochMilli(d.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
                }
                return ret;
            }

            @Override
            protected String toString(final LocalDate parObj) {
                return parObj.toString();
            }
        });
        StringConverter.STRINGER.put(LocalDateTime.class, new Stringer<LocalDateTime>() {
            @Override
            protected LocalDateTime fromString(final String val) {
                final Date d = ds.fromString(val);
                LocalDateTime ret = null;
                if (d != null) {
                    ret = Instant.ofEpochMilli(d.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
                }
                return ret;
            }

            @Override
            protected String toString(final LocalDateTime parObj) {
                return parObj.toString();
            }
        });
        StringConverter.STRINGER.put(LocalDateTime.class, new Stringer<LocalDateTime>() {
            @Override
            protected LocalDateTime fromString(final String val) {
                final Date d = ds.fromString(val);
                return d == null ? null : d.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
            }

            @Override
            protected String toString(final LocalDateTime parObj) {
                return parObj.toString();
            }
        });
        StringConverter.STRINGER.put(Date.class, ds);
        StringConverter.STRINGER.put(Amount.class, new Stringer<Amount<?>>() {
            @Override
            protected Amount<?> fromString(final String parStr) {
                return StringUtils.isEmpty(parStr) ? null : AmountCalculator.INSTANCE.calc(parStr);
            }

            @Override
            protected String toString(final Amount<?> parObj) {
                final double est = parObj.getEstimatedValue();
                return StringConverter.FORMAT.format(est) + " " + parObj.getUnit();
            }
        });
    }

    public static String arrayToString(final Object parArr) {
        Method m;
        try {
            try {
                m = Arrays.class.getMethod("toString", parArr.getClass());
            } catch (final NoSuchMethodException nsm) {
                m = Arrays.class.getMethod("toString", Object[].class);
            }
            return (String) m.invoke(null, parArr);
        } catch (final Exception t) { // NOCS re-throw
            throw new IllegalArgumentException(parArr + " is not an array?", t);
        }
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Stringer<Object> getFor(final Class<?> parAny) {
        Stringer<Object> ret = (Stringer) StringConverter.STRINGER.get(parAny);
        if (ret == null) {
            for (final Map.Entry<Class<?>, Stringer<?>> e : StringConverter.STRINGER.entrySet()) {
                if (e.getKey().isAssignableFrom(parAny)) {
                    ret = (Stringer) e.getValue();
                    break;
                }
            }
        }
        if (ret == null) {
            throw new IllegalArgumentException(parAny + " string converter not found");
        }
        return ret;
    }

    protected StringConverter() {
        // Internal default
    }

    protected Object alternativeConvertType(final Class<?> parClz, final Object parObj) {
        Object ret = null;
        IllegalArgumentException ex = null;
        try {
            ret = Main.converter().convert(parObj, parClz);
        } catch (final IllegalArgumentException | ConversionException ce) {
            ex = new IllegalArgumentException("No 3rd party converter", ce);
        }
        if (parObj == null || parClz == null) {
            return ret;
        }
        if (ex == null && ret == parObj && !parClz.isAssignableFrom(parObj.getClass())) {
            ex = new IllegalArgumentException("No converter");
            ex = new IllegalArgumentException("No converter");
            ret = null;
        }
        if (ret == null) {
            ret = ReflectionUtils.convertClassTypeTo(parClz, parObj);
            ex = null;
        }
        if (ex != null) {
            throw ex;
        }
        return ret;
    }

    public <T> T convertString(final Class<T> parClz, final Object parObj, final boolean excIfNone) {
        Object ret = parObj;
        if (parObj == null) {
            return (T) this.alternativeConvertType(parClz, parObj);
        } else if (parClz.isAssignableFrom(parObj.getClass())) {
            return (T) parObj;
        } else if (parObj instanceof String) {
            try {
                final Stringer<?> trafo = StringConverter.getFor(parClz);
                ret = trafo.fromString((String) parObj);
            } catch (final IllegalArgumentException ia) {
                try {
                    ret = this.alternativeConvertType(parClz, parObj);
                } catch (final IllegalArgumentException iai) {
                    throw ReflectionUtils.extendMessage(ia, "fb-ex=" + iai);
                }
            }
        } else if (parClz.equals(String.class)) {
            try {
                final Stringer<Object> trafo = StringConverter.getFor(parObj.getClass());
                ret = trafo.toString(parObj);
            } catch (final IllegalArgumentException ia) {
                // There is a toString for everything
                if (parObj.getClass().isArray()) {
                    ret = StringConverter.arrayToString(parObj);
                } else {
                    try {
                        ret = this.alternativeConvertType(parClz, parObj);
                    } catch (final IllegalArgumentException iai) {
                        ret = null;
                        if (excIfNone) {
                            throw ReflectionUtils.extendMessage(ia, "No special stringer for " + parClz + " for " + parObj + ", ce=" + iai);
                        }
                    }
                    if (ret == null) {
                        ret = String.valueOf(parObj);
                    }
                }
            }
        } else {
            ret = this.alternativeConvertType(parClz, parObj);
        }
        return (T) ret;
    }
}
