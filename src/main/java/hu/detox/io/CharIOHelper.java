package hu.detox.io;

import hu.detox.utils.strings.StringUtils;
import hu.detox.utils.reflection.ReflectionUtils;
import javolution.io.CharSequenceReader;
import kotlin.Pair;
import lombok.Getter;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.io.input.ReaderInputStream;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.core.io.support.EncodedResource;

import javax.annotation.Nullable;
import java.io.*;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashMap;
import java.util.Map;

@Getter
public class CharIOHelper extends IOHelper {
    public static final String BOM_FN = "-BOM";
    private static final Map<Charset, ByteOrderMark> BOMS = new HashMap<>();

    static {
        for (final ByteOrderMark bom : ReflectionUtils.getFieldsOfType(ByteOrderMark.class, ByteOrderMark.class)) {
            CharIOHelper.BOMS.put(Charset.forName(bom.getCharsetName()), bom);
        }
    }

    public static void addBOM(final OutputStream os, final Charset cs) throws IOException {
        final ByteOrderMark bom = CharIOHelper.BOMS.get(cs);
        if (bom != null) {
            os.write(bom.getBytes());
        }
    }

    public static @Nullable CharIOHelper attempt(Object any) throws IOException {
        return attempt(any, null);
    }

    public static @Nullable CharIOHelper attempt(Object any, String name) throws IOException {
        return attempt(any, name, null);
    }

    public static @Nullable CharIOHelper attempt(Object any, String name, Charset cs) throws IOException {
        return attempt(any, name, null, cs);
    }

    public static @Nullable CharIOHelper attempt(Object any, String name, Number buf, Charset cs) throws IOException {
        CharIOHelper cio;
        if (any instanceof CharIOHelper) {
            cio = (CharIOHelper) any;
            cio.setNameIfNull(name);
            if (buf != null) cio.buffer = calcBufferSize(buf);
            if (cs != null) cio.setCharset(cs);
        } else {
            int abuf = calcBufferSize(buf);
            Reader r = tryBufferedReader(any, abuf, cs);
            if (r == null) {
                Pair<IOHelper, Charset> io = IOHelper.attemptInternal(any, name, buf);
                cio = io == null ? null : new CharIOHelper(io.getFirst(), cs == null ? io.getSecond() : cs);
            } else {
                cio = new CharIOHelper(r, cs);
                cio.buffer = abuf;
            }
        }
        return cio;
    }

    public static @Nullable BufferedReader tryBufferedReader(final Object parIs) throws IOException {
        return CharIOHelper.tryBufferedReader(parIs, (Number) null);
    }

    public static @Nullable BufferedReader tryBufferedReader(final Object parIs, Charset enc) throws IOException {
        return tryBufferedReader(parIs, null, enc);
    }

    public static @Nullable BufferedReader tryBufferedReader(Object parIs, final Number parBuf) throws IOException {
        return tryBufferedReader(parIs, parBuf, null);
    }

    public static @Nullable BufferedReader tryBufferedReader(Object parIs, final Number parBuf, Charset enc) throws IOException {
        if (enc == null) enc = Charset.defaultCharset();
        Reader ret = null;
        try {
            if (parIs instanceof CharSequence) {
                final CharSequenceReader csr = new CharSequenceReader();
                csr.setInput((CharSequence) parIs);
                ret = csr;
            } else if (parIs instanceof BufferedReader) {
                ret = (BufferedReader) parIs;
            } else if (parIs instanceof StringReader) {
                ret = new BufferedReader((StringReader) parIs);
                ret.mark(Integer.MAX_VALUE);
            } else if (parIs instanceof Reader) {
                ret = (Reader) parIs;
            }
            if (ret != null && !(ret instanceof BufferedReader)) {
                final int bs = IOHelper.calcBufferSize(parBuf);
                ret = new BufferedReader(ret, bs);
                ret.mark(bs);
            }
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to open reader for " + parIs + " with buf=" + parBuf + " enc=" + enc, e);
        }
        return (BufferedReader) ret;
    }

    public static BufferedWriter getBufferedWriter(Object parOs) throws IOException {
        return getBufferedWriter(parOs, 0);
    }

    public static BufferedWriter getBufferedWriter(Object parOs, final long parBuf) throws IOException {
        return getBufferedWriter(parOs, parBuf, false, null);
    }

    public static BufferedWriter getBufferedWriter(Object parOs, final long parBuf, Boolean addBom, Boolean append) throws IOException {
        final Writer ret;
        if (parOs instanceof PrintStream) {
            parOs = new PrintWriter((PrintStream) parOs);
        } else if (parOs instanceof File) {
            parOs = toFileWriter((File) parOs, addBom, append);
        } else if (parOs instanceof OutputStream) {
            parOs = new OutputStreamWriter((OutputStream) parOs);
        }
        ret = toBufferedWriter((Writer) parOs, parBuf);
        return (BufferedWriter) ret;
    }

    private static BufferedWriter toBufferedWriter(final Writer parOs, final Number parBuf) {
        final Writer ret;
        if (parOs instanceof BufferedWriter) {
            ret = parOs;
        } else {
            final int bs = IOHelper.calcBufferSize(parBuf);
            ret = new BufferedWriter(parOs, bs);
        }
        return (BufferedWriter) ret;
    }

    public static @Nullable Pair<String, Charset> tryEncoding(String name) {
        Pair<String, Charset> ret = null;
        final int li = name.lastIndexOf('.');
        if (li > 0) {
            final String ext = FilenameUtils.getExtension(name.substring(0, li));
            if (StringUtils.isNotBlank(ext)) {
                try {
                    final Charset cs = Charset.forName(ext);
                    name = name.substring(0, name.length() - ext.length() - (name.length() - li)) + name.substring(li + 1);
                    ret = new Pair<>(name, cs);
                } catch (final UnsupportedCharsetException ucs) {
                    // No probs, fallback
                } catch (final IllegalCharsetNameException ucs) {
                    // No probs, fallback
                }
            }
        }
        return ret;
    }

    private static Writer toFileWriter(final File parF, Boolean addBom, Boolean append) throws IOException {
        if (addBom == null) addBom = parF.getName().contains(CharIOHelper.BOM_FN);
        final Pair<String, Charset> pcs = CharIOHelper.tryEncoding(parF.getName());
        final Charset cs = pcs == null ? Charset.defaultCharset() : pcs.getSecond();
        final long len = parF.length();
        if (append == null) append = parF.getName().contains(APPEND);
        final OutputStream os = IOHelper.getOutput(parF, append);
        if (addBom && (!append || len <= 0)) {
            CharIOHelper.addBOM(os, cs);
        }
        return new OutputStreamWriter(os, cs);
    }

    public static Pair<String, Charset> toEncoding(final String name) {
        Pair<String, Charset> ret = CharIOHelper.tryEncoding(name);
        if (ret == null) {
            ret = new Pair<>(name, Charset.defaultCharset());
        }
        return ret;
    }

    protected transient BufferedReader reader;
    private Charset charset;

    private CharIOHelper(final IOHelper io, Charset cs) throws IOException {
        super(io.getInput(), (long) io.getBuffer());
        setNameIfNull(io.getName());
        setSrc(io.getSrc());
        if (reader == null) this.setCharset(cs);
    }

    private CharIOHelper(final Reader io, Charset cs) throws IOException {
        super(new ReaderInputStream(io), null);
        setSrc(io);
        this.setCharset(cs);
    }

    @Override
    public void setSrc(Object src) throws IOException {
        if (src instanceof EncodedResource er) setCharset(er.getCharset());
        super.setSrc(src);
    }

    public void reset() throws IOException {
        super.reset();
        reader.reset();
    }

    @Override
    public void close() throws IOException {
        super.close();
        closeSilently(this.reader);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Writer copy(final File parF) throws IOException {
        try (final FileOutputStream bw = new FileOutputStream(parF)) {
            return this.copy(bw);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Writer copy(final java.net.URL parUrl) throws IOException {
        final URLConnection uc = parUrl.openConnection();
        uc.setDoOutput(true);
        return this.copy(uc.getOutputStream());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Writer copy(final OutputStream parStr) throws IOException {
        final Writer bw = new OutputStreamWriter(parStr, this.charset);
        this.copy(bw);
        return bw;
    }

    public Writer copy(final Writer parWriter) throws IOException {
        final Writer bw = CharIOHelper.toBufferedWriter(parWriter, this.buffer);
        try {
            IOUtils.copy(this.reader, parWriter);
        } finally {
            IOUtils.closeQuietly(this); // In case of failure we still flush the target
            bw.flush();
        }
        return bw;
    }

    private void initReader() throws IOException {
        InputStream is = getInput();
        String name = this.getName();
        Charset cs = charset;
        final BOMInputStream bis = new BOMInputStream(is, ByteOrderMark.UTF_8, ByteOrderMark.UTF_16BE,
                ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_32BE, ByteOrderMark.UTF_32LE);
        bis.mark(FileUtils.largeBufferSize);
        final ByteOrderMark bom = bis.getBOM();
        if (bom == null) {
            if (name != null) {
                final Pair<String, Charset> enc = CharIOHelper.tryEncoding(name);
                if (enc != null) {
                    cs = enc.getSecond();
                    name = enc.getFirst();
                    setName(name);
                }
            }
            if (cs == null) {
                // No Charset found, try to detect
                final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                final UniversalDetector detector = new UniversalDetector(null);
                int nread;
                final byte[] buf = new byte[IOHelper.DEFUALT_BUFFER_SIZE];
                //CHOFF Standard resource iteration
                int totalRead = IOHelper.DEFUALT_BUFFER_SIZE; // We do not want to go over the actual buffersize
                while ((nread = bis.read(buf)) > 0) {
                    totalRead += nread;
                    if (!detector.isDone()) {
                        detector.handleData(buf, 0, nread);
                    }
                    bos.write(buf, 0, nread);
                    if (totalRead > FileUtils.largeBufferSize) {
                        break;
                    }
                }
                //CHON Continue
                detector.dataEnd();
                final String dcs = detector.getDetectedCharset();
                if (dcs == null) {
                    cs = Charset.defaultCharset();
                } else {
                    cs = Charset.forName(dcs);
                }
            }
        } else {
            cs = Charset.forName(bom.getCharsetName());
        }
        bis.reset();
        this.reader = tryBufferedReader(new InputStreamReader(bis, cs));
    }

    @Override
    public void mark(final int parLimit) throws IOException {
        super.mark(parLimit);
        this.reader.mark(parLimit);
    }

    public void setCharset(final Charset parCharset) throws IOException {
        if (reader == null || charset == null || !this.charset.equals(parCharset)) {
            this.charset = parCharset;
            this.initReader();
        }
    }

    public char[] toChars() throws IOException {
        return this.toText().toCharArray();
    }

    public String toText() throws IOException {
        final StringWriter sw = new StringWriter(this.buffer);
        this.copy(sw);
        return sw.toString();
    }

    public StringReader toTextReader() throws IOException {
        return new StringReader(this.toText());
    }

}
