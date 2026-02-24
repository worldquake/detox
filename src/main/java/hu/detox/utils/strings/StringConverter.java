package hu.detox.utils.strings;

import hu.detox.Main;
import hu.detox.parsers.AmountCalculator;
import hu.detox.utils.SystemUtils;
import hu.detox.utils.reflection.ReflectionUtils;
import hu.detox.utils.url.URL;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.spi.StandardLevel;
import org.jetbrains.annotations.NotNull;
import org.jscience.physics.amount.Amount;
import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.ConversionException;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class StringConverter {
    public static class Stringer<T> {
        public T fromString(final String parStr) {
            return (T) parStr;
        }

        public String toString(final @NotNull T parObj) {
            return String.valueOf(parObj);
        }
    }

    public static final NumberFormat FORMAT = new DecimalFormat("#.#");
    public static final StringConverter INSTANCE = new StringConverter();
    public static final Map<Class<?>, Stringer<?>> STRINGER = new LinkedHashMap<Class<?>, Stringer<?>>();

    static {
        final Stringer<Number> ns = new Stringer<>() {
            @Override
            public Number fromString(final String parStr) {
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
            public String toString(final @NonNull Number parObj) {
                return NumberFormat.getInstance().format(parObj);
            }
        };
        StringConverter.STRINGER.put(Number.class, ns);
        StringConverter.STRINGER.put(Integer.class, new Stringer<Number>() {
            @Override
            public Integer fromString(final String parStr) {
                final Number n = ns.fromString(parStr);
                return n == null ? null : n.intValue();
            }
        });
        StringConverter.STRINGER.put(Level.class, new Stringer<Level>() {
            @Override
            public Level fromString(final String parStr) {
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
            public java.net.URL fromString(final String parStr) {
                final URL url = URL.valueOf(parStr);
                url.setEncode(SystemUtils.UTF8CS);
                return url.toURL();
            }
        });
        StringConverter.STRINGER.put(Float.class, new Stringer<Float>() {
            @Override
            public Float fromString(final String parStr) {
                final Number n = ns.fromString(parStr);
                return n == null ? null : n.floatValue();
            }
        });
        StringConverter.STRINGER.put(Double.class, new Stringer<Double>() {
            @Override
            public Double fromString(final String parStr) {
                final Number n = ns.fromString(parStr);
                return n == null ? null : n.doubleValue();
            }
        });
        StringConverter.STRINGER.put(Pattern.class, new Stringer<Pattern>() {
            @Override
            public Pattern fromString(final String parStr) {
                return StringUtils.isExplicitNull(parStr) ? null : Pattern.compile(parStr);
            }

            @Override
            public String toString(final @NonNull Pattern parObj) {
                return parObj.pattern();
            }
        });
        StringConverter.STRINGER.put(byte[].class, new Stringer<byte[]>() {
            @Override
            public byte[] fromString(final String parStr) {
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
            public String toString(final byte @NonNull [] parObj) {
                return Hex.encodeHexString(parObj);
            }
        });
        StringConverter.STRINGER.put(Byte.class, new Stringer<Byte>() {
            @Override
            public Byte fromString(final String parStr) {
                final Number n = ns.fromString(parStr);
                return n == null ? null : n.byteValue();
            }
        });
        StringConverter.STRINGER.put(Short.class, new Stringer<Short>() {
            @Override
            public Short fromString(final String parStr) {
                final Number n = ns.fromString(parStr);
                return n == null ? null : n.shortValue();
            }
        });
        StringConverter.STRINGER.put(Long.class, new Stringer<Number>() {
            @Override
            public Long fromString(final String parStr) {
                final Number n = ns.fromString(parStr);
                return n == null ? null : n.longValue();
            }
        });
        StringConverter.STRINGER.put(BigDecimal.class, new Stringer<BigDecimal>() {
            @Override
            public BigDecimal fromString(final String parStr) {
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
        StringConverter.STRINGER.put(Boolean.class, new Stringer<Boolean>() {
            @Override
            public Boolean fromString(final String val) {
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
            public String toString(final @NonNull Boolean parObj) {
                return Boolean.toString(parObj);
            }
        });
        StringConverter.STRINGER.put(Amount.class, new Stringer<Amount<?>>() {
            @Override
            public Amount<?> fromString(final String parStr) {
                return StringUtils.isEmpty(parStr) ? null : AmountCalculator.INSTANCE.calc(parStr);
            }

            @Override
            public String toString(final @NonNull Amount<?> parObj) {
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

    private Object alternativeConvertType(final Class<?> parClz, @NotNull final Object parObj) {
        Object ret = parObj;
        if (parClz == null) {
            return ret;
        }
        RuntimeException ex = null;
        try {
            ret = Main.converter().convert(parObj, parClz);
        } catch (final IllegalArgumentException | ConversionException ce) {
            ex = ce;
        }
        if (ex == null && ret == parObj && !parClz.isAssignableFrom(parObj.getClass())) {
            ex = new IllegalArgumentException("No converter from " + parObj.getClass() + " to " + parClz);
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
        Object ret;
        if (parObj == null) {
            return null;
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
