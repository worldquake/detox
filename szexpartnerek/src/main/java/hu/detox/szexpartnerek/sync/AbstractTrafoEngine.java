package hu.detox.szexpartnerek.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import hu.detox.Agent;
import hu.detox.io.CharIOHelper;
import hu.detox.io.FileUtils;
import hu.detox.szexpartnerek.Main;
import hu.detox.szexpartnerek.sync.rl.Entry;
import hu.detox.utils.Database;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.configuration2.Configuration;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static hu.detox.szexpartnerek.spring.SzexConfig.query;

public abstract class AbstractTrafoEngine implements ITrafoEngine.Filters, ITrafoEngine {
    private static final Set<AbstractTrafoEngine> FLUSHING = new HashSet<>();
    private static final Set<AbstractTrafoEngine> CLOSING = new HashSet<>();
    public static Configuration LISTING;
    private static Configuration MAPPING;

    public static final Properties PROPS = new Properties();
    protected static final Properties MASSAGES = new Properties();
    protected static final Properties LIKES = new Properties();
    protected static final Properties LOOKING = new Properties();
    protected static final Properties ANSWERS = new Properties();
    protected static final Properties FBGBTYPE = new Properties();
    protected static final Properties FBRTYPE = new Properties();
    protected static final Properties FBTYPE = new Properties();
    protected static final Properties FBDTYPE = new Properties();

    // Map for iterative initialization
    private static final Map<String, Properties> ENUM_PROPERTIES = Map.of(
            "properties", PROPS,
            "massage", MASSAGES,
            "likes", LIKES,
            "looking", LOOKING,
            "answers", ANSWERS,
            "fbgbtype", FBGBTYPE,
            "fbrtype", FBRTYPE,
            "fbtype", FBTYPE,
            "fbdtype", FBDTYPE
    );

    protected static List<Integer> asInt(ResultSet rs) throws SQLException {
        return Database.quickListOne(rs, Integer.class);
    }

    static {
        try {
            LISTING = Entry.cfg("PartnerList-mapping.kv");
            MAPPING = Entry.cfg("mapping.kv");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void initEnums() {
        if (!PROPS.isEmpty()) return;
        query("SELECT id, type, name FROM int_enum", rs -> {
            while (rs.next()) {
                int id = rs.getInt(1);
                String enumType = rs.getString(2);
                String value = rs.getString(3);

                Properties props = ENUM_PROPERTIES.get(enumType);
                if (props != null) {
                    props.put(value, id);
                }
            }
            return null;
        });
    }

    protected transient Set<Integer> untouchableIds;
    protected transient Set<Integer> processableIds;

    protected AbstractTrafoEngine() {
        // Nothing
    }

    @Override
    public void reStart(boolean force) {
        if (untouchableIds != null && !force) return;
        untouchableIds = new HashSet<>(10000);
        processableIds = findProcessableIds();
        if (processableIds != null && untouchableIds != null) {
            if (hu.detox.szexpartnerek.sync.Main.args().isFull()) untouchableIds.clear();
            System.err.println("Found " + processableIds.size() + " processable ids (Not touchable " + untouchableIds.size() + ") for " + getId());
        }
    }

    protected abstract Integer onEnum(Properties map, ArrayNode arr, ArrayNode propsArr, AtomicReference<String> en);

    protected final Integer addProp(StringBuilder sb, Properties map, ArrayNode arr, ArrayNode propsArr, String prp) {
        String enm = Main.toEnumLike(prp);
        enm = MAPPING.getString(enm, enm);
        if (enm == null) return null;
        var ar = new AtomicReference<>(enm);
        Integer res = onEnum(map, arr, propsArr, ar);
        if (res == null) {
            res = doAddProp(map, arr, ar.get());
            if (sb != null && res == null) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(prp);
            }
        } else if (res == -1) {
            res = null;
        }
        return res;
    }

    @Override
    public CharIOHelper findIn() throws IOException {
        CharIOHelper io = ITrafoEngine.super.findIn();
        if (io == null) {
            File tmp = new File(Agent.WORK, "tmp-" + getId() + ".txt");
            if (CollectionUtils.isEmpty(processableIds)) {
                tmp.delete();
                return null;
            } else {
                FileUtils.writeLines(tmp, processableIds);
            }
            io = CharIOHelper.attempt(tmp);
        }
        return io;
    }

    protected static Integer doAddProp(Properties map, ArrayNode arr, String prp) {
        if (prp == null) return null;
        Integer res = (Integer) map.get(prp); // Here there was a way to add, but it is deprecated from now on!
        if (arr != null && res != null) {
            boolean alreadyPresent = false;
            for (JsonNode node : arr) {
                if (node.isInt() && node.intValue() == res) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) arr.add(res);
        }
        return res;
    }

    protected abstract Set<Integer> findProcessableIds();

    @Override
    public void flush() {
        if (!FLUSHING.add(this)) return;
        try {
            ITrafoEngine.super.flush();
        } finally {
            FLUSHING.remove(this);
        }
    }

    @Override
    public void close() {
        if (!CLOSING.add(this)) return;
        try {
            ITrafoEngine.super.close();
            processableIds = null;
            untouchableIds = null;
        } finally {
            CLOSING.remove(this);
        }
    }
}
