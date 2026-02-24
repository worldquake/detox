package hu.detox.parsers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.gson.*;
import com.google.gson.internal.bind.ReflectiveTypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.jayway.jsonpath.*;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import hu.detox.io.CharIOHelper;
import hu.detox.utils.reflection.ReflectionUtils;
import hu.detox.utils.strings.StringUtils;
import hu.detox.utils.url.URL;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class JSonUtils {
    private static class DateAdapter<T extends Date> extends TypeAdapter<T> {
        private final DateFormat format;
        private final Class<T> clazz;

        public DateAdapter(final Class<T> clz) {
            this(clz, DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, Locale.getDefault()));
        }

        public DateAdapter(final Class<T> clz, final DateFormat fmt) {
            this.format = fmt;
            this.clazz = (Class) (clz == null ? Date.class : clz);
        }

        @Override
        public T read(final JsonReader in) throws IOException {
            final JsonToken t = in.peek();
            if (t.equals(JsonToken.STRING)) {
                final String d = in.nextString();
                if (this.format == null) {
                    return StringUtils.to(this.clazz, d, this);
                } else {
                    try {
                        Date dob = this.format.parse(d);
                        if (this.clazz.equals(Timestamp.class)) {
                            dob = new Timestamp(dob.getTime());
                        }
                        return (T) dob;
                    } catch (final ParseException e) {
                        return StringUtils.to(this.clazz, d, this);
                    }
                }
            } else if (t.equals(JsonToken.NULL)) {
                in.nextNull();
                return null;
            } else {
                return ReflectionUtils.getInstance(this.clazz, in.nextLong());
            }
        }

        @Override
        public void write(final JsonWriter out, final T value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else if (this.format == null) {
                out.value(value.getTime());
            } else {
                out.value(this.format.format(value));
            }
        }
    }

    private static class DetoxTypeAdapterFactory implements TypeAdapterFactory {
        private TypeAdapterFactory reflective;
        private final DateFormat format;

        private DetoxTypeAdapterFactory(final DateFormat fmt) {
            this.format = fmt;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(final Gson gson, final TypeToken<T> type) {
            TypeAdapter<T> ret = null;
            if (type.getRawType().equals(URL.class)) {
                ret = (TypeAdapter<T>) new TypeAdapter<URL>() {
                    @Override
                    public URL read(final JsonReader in) throws IOException {
                        final JsonToken t = in.peek();
                        if (t.equals(JsonToken.NULL)) {
                            in.nextNull();
                            return null;
                        }
                        final String url = in.nextString();
                        return URL.valueOf(url);
                    }

                    @Override
                    public void write(final JsonWriter out, final URL value) throws IOException {
                        out.value(value == null ? null : value.toFullString());
                    }
                };
            } else if (Enum.class.isAssignableFrom(type.getRawType())) {
                ret = (TypeAdapter<T>) new TypeAdapter<Enum>() {
                    @Override
                    public Enum read(final JsonReader in) throws IOException {
                        final JsonToken t = in.peek();
                        if (t.equals(JsonToken.NULL)) {
                            in.nextNull();
                            return null;
                        } else if (t.equals(JsonToken.STRING)) {
                            final String es = in.nextString();
                            return StringUtils.to(type.getRawType(), es, this);
                        } else {
                            return (Enum) type.getRawType().getEnumConstants()[in.nextInt()];
                        }
                    }

                    @Override
                    public void write(final JsonWriter out, final Enum value) throws IOException {
                        out.value(value == null ? null : value.toString());
                    }
                };
            } else if (Date.class.isAssignableFrom(type.getRawType())) {
                ret = new DateAdapter(type.getRawType(), this.format);
            }
            return ret;
        }

        private synchronized TypeAdapterFactory reflect(final Gson gson) {
            if (this.reflective == null) {
                final Collection<TypeAdapterFactory> fcts = ReflectionUtils.getProperty(gson, "factories");
                for (final TypeAdapterFactory fct : fcts) {
                    if (fct instanceof ReflectiveTypeAdapterFactory) {
                        this.reflective = fct;
                        break;
                    }
                }
            }
            return this.reflective;
        }
    }

    private static final MessageDigest MD5;
    public static final ObjectMapper OM = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    public static final Gson GSON;
    public static final Gson GSON_PRETTY;
    public static final JsonParser JSON_PARSER = new JsonParser();

    static {
        GSON = JSonUtils.gson(StringUtils.FORMAT1).create();
        GSON_PRETTY = JSonUtils.gson(StringUtils.FORMAT1).setPrettyPrinting().create();
        try {
            MD5 = MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException e) {
            throw new ExceptionInInitializerError(e);
        }
        Configuration.setDefaults(defaults());
    }

    private static Configuration.Defaults defaults() {
        return new Configuration.Defaults() {
            private final JsonProvider jsonProvider = new GsonJsonProvider(JSonUtils.GSON);
            private final MappingProvider mappingProvider = new GsonMappingProvider(JSonUtils.GSON);

            @Override
            public JsonProvider jsonProvider() {
                return this.jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider() {
                return this.mappingProvider;
            }

            @Override
            public Set<Option> options() {
                return EnumSet.noneOf(Option.class);
            }
        };
    }

    public static boolean expected(final Object el, final Object expected, final Function<Object[], Boolean> failureListener) throws IOException {
        return JSonUtils.expected(el, expected, failureListener, false, false);
    }

    public static boolean expected(final Object el, final Object expected, final Function<Object[], Boolean> failureListener,
                                   final boolean sameSize, final boolean checkAllowedFields) throws IOException {
        return JSonUtils.expected(el, expected, failureListener, sameSize, "$", checkAllowedFields);
    }

    public static boolean expected(final Object eloo, final Object expectedo, final Function<Object[], Boolean> failureListener,
                                   final boolean sameSize, final String pth, final boolean checkNotAllowedFields) throws IOException {
        final JsonElement el = JSonUtils.toElement(eloo);
        final JsonElement expected = JSonUtils.toElement(expectedo);
        boolean ret = false;
        boolean hasNotAllowedFields = false;
        if (StringUtils.isNull(expected)) {
            ret = true; // We have no expectation, this field can be there or not, has value or not
        } else if (expected.isJsonPrimitive()) {
            final JsonPrimitive pexp = expected.getAsJsonPrimitive();
            if (pexp.isBoolean()) {
                // If the expected is a boolean then if true,
                // then the value can't be null-like
                if (StringUtils.isNull(el) || !el.isJsonPrimitive() || !((JsonPrimitive) el).isBoolean()) {
                    final boolean elSet = !StringUtils.isNull(el);
                    ret = pexp.getAsBoolean() && elSet ? true : !pexp.getAsBoolean() && !elSet ? true : false;
                }
            } else if (!StringUtils.isNull(el)) {
                if (pexp.isString() && (el.isJsonArray() || el.isJsonObject())) {
                    // If the expectation is a string for an object,
                    // then it is the hash of the data that we expect
                    synchronized (JSonUtils.MD5) {
                        JSonUtils.MD5.reset();
                        byte[] digest = JSonUtils.MD5.digest(el.toString().getBytes());
                        final String eld = new String(Hex.encodeHex(digest));
                        ret = pexp.getAsString().equalsIgnoreCase(eld);
                    }
                } else if (pexp.isNumber()) {
                    // Expectation can be a number, then just check
                    // the "size" of the element we have
                    final int nexp = pexp.getAsInt();
                    ret = el.isJsonArray() && el.getAsJsonArray().size() == nexp || //
                            el.isJsonObject() && el.getAsJsonObject().entrySet().size() == nexp || //
                            el.isJsonPrimitive() && ((JsonPrimitive) el).isString() && ((JsonPrimitive) el).getAsString().length() == nexp;
                }
            }
        } else if (expected.isJsonArray()) {
            if (el.isJsonArray()) {
                final JsonArray exa = expected.getAsJsonArray();
                final Iterator<JsonElement> elai = el.getAsJsonArray().iterator();
                boolean allOk = !sameSize || el.getAsJsonArray().size() == exa.size();
                if (allOk) {
                    int i = 0;
                    JsonElement lse = null;
                    for (final JsonElement se : expected.getAsJsonArray()) {
                        lse = se;
                        if (!elai.hasNext()) {
                            allOk = false;
                            break;
                        }
                        final JsonElement extn = elai.next();
                        allOk &= JSonUtils.expected(extn, se, failureListener, sameSize, pth + "[" + i++ + "]", checkNotAllowedFields);
                        if (!allOk && failureListener == null) {
                            break;
                        }
                    }
                    if (lse != null) {
                        while (elai.hasNext()) {
                            allOk &= JSonUtils.expected(elai.next(), lse, failureListener, sameSize, pth + "[" + i++ + "]", checkNotAllowedFields);
                            if (!allOk && failureListener == null) {
                                break;
                            }
                        }
                    }
                    ret = allOk;
                }
            } else {
                // We set an array as expectation,
                // then the element must match one of the expectation in the array
                boolean cret;
                for (final JsonElement iexp : expected.getAsJsonArray()) {
                    cret = JSonUtils.expected(el, iexp, null);
                    if (cret) {
                        ret = cret;
                        break;
                    }
                }
            }
        } else if (expected.isJsonObject()) {
            final JsonObject exo = expected.getAsJsonObject();
            if (el.isJsonObject()) {
                final JsonObject elo = el.getAsJsonObject();
                ret = !sameSize || exo.entrySet().size() == elo.entrySet().size();
                if (checkNotAllowedFields) {
                    final List allowedFields = exo.entrySet().stream().map(entry -> entry.getKey()).collect(Collectors.toList());
                    hasNotAllowedFields = elo.entrySet().stream().filter(entry -> !allowedFields.contains(entry.getKey())).count() != 0;
                    ret &= !checkNotAllowedFields || !hasNotAllowedFields;
                }
                if (ret) {
                    JsonElement elsue;
                    for (final Map.Entry<String, JsonElement> sekv : exo.entrySet()) {
                        elsue = elo.get(sekv.getKey());
                        ret &= JSonUtils.expected(elsue, sekv.getValue(), failureListener, sameSize, pth + "." + sekv.getKey(), checkNotAllowedFields);
                        if (!ret && failureListener == null) {
                            break;
                        }
                    }
                }
            }
        }
        if (!ret) {
            if (ObjectUtils.equals(expected, el)) {
                ret = true;
            } else if (failureListener != null) {
                final Object[] state = new Object[]{pth, el, expected, checkNotAllowedFields && hasNotAllowedFields};
                try {
                    final Boolean reto = failureListener.apply(state);
                    if (reto != null) {
                        ret = reto;
                    }
                } catch (final RuntimeException re) {
                    throw ReflectionUtils.extendMessage(re, "args=" + Arrays.toString(state));
                }
            }
        }
        return ret;
    }

    public static Object getProperty(final JsonObject obj, final String key) {
        if (obj == null) {
            return null;
        }
        final JsonElement el = obj.get(key);
        return JSonUtils.unwrap(el);
    }

    public static GsonBuilder gson(final DateFormat fmt) {
        return new GsonBuilder() // We have our own strategy...
                .registerTypeAdapterFactory(new DetoxTypeAdapterFactory(fmt)) // Extended type handling
                ;
    }

    public static <T extends JsonElement> List<T> gsonPath(final Object any, final String pth, final EvaluationListener... list) throws IOException {
        return JSonUtils.gsonPath(any, pth, null, list);
    }

    public static <T extends JsonElement> List<T> gsonPath(Object any, final String pth, final InheritableThreadLocal<ReadContext> ctxl,
                                                           final EvaluationListener... list) throws IOException {
        any = JSonUtils.toElement(any);
        final String[] pths = pth.split(",");
        final List<T> ret = new LinkedList<>();
        final ReadContext ctx = JsonPath.parse(any, Configuration.defaultConfiguration())
                .withListeners(list);
        if (ctxl != null) {
            ctxl.set(ctx);
        }
        for (final String p : pths) {
            try {
                final Object sany = ctx.read(p);
                if (sany instanceof JsonArray) {
                    var iter = (Iterator) ((JsonArray) sany).iterator();
                    ret.addAll(org.apache.commons.collections4.IteratorUtils.toList(iter));
                } else {
                    ret.add((T) sany);
                }
            } catch (final com.jayway.jsonpath.PathNotFoundException pnf) {
                // So what? Skip it then
                pnf.printStackTrace();
            }
        }
        return ret;
    }

    public static <T> T gsonPathObject(final Object any, final String pth, final EvaluationListener... list) throws IOException {
        final JsonElement el = JSonUtils.gsonPathSingle(any, pth, list);
        return JSonUtils.unwrap(el);
    }

    public static <T extends JsonElement> T gsonPathSingle(final Object any, final String pth, final EvaluationListener... list) throws IOException {
        final List<T> lst = JSonUtils.gsonPath(any, pth, list);
        T ret = CollectionUtils.isEmpty(lst) ? null : lst.get(0);
        if (ret != null && ret.isJsonNull()) {
            ret = null;
        }
        return ret;
    }

    public static boolean has(final JsonArray arr, final Object obj) {
        final int idx = JSonUtils.indexOf(arr, obj);
        return idx > org.apache.commons.lang3.ArrayUtils.INDEX_NOT_FOUND;
    }

    public static int indexOf(final JsonArray arr, final Object obj) {
        final Object uobj = obj instanceof JsonElement ? JSonUtils.unwrap((JsonElement) obj) : obj;
        int idx = 0;
        for (final JsonElement e : arr) {
            final Object ue = JSonUtils.unwrap(e);
            if (ObjectUtils.equals(ue, uobj)) {
                return idx;
            }
            idx++;
        }
        return org.apache.commons.lang3.ArrayUtils.INDEX_NOT_FOUND;
    }

    public static <T> T iterator(final Object any) throws IOException {
        final JsonElement el = JSonUtils.toElement(any);
        if (el.isJsonPrimitive() || el.isJsonNull()) {
            return (T) org.apache.commons.collections4.IteratorUtils.singletonIterator(el);
        } else if (el.isJsonObject()) {
            return (T) el.getAsJsonObject().entrySet().iterator();
        } else if (el.isJsonArray()) {
            final List lst = (List) ReflectionUtils.getProperty(el.getAsJsonArray(), "elements");
            return (T) lst.listIterator();
        }
        return null;
    }

    public static JsonObject read(final ResultSet rs, final DateFormat dates) throws SQLException {
        final ResultSetMetaData rsm = rs.getMetaData();
        final int cc = rsm.getColumnCount();
        final JsonObject ret = new JsonObject();
        for (int i = 1; i <= cc; i++) {
            Object o = rs.getObject(i);
            final String lab = rsm.getColumnLabel(i);
            if (o instanceof Date && dates != null) {
                o = dates.format(o);
            }
            JSonUtils.setProperty(ret, lab, o);
        }
        return ret;
    }

    public static void setProperty(final JsonObject obj, final String key, final Object val) {
        if (val instanceof Number) {
            obj.addProperty(key, (Number) val);
        } else if (val instanceof String) {
            obj.addProperty(key, (String) val);
        } else if (val instanceof Character) {
            obj.addProperty(key, (Character) val);
        } else if (val instanceof Boolean) {
            obj.addProperty(key, (Boolean) val);
        } else if (val == null) {
            obj.add(key, JsonNull.INSTANCE);
        }
    }

    public static <T extends JsonElement> T toElement(Object any) throws IOException {
        CharIOHelper cio = CharIOHelper.attempt(any);
        if (cio != null) return (T) JSonUtils.JSON_PARSER.parse(cio.toText());
        else if (any instanceof JsonElement) return (T) any;
        else return (T) JSonUtils.GSON.toJsonTree(any);
    }

    public static <T> T toObject(final Object any, final Class<T> clz) throws IOException {
        final JsonElement el = JSonUtils.toElement(any);
        if (JsonElement.class.isAssignableFrom(clz) || clz == null) {
            return (T) el;
        }
        return JSonUtils.GSON.fromJson(el, clz);
    }

    public static String toString(final Object any) {
        if (any instanceof CharSequence) {
            return String.valueOf(any);
        } else if (any instanceof JsonElement) {
            return JSonUtils.GSON.toJson((JsonElement) any);
        }
        return JSonUtils.GSON.toJson(any);
    }

    public static <T> T unwrap(final JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return null;
        } else if (el.isJsonPrimitive()) {
            final JsonPrimitive pri = (JsonPrimitive) el;
            if (pri.isBoolean()) {
                return (T) (Boolean) pri.getAsBoolean();
            } else if (pri.isNumber()) {
                return (T) pri.getAsNumber();
            } else {
                return (T) pri.getAsString();
            }
        } else {
            return (T) el;
        }
    }

    public static Object getField(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) return null;
        if (value.isInt()) return value.intValue();
        if (value.isLong()) return value.longValue();
        if (value.isDouble()) return value.doubleValue();
        if (value.isTextual()) return value.textValue();
        if (value.isBoolean()) return value.booleanValue();
        return value.toString();
    }
}
