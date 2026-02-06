package hu.detox.parsers;

import com.google.gson.*;
import hu.detox.utils.ReflectionUtils;
import hu.detox.utils.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;

import java.io.IOException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonConverter implements Function<Object, Object> {
    private static final String PELEMENT = "e";
    private static final Pattern NUMERIC = Pattern.compile("^[0-9]");
    public static final String ATYPE = "t";
    public static final String APREFX = "p";
    public static final JsonConverter INSTANCE = new JsonConverter();

    private JsonConverter() {
        // Singleton
    }

    private JsonElement getFromString(final String str, final Class<?> typ) {
        if (str == null || Void.class.equals(typ)) {
            return JsonNull.INSTANCE;
        } else if (typ == null) {
            if (Boolean.FALSE.toString().equalsIgnoreCase(str) || Boolean.TRUE.toString().equalsIgnoreCase(str)) {
                return new JsonPrimitive(Boolean.parseBoolean(str));
            }
            try {
                final Number n = StringUtils.to(Double.class, str, this);
                return new JsonPrimitive(n);
            } catch (IllegalArgumentException | IllegalStateException e) {
                return new JsonPrimitive(str);
            }
        } else if (Number.class.isAssignableFrom(typ)) {
            if (Float.class.isAssignableFrom(typ) || Double.class.isAssignableFrom(typ)) {
                final Double d = StringUtils.to(Double.class, str, this);
                return new JsonPrimitive(d);
            }
            final BigDecimal d = StringUtils.to(BigDecimal.class, str, this);
            return new JsonPrimitive(d);
        } else if (Boolean.class.equals(typ)) {
            final Boolean b = StringUtils.to(Boolean.class, str, this);
            return new JsonPrimitive(b);
        } else {
            return new JsonPrimitive(str);
        }
    }

    private String getName(final Element e) {
        final String pfx = e.attributeValue(JsonConverter.APREFX);
        String ret = e.attributeValue(JsonConverter.PELEMENT);
        if (ret == null) {
            ret = e.getName();
        }
        if (pfx != null) {
            ret = ret.replaceFirst(pfx, org.apache.commons.lang3.StringUtils.EMPTY);
        }
        return ret;
    }

    private void setName(final Element e, String nam) {
        final Matcher m = JsonConverter.NUMERIC.matcher(nam);
        if (m.find()) {
            nam = JsonConverter.PELEMENT + nam;
            e.addAttribute(JsonConverter.APREFX, JsonConverter.PELEMENT);
        }
        e.setName(nam);
    }

    private JsonElement toJson(final Element node) {
        if (node instanceof Document) {
            return this.toJson(((Document) node).getRootElement());
        }
        if (!node.hasContent()) {
            return JsonNull.INSTANCE;
        }
        String at = node.attributeValue(JsonConverter.ATYPE);
        final String nam = this.getName(node);
        Class<?> el = JsonPrimitive.class;
        Class<?> act = null;
        boolean ni = false;
        if (at == null) {
            at = nam;
            ni = true;
        }
        try {
            act = org.apache.commons.lang3.StringUtils.EMPTY.equals(at) ? String.class : ReflectionUtils.toClass(at);
        } catch (final ClassNotFoundException e) {
            try {
                el = ReflectionUtils.toClass(JsonElement.class.getName().replace(Element.class.getSimpleName(), at));
            } catch (final ClassNotFoundException e1) {
                try {
                    el = ReflectionUtils.toClass(JsonElement.class.getName().replace(Element.class.getSimpleName(), nam));
                } catch (final ClassNotFoundException e2) {
                    // Nothing we can do more... We will detect the type
                }
            }
        }
        if (Object.class.equals(act)) {
            el = JsonObject.class;
        }
        if (JsonPrimitive.class.equals(el) || JsonElement.class.equals(el)) {
            return this.getFromString(node.getText(), act);
        } else if (JsonNull.class.isAssignableFrom(el)) {
            return JsonNull.INSTANCE;
        } else if (JsonArray.class.isAssignableFrom(el)) {
            final JsonArray ret = new JsonArray();
            if (ni) {
                final Iterator<Node> nii = node.nodeIterator();
                Node sn;
                while (nii.hasNext()) {
                    sn = nii.next();
                    ret.add(this.toJson(sn));
                }
            } else {
                for (final Element se : (List<Element>) node.elements()) {
                    ret.add(this.toJson(se));
                }
            }
            return ret;
        }
        final JsonObject ret = new JsonObject();
        for (final Element se : (List<Element>) node.elements()) {
            final String kn = this.getName(se);
            ret.add(kn, this.toJson(se));
        }
        return ret;
    }

    private JsonElement toJson(final Node node) {
        if (node instanceof Document) {
            return this.toJson(((Document) node).getRootElement());
        } else if (node instanceof Element) {
            return this.toJson((Element) node);
        }
        return this.getFromString(node.getText(), null);
    }

    private Element toXmlElement(final JsonElement el) {
        Element xel = null;
        String tattr = null;
        if (el == null || el instanceof JsonNull) {
            xel = DocumentHelper.createElement("Null");
        } else if (el instanceof JsonPrimitive) {
            final JsonPrimitive pri = (JsonPrimitive) el;
            xel = DocumentHelper.createElement("Primitive");
            tattr = pri.isBoolean() ? Boolean.class.getSimpleName() : pri.isNumber() ? Number.class.getSimpleName() : org.apache.commons.lang3.StringUtils.EMPTY;
            xel.addAttribute(JsonConverter.ATYPE, tattr);
            xel.setText(pri.getAsString());
        } else if (el instanceof JsonArray) {
            final JsonArray arr = (JsonArray) el;
            xel = DocumentHelper.createElement(Array.class.getSimpleName());
            Element sn;
            for (final JsonElement sel : arr) {
                sn = this.toXmlElement(sel);
                xel.add(sn);
            }
        } else {
            final JsonObject obj = (JsonObject) el;
            xel = DocumentHelper.createElement(Object.class.getSimpleName());
            Element sn;
            for (final Map.Entry<String, JsonElement> sel : obj.entrySet()) {
                sn = this.toXmlElement(sel.getValue());
                tattr = sn.attributeValue(JsonConverter.ATYPE);
                if (tattr == null) {
                    sn.addAttribute(JsonConverter.ATYPE, sn.getName());
                }
                this.setName(sn, sel.getKey());
                xel.add(sn);
            }
        }
        return xel;
    }

    @Override
    public Object apply(final Object input) {
        if (input instanceof Node) {
            return this.toJson((Node) input);
        } else if (input instanceof JsonElement) {
            return this.toXmlElement((JsonElement) input);
        } else if (input instanceof CharSequence) {
            final JsonElement el;
            try {
                el = JSonUtil.toElement(input);
                return this.toXmlElement(el);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        } else {
            return input;
        }
    }
}
