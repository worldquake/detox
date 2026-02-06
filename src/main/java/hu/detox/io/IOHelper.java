package hu.detox.io;

import com.amazonaws.util.StringInputStream;
import com.google.common.io.ByteStreams;
import hu.detox.Agent;
import hu.detox.Main;
import hu.detox.config.ConfigReader;
import hu.detox.utils.Http;
import hu.detox.utils.StringUtils;
import hu.detox.utils.ThreadUtils;
import hu.detox.utils.url.URL;
import kotlin.Pair;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.ssl.OpenSSL;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.http.entity.ContentType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.measure.unit.NonSI;
import java.io.*;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * This class can be used to copy a data to another location. This supports several data related class like: {@link Reader}, {@link InputStream}, {@link File}
 * or {@link URL}.
 */
//CHOFF Copied code, a util class dealing with multiple kind of streams
@Getter
public class IOHelper extends org.apache.commons.io.IOUtils implements Closeable {
    public static final String APPEND = "-append";
    public static final PrintStream NULL = new PrintStream(ByteStreams.nullOutputStream());
    static int DEFUALT_BUFFER_SIZE = 8192;
    private static Configuration OPENSSLCONFIG;

    public static void initStatic() {
        String dbs = Main.prop("default_buffer_size", IOHelper.DEFUALT_BUFFER_SIZE + "B");
        IOHelper.DEFUALT_BUFFER_SIZE = (int) Main.toAmount(dbs).to(NonSI.BYTE).getMaximumValue();
        OPENSSLCONFIG = ConfigReader.INSTANCE.toCfg(null, Agent.resource("config/javaSSL.kv"));
    }

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

    public static int calcBufferSize(Number any) {
        int res = DEFUALT_BUFFER_SIZE;
        if (any == null) return res;
        if (any instanceof Integer) res = any.intValue();
        else if (any.longValue() <= 0L) res = IOHelper.calcOptimalBufferSize(-any.longValue());
        return Math.min(FileUtils.largeFile, res);
    }

    private static int calcOptimalBufferSize(final long fullSize) {
        return (int) Math.max(1, Math.min(fullSize * 0.1f, FileUtils.largeBufferSize));
    }

    public static InputStream getBufferedInput(final Object parIs) throws IOException {
        return getBufferedInput(parIs, null);
    }


    public static IOHelper attempt(Object any) throws IOException {
        return attempt(any, null);
    }

    public static IOHelper attempt(Object any, String name) throws IOException {
        Pair<IOHelper, Charset> pair = attemptInternal(any, name, null);
        return pair == null ? null : pair.getFirst();
    }

    protected static Pair<IOHelper, Charset> attemptInternal(Object any, String name, Number buf) throws IOException {
        Charset cs = null;
        Object src = null;
        int bufs = calcBufferSize(buf);
        if (any instanceof EncodedResource res) {
            cs = res.getCharset();
            any = res.getResource();
        }
        if (any instanceof StringWriter || any instanceof CharSequence) {
            any = new org.apache.commons.io.input.CharSequenceReader(String.valueOf(any));
        } else if (any instanceof Resource res) {
            if (!res.exists()) return null;
            if (name == null) name = res.getFilename();
            if (buf == null) bufs = calcOptimalBufferSize(res.contentLength());
            any = res.getInputStream();
        } else if (any instanceof File) {
            File f = (File) any;
            src = f;
            if (buf == null) bufs = calcOptimalBufferSize(f.length());
            any = new FileInputStream(f);
            if (name == null) name = f.getName();
        } else if (any instanceof URL) {
            URL u1 = ((URL) any);
            if (name == null) name = u1.getPath();
            if (u1.getProtocol() != null && u1.getProtocol().startsWith("http")) {
                HttpGet get = u1.toMethod(HttpGet.class);
                ClassicHttpResponse cr = Http.cli().executeOpen(null, get, null);
                src = cr;
                any = cr.getEntity().getContent();
            } else any = u1.toURL();
        } else if (any instanceof byte[]) {
            any = new ByteArrayInputStream((byte[]) any);
        }
        if (any instanceof RestClient.ResponseSpec) {
            any = ((RestClient.ResponseSpec) any).toEntity(InputStream.class);
        }
        if (any instanceof ResponseEntity<?> rei) {
            src = rei;
            final org.springframework.http.MediaType cn = rei.getHeaders().getContentType();
            if (cn != null) cs = cn.getCharset();
            String sname = FileUtils.tryFilename(rei);
            if (sname != null) name = sname;
            var res = attemptInternal(rei.getBody(), name, null);
            if (cs == null) cs = res.getSecond();
            return new Pair<>(res.getFirst(), cs);
        }
        if (any instanceof java.net.URL u) {
            src = u;
            if (name == null) name = u.getPath();
            any = u.openConnection();
        }
        if (any instanceof URLConnection uc) {
            if (src == null) src = uc;
            else src = uc.getURL();
            if (name == null) name = uc.getURL().getFile();
            if (cs == null) {
                final String ct = uc.getContentType();
                if (ct != null) {
                    cs = ContentType.parse(ct).getCharset();
                }
                any = uc.getInputStream();
            }
        }
        if (!(any instanceof InputStream)) {
            return null;
        }
        IOHelper ret = new IOHelper((InputStream) any, bufs);
        ret.name = name;
        ret.src = src;
        return new Pair<>(ret, cs);
    }

    public static InputStream getBufferedInput(final Object parIs, Long bufs) throws IOException {
        IOHelper h = attemptInternal(parIs, null, bufs).getFirst();
        InputStream is = attemptInternal(parIs, null, bufs).getFirst().getInput();
        return toBuffered(is, h.getBuffer());
    }

    private static InputStream toBuffered(final InputStream parIs, final int parBuf) {
        final InputStream ret;
        if (parIs instanceof BufferedInputStream) {
            ret = parIs;
        } else if (parIs instanceof ByteArrayInputStream
                || parIs instanceof StringInputStream) {
            ret = parIs;
            ret.mark(Integer.MAX_VALUE);
        } else {
            ret = new BufferedInputStream(parIs, parBuf);
            ret.mark(parBuf);
        }
        return ret;
    }

    public static OutputStream getBufferedOutput(final OutputStream parOs) {
        return IOHelper.getBufferedOutput(parOs, null);
    }

    public static OutputStream getBufferedOutput(final OutputStream parOs, final Number parBuf) {
        final OutputStream ret;
        if (parOs instanceof BufferedOutputStream) {
            ret = parOs;
        } else if (parOs instanceof ByteArrayOutputStream) {
            ret = parOs;
        } else {
            final int bs = IOHelper.calcBufferSize(parBuf);
            ret = new BufferedOutputStream(parOs, bs);
        }
        return ret;
    }

    public static BufferedOutputStream getOutput(final File parF) throws FileNotFoundException {
        return IOHelper.getOutput(parF, null);
    }

    public static BufferedOutputStream getOutput(final File parF, Boolean app) throws FileNotFoundException {
        final BufferedOutputStream ret;
        final int buf = IOHelper.calcOptimalBufferSize(parF.length());
        if (app == null) app = parF.getName().contains(APPEND);
        ret = new BufferedOutputStream(new FileOutputStream(parF, app), buf);
        return ret;
    }

    protected transient InputStream input;
    protected int buffer;
    @Setter
    private String name;
    @Setter
    private Object src;

    protected IOHelper(final InputStream parIs, final Number parBuf) {
        this.buffer = calcBufferSize(parBuf);
        this.input = toBuffered(parIs, buffer);
    }

    public OutputStream copyBytes(final File parFile) throws IOException {
        try (final OutputStream bos = IOHelper.getBufferedOutput(new FileOutputStream(parFile), this.buffer)) {
            return this.copyBytes(bos);
        }
    }

    public void setSrc(Object src) throws IOException {
        this.src = src;
    }

    public OutputStream copyBytes(final java.net.URL parUrl) throws IOException {
        final URLConnection uc = parUrl.openConnection();
        uc.setDoOutput(true);
        return this.copyBytes(uc.getOutputStream());
    }

    public OutputStream copyBytes(final OutputStream parOut) throws IOException {
        final OutputStream bos = IOHelper.getBufferedOutput(parOut, this.buffer);
        try {
            IOUtils.copy(this.input, bos);
        } finally {
            try {
                bos.flush();
            } catch (final IOException ex) {
                // No worries, ignore
            }
            this.close();
        }
        return bos;
    }

    public void mark(final int parLimit) throws IOException {
        this.input.mark(parLimit);
    }

    public void setNameIfNull(String n) {
        if (name == null) name = n;
    }

    public void reset() throws IOException {
        this.input.reset();
    }

    public byte[] toBytes() throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream(this.buffer);
        this.copyBytes(bos);
        return bos.toByteArray();
    }

    public ByteArrayInputStream toByteStream() throws IOException {
        return new ByteArrayInputStream(this.toBytes());
    }

    @Override
    public void close() throws IOException {
        if (this.input != null) {
            this.input.close();
            this.input = null;
        }
        closeSilently(this.src);
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
}
