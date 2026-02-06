package hu.detox.utils;

import java.util.regex.Pattern;

/**
 * Conversions between different java naming conventions, and free texts.
 */
public class Naming {
    /**
     * Supported recognizable one-line naming formats (naming will be based on words).
     */
    public static enum Format {
        /**
         * camelCase format (such as member fields in java).
         */
        CAMEL("^[a-z][a-zA-Z0-9]*$") {
            @Override
            public String fromWords(final String[] parWords) {
                final StringBuilder sb = new StringBuilder(parWords.length * 20);
                for (int i = 0; i < parWords.length; i++) {
                    String seg = parWords[i];
                    if (!org.apache.commons.lang3.StringUtils.isAllUpperCase(parWords[i]) || parWords.length == 1) {
                        seg = seg.toLowerCase();
                        if (i > 0) {
                            if (seg.length() <= 1) {
                                seg = seg.toUpperCase();
                            } else {
                                seg = Character.toUpperCase(seg.charAt(0)) + seg.substring(1);
                            }
                        }
                    }
                    sb.append(seg);
                }
                return sb.toString();
            }

            @Override
            public String[] getWords(final String parCamel) {
                final String rep = parCamel.replaceAll("([^A-Z])([A-Z]|$)", "$1 $2");
                return FREE.getWords(rep);
            }
        },
        /**
         * CONSTANT_NAMING convention format (such as static final class variables in java).
         */
        CONSTANT("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$") {
            @Override
            public String fromWords(final String[] parWords) {
                final StringBuilder sb = new StringBuilder(parWords.length * 20);
                for (int i = 0; i < parWords.length; i++) {
                    final String seg = parWords[i].toUpperCase();
                    sb.append(seg);
                    if (i < parWords.length - 1) {
                        sb.append('_');
                    }
                }
                return sb.toString();
            }

            @Override
            public String[] getWords(final String parConst) {
                return parConst.split("_+");
            }
        },
        /**
         * ClassName naming convention format (Such as classnames in java).
         */
        CLASS("^[A-Z][^    ]*$") {
            @Override
            public String fromWords(final String[] parWords) {
                final StringBuilder sb = new StringBuilder(parWords.length * 20);
                for (final String parWord : parWords) {
                    String seg = parWord.toLowerCase();
                    if (seg.length() <= 1) {
                        seg = seg.toUpperCase();
                    } else {
                        seg = Character.toUpperCase(seg.charAt(0)) + seg.substring(1);
                    }
                    sb.append(seg);
                }
                return sb.toString();
            }

            @Override
            public String[] getWords(final String parClz) {
                final String rep = parClz.replaceAll("([^A-Z])([A-Z]+)", "$1 $2");
                return FREE.getWords(rep);
            }
        },
        /**
         * The words can be separated by '_' with this naming convention.
         */
        UNDERSCORE("^[^    ]+(_+[^    ]+)*$") {
            @Override
            public String fromWords(final String[] parWords) {
                return org.apache.commons.lang3.StringUtils.join(parWords, '_');
            }

            @Override
            public String[] getWords(final String parClz) {
                return parClz.split("_+");
            }
        },
        /**
         * The words can be separated by '-' with this naming convention.
         */
        DASH("^[^    ]+(\\-+[^    ]+)*$") {
            @Override
            public String fromWords(final String[] parWords) {
                return org.apache.commons.lang3.StringUtils.join(parWords, '-');
            }

            @Override
            public String[] getWords(final String parClz) {
                return parClz.split("\\-+");
            }
        },
        /**
         * Any one-line text will be this type.
         */
        FREE(".*") {
            @Override
            public String fromWords(final String[] parWords) {
                return org.apache.commons.lang3.StringUtils.join(parWords, ' ');
            }

            @Override
            public String[] getWords(final String parAny) {
                return parAny.trim().split("\\s+");
            }
        };

        /**
         * Based on a string this will try to find the proper format.
         *
         * @param parStr The string to check for naming match.
         * @return The format (or {@link #FREE} if none).
         */
        public static synchronized Format fromString(final String parStr) {
            Format ret = FREE;
            for (final Format f : Format.values()) {
                if (f.pattern.matcher(parStr).matches()) {
                    ret = f;
                    break;
                }
            }
            return ret;
        }

        private final Pattern pattern;

        private Format(final String parPatt) {
            this.pattern = Pattern.compile(parPatt);
        }

        /**
         * Creates a string based on the naming convention represented by the actual instance.
         *
         * @param parWords The words of the final string.
         * @return The formatted string.
         */
        public abstract String fromWords(String[] parWords);

        public Pattern getPattern() {
            return this.pattern;
        }

        /**
         * PArses the words out from a string based on the current naming convention.
         *
         * @param parWhich The string to parse.
         * @return The words from the string without transformation.
         */
        public abstract String[] getWords(String parWhich);
    }

    private static final long serialVersionUID = -2008644537180534L;

    private Format format;
    private String text;

    public Naming() {
        this.setText(null);
    }

    /**
     * Initializes naming with a given text (and detects the format).
     *
     * @param parText The text to recognize, null to leave unset.
     */
    public Naming(final String parText) {
        this.setText(parText);
    }

    public Format getFormat() {
        return this.format;
    }

    public String getText() {
        return this.text;
    }

    public void setFormat(final Format parFormat) {
        final Naming n = this.toFormat(parFormat);
        this.format = n.format;
        this.text = n.text;
    }

    /**
     * You can set the text of this naming (and may changes the format).
     *
     * @param parText The text to set, null to make this naming unset.
     */
    public final void setText(final String parText) {
        this.text = parText;
        if (parText == null) {
            this.format = null;
        } else {
            this.format = Format.fromString(parText);
        }
    }

    public String toString() {
        return "Fmt=" + this.format + ", txt=" + text;
    }

    public Naming toFormat(final Format parTyp) {
        final Naming ret = new Naming();
        ret.text = this.toString(parTyp);
        ret.format = parTyp;
        return ret;
    }

    public String toString(final Format parTyp) {
        final String[] wrds = this.format.getWords(this.text);
        return parTyp.fromWords(wrds);
    }
}
