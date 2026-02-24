package hu.detox.utils;

import hu.detox.Agent;
import hu.detox.config.ConfigReader;
import hu.detox.io.CharIOHelper;
import hu.detox.utils.reflection.ReflectionUtils;
import hu.detox.utils.strings.StringUtils;
import org.apache.commons.configuration2.*;
import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Database {
    private static final Logger logger = LogManager.getLogger(Database.class);
    private static final String PROPERTIES = "properties";
    public static final String PROP_SCHEMA = "schema";
    public static final String SCHEMA = PROP_SCHEMA + "." + PROPERTIES;

    private static Configuration propCSV(final File dir, final Configuration cfg) {
        final AbstractConfiguration p = cfg == null ? new BaseConfiguration() : (AbstractConfiguration) cfg.subset("csv_format");
        p.setListDelimiterHandler(new DisabledListDelimiterHandler());
        final Properties pcols = Database.getTableTypes(dir);
        for (final Map.Entry<Object, Object> pi : pcols.entrySet()) {
            p.setProperty("columnTypes." + pi.getKey(), pi.getValue());
        }
        return p;
    }

    public static List<String> getRs(final ResultSet rs, List<String> to) throws SQLException {
        final int cc = rs.getMetaData().getColumnCount();
        if (to == null) {
            to = new LinkedList<String>();
        } else {
            to.clear();
        }
        for (int i = 1; i <= cc; i++) {
            final Object o = rs.getObject(i);
            final String ret = o == null ? null : String.valueOf(o);
            to.add(ret);
        }
        return to;
    }

    public static <T extends List<Object>> T getRsObject(final ResultSet rs, final T to) throws SQLException {
        return getRsObject(rs, to, null);
    }

    public static <T extends List<Object>> T getRsObject(final ResultSet rs, T to, final Object nullOrNUll) throws SQLException {
        final int cc = rs.getMetaData().getColumnCount();
        if (to == null) {
            to = (T) new LinkedList<>();
        } else {
            to.clear();
        }
        for (int i = 1; i <= cc; i++) {
            Object o;
            if (nullOrNUll instanceof Class clz) {
                o = rs.getObject(i, clz);
            } else {
                o = rs.getObject(i);
            }
            if (ObjectUtils.equals(o, nullOrNUll)) {
                o = null;
            }
            to.add(o);
        }
        return to;
    }

    public static boolean emptyEquals(final Object o1, final Object o2) {
        if (StringUtils.isEmpty(o1) || StringUtils.isEmpty(o2)) {
            return false;
        }
        return ObjectUtils.equals(o1, o2);
    }

    public static boolean emptyNotEquals(final Object o1, final Object o2) {
        if (StringUtils.isEmpty(o1) || StringUtils.isEmpty(o2)) {
            return false;
        }
        return !ObjectUtils.equals(o1, o2);
    }

    public static boolean equals(final Object o1, final Object o2) {
        if (o1 == null || o2 == null) {
            return false;
        }
        return ObjectUtils.equals(o1, o2);
    }

    public static boolean executeFully(final PreparedStatement stmt) throws SQLException {
        final boolean ret = stmt.execute();
        if (ret) {
            do {
                stmt.getResultSet().close();
            } while (stmt.getMoreResults());
        }
        return ret;
    }

    public static Object extractValue(final ResultSet rs, final int columnIndex, final Integer forcedType) throws SQLException {
        final int columnType = forcedType != null ? forcedType : rs.getMetaData().getColumnType(columnIndex);

        if (rs.getObject(columnIndex) == null) {
            return null;
        }

        switch (columnType) {
            case Types.BIGINT:
                return rs.getLong(columnIndex);
            case Types.BINARY:
                return ArrayUtils.toObject(rs.getBytes(columnIndex));
            case Types.BIT:
                return rs.getBoolean(columnIndex);
            case Types.BOOLEAN:
                return rs.getBoolean(columnIndex);
            case Types.CHAR:
                return rs.getString(columnIndex);
            case Types.DATE:
                return rs.getDate(columnIndex);
            case Types.DECIMAL:
                return rs.getBigDecimal(columnIndex);
            case Types.DOUBLE:
                return rs.getDouble(columnIndex);
            case Types.FLOAT:
                return rs.getFloat(columnIndex);
            case Types.INTEGER:
                return rs.getInt(columnIndex);
            case Types.LONGNVARCHAR:
                return rs.getString(columnIndex);
            case Types.LONGVARBINARY:
                return ArrayUtils.toObject(rs.getBytes(columnIndex));
            case Types.LONGVARCHAR:
                return rs.getString(columnIndex);
            case Types.NCHAR:
                return rs.getString(columnIndex);
            case Types.NCLOB:
                return rs.getString(columnIndex);
            case Types.NULL:
                return null;
            case Types.NUMERIC:
                return rs.getBigDecimal(columnIndex);
            case Types.NVARCHAR:
                return rs.getString(columnIndex);
            case Types.REAL:
                return rs.getFloat(columnIndex);
            case Types.SMALLINT:
                return rs.getShort(columnIndex);
            case Types.TIME:
                return rs.getTime(columnIndex);
            case Types.TIMESTAMP:
                return rs.getTimestamp(columnIndex);
            case Types.TINYINT:
                return rs.getByte(columnIndex);
            case Types.VARBINARY:
                return ArrayUtils.toObject(rs.getBytes(columnIndex));
            case Types.VARCHAR:
                return rs.getString(columnIndex);
            default:
                return null;
        }
    }

    public static Object getGeneratedKey(final Statement stmt) throws SQLException {
        final List<Object> ret = Database.getGeneratedKeys(stmt);
        return org.apache.commons.collections.CollectionUtils.isEmpty(ret) ? null : ret.get(0);
    }

    public static List<Object> getGeneratedKeys(final Statement stmt) throws SQLException {
        final List<Object> ret = new LinkedList<>();
        try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
            while (generatedKeys.next()) {
                ret.add(generatedKeys.getObject(1));
            }
        }
        return ret;
    }

    private static Properties getTableTypes(final File dir) {
        final File schema = dir.isFile() ? dir : new File(dir, SCHEMA);
        Properties ret = null;
        if (schema.isFile()) {
            try (CharIOHelper cio = CharIOHelper.attempt(schema)) {
                Configuration cfg = ConfigReader.INSTANCE.toCfg(PropertiesConfiguration.class, cio);
                ret = ConfigurationConverter.getProperties(cfg);
            } catch (final IOException e) {
                throw new IllegalStateException("DB schema=" + schema + " invalid", e);
            }
        }
        return ret;
    }

    public static SQLException getViolation(final SQLException ex, final Object... ignore) throws SQLException {
        if (org.apache.commons.lang3.ArrayUtils.isEmpty(ignore)) {
            return ex;
        }
        for (final Object ign : ignore) {
            if (ObjectUtils.equals(ign, ex.getSQLState()) || ObjectUtils.equals(ign, ex.getErrorCode()) || ObjectUtils.equals(ign, ex.getMessage())) {
                return null;
            }
            if (ign instanceof Pattern) {
                Matcher m = ((Pattern) ign).matcher(ex.getSQLState());
                if (m.matches()) {
                    return null;
                }
                m = ((Pattern) ign).matcher(ex.getMessage());
                if (m.matches()) {
                    return null;
                }
                m = ((Pattern) ign).matcher("" + ex.getErrorCode());
                if (m.matches()) {
                    return null;
                }
            } else if (ign instanceof Number) {
                if (((Number) ign).intValue() == ex.getErrorCode()) {
                    return null;
                }
            }
        }
        return ex;
    }

    public static void ignoreViolation(SQLException ex, final Object... ignore) throws SQLException {
        ex = Database.getViolation(ex, ignore);
        if (ex != null) {
            throw ex;
        }
    }

    public static int indexOf(final ResultSet orig, final String idxDef) throws SQLException, IOException {
        int idx = 1;
        final ResultSetMetaData md = orig.getMetaData();
        final int cc = md.getColumnCount();
        try {
            idx = Integer.parseInt(idxDef);
        } catch (final NumberFormatException nfe) {
            for (int i = 1; i <= cc; i++) {
                final String lbl = md.getColumnLabel(i);
                if (lbl.equalsIgnoreCase(idxDef)) {
                    idx = i;
                    break;
                }
            }
        }
        if (idx <= 0) {
            idx = cc + idx;
        }
        return idx;
    }

    public static void logThisWarning(final SQLWarning wrn) {
        Database.logThisWarning(wrn, logger);
    }

    public static void logThisWarning(final SQLWarning wrn, Object lg) {
        String msg = wrn.getLocalizedMessage();
        Level ret = Level.WARN;
        Marker marker = null;
        if (lg instanceof Logger || lg instanceof Appendable) {
            msg = "SQL Code=" + wrn.getErrorCode() + ", State=" + wrn.getSQLState() + ": " + msg;
            if (lg instanceof Logger) {
                ((Logger) lg).log(ret, marker, msg);
            } else {
                msg = ret + ": " + msg;
                try {
                    ((Appendable) lg).append(msg + org.apache.commons.lang3.SystemUtils.LINE_SEPARATOR);
                } catch (final IOException e) {
                    Database.logger.error("Failed to write to " + lg + ": " + msg, e);
                }
            }
        }
    }

    public static void logWarning(final SQLWarning wrn) {
        Database.logWarning(wrn, null);
    }

    public static void logWarning(SQLWarning wrn, final Object lg) {
        while (wrn != null) {
            Database.logThisWarning(wrn, lg);
            wrn = wrn.getNextWarning();
        }
    }

    public static void logWarning(final Statement stmt) throws SQLException {
        Database.logWarning(stmt.getWarnings(), null);
    }

    public static void logWarning(final Statement stmt, final Object lg) throws SQLException {
        Database.logWarning(stmt.getWarnings(), lg);
    }

    public static boolean notEquals(final Object o1, final Object o2) {
        if (o1 == null || o2 == null) {
            return false;
        }
        return !ObjectUtils.equals(o1, o2);
    }

    public static String prepArray(final Collection arr) {
        return Database.prepArray(arr, null);
    }

    public static String prepArray(final Collection arr, String quot) {
        final List<String> to = new ArrayList<>(arr.size());
        if (quot == null) {
            quot = StringUtils.EMPTY;
        }
        for (final Object o : arr) {
            if (StringUtils.isNull(o)) {
                to.add(StringUtils.NULL.toUpperCase(Locale.ENGLISH));
            } else if (o instanceof Number) {
                to.add(o.toString());
            } else {
                to.add(quot + o + quot);
            }
        }
        return "(" + StringUtils.join(to, ',') + ")";
    }

    public static String prepArray(final int len) {
        final String arr = StringUtils.repeat("?,", len);
        return "(" + arr.substring(0, arr.length() - 1) + ")";
    }

    public static String prepArray(final Object[] arr) {
        return Database.prepArray(arr, null);
    }

    public static String prepArray(final Object[] arr, final String quot) {
        return Database.prepArray(Arrays.asList(arr), quot);
    }

    public static <T> List<T> quickListOne(final Connection c, final String sql, final int idx) throws SQLException {
        try (Statement s = c.createStatement()) {
            return Database.quickListOne(s, sql, idx);
        }
    }

    public static <T> List<T> quickListOne(final PreparedStatement s, final int idx) throws SQLException {
        try (ResultSet rs = s.executeQuery()) {
            return Database.quickListOne(rs, idx, null);
        }
    }

    public static <T> List<T> quickListOne(final ResultSet rs, final int idx, Class<T> type) throws SQLException {
        final List<T> ret = new LinkedList<>();
        while (rs.next()) {
            ret.add(type == null ? (T) rs.getObject(idx) : rs.getObject(idx, type));
        }
        return ret;
    }

    public static <T> List<T> quickListOne(final ResultSet rs) throws SQLException {
        return quickListOne(rs, 1, null);
    }

    public static <T> List<T> quickListOne(final ResultSet rs, Class<T> type) throws SQLException {
        return quickListOne(rs, 1, type);
    }

    public static <T> List<T> quickListOne(final Statement c, final String sql, final int idx) throws SQLException {
        try (ResultSet rs = c.executeQuery(sql)) {
            Database.logWarning(rs.getWarnings());
            return Database.quickListOne(rs, idx, null);
        }
    }

    public static <T> List<T> quickView(final Connection c, final String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            return Database.quickView(s, sql);
        }
    }

    public static <T> List<T> quickView(final PreparedStatement s) throws SQLException {
        try (ResultSet rs = s.executeQuery()) {
            return Database.quickView(rs);
        }
    }

    public static <T> List<T> quickView(final ResultSet rs) throws SQLException {
        return quickView(rs, null);
    }

    public static <T> List<T> quickView(final ResultSet rs, Class<T> clz) throws SQLException {
        List ret = null;
        if (rs.next()) {
            ret = new LinkedList<>();
            getRsObject(rs, ret, clz);
        }
        return ret;
    }

    public static <T> List<T> quickView(final Statement c, final String sql) throws SQLException {
        try (ResultSet rs = c.executeQuery(sql)) {
            Database.logWarning(rs.getWarnings());
            return Database.quickView(rs);
        }
    }

    public static <T> T quickViewOne(final Connection c, final String sql) throws SQLException {
        final List<Object> lst = Database.quickView(c, sql);
        return lst == null ? null : (T) lst.get(0);
    }

    public static <T> T quickViewOne(final ResultSet rs, final Class<T> typ) throws SQLException {
        final List<T> lst = Database.quickView(rs, typ);
        return lst == null ? null : lst.get(0);
    }

    public static <T> T quickViewOne(final Object s, final String sql) throws SQLException {
        if (s instanceof Connection) {
            return Database.quickViewOne((Connection) s, sql);
        } else {
            return Database.quickViewOne((Statement) s, sql);
        }
    }

    public static <T> T quickViewOne(final PreparedStatement s) throws SQLException {
        final List<Object> l = Database.quickView(s);
        Database.logWarning(s);
        return (T) (l == null ? null : l.get(0));
    }

    public static <T> T quickViewOne(final ResultSet rs) throws SQLException {
        try {
            return Database.quickViewOneCont(rs);
        } finally {
            rs.close();
        }
    }

    public static <T> T quickViewOne(final Statement s, final String sql) throws SQLException {
        final List<Object> l = Database.quickView(s, sql);
        Database.logWarning(s);
        return (T) (l == null ? null : l.get(0));
    }

    public static <T> T quickViewOneCont(final ResultSet rs) throws SQLException {
        List<Object> ret = null;
        if (rs.next()) {
            ret = new LinkedList<>();
            getRsObject(rs, ret);
        }
        return ret == null ? null : (T) ret.get(0);
    }

    public static int tempExecute(final Connection conn, final String qs) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            return Database.tempExecute(stmt, qs);
        }
    }

    public static int tempExecute(final PreparedStatement stmt) throws SQLException {
        return Database.tempExecute(stmt, (String) null);
    }

    public static int tempExecute(final Statement stmt, final String... cs) throws SQLException {
        return Database.tempExecuteList(stmt, Arrays.asList(cs));
    }

    public static int tempExecute(final Statement stmt, final String qs) {
        int ret = 0;
        try {
            if (stmt instanceof PreparedStatement) {
                ret = ((PreparedStatement) stmt).executeUpdate();
            } else {
                ret = stmt.executeUpdate(qs);
            }
            Database.logWarning(stmt);
            final String prnt = "# Ran " + (qs == null ? stmt : qs) + (ret != 0 ? " = " + ret : "");
            Database.logger.debug(prnt);
            if (Agent.debug) {
                System.err.println(prnt);
            }
        } catch (final SQLException ex) {
            ret = ArrayUtils.INDEX_NOT_FOUND;
            Database.logger.error("Failed to execute " + stmt + " temp query " + qs, ex);
        }
        return ret;
    }

    public static int tempExecuteList(final Statement stmt, final Collection<String> cs) throws SQLException {
        int ret = 0;
        for (final String q : cs) {
            if (StringUtils.isBlank(q)) {
                continue;
            }
            ret += Database.tempExecute(stmt, q);
        }
        return ret;
    }

    public static Class<?> toClass(final int type) {
        Class<?> result = Object.class;

        switch (type) {
            case Types.CHAR:
            case Types.NCHAR:
            case Types.VARCHAR:
            case Types.NVARCHAR:
            case Types.NCLOB:
            case Types.LONGVARCHAR:
                result = String.class;
                break;

            case Types.NUMERIC:
            case Types.DECIMAL:
                result = java.math.BigDecimal.class;
                break;

            case Types.BIT:
                result = Boolean.class;
                break;

            case Types.TINYINT:
                result = Byte.class;
                break;

            case Types.SMALLINT:
                result = Short.class;
                break;

            case Types.INTEGER:
                result = Integer.class;
                break;

            case Types.BIGINT:
                result = Long.class;
                break;

            case Types.REAL:
            case Types.FLOAT:
                result = Float.class;
                break;

            case Types.DOUBLE:
                result = Double.class;
                break;

            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                result = Byte[].class;
                break;

            case Types.DATE:
                result = java.sql.Date.class;
                break;

            case Types.TIME:
            case Types.TIME_WITH_TIMEZONE:
                result = java.sql.Time.class;
                break;

            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                result = java.sql.Timestamp.class;
                break;
        }
        return result;
    }

    public static String toString(final Connection o) {
        try {
            return "Connection to " + o.getCatalog() + " on " + Database.toString(o.getMetaData());
        } catch (final SQLException ex) {
            return ex.toString();
        }
    }

    public static String toString(final DatabaseMetaData o) {
        try {
            return "Database of " + o.getDatabaseProductName() + " " + o.getDatabaseProductVersion() + " using " + o.getDriverName() + " v"
                    + o.getDriverVersion();
        } catch (final SQLException ex) {
            return ex.toString();
        }
    }

    public static String toString(final ResultSet o) {
        try {
            return Database.toString(o.getMetaData());
        } catch (final SQLException ex) {
            return ex.toString();
        }
    }

    public static String toString(final ResultSetMetaData o) {
        try {
            String table = null;
            for (int i = 0; i < o.getColumnCount(); i++) {
                table = o.getTableName(i + 1);
                if (StringUtils.isNotEmpty(table)) {
                    break;
                }
            }
            return "Resuts of " + table + "[" + o.getColumnCount() + "]";
        } catch (final SQLException ex) {
            return ex.toString();
        }
    }

    public static Object tryConvertToSqlType(final Class<?> cn, Object o) {
        if (o == null || cn == null || o.getClass().equals(cn)) {
            return o;
        }
        if (o instanceof LocalDateTime) {
            LocalDateTime ldt = (LocalDateTime) o;
            ZoneId zone = ZoneId.systemDefault(); // or specify your desired zone
            o = Date.from(ldt.atZone(zone).toInstant());
        }
        if (o instanceof InputStream) {
            try {
                o = org.apache.commons.io.IOUtils.toByteArray((InputStream) o);
            } catch (final IOException e) {
                throw new IllegalArgumentException("Failed to read stream " + o);
            }
        }
        if (o instanceof Date) {
            if (cn.equals(java.sql.Date.class)) {
                o = new java.sql.Date(((Date) o).getTime());
            } else if (cn.equals(Timestamp.class)) {
                o = new Timestamp(((Date) o).getTime());
            }
        } else if (o instanceof CharSequence) {
            // At least try to convert to the requested type
            try {
                o = StringUtils.to(cn, o.toString(), null);
            } catch (final IllegalArgumentException ia) {
                if (Database.logger.isDebugEnabled()) {
                    Database.logger.debug("Converting to " + cn + " failed", ia);
                    // Does not matter, the value will be passed to SQL for conversion
                }
            }
        }
        return o;
    }

    public static Object tryConvertToSqlType(final String clz, final Object o) {
        if (o == null || StringUtils.isEmpty(clz)) {
            return o;
        }
        try {
            return Database.tryConvertToSqlType(ReflectionUtils.toClass(clz), o);
        } catch (final ClassNotFoundException e) {
            throw new IllegalArgumentException("Conversion for " + o + " failed", e);
        }
    }
}
