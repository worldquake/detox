/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package hu.detox.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import hu.detox.Main;
import hu.detox.io.FileUtils;
import kotlin.Pair;
import org.apache.commons.beanutils.DynaBean;
import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.commons.lang3.StringEscapeUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//CHOFF This is a class copied from the Internet without change
public class StringUtils extends org.apache.commons.lang3.StringUtils {
    private static final Pattern KEY_VALUE1 = Pattern.compile("^(\\p{Graph}+)\\s*=\\s*(.*)$");
    private static final Pattern KEY_VALUE2 = Pattern.compile("^(\\p{Graph}+)\\s+(.*)$");
    public static final String SPLIT = "\\s*[,;]\\s*";
    private static final String NULL_CHR = "\u0000";
    public static final String NULL_UNI = "␀";
    public static final String NULL = "null";
    public static final String EOT = "eot";
    public static final String EOT_UNI1 = "␄";
    public static final String EOT_UNI2 = "\u0004";
    private static final String[] EOT_ARR = {StringUtils.EOT, StringUtils.EOT_UNI1, StringUtils.EOT_UNI2};
    public static final String FORCEEMPTY = "\uFEFF"; // ZERO WIDTH NO-BREAK SPACE "﻿" &#xFEFF;
    private static final String[] NULL_ARR = {StringUtils.NULL, StringUtils.NULL_UNI, StringUtils.NULL_CHR};
    public static final Pattern NAMEPATT = Pattern.compile("\\[([^\\]]+)\\]");
    public static final SimpleDateFormat FORMAT1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
    private static final SimpleDateFormat FORMAT2 = new SimpleDateFormat("M/d/yy h:m:s a", Locale.ENGLISH);
    private static final SimpleDateFormat FORMAT3 = new SimpleDateFormat("dd MMM yyyy HH:mm:ss zz", Locale.ENGLISH);
    private static final SimpleDateFormat FORMAT4 = new SimpleDateFormat("MMM dd, yyyy K:mm:ss a", Locale.ENGLISH);
    private static final SimpleDateFormat FORMAT5 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH); // date format required by Solr queries
    private static final SimpleDateFormat FORMAT6 = new SimpleDateFormat("yyyy.MM.dd. HH:mm:ss", Locale.ENGLISH);
    private static final SimpleDateFormat FORMAT7 = new SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH);
    public static final SimpleDateFormat[] NUM_FORMAT = new SimpleDateFormat[]{FORMAT1, FORMAT2, FORMAT3, FORMAT4, FORMAT5, FORMAT6, FORMAT7};
    public static final String SUBTRANSCRIPTCHR = "☇";

    public static void appendIfNotEmpty(final StringBuilder sb, final String sep, final Object... os) {
        sb.setLength(0);
        for (final Object o : os) {
            if (o == null || o instanceof String && StringUtils.isEmpty((String) o)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(String.valueOf(o).trim());
        }
    }

    private static Pair<String, String> toPair(String kv) {
        Matcher m = KEY_VALUE1.matcher(kv);
        if (!m.matches()) {
            m = KEY_VALUE2.matcher(kv);
        }
        return m.matches() ? new Pair<>(m.group(1), m.group(2)) : null;
    }

    public static Collection<String> asList(final String commaList) {
        return Arrays.asList(commaList.split(","));
    }

    public static String concat(final String join, final Object... paths) {
        final StringBuilder sb = new StringBuilder(paths.length * 20);
        boolean endsSlash = true;
        String spath;
        for (final Object path : paths) {
            spath = FileUtils.toPath(path);
            if (StringUtils.isEmpty(spath)) {
                continue;
            }
            if (!endsSlash && !spath.startsWith(join)) {
                sb.append(join);
            }
            sb.append(spath);
            endsSlash = spath.endsWith(join);
        }

        return sb.toString();
    }

    public static String resolveName(final String n, final Object to, final Class<?>... classes) {
        final Matcher m = StringUtils.NAMEPATT.matcher(n);
        return StringUtils.resolveName(m, to, classes);
    }

    public static String resolveName(final Matcher m, final Object to, final Class<?>... classes) {
        final StringBuffer sb = new StringBuffer();
        String g;
        String frst = org.apache.commons.lang3.StringUtils.EMPTY;
        Object scnd;
        while (m.find()) {
            scnd = org.apache.commons.lang3.StringUtils.EMPTY;
            g = m.group(1);
            Pair<String, String> p = StringUtils.toPair(g);
            if (p == null) frst = g;
            else {
                scnd = p.getSecond();
                if (StringUtils.isNull(scnd)) {
                    scnd = null;
                } else {
                    final String[] spl = frst.split("@");
                    if (spl.length > 1) {
                        Class<?> c;
                        try {
                            c = ReflectionUtils.toClass(spl[1], classes);
                        } catch (final ClassNotFoundException e) {
                            throw new IllegalArgumentException(m + " has invalid mapping: " + p, e);
                        }
                        final Object v = StringUtils.to(c, (String) scnd, to);
                        frst = spl[0];
                        scnd = v;
                    }
                }
            }
            if (to instanceof DynaBean) {
                ((DynaBean) to).set(frst, scnd);
            } else if (to instanceof Map) {
                ((Map) to).put(frst, scnd);
            } else {
                ReflectionUtils.setProperty(to, frst, scnd);
            }
            m.appendReplacement(sb, org.apache.commons.lang3.StringUtils.EMPTY);
        }
        m.appendTail(sb);
        return sb.toString();

    }

    public static int countMatches(final Matcher m) {
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    public static int countMatches(final String str, final Pattern sub) {
        if (StringUtils.isEmpty(str) || sub == null) {
            return 0;
        }
        return StringUtils.countMatches(sub.matcher(str));
    }

    public static int countRegexMatches(final String str, final String sub) {
        if (StringUtils.isEmpty(str) || StringUtils.isEmpty(sub)) {
            return 0;
        }
        return StringUtils.countMatches(str, Pattern.compile(sub));
    }

    public static String forceToString(final Object o) {
        try {
            return String.valueOf(o);
        } catch (final RuntimeException re) {
            return o.getClass().getName() + "@" + Integer.toHexString(o.hashCode());
        }
    }

    private static kotlin.Pair<String, String> getNumberWithformat(final Class any, final Locale curr, String num) {
        final String[] patt = StringEscapeUtils.unescapeHtml4(num).split(StringUtils.SUBTRANSCRIPTCHR, -1);
        String mnf;
        if (patt.length > 1) {
            mnf = org.apache.commons.lang3.StringUtils.join(patt, StringUtils.SUBTRANSCRIPTCHR, 1, patt.length);
            num = patt[0];
        } else {
            mnf = Main.prop(any + "." + curr);
        }
        return new Pair<>(num, mnf);
    }

    public static boolean isBlank(String val) {
        val = org.apache.commons.lang3.StringUtils.trimToNull(val);
        return val == null || StringUtils.isExplicitNull(val);
    }

    public static boolean isEmpty(final JsonElement val) {
        return StringUtils.isNull(val) || val.isJsonArray() && val.getAsJsonArray().size() == 0
                || val.isJsonPrimitive() && val.getAsJsonPrimitive().isString() && StringUtils.isEmpty(val.getAsString());
    }

    public static boolean isNotBlank(final String el) {
        return !isBlank(el);
    }

    public static boolean isEmpty(final Object val) {
        if (val == null) {
            return true;
        }
        if (val instanceof CharSequence) {
            final String sval = String.valueOf(val);
            return org.apache.commons.lang3.StringUtils.isEmpty(sval) || StringUtils.isExplicitNull(sval)
                    || StringUtils.isEOT(sval) || sval.equals(FORCEEMPTY);
        }
        return false;
    }

    public static boolean isEmpty(final String val) {
        return StringUtils.isEmpty((Object) val);
    }

    public static boolean isEOT(final Object val) {
        return val != null && org.apache.commons.lang3.ArrayUtils.contains(StringUtils.EOT_ARR, String.valueOf(val).toLowerCase(Locale.ENGLISH));
    }

    public static boolean isExplicitNull(final CharSequence val) {
        return val != null && StringUtils.isTextNull(val);
    }

    private static boolean isInteger(final Class<? extends Number> clz) {
        return Integer.class.isAssignableFrom(clz) || Long.class.isAssignableFrom(clz) || Short.class.isAssignableFrom(clz) || Byte.class.isAssignableFrom(clz);
    }

    public static boolean isNotEmpty(final String v) {
        return !StringUtils.isEmpty(v);
    }

    public static boolean isNull(final Object val) {
        return val == null || JsonNull.INSTANCE.equals(val) || //
                val instanceof CharSequence && StringUtils.isExplicitNull((CharSequence) val);
    }

    public static boolean isRepetitiveValue(final String val) {
        return StringUtils.isRepetitiveValue(val, 1);
    }

    public static boolean isRepetitiveValue(final String val, final int ml) {
        final Set<Character> chrs = new HashSet<Character>();
        chrs.addAll(Arrays.asList(org.apache.commons.lang3.ArrayUtils.toObject(val.toCharArray())));
        return chrs.size() <= ml;
    }

    public static boolean isTextNull(final CharSequence val) {
        return org.apache.commons.lang3.ArrayUtils.contains(StringUtils.NULL_ARR, String.valueOf(val).toLowerCase(Locale.ENGLISH));
    }

    public static String normalize(String any) {
        if (StringUtils.isNull(any)) {
            return null;
        }
        any = StringEscapeUtils.unescapeHtml4(any) // Un-HTML everything
                .replaceFirst("^\"", "").replaceFirst("\"$", "") // Remove quotes
                .replaceAll("\\s{2,}", " ") // Multiple spaces to one
        ;
        return StringUtils.trimToNull(any);
    }

    public static String normalize(final String any, final int ml) {
        return StringUtils.truncateToNull(StringUtils.normalize(any), ml);
    }

    public static synchronized Date parse(final int df, final String val) throws ParseException {
        return NUM_FORMAT[df - 1].parse(val);
    }

    public static synchronized String format(final int df, final Date val) {
        return NUM_FORMAT[df - 1].format(val);
    }

    public static String replaceEach(final String text, final String[] searchList, final String[] replacementList) {
        return StringUtils.replaceEach(text, searchList, replacementList, false, 0);
    }

    private static String replaceEach(final String text, final String[] searchList, final String[] replacementList, final boolean repeat,
                                      final int timeToLive) {

        // mchyzer Performance note: This creates very few new objects (one major goal)
        // let me know if there are performance requests, we can create a harness to measure

        if (text == null || text.length() == 0 || searchList == null || searchList.length == 0 || replacementList == null || replacementList.length == 0) {
            return text;
        }

        // if recursing, this shouldnt be less than 0
        if (timeToLive < 0) {
            throw new IllegalStateException("TimeToLive of " + timeToLive + " is less than 0: " + text);
        }

        final int searchLength = searchList.length;
        final int replacementLength = replacementList.length;

        // make sure lengths are ok, these need to be equal
        if (searchLength != replacementLength) {
            throw new IllegalArgumentException("Search and Replace array lengths don't match: " + searchLength + " vs " + replacementLength);
        }

        // keep track of which still have matches
        final boolean[] noMoreMatchesForReplIndex = new boolean[searchLength];

        // index on index that the match was found
        int textIndex = -1;
        int replaceIndex = -1;
        int tempIndex = -1;

        // index of replace array that will replace the search string found
        // NOTE: logic duplicated below START
        for (int i = 0; i < searchLength; i++) {
            if (noMoreMatchesForReplIndex[i] || searchList[i] == null || searchList[i].length() == 0 || replacementList[i] == null) {
                continue;
            }
            tempIndex = text.indexOf(searchList[i]);

            // see if we need to keep searching for this
            if (tempIndex == -1) {
                noMoreMatchesForReplIndex[i] = true;
            } else {
                if (textIndex == -1 || tempIndex < textIndex) {
                    textIndex = tempIndex;
                    replaceIndex = i;
                }
            }
        }
        // NOTE: logic mostly below END

        // no search strings found, we are done
        if (textIndex == -1) {
            return text;
        }

        int start = 0;

        // get a good guess on the size of the result buffer so it doesnt have to double if it goes over a bit
        int increase = 0;

        // count the replacement text elements that are larger than their corresponding text being replaced
        for (int i = 0; i < searchList.length; i++) {
            final int greater = replacementList[i].length() - searchList[i].length();
            if (greater > 0) {
                increase += 3 * greater; // assume 3 matches
            }
        }
        // have upper-bound at 20% increase, then let Java take over
        increase = Math.min(increase, text.length() / 5);

        final StringBuffer buf = new StringBuffer(text.length() + increase);

        while (textIndex != -1) {

            for (int i = start; i < textIndex; i++) {
                buf.append(text.charAt(i));
            }
            buf.append(replacementList[replaceIndex]);

            start = textIndex + searchList[replaceIndex].length();

            textIndex = -1;
            replaceIndex = -1;
            tempIndex = -1;
            // find the next earliest match
            // NOTE: logic mostly duplicated above START
            for (int i = 0; i < searchLength; i++) {
                if (noMoreMatchesForReplIndex[i] || searchList[i] == null || searchList[i].length() == 0 || replacementList[i] == null) {
                    continue;
                }
                tempIndex = text.indexOf(searchList[i], start);

                // see if we need to keep searching for this
                if (tempIndex == -1) {
                    noMoreMatchesForReplIndex[i] = true;
                } else {
                    if (textIndex == -1 || tempIndex < textIndex) {
                        textIndex = tempIndex;
                        replaceIndex = i;
                    }
                }
            }
            // NOTE: logic duplicated above END

        }
        final int textLength = text.length();
        for (int i = start; i < textLength; i++) {
            buf.append(text.charAt(i));
        }
        final String result = buf.toString();
        if (!repeat) {
            return result;
        }

        return StringUtils.replaceEach(result, searchList, replacementList, repeat, timeToLive - 1);
    }

    public static String replaceEachRepeatedly(final String text, final String[] searchList, final String[] replacementList) {
        // timeToLive should be 0 if not used or nothing to replace, else it's
        // the length of the replace array
        final int timeToLive = searchList == null ? 0 : searchList.length;
        return StringUtils.replaceEach(text, searchList, replacementList, true, timeToLive);
    }

    public static String replaceOnce(final String text, final String searchString, final String replacement) {
        return org.apache.commons.lang3.StringUtils.replace(text, searchString, replacement, 1);
    }

    public static String replaceVars(String ln, final Map<?, ?> vars, final boolean kv, final AtomicInteger h) {
        if (org.apache.commons.collections4.MapUtils.isEmpty(vars) || ln == null) {
            return ln;
        }
        CharSequence k, v;
        String nln;
        for (final Map.Entry<?, ?> e : vars.entrySet()) {
            if (e.getValue() == null || e.getKey() == null) {
                continue;
            }
            k = e.getKey() instanceof CharSequence ? (CharSequence) e.getKey() : String.valueOf(e.getKey());
            v = e.getValue() instanceof CharSequence ? (CharSequence) e.getValue() : String.valueOf(e.getValue());
            nln = kv ? ln.replace(k, v) : ln.replace(k, v);
            if (!nln.equals(ln)) {
                if (h != null) {
                    h.incrementAndGet();
                }
                ln = nln;
            }
        }
        return ln;
    }

    public static String replaceVars(final String ln, final Map<?, ?> vars, final AtomicInteger h) {
        return StringUtils.replaceVars(ln, vars, true, h);
    }

    public static synchronized String timezoneRawID(final TimeZone jtz) {
        return jtz.observesDaylightTime() + "/" + jtz.getDSTSavings() + "/" + jtz.getRawOffset();
    }

    public static final String toString(final Throwable e) {
        return StringUtils.toString(e, null);
    }

    public static final String toString(final Throwable e, final Locale safe) {
        return StringUtils.toString(e, safe, (StringWriter) null);
    }

    public static final String toString(final Throwable e, final Locale safe, final StringBuilder sb) {
        final StringBuilderWriter sbw = new StringBuilderWriter(sb);
        return StringUtils.toString(e, safe, sbw);
    }

    public static final String toString(final Throwable e, final Locale safe, Writer w) {
        if (w == null) {
            w = new StringWriter();
        }
        final PrintWriter pw = new PrintWriter(w, true);
        String ret = null;
        if (safe == null) {
            try {
                e.printStackTrace(pw);
            } finally {
                pw.flush();
            }
        } else {
            Throwable p;
            Throwable x = e;
            do {
                p = x;
                x = x.getCause();
            } while (x != null && x != p);
            pw.flush();
            if (w instanceof StringWriter) {
                ret = w.toString();
                ((StringWriter) w).getBuffer().setLength(0);
            }
        }
        return ret == null ? w.toString() : ret;
    }

    public static TimeZone toTimeZone(Object o) {
        if (o instanceof CharSequence) {
            o = String.valueOf(o).replaceAll("(\\()|(\\).+)", "");
        } else if (o == null) {
            o = TimeZone.getDefault();
        }
        if (o instanceof Locale) {
            o = ((Locale) o).getCountry();
        }
        if (o instanceof String) {
            String s = (String) o;
            if (s.length() == 0) {
                o = TimeZone.getDefault();
            } else if (s.length() == 2) {
                final String[] ids = com.ibm.icu.util.TimeZone.getAvailableIDs(s);
                if (ids.length == 0) {
                    o = TimeZone.getDefault();
                } else {
                    s = ids[0];
                }
            }
            if (o instanceof String) {
                o = TimeZone.getTimeZone(s);
            }
        }
        return (TimeZone) o;
    }

    public static synchronized String trimToEmpty(final String s) {
        return StringUtils.truncateToEmpty(s, Integer.MAX_VALUE);
    }

    public static String trimToNull(String str) {
        str = org.apache.commons.lang3.StringUtils.trimToNull(str);
        return str == null || StringUtils.isExplicitNull(str) ? null : str;
    }

    public static synchronized String truncateToEmpty(String s, final int limit) {
        s = StringUtils.truncateToNull(s, limit);
        return isEmpty(s) ? org.apache.commons.lang3.StringUtils.EMPTY : s;
    }

    public static synchronized String truncateToNull(String s, final int limit) {
        if (org.apache.commons.lang3.StringUtils.isNotEmpty(s)) {
            s = s.substring(0, Math.min(limit, s.length()));
        }
        return StringUtils.isEmpty(s) ? null : s;
    }

    public static final String userError(final Throwable e) {
        return StringUtils.toString(e, SystemUtils.locale()).trim();
    }

    private static <T> T to(final Class<T> type, final String val, final Object caller) {
        if (val == null) {
            return null;
        }
        Object ret;
        try {
            if (type == Method.class) {
                try {
                    final String[] ma = org.apache.commons.lang3.StringUtils.split(val.replace(")", ""), "(");
                    Class[] args = null;
                    if (ma.length == 2) {
                        args = ReflectionUtils.toClasses(ma[1].split(StringUtils.SPLIT));
                    }
                    ret = ReflectionUtils.getMethod(ReflectionUtils.toClass(caller), ma[0], args);
                } catch (final ClassNotFoundException | NoSuchMethodException e) {
                    throw new IllegalArgumentException(e);
                }
            } else if (type == List.class) {
                ret = new LinkedList<>();
                ((LinkedList<Object>) ret).add(val);
            } else {
                ret = StringConverter.INSTANCE.convertString(type, val, true);
            }
        } catch (final IllegalArgumentException ia) {
            throw ReflectionUtils.extendMessage(ia, (caller == null ? "" : "c=" + caller + ", ") + "val=" + val + ", target=" + type);
        }
        return (T) ret;
    }

    public static <T> T to(Object type, final String val, final Object caller) {
        if (val == null) {
            return null;
        }
        Object ret = null;
        if (type instanceof Pair) {
            final Pair<Class, Object> p = (Pair) type;
            boolean m;
            Object oo;
            Matcher mm = null;
            for (final Field fi : p.getFirst().getFields()) {
                if (p.getSecond() instanceof String) {
                    m = fi.getName().startsWith((String) p.getSecond());
                } else {
                    mm = ((Pattern) p.getSecond()).matcher(fi.getName());
                    m = mm.matches();
                }
                if (m) {
                    oo = ReflectionUtils.getProperty(p.getSecond(), fi);
                    if (mm == null && fi.getName().replaceFirst((String) p.getSecond(), org.apache.commons.lang3.StringUtils.EMPTY).equalsIgnoreCase(val)
                            || mm != null && mm.group(1).equalsIgnoreCase(val)) {
                        ret = oo;
                        break;
                    }
                }
                mm = null;
            }
        } else {
            try {
                type = ReflectionUtils.toClass(type);
                ret = StringUtils.to((Class) type, val, caller);
            } catch (final ClassNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        }
        return (T) ret;
    }

    public static Date toDate(Object v) {
        if (v instanceof Calendar) {
            v = ((Calendar) v).getTime();
        } else if (v instanceof Number) {
            v = new Date(((Number) v).longValue());
        } else if (!(v instanceof Date)) {
            v = StringUtils.to(Date.class, String.valueOf(v), null);
        }
        return (Date) v;
    }

    private StringUtils() {
        // No instance
    }
}
