package hu.detox.utils.strings;

import java.util.Random;
import java.util.regex.Pattern;

public class PasswordBuilder {
    public static final String PASSWORD_STRING = "password";
    public final static Random RANDOM = new Random();
    // we keep our data in lists. Arrays would suffice as data never changes though.
    private final static char[] LOWER_CAPS, UPPER_CAPS, DIGITS, SPECIALS;
    private final static Pattern P_SPECIALS;
    private final static Pattern P_LOWER_CAPS;
    private final static Pattern P_UPPER_CAPS;
    private final static Pattern P_DIGITS;

    // initialize statics
    static {
        LOWER_CAPS = new char[26];
        UPPER_CAPS = new char[26];
        for (int i = 0; i < 26; i++) {
            PasswordBuilder.LOWER_CAPS[i] = (char) (i + 'a');
            PasswordBuilder.UPPER_CAPS[i] = (char) (i + 'A');
        }
        DIGITS = new char[10];
        for (int i = 0; i < 10; i++) {
            PasswordBuilder.DIGITS[i] = (char) (i + '0');
        }
        SPECIALS = "!\"#$%&'()*+,-./:;<=>?@[\\]^_{|}~".toCharArray();
        P_SPECIALS = Pattern.compile("(?=.*[" + Pattern.quote(new String(PasswordBuilder.SPECIALS)) + "]).*");
        P_LOWER_CAPS = Pattern.compile("[a-z]");
        P_UPPER_CAPS = Pattern.compile("[A-Z]");
        P_DIGITS = Pattern.compile("[0-9]");
    }

    /**
     * Factory method to create our builder.
     *
     * @return New PasswordBuilder instance.
     */
    public static PasswordBuilder builder() {
        return new PasswordBuilder();
    }

    private final StringBuilder password = new StringBuilder();

    public PasswordBuilder add(final int cnt, final char... source) {
        if (cnt <= 0) {
            return this;
        }
        for (int i = 0; i < cnt; i++) {
            this.password.append(source[PasswordBuilder.RANDOM.nextInt(source.length)]);
        }
        return this;
    }

    public char[] build(final char... characters) {
        this.password.append(characters);
        final char[] ret = this.password.toString().toCharArray();
        org.apache.commons.lang3.ArrayUtils.shuffle(ret);
        return ret;
    }

    public PasswordBuilder digits(final int count) {
        this.add(count, PasswordBuilder.DIGITS);
        return this;
    }

    private int indiCnt(final Pattern p) {
        final int cnt = StringUtils.countMatches(p.matcher(this.password));
        return Math.min(cnt, 2);
    }

    public PasswordBuilder lowercase(final int count) {
        this.add(count, PasswordBuilder.LOWER_CAPS);
        return this;
    }

    public PasswordBuilder specials(final int count) {
        this.add(count, PasswordBuilder.SPECIALS);
        return this;
    }

    public int strength() {
        int iPasswordScore = 0;
        if (this.password.length() >= 10) {
            iPasswordScore += 2;
        } else if (this.password.length() >= 8) {
            iPasswordScore += 1;
        }
        iPasswordScore += this.indiCnt(PasswordBuilder.P_DIGITS);
        iPasswordScore += this.indiCnt(PasswordBuilder.P_LOWER_CAPS);
        iPasswordScore += this.indiCnt(PasswordBuilder.P_UPPER_CAPS);
        iPasswordScore += this.indiCnt(PasswordBuilder.P_SPECIALS);
        if (this.password.length() < 8) {
            iPasswordScore = Math.min(iPasswordScore, 6);
        }
        return iPasswordScore;
    }

    @Override
    public String toString() {
        return "Length=" + this.password.length() + ", Strength=" + this.strength();
    }

    public PasswordBuilder uppercase(final int count) {
        this.add(count, PasswordBuilder.UPPER_CAPS);
        return this;
    }
}
