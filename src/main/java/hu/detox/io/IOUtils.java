package hu.detox.io;

import com.google.common.io.ByteStreams;
import hu.detox.Agent;
import hu.detox.Main;
import hu.detox.config.ConfigReader;
import hu.detox.utils.strings.StringUtils;
import hu.detox.utils.ThreadUtils;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.ssl.OpenSSL;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.measure.unit.NonSI;
import java.io.*;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class IOUtils extends org.apache.commons.io.IOUtils {
    public static final String APPEND = "-append";
    public static final PrintStream NULL = new PrintStream(ByteStreams.nullOutputStream());
    static int DEFUALT_BUFFER_SIZE = 8192;
    private static Configuration OPENSSLCONFIG;

    public static class PBEOpenSSL {
        public static final String SALT8 = "changeit";
        public String algo = "PBEWithMD5AndDES";
        public String pass;
        public String salt = SALT8;
        public int iter = 1;

        public Cipher makeCipher(final int mode) {
            final PBEParameterSpec pbeParamSpec = new PBEParameterSpec(this.salt.getBytes(), this.iter);
            final PBEKeySpec pbeKeySpec = new PBEKeySpec(this.pass.toCharArray());
            try {
                final SecretKeyFactory keyFac = SecretKeyFactory.getInstance(this.algo);
                final Key pbeKey = keyFac.generateSecret(pbeKeySpec);
                final Cipher pbeCipher = Cipher.getInstance(this.algo);
                pbeCipher.init(mode, pbeKey, pbeParamSpec);
                return pbeCipher;
            } catch (final Exception ex) {
                throw new IllegalStateException("Failed " + this.algo + ", salt size=" + this.salt.length() + ", plen=" + this.pass.length(), ex);
            }
        }

        public String toSSLCipher() {
            return OPENSSLCONFIG.getString(this.algo, this.algo);
        }
    }

    public static void initStatic() {
        String dbs = Main.prop("default_buffer_size", DEFUALT_BUFFER_SIZE + "B");
        DEFUALT_BUFFER_SIZE = (int) Main.toAmount(dbs).to(NonSI.BYTE).getMaximumValue();
        OPENSSLCONFIG = ConfigReader.INSTANCE.toCfg(null, Agent.resource("config/javaSSL.kv"));
    }

    public static int calcBufferSize(Number any) {
        int res = DEFUALT_BUFFER_SIZE;
        if (any == null) return res;
        if (any instanceof Integer) res = any.intValue();
        else if (any.longValue() <= 0L) res = calcOptimalBufferSize(-any.longValue());
        return Math.min(FileUtils.largeFile, res);
    }

    protected static int calcOptimalBufferSize(final long fullSize) {
        return (int) Math.max(1, Math.min(fullSize * 0.1f, FileUtils.largeBufferSize));
    }

    public static boolean deleteIfOld(final File f, final long to) {
        boolean ret = f.exists();
        if (ret && f.lastModified() < to) {
            ret = !org.apache.commons.io.FileUtils.deleteQuietly(f);
        }
        return ret;
    }

    public static void closeSilently(final Object o) {
        if (o == null) {
            return;
        }
        if (Agent.debug) {
            System.err.println("Closing " + StringUtils.forceToString(o) + "...");
        }
        try {
            if (o instanceof AutoCloseable) {
                ((AutoCloseable) o).close();
            } else if (o instanceof Statement) {
                if (!((Statement) o).isClosed()) {
                    ((Statement) o).close();
                }
            } else if (o instanceof Connection) {
                if (!((Connection) o).isClosed()) {
                    ((Connection) o).close();
                }
            } else if (o instanceof ResultSet) {
                if (!((ResultSet) o).isClosed()) {
                    ((ResultSet) o).close();
                }
            } else if (o instanceof Runnable) {
                ((Runnable) o).run();
            }
        } catch (final Exception e) {
            ThreadUtils.uncaught(e);
        }
    }

    public static OutputStream openSSL(final int mode, final PBEOpenSSL cipher, final OutputStream os) throws IOException {
        if (cipher == null || cipher.pass == null) {
            return os;
        }
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        return new FilterOutputStream(bos) {
            @Override
            public void close() throws IOException {
                super.close();
                try {
                    final byte[] out;
                    if (mode == Cipher.ENCRYPT_MODE) {
                        out = OpenSSL.encrypt(cipher.toSSLCipher(), cipher.pass.toCharArray(), bos.toByteArray());
                    } else {
                        out = OpenSSL.decrypt(cipher.toSSLCipher(), cipher.pass.toCharArray(), bos.toByteArray());
                    }
                    os.write(out);
                } catch (final GeneralSecurityException e) {
                    throw new IOException("Failed to encrypt with " + cipher + " to " + os, e);
                }
            }
        };
    }

    public static OutputStream openSSL(final int mode, final String pass, final OutputStream os) throws IOException {
        final PBEOpenSSL cipher = new PBEOpenSSL();
        cipher.pass = pass;
        return openSSL(mode, cipher, os);
    }

    public static OutputStream getOutput(final OutputStream parOs) {
        return getOutput(parOs, null);
    }

    public static OutputStream getOutput(final OutputStream parOs, final Number parBuf) {
        final OutputStream ret;
        if (parOs instanceof BufferedOutputStream) {
            ret = parOs;
        } else if (parOs instanceof ByteArrayOutputStream) {
            ret = parOs;
        } else {
            final int bs = calcBufferSize(parBuf);
            ret = new BufferedOutputStream(parOs, bs);
        }
        return ret;
    }

    public static BufferedOutputStream getOutput(final File parF) throws FileNotFoundException {
        return getOutput(parF, null);
    }

    public static BufferedOutputStream getOutput(final File parF, Boolean app) throws FileNotFoundException {
        final BufferedOutputStream ret;
        final int buf = calcOptimalBufferSize(parF.length());
        if (app == null) app = parF.getName().contains(APPEND);
        ret = new BufferedOutputStream(new FileOutputStream(parF, app), buf);
        return ret;
    }

}
