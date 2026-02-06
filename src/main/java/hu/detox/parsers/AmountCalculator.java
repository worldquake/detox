package hu.detox.parsers;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.jscience.physics.amount.Amount;

import javax.measure.quantity.Duration;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;
import javax.measure.unit.UnitFormat;
import java.io.IOException;
import java.io.StringReader;
import java.text.MessageFormat;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class AmountCalculator {
    public static final AmountCalculator INSTANCE = new AmountCalculator();

    static Object divide(final Object l, final Object r) {
        if (l instanceof Amount) {
            if (r instanceof Double) {
                return ((Amount) l).divide((Double) r);
            } else if (r instanceof Number) {
                return ((Amount) l).divide(((Number) r).longValue());
            } else {
                return ((Amount) l).divide((Amount) r);
            }
        } else {
            final Number lo = (Number) l;
            if (r instanceof Double) {
                return lo.doubleValue() / (Double) r;
            } else if (r instanceof Number) {
                return lo.longValue() / ((Number) r).longValue();
            } else {
                return lo.doubleValue() / ((Amount) r).getMaximumValue();
            }
        }
    }

    static Object minus(final Object l, final Object r) {
        if (l instanceof Amount) {
            return ((Amount) l).minus((Amount) r);
        } else {
            final Number lo = (Number) l;
            if (r instanceof Double) {
                return lo.doubleValue() - (Double) r;
            } else if (r instanceof Number) {
                return lo.longValue() - ((Number) r).longValue();
            } else {
                return lo.doubleValue() - ((Amount) r).getMaximumValue();
            }
        }
    }

    static Object plus(final Object l, final Object r) {
        if (l instanceof Amount) {
            return ((Amount) l).plus((Amount) r);
        } else {
            final Number lo = (Number) l;
            if (r instanceof Double) {
                return lo.doubleValue() + (Double) r;
            } else if (r instanceof Number) {
                return lo.longValue() + ((Number) r).longValue();
            } else {
                return lo.doubleValue() + ((Amount) r).getMaximumValue();
            }
        }
    }

    static Object times(final Object l, final Object r) {
        if (l instanceof Amount) {
            if (r instanceof Double) {
                return ((Amount) l).times((Double) r);
            } else if (r instanceof Number) {
                return ((Amount) l).times(((Number) r).longValue());
            } else {
                return ((Amount) l).times((Amount) r);
            }
        } else {
            final Number lo = (Number) l;
            if (r instanceof Double) {
                return lo.doubleValue() * (Double) r;
            } else if (r instanceof Number) {
                return lo.longValue() * ((Number) r).longValue();
            } else {
                return lo.doubleValue() * ((Amount) r).getMaximumValue();
            }
        }
    }

    public static ChronoUnit toChronoUnit(final Unit<Duration> dr) {
        if (NonSI.DAY.equals(dr)) {
            return ChronoUnit.DAYS;
        } else if (NonSI.HOUR.equals(dr)) {
            return ChronoUnit.HOURS;
        } else if (SI.SECOND.equals(dr)) {
            return ChronoUnit.SECONDS;
        } else if (SI.MILLI(SI.SECOND).equals(dr)) {
            return ChronoUnit.MILLIS;
        } else if (SI.NANO(SI.SECOND).equals(dr)) {
            return ChronoUnit.NANOS;
        } else if (SI.MICRO(SI.SECOND).equals(dr)) {
            return ChronoUnit.MICROS;
        } else if (SI.DEKA(NonSI.YEAR).equals(dr)) {
            return ChronoUnit.DECADES;
        } else if (SI.KILO(NonSI.YEAR).equals(dr)) {
            return ChronoUnit.MILLENNIA;
        }
        throw new IllegalArgumentException("Failed to convert " + dr);
    }

    public static TimeUnit toTimeUnit(final Unit<Duration> dr) {
        if (NonSI.DAY.equals(dr)) {
            return TimeUnit.DAYS;
        } else if (NonSI.HOUR.equals(dr)) {
            return TimeUnit.HOURS;
        } else if (SI.SECOND.equals(dr)) {
            return TimeUnit.SECONDS;
        } else if (SI.MILLI(SI.SECOND).equals(dr)) {
            return TimeUnit.MILLISECONDS;
        } else if (SI.NANO(SI.SECOND).equals(dr)) {
            return TimeUnit.NANOSECONDS;
        } else if (SI.MICRO(SI.SECOND).equals(dr)) {
            return TimeUnit.MICROSECONDS;
        }
        throw new IllegalArgumentException("Failed to convert " + dr);
    }

    public synchronized <T> T calc(final CharSequence expr, final HashMap<String, Object> memo) {
        hu.detox.parsers.AmountCalculatorLexer lexer;
        try {
            lexer = new hu.detox.parsers.AmountCalculatorLexer(new ANTLRInputStream(new StringReader(String.valueOf(expr))));
        } catch (final IOException e1) {
            throw new IllegalStateException(e1);
        }
        final hu.detox.parsers.AmountCalculatorParser parser = new hu.detox.parsers.AmountCalculatorParser(null);
        parser.memory = memo;
        parser.setTokenStream(new CommonTokenStream(lexer));
        final Object ret = parser.prog().value;
        return (T) ret;
    }

    public synchronized <T> T calc(final String expr) {
        return this.calc(expr, new HashMap<>());
    }

    public String format(final Amount<?> amount) {
        return this.format(amount, 1000, null);
    }

    public String format(final Amount<?> amount, final float div, final Unit<?> base) {
        return this.format(amount, div, base, "{0}{1}", ' ');
    }

    public String format(Amount<?> amount, final float div, final Unit<?> base, final String fmt, final Object sep) {
        Unit<?> u = base == null ? amount.getUnit().getStandardUnit() : base;
        double val = amount.to(u).getEstimatedValue();
        final StringBuilder sb = new StringBuilder();
        final boolean neg = val < 0;
        val = Math.abs(val);
        while (true) {
            final double seg = val % div;
            if (seg != 0) {
                if (sb.length() > 0) {
                    sb.insert(0, sep);
                }
                sb.insert(0, MessageFormat.format(fmt, seg, UnitFormat.getInstance().format(u)));
            }
            val = (long) (val / div);
            if (val == 0) {
                break;
            }
            u = u.times(div);
            amount = Amount.valueOf(val, u).times(div);
        }
        if (neg) {
            sb.insert(0, '-');
        }
        return sb.toString();
    }

    public String format(final double amount, final float div, final Unit<?> base) {
        final Amount<?> amo = Amount.valueOf(amount, base);
        return this.format(amo, div, base, "{0}{1}", ' ');
    }
}
