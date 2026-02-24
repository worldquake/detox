package hu.detox;

import hu.detox.config.ConfigReader;
import hu.detox.utils.strings.StringUtils;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.SystemProperties;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.UnitFormat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.*;

public class Agent {
    private static String USERNAME_KEY = "user.name";
    public static String DEBUG_KEY = "debug";
    private static String TEST_KEY = "dtx.test";
    private static String SYS_KEY = "dtx.sys";
    private static String EMAIL_KEY = "dtx.myemail";
    private static String HOME_FIRST_KEY = "dtx.home.first";
    public static final String PATHSPLIT = "[" + (SystemUtils.IS_OS_WINDOWS ? ";" : ":;") + "]";
    public static final String EMAIL;
    public static boolean debug = Boolean.getBoolean(Agent.DEBUG_KEY) || Boolean.parseBoolean(Agent.getEnvOfKey(Agent.DEBUG_KEY));
    public static boolean test = Boolean.getBoolean(Agent.TEST_KEY) || Boolean.parseBoolean(Agent.getEnvOfKey(Agent.TEST_KEY));
    public static String user = System.getProperty(Agent.USERNAME_KEY, Agent.getEnvOfKey(Agent.USERNAME_KEY));
    public static final boolean HOME_FIRST = Boolean.getBoolean(Agent.HOME_FIRST_KEY) || Boolean.parseBoolean(Agent.getEnvOfKey(Agent.HOME_FIRST_KEY));
    public static final boolean IDE = Boolean.getBoolean("IDE");
    public static final File ENV;
    public static final File[] SYS;
    public static final String TARGET = "target";
    public static final String BASEP = "base";
    public static final String SOURCESH = "src/main/sh";
    private static final String DTX_HOME_DIRNAME = ".detox-utils";
    private static final String[] PATH_EXT;
    public static final File HOME;
    public static final File BASE;
    public static final File WORK;
    private static Date time;

    static {
        Agent.timer("Start");
        final String sys = System.getProperty(Agent.SYS_KEY, Agent.getEnvOfKey(Agent.SYS_KEY));
        if (StringUtils.isEmpty(sys)) {
            SYS = null;
        } else {
            final String[] asys = sys.split(Agent.PATHSPLIT);
            SYS = new File[asys.length];
            int i = 0;
            for (final String p : asys) {
                Agent.SYS[i++] = new File(p);
            }
        }
        final String env = System.getProperty("dtx.env.home", System.getenv("DTX_ENV_HOME"));
        if (StringUtils.isEmpty(env)) {
            ENV = null;
        } else {
            ENV = new File(env);
        }
        final File home = Agent.initHome();
        if (home == null) {
            HOME = null;
        } else {
            HOME = home.getAbsoluteFile();
            System.setProperty("user_base", Agent.HOME.toString());
        }
        String em = System.getProperty(Agent.EMAIL_KEY, Agent.getEnvOfKey(Agent.EMAIL_KEY));
        if (em == null) {
            final File mecfg = new File(org.apache.commons.lang3.SystemUtils.USER_HOME, ".gitconfig");
            if (mecfg.canRead()) {
                INIConfiguration ini = ConfigReader.INSTANCE.toCfg(INIConfiguration.class, mecfg);
                if (ini != null) {
                    em = ini.getString("user.email");
                    final String un = ini.getString(Agent.USERNAME_KEY);
                    if (un != null && Agent.user == null) {
                        Agent.user = un;
                    }
                }
            }
        }
        EMAIL = em;
        String base = System.getProperty(Agent.BASEP, ".");
        if (base.isEmpty()) {
            base = Agent.SOURCESH;
        }
        File bse;
        try {
            bse = new File(base).getCanonicalFile();
        } catch (final IOException e) {
            bse = new File(base).getAbsoluteFile();
        }
        BASE = bse;
        System.setProperty(Agent.BASEP, Agent.BASE.getAbsolutePath());
        String defw = Agent.BASE.getAbsolutePath();
        if (!Agent.BASE.getName().equals(Agent.TARGET)) {
            defw += "/" + Agent.TARGET;
        }
        File wrk;
        if (FilenameUtils.getName(Agent.SOURCESH).equals(Agent.BASE.getName())) {
            wrk = new File(defw);
        } else {
            wrk = new File(System.getProperty(Agent.TARGET, defw));
        }
        WORK = wrk.getAbsoluteFile();
        System.setProperty(Agent.TARGET, Agent.WORK.getAbsolutePath());
        Agent.WORK.mkdir();
        final String pext = System.getenv("PATHEXT");
        if (pext == null) {
            PATH_EXT = new String[]{""};
        } else {
            PATH_EXT = pext.split(Agent.PATHSPLIT);
        }
        if (Agent.user == null) {
            Agent.user = org.apache.commons.lang3.SystemUtils.USER_NAME;
        }
        Agent.timer("Finish Agent Init (w=" + Agent.WORK + ", b=" + Agent.BASE + ")");
    }

    private static File initHome() {
        final String nuhome = System.getProperty("dtx.user.home", System.getenv("DTX_USER_HOME"));
        File home;
        File nhome = null;
        String nun = org.apache.commons.lang3.SystemUtils.USER_NAME;
        if (nuhome == null) {
            home = new File(org.apache.commons.lang3.SystemUtils.USER_HOME);
            if (!home.isDirectory()) {
                nhome = home.getParentFile();
                nun = System.getenv("DTX_USER_NAME");
                if (StringUtils.isEmpty(nun)) {
                    try {
                        final Process p = Runtime.getRuntime().exec("whoami");
                        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        org.apache.commons.io.IOUtils.copy(p.getInputStream(), bos);
                        p.waitFor();
                        nun = FilenameUtils.getName(bos.toString().trim());
                        nhome = new File(nhome, nun);
                    } catch (final Exception ex) {
                        // No worries, nothing to do
                    }
                } else {
                    nhome = new File(nhome, nun);
                }
            }
        } else {
            if (nuhome.isEmpty()) {
                // We will not set the user home
                return null;
            }
            nhome = home = new File(nuhome);
            nun = nhome.getName();
        }
        if (nhome != null) {
            home = nhome;
            System.setProperty(SystemProperties.USER_HOME, home.toString());
            System.setProperty(SystemProperties.USER_NAME, nun);
        }
        nhome = new File(home, Agent.DTX_HOME_DIRNAME);
        if (!nhome.isDirectory()) {
            nhome = home;
        }
        if (Agent.debug) {
            System.err.println("Home=" + home + ", UID=" + nun + " --> " + nhome);
        }
        return nhome;
    }

    public static void init() {
        final UnitFormat unitFormat = UnitFormat.getInstance();
        // Duration units
        unitFormat.label(SI.MILLI(SI.SECOND), "ms");
        unitFormat.label(NonSI.HOUR, "h");
        unitFormat.label(NonSI.MINUTE, "m");
        unitFormat.label(NonSI.DAY, "d");
        unitFormat.label(NonSI.BYTE, "B");
        unitFormat.label(SI.BIT, "b");

        // Size units...
        unitFormat.label(NonSI.BYTE.times(1024), "kiB");
        unitFormat.label(NonSI.BYTE.times(1024 * 1024), "MiB");
        unitFormat.label(NonSI.BYTE.times(1024 * 1024 * 1024), "GiB");
        unitFormat.label(SI.KILO(NonSI.BYTE), "kB");
        unitFormat.label(SI.MEGA(NonSI.BYTE), "MB");
        unitFormat.label(SI.GIGA(NonSI.BYTE), "GB");
        unitFormat.label(SI.TERA(NonSI.BYTE), "TB");
        unitFormat.label(SI.BIT.times(1024), "kib");
        unitFormat.label(SI.BIT.times(1024 * 1024), "Mib");
        unitFormat.label(SI.BIT.times(1024 * 1024 * 1024), "Gib");
        unitFormat.label(SI.KILO(SI.BIT), "kb");
        unitFormat.label(SI.MEGA(SI.BIT), "Mb");
        unitFormat.label(SI.GIGA(SI.BIT), "Gb");
        unitFormat.label(SI.TERA(SI.BIT), "Tb");
    }

    public static File findExecutableOnPath(final String executableName) {
        final String systemPath = System.getenv("PATH");
        final String[] pathDirs = systemPath.split(File.pathSeparator);
        File fullyQualifiedExecutable = null;
        outer:
        for (final String pathDir : pathDirs) {
            for (final String ex : Agent.PATH_EXT) {
                final File file = new File(pathDir, executableName + ex);
                if (file.isFile()) {
                    fullyQualifiedExecutable = file;
                    break outer;
                }
            }
        }

        return fullyQualifiedExecutable;
    }

    public static void getBaseFile(final File base, String f, final FileFilter filter, final List<File> ret, final boolean weak) {
        if (base == null) {
            return;
        }
        f = FilenameUtils.separatorsToSystem(f);
        final File fo = new File(base, f);
        int i;
        final boolean acc = filter.accept(fo);
        if (acc) {
            i = ret.indexOf(fo);
            if (i == org.apache.commons.lang3.ArrayUtils.INDEX_NOT_FOUND) {
                ret.add(fo.getAbsoluteFile());
            } else if (weak) {
                ret.remove(i);
                ret.add(fo);
            }
        }
    }

    public static void getBaseFile(final File base, final String f, final FileFilter filter, final String res, final List<File> ret, final boolean weak) {
        Agent.getBaseFile(base, f, filter, ret, weak);
        Agent.getBaseFile(base, res, filter, ret, weak);
    }

    public static Resource resource(final String f) {
        return resource(f, FileFilterUtils.fileFileFilter());
    }

    public static Resource resource(final String f, final FileFilter filter) {
        File res = filter == null ? null : getFile(f, filter);
        if (res != null) return new FileSystemResource(res);
        return Main.resource(ResourceLoader.CLASSPATH_URL_PREFIX + f);
    }

    public static File getFile(final String f, final FileFilter filter) {
        final List<File> lst = Agent.getFiles(f, filter);
        if (lst.isEmpty()) {
            return null;
        }
        return lst.get(0);
    }

    public static List<File> getFiles(final String f, FileFilter filter) {
        if (filter == null) {
            filter = TrueFileFilter.INSTANCE;
        }
        final List<File> ret = new LinkedList<File>();
        final File fo = new File(f);
        if (filter.accept(fo)) {
            ret.add(fo.getAbsoluteFile());
        }
        final String res = "res/" + f;
        if (Agent.HOME_FIRST) {
            Agent.getBaseFile(Agent.HOME, f, filter, res, ret, false);
            Agent.getBaseFile(Agent.WORK, f, filter, res, ret, false);
        } else {
            Agent.getBaseFile(Agent.WORK, f, filter, res, ret, false);
            Agent.getBaseFile(Agent.HOME, f, filter, res, ret, false);
        }
        Agent.getSysFiles(f, filter, ret);
        Agent.getBaseFile(Agent.ENV, f, filter, res, ret, false);
        Agent.getBaseFile(Agent.BASE, f, filter, res, ret, true);
        return ret;
    }

    public static void getSysFiles(final String f, final FileFilter filter, final List<File> ret) {
        if (org.apache.commons.lang3.ArrayUtils.isEmpty(Agent.SYS)) {
            return;
        }
        for (final File p : Agent.SYS) {
            Agent.getBaseFile(p, f, filter, ret, false);
        }
    }

    private static List<File> listFilesReverse(final String dname) {
        final List<File> tcfdir = Agent.getFiles(dname, DirectoryFileFilter.DIRECTORY);
        Collections.reverse(tcfdir);
        if (Agent.debug) {
            final List<File> stcfdir = Agent.getFiles(dname + "/" + Agent.DEBUG_KEY, DirectoryFileFilter.DIRECTORY);
            Collections.reverse(stcfdir);
            tcfdir.addAll(stcfdir);
        }
        return tcfdir;
    }

    public static synchronized void timer(final Object message) {
        if (Agent.debug) {
            if (Agent.time == null) {
                Agent.time = new Date();
                System.err.println(message + ": " + Agent.time);
            } else {
                final Date end = new Date();
                System.err.println(
                        message + ": " + Agent.time + " - " + end + " - " + DurationFormatUtils.formatDurationHMS(end.getTime() - Agent.time.getTime()));
                Agent.time = end;
            }
        }
    }

    private static String getEnvOfKey(final String k) {
        return System.getenv(k.toUpperCase(Locale.ENGLISH).replace('.', '_'));
    }

    private Agent(final Instrumentation i) {
        // Static class
    }
}
