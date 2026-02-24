package hu.detox.utils;

import hu.detox.Main;
import hu.detox.utils.reflection.ReflectionUtils;
import hu.detox.utils.strings.StringUtils;
import org.apache.commons.io.IOUtils;
import org.w3c.tidy.Tidy;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class SystemUtils extends org.apache.commons.lang3.SystemUtils {
    public static final Tidy HTML_TIDY = new Tidy();
    public static final String UTF8 = "UTF-8";
    public static final Charset UTF8CS = Charset.forName(SystemUtils.UTF8);
    public static int returnCode = 0;

    static {
        SystemUtils.HTML_TIDY.setOutputEncoding(SystemUtils.UTF8);
        SystemUtils.HTML_TIDY.setInputEncoding(SystemUtils.UTF8);
        SystemUtils.HTML_TIDY.setXmlOut(false);
        SystemUtils.HTML_TIDY.setXmlTags(true);
        SystemUtils.HTML_TIDY.setQuiet(true);
        SystemUtils.HTML_TIDY.setBreakBeforeBR(false);
        SystemUtils.HTML_TIDY.setXmlSpace(false);
        SystemUtils.HTML_TIDY.setIndentContent(false);
        SystemUtils.HTML_TIDY.setIndentCdata(false);
        SystemUtils.HTML_TIDY.setSmartIndent(false);
        SystemUtils.HTML_TIDY.setForceOutput(true);
        SystemUtils.HTML_TIDY.setWraplen(Integer.MAX_VALUE);
    }

    public static boolean MAINBASE;
    private static InetAddress PUBLIC;

    public static String classNotationToResource(final String cls) {
        String ret = cls.replace(".", "/").replaceAll("[<>]", "-");
        if (!ret.equals(cls) && !ret.startsWith("/")) {
            ret = "/" + ret;
        }
        return ret;
    }

    public static Process execAbandon(final String[] cmdarray) throws IOException {
        return SystemUtils.execAbandon(cmdarray, null);
    }

    public static Process execAbandon(final String[] cmdarray, final File dir) throws IOException {
        return SystemUtils.execAbandon(cmdarray, null, dir);
    }

    public static Process execAbandon(final String[] cmdarray, final OutputStream out, final OutputStream err) throws IOException {
        return SystemUtils.execAbandon(cmdarray, null, null, out, err);
    }

    public static Process execAbandon(final String[] cmdarray, final String[] envp, final File dir) throws IOException {
        return SystemUtils.execAbandon(cmdarray, envp, dir, null, null);
    }

    public static Process execAbandon(final String[] cmdarray, final String[] envp, final File dir, OutputStream out, OutputStream err) throws IOException {
        if (out == null) {
            out = org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;
        }
        if (err == null) {
            err = org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;
        }
        final OutputStream errf = err;
        final PrintStream errfp = new PrintStream(errf);
        final OutputStream outf = out;
        cmdarray[0] = Main.prop("command." + cmdarray[0], cmdarray[0]);
        final Process ret;
        try {
            ret = Runtime.getRuntime().exec(cmdarray, envp, dir);
        } catch (final RuntimeException re) {
            throw ReflectionUtils.extendMessage(re, "cmd=" + Arrays.toString(cmdarray) + ", dir=" + dir);
        }
        Main.async(() -> {
            try {
                IOUtils.copy(ret.getInputStream(), outf);
            } catch (final IOException e) {
                e.printStackTrace(errfp);
            }
        });
        Main.async(() -> {
            try {
                IOUtils.copy(ret.getErrorStream(), errf);
            } catch (final IOException e) {
                e.printStackTrace(errfp);
            }
        });
        return ret;
    }

    public static void exit(final boolean wait, final boolean killThreads) {
        if (wait) {
            ThreadUtils.waitThreads(killThreads);
        }
        System.err.println("Exit by " + Thread.currentThread().getName() + " returning on " + new Date() + " with " + SystemUtils.returnCode + " code");
        System.exit(SystemUtils.returnCode);
    }

    public static Reader getClipboard() {
        final Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        try {
            final String data = (String) cb.getData(DataFlavor.stringFlavor);
            return new StringReader(data);
        } catch (final IOException | UnsupportedFlavorException uf) {
            return new StringReader(StringUtils.EMPTY);
        }
    }

    public static boolean isMyAddress(final InetAddress addr) throws SocketException {
        boolean ret = addr.isLoopbackAddress();
        if (!ret) {
            ret = NetworkInterface.getByInetAddress(addr) != null;
        }
        return ret;
    }

    public static Locale locale() {
        return Locale.getDefault(); // Returns a thread local
    }

    public static void setClipboardText(final Object txt) {
        final Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        if (cb != null) {
            final StringSelection select = new StringSelection(String.valueOf(txt));
            cb.setContents(select, select);
        }
    }

    public static Integer tcpAlternativePortNeeded(final int port) {
        return SystemUtils.tcpAlternativePortNeeded(null, port);
    }

    public static Integer tcpAlternativePortNeeded(final String hst, final int port) {
        try (Socket ignored = new Socket(StringUtils.isEmpty(hst) ? InetAddress.getLocalHost() : InetAddress.getByName(hst), port)) {
            return Integer.parseInt(Integer.toString(port) + Integer.toString(port));
        } catch (final IOException ignored) {
            return null;
        }
    }

    public static int toIndex(final int siz, final int idx) {
        if (idx >= 0) {
            return idx;
        } else {
            return siz + idx;
        }
    }

    public static <T> int toIndex(final T[] spec, final int idx) {
        return SystemUtils.toIndex(spec == null ? 0 : spec.length, idx);
    }

}
