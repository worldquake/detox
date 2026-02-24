package hu.detox.config;

import hu.detox.Agent;
import hu.detox.io.CharIOHelper;
import hu.detox.utils.strings.StringUtils;
import hu.detox.utils.ThreadUtils;
import hu.detox.utils.TimeUtils;
import kotlin.Pair;
import kotlin.jvm.functions.Function2;
import org.apache.commons.configuration2.*;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;
import org.apache.commons.configuration2.convert.LegacyListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.io.FileBased;
import org.apache.commons.configuration2.io.FileHandler;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class ConfigReader {
    public static final ConfigReader INSTANCE = new ConfigReader();
    public static final String FILEFMT_KV = "kv";
    public static final String FILEFMT_CKV = "ckv";
    public static final String FILEFMT_LST = "lst";
    private static final String TIMED = "timed";
    private static final String[] YAML = "yml,yaml".split(",");
    public static final String JSON = "json";
    public static final String PLIST = "plist";
    public static final String XML = "xml";

    public static void makeConfigSafeFromSplit(final Configuration c) {
        AbstractConfiguration ac = (AbstractConfiguration) c;
        ac.setListDelimiterHandler(new DisabledListDelimiterHandler());
    }

    public static Pair<Character, String> getSeparatorChar(final String name) {
        Character ret = null;
        int lid;
        String nfn = name;
        do {
            lid = nfn.lastIndexOf('.');
            if (lid == -1) {
                break;
            }
            final String ext = nfn.substring(lid + 1);
            nfn = nfn.substring(0, lid);
            ret = ConfigReader.toSeparatorChar(ext);
            if (ret != null) {
                nfn = nfn + name.substring(lid + ext.length() + 1);
            }
        } while (lid >= 0 && ret == null);
        return ret == null ? null : new Pair<>(ret, nfn);
    }

    public static Character toSeparatorChar(final Object any) {
        if (any == null || any instanceof Character) {
            return (Character) any;
        }
        String ext = String.valueOf(any);
        Character ret = null;
        if (StringUtils.isNotBlank(ext)) {
            final int exl = ext.length();
            if (exl == 1) {
                ret = ext.charAt(0);
            } else if (ext.matches("#[a-zA-Z0-9]+")) {
                ext = StringEscapeUtils.escapeHtml4("&" + ext + ";");
                if (ext.length() == 1) {
                    ret = ext.charAt(0);
                }
            }
        }
        return ret;
    }

    private static void loadInternal(final CharIOHelper cio, final Configuration lconfig) {
        final String oext = FilenameUtils.getExtension(cio.getName()).toLowerCase(Locale.ENGLISH);
        String nfn = cio.getName();
        makeConfigSafeFromSplit(lconfig);
        final Pair<Character, String> cf = ConfigReader.getSeparatorChar(nfn);
        if (cf != null && lconfig instanceof AbstractConfiguration ac) {
            ac.setListDelimiterHandler(new LegacyListDelimiterHandler(cf.getFirst()));
            nfn = cf.getSecond();
        }
        nfn = FilenameUtils.getBaseName(nfn);
        try {
            if (lconfig instanceof FileBased) {
                FileHandler handler = new FileHandler((FileBased) lconfig);
                handler.load(cio.getReader());
            } else if (FILEFMT_LST.equals(oext)) {
                loadLst(cio, lconfig);
            } else {
                final boolean commentedkv = FILEFMT_CKV.equals(oext);
                loadKV(commentedkv, cio, lconfig);
            }
        } catch (ConfigurationException | IOException e) {
            ThreadUtils.uncaught(e, null, "Failed to read " + cio + " into " + lconfig);
        }
        cio.setName(nfn);
    }

    private static void loadKV(final boolean commentedkv, final CharIOHelper cio, final Configuration lconfig) {
        try (final LineIterator br = new LineIterator(cio.getReader()) {
            @Override
            protected boolean isValidLine(String line) {
                if (commentedkv) {
                    line = line.replaceFirst("\\s*#.+$", "");
                }
                return StringUtils.isNotEmpty(line);
            }
        }) {
            String ln, k;
            String cv;
            Object pv;
            while (br.hasNext()) {
                ln = br.next();
                final int sp = ln.indexOf('=');
                k = ln.substring(0, sp);
                cv = ln.substring(sp + 1);
                pv = lconfig.getProperty(k);
                if (pv == null) {
                    lconfig.setProperty(k, cv);
                } else if (pv instanceof List<?>) {
                    ((List) pv).add(cv);
                } else {
                    final LinkedList<Object> lpv = new LinkedList<>();
                    lpv.add(pv);
                    lpv.add(cv);
                    lconfig.setProperty(k, lpv);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read " + cio + ", commented=" + commentedkv, e);
        }
    }

    private static void loadLst(final CharIOHelper cio, final Configuration lconfig) throws IOException {
        final List<String> ret = org.apache.commons.io.IOUtils.readLines(cio.getReader());
        lconfig.setProperty(org.apache.commons.lang3.StringUtils.EMPTY, ret);
    }

    private Configuration toConfiguration(final CharIOHelper cio) {
        if (cio == null) {
            return null;
        }
        final String oext = FilenameUtils.getExtension(cio.getName());
        return toConfiguration(oext);
    }

    public static void saveConfig(final Configuration lconfig, Object ps) throws ConfigurationException {
        try {
            ((FileBasedConfiguration) lconfig).write(CharIOHelper.getBufferedWriter(ps));
        } catch (IOException e) {
            throw new ConfigurationException("Failed to save to " + ps, e);
        }
    }

    public static Configuration toConfiguration(String oext) {
        final Configuration lconfig;
        oext = oext.toLowerCase(Locale.ENGLISH);
        if (ConfigReader.XML.equals(oext)) {
            lconfig = new XMLConfiguration();
        } else if (FILEFMT_KV.equals(oext) || FILEFMT_CKV.equals(oext) || FILEFMT_LST.equals(oext)) {
            lconfig = new BaseConfiguration();
        } else if ("ini".equals(oext)) {
            lconfig = new INIConfiguration();
        } else if (ConfigReader.PLIST.equals(oext)) {
            lconfig = new PropertyListConfiguration();
        } else if (ArrayUtils.contains(YAML, oext)) {
            lconfig = new org.apache.commons.configuration2.YAMLConfiguration();
        } else if (JSON.equals(oext)) {
            lconfig = new org.apache.commons.configuration2.JSONConfiguration();
        } else {
            lconfig = new PropertiesConfiguration();
        }
        return lconfig;
    }

    public <T extends Configuration> T toCfg(
            Class type, Object src,
            Function2<CharIOHelper, AbstractConfiguration, Charset> interp) {
        T res = null;
        try (CharIOHelper h = CharIOHelper.attempt(src)) {
            T lconfig;
            if (type == null) {
                lconfig = (T) toConfiguration(h);
            } else {
                FileBasedConfigurationBuilder builder = new FileBasedConfigurationBuilder<>(type);
                lconfig = (T) builder.getConfiguration();
            }
            load(h, lconfig);
            if (interp != null) interp.invoke(h, (AbstractConfiguration) res);
            res = lconfig;
        } catch (ConfigurationException | IOException e) {
            ThreadUtils.uncaught(e, null, "Failed to read " + src + " into " + type);
        }
        return res;
    }

    public final <T extends Configuration> T toCfg(Class<T> type, Object in) {
        return toCfg(type, in, null);
    }

    public final Configuration toCfg(final String... names) throws IOException {
        final CompositeConfiguration cc = new CompositeConfiguration();
        Configuration last = null;
        int added = 0;
        for (final String name : names) {
            CharIOHelper cio = CharIOHelper.attempt(Agent.resource(name));
            if (cio == null) continue;
            last = toCfg(null, cio);
            added++;
            cc.addConfiguration(last);
        }
        return added == 0 ? null : added == 1 ? last : cc;
    }

    private Configuration load(final CharIOHelper cio, Configuration lconfig) throws ConfigurationException, IOException {
        String msg = "Load ";
        ConfigReader.loadInternal(cio, lconfig);
        msg += cio + ", keys=" + org.apache.commons.collections4.IteratorUtils.toList(lconfig.getKeys());
        if (cio.getSrc() instanceof File) {
            final File cond = new File(cio + "." + TIMED);
            if (cond.isFile() && lconfig instanceof AbstractConfiguration ac) {
                final Configuration clconfig = toConfiguration(cio);
                ConfigReader.loadInternal(CharIOHelper.attempt(cond), clconfig);
                final String conds = clconfig.getString(TIMED);
                if (StringUtils.isEmpty(conds) || ((Date) StringUtils.to(Date.class, conds, this)).before(TimeUtils.date())) {
                    msg += ", applied=" + cond + " because " + conds + " reached";
                    clconfig.clearProperty(TIMED);
                    ac.copy(clconfig);
                    saveConfig(lconfig, null);
                    cond.delete();
                }
            }
        }
        if (Agent.debug) {
            System.err.println(msg);
        }
        return lconfig;
    }

}
