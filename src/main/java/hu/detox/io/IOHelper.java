package hu.detox.io;

import com.amazonaws.util.StringInputStream;
import hu.detox.utils.Http;
import hu.detox.utils.url.URL;
import kotlin.Pair;
import lombok.Getter;
import lombok.Setter;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.http.entity.ContentType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import javax.annotation.Nullable;
import java.io.*;
import java.net.URLConnection;
import java.nio.charset.Charset;

/**
 * This class can be used to copy a data to another location. This supports several data related class like: {@link Reader}, {@link InputStream}, {@link File}
 * or {@link URL}.
 */
//CHOFF Copied code, a util class dealing with multiple kind of streams
@Getter
public class IOHelper extends IOUtils implements Closeable {
    public static @Nullable IOHelper attempt(Object any) throws IOException {
        return attempt(any, null);
    }

    public static @Nullable IOHelper attempt(Object any, String name) throws IOException {
        Pair<IOHelper, Charset> pair = attemptInternal(any, name, null);
        return pair == null ? null : pair.getFirst();
    }

    protected static @Nullable Pair<IOHelper, Charset> attemptInternal(Object any, String name, Number bufHint) throws IOException {
        Charset cs = null;
        Object src = null;
        int bufs = calcBufferSize(bufHint);
        if (any instanceof EncodedResource res) {
            cs = res.getCharset();
            any = res.getResource();
        }
        if (any instanceof StringWriter || any instanceof CharSequence) {
            any = new org.apache.commons.io.input.CharSequenceReader(String.valueOf(any));
        } else if (any instanceof Resource res) {
            if (!res.exists()) return null;
            if (name == null) name = res.getFilename();
            if (bufHint == null) bufs = calcOptimalBufferSize(res.contentLength());
            any = res.getInputStream();
        } else if (any instanceof File) {
            File f = (File) any;
            src = f;
            if (bufHint == null) bufs = calcOptimalBufferSize(f.length());
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
            final org.springframework.http.MediaType cn = rei.getHeaders().getContentType();
            if (cn != null) cs = cn.getCharset();
            String sname = FileUtils.tryFilename(rei);
            if (sname != null) name = sname;
            var res = attemptInternal(rei.getBody(), name, bufHint);
            if (res == null) return null;
            if (cs == null) cs = res.getSecond();
            res.getFirst().src = rei;
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

    public static @Nullable InputStream getBuffered(final Object parIs) throws IOException {
        return getBuffered(parIs, null);
    }

    public static @Nullable InputStream getBuffered(final Object parIs, Number bufHint) throws IOException {
        var intResult = attemptInternal(parIs, null, bufHint);
        if (intResult == null) return null;
        IOHelper h = intResult.getFirst();
        InputStream is = h.getInput();
        return toBuffered(is, h.getBuffer());
    }

    private static InputStream toBuffered(final InputStream parIs, final int bufSize) {
        final InputStream ret;
        if (parIs instanceof BufferedInputStream) {
            ret = parIs;
        } else if (parIs instanceof ByteArrayInputStream
                || parIs instanceof StringInputStream) {
            ret = parIs;
            ret.mark(Integer.MAX_VALUE);
        } else {
            ret = new BufferedInputStream(parIs, bufSize);
            ret.mark(bufSize);
        }
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

    public void setSrc(Object src) throws IOException {
        this.src = src;
    }

    public <T extends Closeable> T copy(final File parFile) throws IOException {
        try (final OutputStream bos = IOHelper.getOutput(new FileOutputStream(parFile), this.buffer)) {
            return this.copy(bos);
        }
    }

    public <T extends Closeable> T copy(final java.net.URL parUrl) throws IOException {
        final URLConnection uc = parUrl.openConnection();
        uc.setDoOutput(true);
        return this.copy(uc.getOutputStream());
    }

    public <T extends Closeable> T copy(final OutputStream parOut) throws IOException {
        final OutputStream bos = IOHelper.getOutput(parOut, this.buffer);
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
        return (T) bos;
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
        this.copy(bos);
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
}
