package hu.detox.utils.url;

import hu.detox.utils.strings.StringUtils;
import hu.detox.utils.SystemUtils;
import hu.detox.utils.reflection.ReflectionUtils;
import org.apache.commons.collections4.keyvalue.DefaultMapEntry;
import org.apache.commons.configuration2.Configuration;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//CHOFF This is a class copied from the Internet without change

/**
 * URL (Uniform Resource Locator) that is modifiable, and can parse almost any string that can be a part of an URL.
 * <p>
 * URL examples:
 * <ul>
 * <li>http://www.facebook.com/friends?param1=value1&amp;param2=value2
 * <li>http://username:password@10.20.130.230:8080/list?version=1.0.0
 * <li>ftp://username:password@192.168.1.7:21/1/read.txt
 * <li>registry://192.168.1.7:9090/com.alibaba.service1?param1=value1&amp;param2=value2
 * </ul>
 * <p>
 *
 * @see java.net.URL
 * @see java.net.URI
 */
public final class URL implements Cloneable, Serializable {
    public enum Segment {
        protocol, username, password, host, port, path, parameters, queryString, ref;

        public boolean included(final Segment parFrom, final Segment parTo) {
            return this.ordinal() >= parFrom.ordinal() && this.ordinal() <= parTo.ordinal();
        }
    }

    private static final String FILE = "file";

    public static final int NO_PORT = -1;
    private static final String HOST_START = "//";
    private static final String CRED_END = "@";
    private static final String PROTO_END = ":";
    public static final String QUERY_START = "?";
    private static final String REF_START = "#";
    // QUERY parser constants
    public static final String QUERY_SEP = "&";
    private static final String QUERY_VALUE_SEP = "=";
    private static final Pattern KVP_PATTERN = Pattern.compile("([^" + URL.QUERY_VALUE_SEP + "]+)" + URL.QUERY_VALUE_SEP + "(.*)");
    private static Configuration protoPorts; // Lazy init as this call can be used early on startup
    private static final long serialVersionUID = -913324759174839320L;

    public static Map<String, String> toStringMap(final String... parPairs) {
        final Map<String, String> parameters = new HashMap<>();
        if (parPairs.length > 0) {
            if (parPairs.length % 2 != 0) {
                throw new IllegalArgumentException("pairs must be even.");
            }
            for (int i = 0; i < parPairs.length; i = i + 2) {
                parameters.put(parPairs[i], parPairs[i + 1]);
            }
        }
        return parameters;
    }

    public static boolean buildParameters(final Charset encode, final String sep, final Collection<Map.Entry<String, Object>> entries, final boolean nullSkip,
                                          final StringBuilder parBuf, final String... parIncluded) {
        if (entries == null) {
            return false;
        }
        final List<String> includes = parIncluded == null || parIncluded.length == 0 ? null : Arrays.asList(parIncluded);
        boolean first = true;
        for (final Map.Entry<String, Object> entry : entries) {
            final String key = entry.getKey();
            if (nullSkip && entry.getValue() == null) {
                continue;
            }
            String value = String.valueOf(entry.getValue());
            if (encode != null) {
                value = URL.encode(value, encode);
            }
            if (StringUtils.isNotBlank(key) && (includes == null || includes.contains(key))) {
                if (first) {
                    first = false;
                } else {
                    parBuf.append(sep);
                }
                parBuf.append(key);
                if (value != null) {
                    parBuf.append(URL.QUERY_VALUE_SEP).append(value);
                }
            }
        }
        return true;

    }

    public static boolean buildParameters(final Charset encode, final String sep, final Map<String, Object> entries, final boolean nullSkip,
                                          final StringBuilder parBuf, final String... parIncluded) {
        if (entries == null) {
            return false;
        }
        return URL.buildParameters(encode, sep, entries.entrySet(), nullSkip, parBuf, parIncluded);
    }

    public static boolean buildParameters(final String sep, final Collection<Map.Entry<String, Object>> entries, final boolean nullSkip,
                                          final StringBuilder parBuf, final String... parIncluded) {
        return URL.buildParameters(Charset.defaultCharset(), sep, entries, nullSkip, parBuf, parIncluded);
    }

    public static boolean buildParameters(final String sep, final Map<String, Object> entries, final boolean nullSkip, final StringBuilder parBuf,
                                          final String... parIncluded) {
        return URL.buildParameters(Charset.defaultCharset(), sep, entries, nullSkip, parBuf, parIncluded);
    }

    public static String decode(final String parValue) {
        return URL.decode(parValue, Charset.defaultCharset());
    }

    public static String decode(final String parValue, final Charset cs) {
        if (parValue == null) {
            return null;
        }
        try {
            return URLDecoder.decode(parValue, (cs == null ? Charset.defaultCharset() : cs).name());
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public static String encode(final String parValue) {
        return URL.encode(parValue, Charset.defaultCharset());
    }

    public static String encode(final String parValue, final Charset cs) {
        try {
            return URLEncoder.encode(parValue, (cs == null ? Charset.defaultCharset() : cs).name());
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static Configuration getPorts() {
        if (URL.protoPorts == null) {
            //URL.protoPorts = Main.mine().subset("protoPort");
        }
        return URL.protoPorts;
    }

    public static boolean isKnownProtocol(String url) {
        if (StringUtils.isEmpty(url)) {
            return false;
        }
        try {
            final java.net.URL jurl = new java.net.URL(url);
            jurl.getClass();
            return true;
        } catch (final MalformedURLException e) {
            int cln = url.indexOf(':');
            if (cln == org.apache.commons.lang3.ArrayUtils.INDEX_NOT_FOUND) {
                return false;
            }
            url = url.substring(0, cln);
            cln = URL.getPorts().getInt(url, org.apache.commons.lang3.ArrayUtils.INDEX_NOT_FOUND);
            if (cln > org.apache.commons.lang3.ArrayUtils.INDEX_NOT_FOUND) {
                return true;
            }
            return false;
        }
    }

    private static LinkedList<Map.Entry<String, Object>> parseKeyValuePair(final String parStr, final String parItemSeparator) {
        if (parStr == null) {
            return new LinkedList<>();
        }
        final String[] tmp = parStr.split(parItemSeparator);
        final LinkedList<Map.Entry<String, Object>> map = new LinkedList<>();
        for (final String element : tmp) {
            final Matcher matcher = URL.KVP_PATTERN.matcher(element);
            if (matcher.matches()) {
                final String val = matcher.group(2);
                map.add(new DefaultMapEntry<>(URL.decode(matcher.group(1)), URL.decode(val)));
            } else {
                map.add(new DefaultMapEntry<>(URL.decode(element), null));
            }
        }
        return map;
    }

    public static Map<String, Object> parseListedQueryString(final String parQs) {
        return URL.parseListedQueryString(parQs, URL.QUERY_SEP);
    }

    public static Map<String, Object> parseListedQueryString(final String parQs, final String sep) {
        final LinkedList<Map.Entry<String, Object>> list = URL.parseKeyValuePair(parQs, sep);
        final Map<String, Object> ret = new HashMap<>();
        for (final Map.Entry<String, Object> p : list) {
            final Object val = ret.get(p.getKey());
            if (val == null) {
                ret.put(p.getKey(), p.getValue());
            } else if (val instanceof List) {
                ((List<Object>) val).add(p.getValue());
            } else {
                final LinkedList<Object> nv = new LinkedList<>();
                nv.add(val);
                nv.add(p.getValue());
                ret.put(p.getKey(), nv);
            }
        }
        return ret;
    }

    public static LinkedList<Map.Entry<String, Object>> parseQueryString(final String parQs) {
        return URL.parseKeyValuePair(parQs, URL.QUERY_SEP);
    }

    public static URL valueOf(String parUrl) {
        parUrl = StringUtils.trimToNull(parUrl.trim());
        if (parUrl == null) return null;
        URL t = new URL();
        t.setPort(URL.NO_PORT);

        int i = parUrl.indexOf(URL.REF_START);
        if (i >= 0) {
            t.ref = parUrl.substring(i + 1);
            parUrl = parUrl.substring(0, i);
        }
        final Matcher m = t.queryStart.matcher(parUrl);
        if (m.find()) {
            final String q = parUrl.substring(m.end());
            t.parameters = URL.parseKeyValuePair(q, t.querySep);
            parUrl = parUrl.substring(0, m.start());
        }
        i = parUrl.indexOf(URL.HOST_START);
        if (i == 0) {
            parUrl = parUrl.substring(URL.HOST_START.length());
        }
        i = parUrl.indexOf(URL.PROTO_END + URL.HOST_START);
        if (i == org.apache.commons.lang3.ArrayUtils.INDEX_NOT_FOUND) {
            i = parUrl.indexOf(URL.PROTO_END);
            if (i == 1) {
                // This is potentially a full path with drive at the beginning in windows..
                t.setInternalProtocol(URL.FILE);
                t.setPath("/" + parUrl);
                parUrl = StringUtils.EMPTY;
            } else if (i > 1) {
                t.setInternalProtocol(parUrl.substring(0, i));
                if (!URL.FILE.equals(t.getProtocol()) && t.isNotRecognizedProtocol()) {
                    throw new IllegalStateException("Unknown protocol '" + t.getProtocol() + "' in \"" + parUrl + "\"");
                }
                parUrl = parUrl.substring(i + URL.PROTO_END.length());
            }
        } else if (i > 0) {
            t.setInternalProtocol(parUrl.substring(0, i));
            parUrl = parUrl.substring(i + 3);
        } else {
            throw new IllegalStateException("Explicitely omitted protocol in \"" + parUrl + "\"");
        }
        i = parUrl.indexOf(URL.CRED_END);
        if (i >= 0) {
            final String up = parUrl.substring(0, i);
            t.setCredentials(up);
            parUrl = parUrl.substring(i + 1);
        }
        if (t.path == null) {
            i = parUrl.indexOf("/");
            if (i >= 0) {
                t.setPath(URL.decode(parUrl.substring(i)));
                parUrl = parUrl.substring(0, i);
            }
            i = parUrl.indexOf(":");
            if (i >= 0 && i < parUrl.length() - 1) {
                final String pss = parUrl.substring(i + 1);
                try {
                    t.setPort(Integer.parseInt(pss));
                    parUrl = parUrl.substring(0, i);
                } catch (final NumberFormatException nfe) {
                    if (t.path == null) {
                        t.setPath(pss);
                    } else {
                        t.setPath(pss + t.path);
                    }
                    t.protocol = parUrl.substring(0, i);
                    parUrl = "";
                }
            }
        }
        if (parUrl.length() > 0) {
            t.host = parUrl;
        }
        return t;
    }

    private Charset encode = Charset.defaultCharset();
    private String protocol;
    private UserPass credentials;
    private String host;
    private int port;
    private String path;
    private LinkedList<Map.Entry<String, Object>> parameters;
    private String ref;
    private transient volatile InetAddress ip;
    private Pattern queryStart;
    private String queryStartString;
    private String querySep = URL.QUERY_SEP;

    public URL() {
        this.setQueryStartString(URL.QUERY_START);
    }

    public URL(final String parProtocol, final String parHost, final int parPort) {
        this(parProtocol, null, null, parHost, parPort, null, (Map<String, String>) null);
    }

    public URL(final String parProtocol, final String parHost, final int parPort, final Map<String, ?> parPars) {
        this(parProtocol, null, null, parHost, parPort, null, parPars);
    }

    public URL(final String parProtocol, final String parHost, final int parPort, final String... parPars) {
        this(parProtocol, null, null, parHost, parPort, null, toStringMap(parPars));
    }

    public URL(final String parProtocol, final String parHost, final int parPort, final String parPath) {
        this(parProtocol, null, null, parHost, parPort, parPath, (Map<String, String>) null);
    }

    public URL(final String parProtocol, final String parHost, final int parPort, final String parPath, final Map<String, ?> parPars) {
        this(parProtocol, null, null, parHost, parPort, parPath, parPars);
    }

    public URL(final String parProtocol, final String parHost, final int parPort, final String parPath, final String... parPars) {
        this(parProtocol, null, null, parHost, parPort, parPath, toStringMap(parPars));
    }

    public URL(final String parProtocol, final String parUsername, final String parPassword, final String parHost, final int parPort, final String parPath) {
        this(parProtocol, parUsername, parPassword, parHost, parPort, parPath, (Map<String, String>) null);
    }

    public URL(final String parProtocol, final String parUsername, final String parPassword, final String parHost, final int parPort, final String parPath,
               final Map<String, ?> parPars) {
        this();
        if (StringUtils.isBlank(parUsername) && StringUtils.isNotBlank(parPassword)) {
            throw new IllegalArgumentException("Invalid url, password without username!");
        }
        this.protocol = parProtocol;
        this.credentials = new UserPass(parUsername, parPassword);
        this.setHost(parHost);
        this.setPort(parPort);
        this.setPath(parPath);
        if (parPars == null) {
            this.parameters = new LinkedList<>();
        } else {
            this.parameters = new LinkedList<>();
        }
    }

    public URL(final String parProtocol, final String parUsername, final String parPassword, final String parHost, final int parPort, final String parPath,
               final String... parPairs) {
        this(parProtocol, parUsername, parPassword, parHost, parPort, parPath, toStringMap(parPairs));
    }

    public void addParameter(final Map.Entry<String, ?> parEnt) {
        this.addParameter(parEnt.getKey(), parEnt.getValue());
    }

    public void addParameter(final String parKey, final byte parValue) {
        this.addParameter(parKey, Byte.valueOf(parValue));
    }

    public void addParameter(final String parKey, final char parValue) {
        this.addParameter(parKey, Character.valueOf(parValue));
    }

    public void addParameter(final String parKey, final double parValue) {
        this.addParameter(parKey, Double.valueOf(parValue));
    }

    public void addParameter(final String parKey, final Enum<?> parValue) {
        this.addParameter(parKey, (Object) parValue);
    }

    public void addParameter(final String parKey, final float parValue) {
        this.addParameter(parKey, Float.valueOf(parValue));
    }

    public void addParameter(final String parKey, final Object parValue) {
        if (this.parameters == null) {
            this.parameters = new LinkedList<>();
        }
        this.parameters.add(new DefaultMapEntry<>(parKey, parValue));
    }

    public void addParameters(final Collection<Map.Entry<String, Object>> parPars) {
        if (parPars == null) {
            return;
        }
        for (final Map.Entry<String, ?> e : parPars) {
            this.addParameter(e);
        }
    }

    public void addParameters(final Map<String, ?> parPars) {
        if (parPars == null) {
            return;
        }
        for (final Map.Entry<String, ?> e : parPars.entrySet()) {
            this.addParameter(e);
        }
    }

    public void addParameters(final String... parPairs) {
        if (parPairs == null) {
            return;
        }
        if (parPairs.length % 2 != 0) {
            throw new IllegalArgumentException("Map pairs can not be odd number.");
        }
        final int len = parPairs.length / 2;
        for (int i = 0; i < len; i++) {
            this.addParameter(parPairs[2 * i], parPairs[2 * i + 1]);
        }

    }

    public void addParameterString(final String parQuery) {
        if (parQuery == null) {
            return;
        }
        this.addParameters(URL.parseKeyValuePair(parQuery, this.querySep));
    }

    public boolean buildParameters(final StringBuilder parBuf, final String... parIncluded) {
        return URL.buildParameters(this.encode, this.querySep, this.getParameters(), false, parBuf, parIncluded);
    }

    public int calcResolvedPort() {
        int ret = this.port;
        if (ret == URL.NO_PORT) {
            ret = URL.getPorts().getInt(this.protocol, URL.NO_PORT);
        }
        return ret;
    }

    public <T extends BasicClassicHttpRequest> T toMethod(final Class<T> mc) throws IOException {
        final BasicClassicHttpRequest m = ReflectionUtils.getInstance(mc, toURI());
        m.setHeader(HttpHeaders.AUTHORIZATION, getAuthentication());
        return (T) m;
    }

    public static URL fromURL(final java.net.URL u) {
        URL t = new URL();
        t.protocol = u.getProtocol();
        t.setPort(u.getPort());
        t.setHost(u.getHost());
        if (!StringUtils.isNotEmpty(u.getAuthority()) && !u.getHost().equals(u.getAuthority())) {
            t.setCredentials(u.getAuthority());
            final UserPass uu = t.getUserPass();
            if (uu != null && StringUtils.isEmpty(uu.getPassword())) {
                t.setCredentials(u.getUserInfo());
            }
        }
        t.setPath(u.getPath());
        if (u.getQuery() == null) {
            t.parameters = null;
        } else {
            t.parameters = URL.parseKeyValuePair(u.getQuery(), t.querySep);
        }
        t.ref = u.getRef();
        return t;
    }

    private void fromString(String parUrl) {
        parUrl = parUrl.trim();
        this.setPort(URL.NO_PORT);

        int i = parUrl.indexOf(URL.REF_START);
        if (i >= 0) {
            this.ref = parUrl.substring(i + 1);
            parUrl = parUrl.substring(0, i);
        }
        final Matcher m = this.queryStart.matcher(parUrl);
        if (m.find()) {
            final String q = parUrl.substring(m.end());
            this.parameters = URL.parseKeyValuePair(q, this.querySep);
            parUrl = parUrl.substring(0, m.start());
        }
        i = parUrl.indexOf(URL.HOST_START);
        if (i == 0) {
            parUrl = parUrl.substring(URL.HOST_START.length());
        }
        i = parUrl.indexOf(URL.PROTO_END + URL.HOST_START);
        if (i == org.apache.commons.lang3.ArrayUtils.INDEX_NOT_FOUND) {
            i = parUrl.indexOf(URL.PROTO_END);
            if (i == 1) {
                // This is potentially a full path with drive at the beginning in windows..
                this.setInternalProtocol(URL.FILE);
                this.setPath("/" + parUrl);
                parUrl = StringUtils.EMPTY;
            } else if (i > 1) {
                this.setInternalProtocol(parUrl.substring(0, i));
                if (!URL.FILE.equals(this.getProtocol()) && this.isNotRecognizedProtocol()) {
                    throw new IllegalStateException("Unknown protocol '" + this.getProtocol() + "' in \"" + parUrl + "\"");
                }
                parUrl = parUrl.substring(i + URL.PROTO_END.length());
            }
        } else if (i > 0) {
            this.setInternalProtocol(parUrl.substring(0, i));
            parUrl = parUrl.substring(i + 3);
        } else {
            throw new IllegalStateException("Explicitely omitted protocol in \"" + parUrl + "\"");
        }
        i = parUrl.indexOf(URL.CRED_END);
        if (i >= 0) {
            final String up = parUrl.substring(0, i);
            this.setCredentials(up);
            parUrl = parUrl.substring(i + 1);
        }
        if (this.path == null) {
            i = parUrl.indexOf("/");
            if (i >= 0) {
                this.setPath(URL.decode(parUrl.substring(i)));
                parUrl = parUrl.substring(0, i);
            }
            i = parUrl.indexOf(":");
            if (i >= 0 && i < parUrl.length() - 1) {
                final String pss = parUrl.substring(i + 1);
                try {
                    this.setPort(Integer.parseInt(pss));
                    parUrl = parUrl.substring(0, i);
                } catch (final NumberFormatException nfe) {
                    if (this.path == null) {
                        this.setPath(pss);
                    } else {
                        this.setPath(pss + this.path);
                    }
                    this.protocol = parUrl.substring(0, i);
                    parUrl = "";
                }
            }
        }
        if (parUrl.length() > 0) {
            this.host = parUrl;
        }
    }

    public String getAbsolutePath() {
        if (this.path != null && !this.path.startsWith("/")) {
            return "/" + this.path;
        }
        return this.path;
    }

    public String getAddress() {
        return this.port <= 0 ? this.host : this.host + ":" + this.port;
    }

    public String getAuthentication() {
        String ret = null;
        final UserPass up = this.getUserPass();
        if (up != null) {
            final String unEnc = up.getUserName() + (up.getPassword() != null ? ":" + up.getPassword() : "");
            ret = "Basic " + new String(org.apache.commons.net.util.Base64.encodeBase64(unEnc.getBytes()));
        }
        return ret;
    }

    public Charset getEncode() {
        return this.encode;
    }

    public String getHost() {
        return this.host;
    }

    public InetAddress getIp() {
        if (this.ip == null) {
            try {
                this.ip = InetAddress.getByName(this.host);
            } catch (final UnknownHostException e) {
                throw new IllegalStateException(this.host + " is invalid");
            }
        }
        return this.ip;
    }

    public Object getParameter(final String parKey) {
        if (this.parameters == null) {
            return null;
        }
        for (final Map.Entry<String, Object> p : this.parameters) {
            if (p.getKey().equals(parKey)) {
                return p.getValue();
            }
        }
        return null;
    }

    public LinkedList<Map.Entry<String, Object>> getParameters() {
        return this.parameters;
    }

    public String getParameterString(final String parKey) {
        final Object value = this.getParameter(parKey);
        return value == null ? null : String.valueOf(value);
    }

    public String getParameterStrings(final String... parIncluded) {
        final StringBuilder buf = new StringBuilder();
        return this.buildParameters(buf, parIncluded) ? buf.toString() : null;
    }

    public String getPassword() {
        if (this.credentials instanceof UserPass) {
            return this.credentials.getPassword();
        }
        return null;
    }

    public String getPath() {
        return this.path;
    }

    public int getPort() {
        return this.port;
    }

    public String getProtocol() {
        return this.protocol;
    }

    public String getQuery(final String... parIncluded) {
        return this.getParameterStrings(parIncluded);
    }

    public String getQuerySep() {
        return this.querySep;
    }

    public Pattern getQueryStart() {
        return this.queryStart;
    }

    public String getQueryStartString() {
        return this.queryStartString;
    }

    public String getRef() {
        return this.ref;
    }

    public int getResolvedPort() {
        this.port = this.calcResolvedPort();
        return this.port;
    }

    public String getUsername() {
        if (this.credentials instanceof UserPass) {
            return this.credentials.getUserName();
        }
        return null;
    }

    public UserPass getUserPass() {
        return this.credentials;
    }

    public boolean hasParameter(final String parKey) {
        if (this.parameters == null) {
            return false;
        }
        for (final Map.Entry<String, Object> p : this.parameters) {
            if (p.getKey().equals(parKey)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNotRecognizedProtocol() {
        boolean not = this.calcResolvedPort() == URL.NO_PORT;
        if (not) {
            try {
                final java.net.URL utmp = new java.net.URL(this.protocol + ":/");
                not = utmp.getProtocol() == null;
            } catch (final MalformedURLException e) {
                not = e.getMessage().contains("unknown protocol");
            }
        }
        return not;
    }

    public boolean matchesTo(final URL curr) {
        final String ssCurr = curr.subString(Segment.protocol, Segment.path);
        final String ssThis = this.subString(Segment.protocol, Segment.path);
        if (!ssCurr.equals(ssThis)) {
            return false;
        }
        boolean ret = true;
        for (final Map.Entry<String, Object> e : this.getParameters()) {
            final Object p = curr.getParameter(e.getKey());
            if (StringUtils.isNull(e.getValue()) && curr.hasParameter(e.getKey())) {
                continue;
            } else if (e.getValue() instanceof String) {
                final String pv = String.valueOf(p);
                ret &= pv.matches((String) e.getValue());
            } else {
                ret &= org.apache.commons.lang3.ObjectUtils.equals(e.getValue(), p);
            }
        }
        return ret;
    }

    public void newParameters() {
        if (this.parameters == null) {
            this.parameters = new LinkedList<Map.Entry<String, Object>>();
        }
        this.parameters.clear();
    }

    public void newParameters(final boolean nullified) {
        if (nullified) {
            this.parameters = null;
        } else {
            this.newParameters();
        }
    }

    public Object removeParameters(final Collection<String> parKey) {
        if (parKey == null || parKey.size() == 0) {
            return null;
        }
        return this.removeParameters(parKey.toArray(new String[0]));
    }

    public Object removeParameters(final String... parKeys) {
        if (this.parameters == null) {
            return null;
        }
        Object ret = null;
        boolean met = false;
        for (final String key : parKeys) {
            for (final Iterator<Map.Entry<String, Object>> ip = this.parameters.iterator(); ip.hasNext(); ) {
                final Map.Entry<String, Object> ent = ip.next();
                if (ent.getKey().equals(key)) {
                    if (ret == null) {
                        ret = ent.getValue();
                    }
                    met = true;
                    ip.remove();
                }
            }
        }
        return ret == null && met ? Void.class : ret;
    }

    public void setAddress(final String parAddress) {
        final int i = parAddress.lastIndexOf(':');
        final String hst;
        int prt = this.port;
        if (i >= 0) {
            hst = parAddress.substring(0, i);
            prt = Integer.parseInt(parAddress.substring(i + 1));
        } else {
            hst = parAddress;
        }
        this.setHost(hst);
        this.setPort(prt);
    }

    public boolean setAlternativePort() {
        final Integer alt = SystemUtils.tcpAlternativePortNeeded(this.getHost(), this.calcResolvedPort());
        if (alt != null) {
            this.setPort(alt);
        }
        return alt != null;
    }

    public void setCredentials(final String parCred) {
        this.credentials = new UserPass(parCred);
    }

    public void setEncode(final Charset encode) {
        this.encode = encode;
    }

    public void setHost(final String parHost) {
        if (StringUtils.isBlank(parHost)) {
            this.host = null;
        } else {
            this.host = parHost.replace(URL.HOST_START, "");
        }
    }

    private void setInternalProtocol(String parProtocol) {
        if (parProtocol != null) {
            if (parProtocol.endsWith(URL.PROTO_END)) {
                parProtocol = parProtocol.substring(0, parProtocol.length() - URL.PROTO_END.length());
            }
        }
        this.protocol = parProtocol;
    }

    public void setParameter(final String parKey, final Object parValue) {
        if (this.parameters == null) {
            this.addParameter(parKey, parValue);
        } else {
            for (final Map.Entry<String, Object> p : this.parameters) {
                if (p.getKey().equals(parKey)) {
                    p.setValue(parValue);
                    return;
                }
            }
            this.addParameter(new DefaultMapEntry<>(parKey, parValue));
        }
    }

    public void setPassword(final String parPassword) {
        if (this.credentials instanceof UserPass) {
            this.credentials.setPassword(parPassword);
        } else {
            this.credentials = new UserPass(null, parPassword);
        }
    }

    public void setPath(final String parPath) {
        this.path = parPath;
    }

    public void setPort(final int parPort) {
        if (parPort <= URL.NO_PORT) {
            this.port = URL.NO_PORT;
        } else {
            this.port = parPort;
        }
    }

    public void setProtocol(final String parProtocol) {
        this.setInternalProtocol(parProtocol);
    }

    public void setQuerySep(final String querySep) {
        this.querySep = querySep;
    }

    public void setQueryStart(final Pattern queryStart) {
        this.queryStart = queryStart;
    }

    public void setQueryStartString(final String queryStartString) {
        this.queryStartString = queryStartString;
        this.queryStart = Pattern.compile(Pattern.quote(queryStartString));
    }

    public void setRef(final String parRef) {
        this.ref = parRef;
    }

    public void setUsername(final String parUsername) {
        if (this.credentials instanceof UserPass) {
            this.credentials.setUserName(parUsername);
        } else {
            this.credentials = new UserPass(parUsername, null);
        }
    }

    public String subString(final boolean parUser, final boolean parPar, Segment parFrom, Segment parTo, final String... parPars) {
        if (parFrom == null) {
            parFrom = Segment.protocol;
        }
        if (parTo == null) {
            parTo = Segment.ref;
        }
        final StringBuilder buf = new StringBuilder();
        if (Segment.protocol.included(parFrom, parTo) && StringUtils.isNotBlank(this.protocol)) {
            buf.append(this.protocol);
            buf.append(URL.PROTO_END);
        }
        final boolean hasHost = StringUtils.isNotBlank(this.host);
        if (hasHost) {
            if (Segment.host.included(parFrom, parTo)) {
                buf.append(URL.HOST_START);
            }
            if (parUser && this.credentials instanceof UserPass) {
                final UserPass up = this.credentials;
                if (StringUtils.isNotBlank(up.getUserName()) && Segment.username.included(parFrom, parTo)) {
                    String un = up.getFullUserName();
                    if (this.encode != null) {
                        un = URL.encode(un, this.encode);
                    }
                    buf.append(un);
                }
                if (StringUtils.isNotBlank(up.getPassword()) && Segment.password.included(parFrom, parTo)) {
                    buf.append(":").append(this.encode == null ? up.getPassword() : URL.encode(up.getPassword(), this.encode));
                }
                buf.append(URL.CRED_END);
            }
            if (Segment.host.included(parFrom, parTo)) {
                buf.append(this.host);
            }
            if (Segment.port.included(parFrom, parTo) && this.port >= 0) {
                buf.append(":");
                buf.append(this.port);
            }
        }
        if (Segment.path.included(parFrom, parTo) && this.path != null) {
            String pi;
            if (hasHost) {
                pi = this.getAbsolutePath();
            } else {
                pi = this.path;
            }
            pi = this.encode == null ? UriUtils.encode(pi, Charset.defaultCharset()) : UriUtils.encode(pi, this.encode.name());
            buf.append(pi);
        }
        if (this.parameters != null && parPar) {
            if (Segment.parameters.included(parFrom, parTo)) {
                buf.append(this.queryStartString);
            }
            if (Segment.queryString.included(parFrom, parTo)) {
                this.buildParameters(buf, parPars);
            }
        }
        if (Segment.ref.included(parFrom, parTo) && this.ref != null) {
            buf.append(URL.REF_START).append(this.ref);
        }
        return buf.toString();
    }

    public String subString(final Segment parFrom) {
        return this.subString(parFrom, Segment.ref);
    }

    public String subString(final Segment parFrom, final Segment parTo) {
        return this.subString(true, true, parFrom, parTo);
    }

    public java.net.URL toURL() {
        try {
            final String url = this.toFullString();
            return new java.net.URL(url);
        } catch (final MalformedURLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public java.net.URI toURI() {
        try {
            return toURL().toURI();
        } catch (final URISyntaxException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public String toFullString(final String... parPars) {
        return this.subString(true, true, Segment.protocol, Segment.ref, parPars);
    }

    public InetSocketAddress toInetSocketAddress() {
        return new InetSocketAddress(this.host, this.port);
    }

    @Override
    public String toString() {
        return this.subString(false, true, Segment.protocol, Segment.ref);
    }

    public String toString(final String... parPars) {
        return this.subString(false, true, Segment.protocol, Segment.ref, parPars);
    }

    public String toUrlString(final String... parPars) {
        return this.subString(false, false, Segment.protocol, Segment.ref, parPars);
    }
}
