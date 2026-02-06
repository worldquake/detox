package hu.detox.io;

import com.google.api.client.util.ByteStreams;
import com.google.common.net.HttpHeaders;
import hu.detox.Agent;
import hu.detox.parsers.AmountCalculator;
import hu.detox.utils.ReflectionUtils;
import hu.detox.utils.StringUtils;
import hu.detox.utils.SystemUtils;
import hu.detox.utils.Time;
import kotlin.Pair;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import javax.measure.unit.NonSI;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

public class FileUtils extends org.apache.commons.io.FileUtils {
    public static final IOFileFilter EXISTS = new ExistsFileFilter();

    private static class ExistsFileFilter implements IOFileFilter {

        private ExistsFileFilter() {
            // Singleton
        }

        @Override
        public boolean accept(final File file) {
            return file.exists();
        }

        @Override
        public boolean accept(final File dir, final String name) {
            return new File(dir, name).exists();
        }
    }

    public static class ID implements Comparable<ID> {
        private long length;
        private boolean full;
        private String hash;

        @Override
        public int compareTo(final ID o) {
            return this.length < o.length ? -1 : this.length == o.length ? this.equals(o) ? 0 : 1 : 1;
        }

        @Override
        public boolean equals(final Object obj) {
            boolean ret = obj == this;
            if (!ret && obj instanceof ID) {
                ret = ObjectUtils.equals(this.hash, ((ID) obj).hash) && ObjectUtils.equals(this.length, ((ID) obj).length);
            }
            return ret;
        }

        public String getHash() {
            return this.hash;
        }

        public long getLength() {
            return this.length;
        }

        public ID hash(final File now) throws IOException {
            this.hash(now, false);
            return this;
        }

        public ID hash(final File now, final boolean full) throws IOException {
            this.full = full;
            if (now == null || !now.isFile()) {
                this.hash = null;
                this.length = -1;
            } else {
                this.length = now.length();
                if (full) {
                    this.hash = FileUtils.md5Hash(now, Long.MAX_VALUE);
                } else {
                    this.full = this.length <= FileUtils.hashLimit;
                    this.hash = FileUtils.md5Hash(now, FileUtils.hashLimit);
                }
            }
            return this;

        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (this.hash == null ? 0 : this.hash.hashCode());
            result = prime * result + (int) (this.length ^ this.length >>> 32);
            return result;
        }

        public boolean isFull() {
            return this.full;
        }

        public void setHash(final String hash) {
            this.hash = hash;
        }

        public void setLength(final long length) {
            this.length = length;
        }

        @Override
        public String toString() {
            final String len = AmountCalculator.INSTANCE.format(this.length, org.apache.commons.io.FileUtils.ONE_KB, NonSI.BYTE) + " (" + this.length + ")";
            return len + (this.hash == null ? "" : ", " + this.hash + (this.full ? " !" : " ?"));
        }
    }

    public static int largeFile = (int) org.apache.commons.io.FileUtils.ONE_MB * 30;
    public static long hashLimit = FileUtils.largeFile;
    public static int largeBufferSize = (int) FileUtils.largeFile;
    private static final Pattern SAFE_FNAME = Pattern.compile("[\\s\\(\\)\\^#%\\&\\!@:\\+=\\{\\}'\\~\\[`\\]]");
    private static Map<String, String> MTTOEXT;

    final static int[] illegalChars = {34, 60, 62, 124, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27,
            28, 29, 30, 31, 58, 42, 63, 92, 47};

    static {
        Arrays.sort(FileUtils.illegalChars);
        MultiValueMap<String, MediaType> mvm = ReflectionUtils.getProperty(MediaTypeFactory.class, "fileExtensionToMediaTypes");
        MTTOEXT = new HashMap<>();
        for (Map.Entry<String, List<MediaType>> e : mvm.entrySet()) {
            for (MediaType mt : e.getValue()) {
                MTTOEXT.put(mt.getType() + '/' + mt.getSubtype(), e.getKey());
            }
        }
    }

    public static void cleanDirectory(final File directory) throws IOException {
        if (directory.isDirectory()) {
            org.apache.commons.io.FileUtils.cleanDirectory(directory);
            directory.setLastModified(Time.time());
        } else {
            FileUtils.createFolder(directory);
        }
    }

    public static String cleanFileName(final String anyFilename) {
        return FileUtils.cleanFileName(anyFilename, null);
    }

    public static String cleanFileName(String anyFilename, final boolean full, final boolean noSpec, String def) {
        def = StringUtils.trimToNull(def);
        if (anyFilename == null) {
            return def;
        }
        anyFilename = FileUtils.replaceAccentLetters(anyFilename);
        final StringBuilder cleanName = new StringBuilder();
        for (int i = 0; i < anyFilename.length(); i++) {
            final int c = anyFilename.charAt(i);
            if (Arrays.binarySearch(FileUtils.illegalChars, c) < 0 && (!full || c <= Byte.MAX_VALUE)) {
                cleanName.append((char) c);
            }
        }
        anyFilename = cleanName.toString();
        if (noSpec) {
            anyFilename = FileUtils.SAFE_FNAME.matcher(cleanName).replaceAll("_");
        }
        return anyFilename.length() > 250 ? def == null ? Integer.toHexString(anyFilename.hashCode()) : def : anyFilename;
    }

    public static String cleanFileName(final String anyFilename, final String def) {
        return FileUtils.cleanFileName(anyFilename, true, true, def);
    }

    public static String concat(final String... paths) {
        return hu.detox.utils.StringUtils.concat("/", paths);
    }

    public static void createFolder(final File parFolder) throws IOException {
        if (!parFolder.isDirectory() && !parFolder.mkdirs()) {
            throw new IOException("Failed to create '" + parFolder + "' directory");
        }
    }

    public static File createFolder(final String parFolderPath) throws IOException {
        final File file = new File(parFolderPath);
        FileUtils.createFolder(file);
        return file;
    }

    public static <T> T deserialize(final File f) throws IOException {
        final InputStream bis = IOHelper.getBufferedInput(f);
        try {
            return (T) SerializationUtils.deserialize(bis);
        } finally {
            bis.close();
        }
    }

    private static String getContentDisposition(String hf) throws IOException {
        hf = hf.replaceFirst("(?i)^.*filename=\"?([^\"]+)\"?.*$", "$1");
        return URLDecoder.decode(hf, StandardCharsets.ISO_8859_1.name());
    }

    public static String getContentDisposition(final URLConnection c) throws IOException {
        final String hf = c.getHeaderField(HttpHeaders.CONTENT_DISPOSITION);
        if (hf != null) {
            return FileUtils.getContentDisposition(hf);
        }
        return null;
    }

    public static MediaType getContentType(final String path) {
        MediaType mime = MediaTypeFactory.getMediaType(path).orElse(null);
        if (mime == null) return null;
        if (mime.getCharset() == null && FileUtils.isTextual(path)) {
            mime = new MediaType(mime.getType(), mime.getSubtype(), CharIOHelper.toEncoding(path).getSecond());
        }
        return mime;
    }

    public static File getFallback(String spec, final String cur) {
        spec = FilenameUtils.normalize(spec, true);
        File ret;
        String ncur = cur;
        do {
            ret = new File(spec.replace("*", ncur));
            if (ret.exists()) {
                return ret;
            }
            final String nncur = ncur.replaceFirst("[\\.\\-]+[^\\.\\-]+$", "");
            if (nncur.equals(ncur)) {
                ncur = cur;
                final String nspec = spec.replaceAll("[^/]+/[^/]+$", "");
                if (nspec.equals(spec)) {
                    break;
                }
                spec = nspec + FilenameUtils.getName(spec);
            }
            ncur = nncur;
        } while (true);
        return null;
    }

    public static String tryFilename(final ResponseEntity<?> query) {
        final ContentDisposition dp = query.getHeaders().getContentDisposition();
        if (dp == null) return null;
        Charset cs = Charset.defaultCharset();
        String ret = dp.getFilename();
        final MediaType mt = query.getHeaders().getContentType();
        if (mt != null) cs = mt.getCharset();
        String ext;
        if (mt != null) {
            ext = MTTOEXT.get(mt.getType() + '/' + mt.getSubtype());
            if (StringUtils.isNotEmpty(ext)) {
                ret += "." + ext;
            }
        }
        // Append Charset to the filename if not present already
        final Pair<String, Charset> csf = CharIOHelper.getEncoding(ret);
        if (cs != null && csf == null) {
            ext = FilenameUtils.getExtension(ret);
            if (StringUtils.isNotEmpty(ext)) {
                ret = FilenameUtils.getBaseName(ret) + "." + cs.name() + "." + ext;
            }
        }
        return ret;
    }

    public static String tryFilename(final URLConnection c) throws IOException {
        String ret = FileUtils.getContentDisposition(c);
        if (ret == null) {
            ret = FilenameUtils.getName(c.getURL().getPath());
        }
        return ret;
    }

    private static int getFirstWildcard(final String pattern) {
        int idx = pattern.indexOf('?');
        if (idx < 0) {
            idx = pattern.indexOf('*');
        }
        return idx;
    }

    public static AutoCloseable getLockToFile(final File file, Object keep) throws Exception {
        final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        if (keep instanceof String) {
            randomAccessFile.writeUTF((String) keep);
        } else if (keep instanceof Number) {
            randomAccessFile.writeDouble(((Number) keep).doubleValue());
        }
        FileLock fl;
        try {
            fl = randomAccessFile.getChannel().tryLock();
        } catch (final java.nio.channels.OverlappingFileLockException ofl) {
            fl = null;
        }
        final FileLock ffl = fl;
        if (ffl == null) {
            if (keep == null) {
                return randomAccessFile;
            }
            randomAccessFile.close();
            String args = null;
            final File who = new File(file + ".txt");
            if (who.canRead()) {
                try {
                    args = org.apache.commons.io.FileUtils.readFileToString(who, hu.detox.utils.SystemUtils.UTF8CS);
                } catch (final IOException ex) {
                    // No problem, not reading arguments
                }
            }
            final String msg = "Locking " + file + " failed" + (args == null ? "" : ": " + args);
            if (keep instanceof Class && Error.class.isAssignableFrom((Class) keep)) {
                throw (Error) ReflectionUtils.getInstance(keep, msg);
            } else if (keep instanceof Class && RuntimeException.class.isAssignableFrom((Class) keep)) {
                throw (RuntimeException) ReflectionUtils.getInstance(keep, msg);
            } else if (Agent.debug) {
                System.err.println(msg);
            }
            return null;
        } else {
            final AutoCloseable cls = new AutoCloseable() {
                @Override
                public void close() throws IOException {
                    try {
                        ffl.release();
                    } finally {
                        randomAccessFile.close();
                    }
                    file.delete();
                }

                @Override
                public String toString() {
                    return "Run lock file " + file;
                }
            };
            if (keep instanceof Class) {
                keep = Boolean.TRUE;
            }
            if (keep == null) {
                return cls;
            } else if (!Boolean.TRUE.equals(keep)) {
                cls.close();
            }
            return fl;
        }
    }

    /**
     * Get the relative path from one file to another, specifying the directory separator. If one of the provided resources does not exist, it is assumed to be
     * a file unless it ends with '/' or '\'.
     *
     * @param targetPath    targetPath is calculated to this file
     * @param basePath      basePath is calculated from this file
     * @param pathSeparator directory separator. The platform default is not assumed so that we can test Unix behaviour when running on Windows (for example)
     * @return
     */
    public static String getRelativePath(final String targetPath, final String basePath, final char pathSeparator) {
        // Normalize the paths
        String normalizedTargetPath = FilenameUtils.normalizeNoEndSeparator(targetPath);
        String normalizedBasePath = FilenameUtils.normalizeNoEndSeparator(basePath);

        // Undo the changes to the separators made by normalization
        if (pathSeparator == '/') {
            normalizedTargetPath = FilenameUtils.separatorsToUnix(normalizedTargetPath);
            normalizedBasePath = FilenameUtils.separatorsToUnix(normalizedBasePath);
        } else if (pathSeparator == '\\') {
            normalizedTargetPath = FilenameUtils.separatorsToWindows(normalizedTargetPath);
            normalizedBasePath = FilenameUtils.separatorsToWindows(normalizedBasePath);
        } else {
            throw new IllegalArgumentException("Unrecognised dir separator '" + pathSeparator + "'");
        }

        final String[] base = StringUtils.split(normalizedBasePath, pathSeparator);
        final String[] target = StringUtils.split(normalizedTargetPath, pathSeparator);

        // First get all the common elements. Store them as a string,
        // and also count how many of them there are.
        final StringBuffer common = new StringBuffer();

        int commonIndex = 0;
        while (commonIndex < target.length && commonIndex < base.length && target[commonIndex].equals(base[commonIndex])) {
            common.append(target[commonIndex] + pathSeparator);
            commonIndex++;
        }

        if (commonIndex == 0) {
            // No single common path element. This most
            // likely indicates differing drive letters, like C: and D:.
            // These paths cannot be relativized.
            throw new IllegalArgumentException("No common path element found for '" + normalizedTargetPath + "' and '" + normalizedBasePath + "'");
        }

        // The number of directories we have to backtrack depends on whether the base is a file or a dir
        // For example, the relative path from
        //
        // /foo/bar/baz/gg/ff to /foo/bar/baz
        //
        // ".." if ff is a file
        // "../.." if ff is a directory
        //
        // The following is a heuristic to figure out if the base refers to a file or dir. It's not perfect, because
        // the resource referred to by this path may not actually exist, but it's the best I can do
        boolean baseIsFile = true;
        if (basePath.lastIndexOf(pathSeparator) == basePath.length() - 1) {
            baseIsFile = false;
        }
        final StringBuffer relative = new StringBuffer();
        if (base.length != commonIndex) {
            final int numDirsUp = baseIsFile ? base.length - commonIndex - 1 : base.length - commonIndex;

            for (int i = 0; i < numDirsUp; i++) {
                relative.append(".." + pathSeparator);
            }
        }
        relative.append(normalizedTargetPath.substring(common.length()));
        return relative.toString();
    }

    public static String getTextSubtype(final String path) {
        if (FileUtils.isXML(path)) {
            return "xml";
        }
        final MediaType mime = MediaTypeFactory.getMediaType(path).orElse(null);
        if (mime == null) return null;
        return mime.getSubtype();
    }

    public static boolean isFilenameValid(final String parFile) {
        final File f = new File(parFile);
        try {
            f.getCanonicalPath();
            return true;
        } catch (final IOException e) {
            return false;
        }
    }

    public static boolean isPathLike(final String any) {
        return any.contains("/") || any.contains("\\");
    }

    public static boolean isShare(final String path) {
        return path.startsWith("//") || path.startsWith("\\\\");
    }

    public static boolean isTextual(final String path) {
        final String st = FileUtils.getTextSubtype(path);
        return st != null;
    }

    public static boolean isWildcard(final String pattern) {
        return FileUtils.getFirstWildcard(pattern) >= 0;
    }

    public static boolean isXML(final String path) {
        return path.contains("/templatedata/") || FilenameUtils.isExtension(path, new String[]{"", "page", "xml", "component", "xsl", "sitemap"});
    }

    public static Collection<File> listFiles(final File any, final IOFileFilter fileFilter, final IOFileFilter dirFilter) {
        if (any.isFile()) {
            if (fileFilter == null || fileFilter.accept(any)) {
                return Collections.singleton(any);
            }
            return Collections.emptyList();
        }
        return org.apache.commons.io.FileUtils.listFiles(any, fileFilter, dirFilter);
    }

    public static List<File> listFiles(final IOFileFilter fileFilter, final IOFileFilter dirFilter, final Collection<File> anys) {
        final List<File> ret = new LinkedList<>();
        for (final File any : anys) {
            if (any.isFile()) {
                ret.add(any);
            } else if (any.isDirectory()) {
                ret.addAll(org.apache.commons.io.FileUtils.listFiles(any, fileFilter, dirFilter));
            }
        }
        return ret;
    }

    public static List<File> listFiles(final IOFileFilter fileFilter, final IOFileFilter dirFilter, final File... anys) {
        return FileUtils.listFiles(fileFilter, dirFilter, Arrays.asList(anys));
    }

    public static Collection<File> listFiles(final IOFileFilter fileFilter, final IOFileFilter dirFilter, final String any) {
        return FileUtils.listFiles(fileFilter, dirFilter, new File(any));
    }

    public static Collection<File> listFiles(final String[] extensions, final boolean recursive, final File... anys) {
        IOFileFilter filter;
        if (extensions == null) {
            filter = TrueFileFilter.INSTANCE;
        } else {
            filter = new SuffixFileFilter(extensions);
        }
        return FileUtils.listFiles(filter, recursive ? TrueFileFilter.INSTANCE : FalseFileFilter.INSTANCE, anys);
    }

    public static String md5Hash(final File f1, long limit) throws IOException {
        if (limit < 0) {
            limit = FileUtils.hashLimit;
        }
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot get hash", e);
        }
        final InputStream br = IOHelper.getBufferedInput(f1);
        final byte[] buf = new byte[FileUtils.largeBufferSize];
        int read;
        final float len = f1.length();
        long clen = 0;
        int pper = -1;
        try {
            if (len > FileUtils.largeBufferSize) {
                System.err.print("Hashing " + f1);
            }
            while ((read = br.read(buf)) >= 0) {
                clen += read;
                if (clen >= limit) {
                    read -= clen - limit; // Exactly the HASH_LIMIT number of bytes needs to be hashed!
                    md.update(buf, 0, read);
                    if (len > FileUtils.largeBufferSize) {
                        System.err.print(" enough!");
                    }
                    break;
                } else {
                    md.update(buf, 0, read);
                }
                final int per = (int) (clen / len * 100);
                if (pper < per) {
                    pper = per;
                    if (len > FileUtils.largeBufferSize) {
                        System.err.print(".");
                    }
                }
            }
        } finally {
            br.close();
        }
        final byte[] thedigest = md.digest();
        final String ret = new String(Hex.encodeHex(thedigest));
        if (len > FileUtils.largeBufferSize) {
            System.err.println(" " + ret);
        }
        return ret;
    }

    private static String mergeToolTitles(String base, final Object[] args, final int startIdx, final Object... argDef) {
        int n = 1;
        final int max = Math.max(args.length, argDef.length);
        for (int i = startIdx; i < max; i++) {
            base = base.replace("__T" + n + "__", String.valueOf(args.length <= i ? argDef[i] : args[i]));
            n++;
        }
        return base;
    }

    /**
     * Search for the next non-existing file based on the given pattern.
     *
     * @param parDir  The directory that my contain files.
     * @param parPatt The file name pattern ('*' will be replaced).
     * @return The next file that not-yet exists.
     */
    public static File nextFile(final File parDir, final String parPatt) {
        int i = 1;
        File next;
        boolean first = true;
        do {
            String file;
            if (first) {
                file = parPatt.replaceAll("\\[.*\\]", "");
                first = false;
            } else {
                file = parPatt.replaceAll("[\\[\\]]", "");
            }
            next = new File(parDir, file.replace("*", "" + i));
            i++;
        } while (next.exists());
        return next;
    }

    public static String replaceAccentLetters(final String str) {
        final String nfdNormalizedString = Normalizer.normalize(str, Normalizer.Form.NFD);
        final Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfdNormalizedString).replaceAll("");
    }

    public static void serialize(final Serializable s, final File f) throws IOException {
        final BufferedOutputStream bos = IOHelper.getOutput(f);
        try {
            SerializationUtils.serialize(s, bos);
        } finally {
            bos.close();
        }
    }

    public static boolean shallowContentEquals(final File f1, final File f2) throws IOException {
        return FileUtils.shallowContentEquals(f1, f2, FileUtils.hashLimit);
    }

    public static boolean shallowContentEquals(final File f1, final File f2, final long lenLimit) throws IOException {
        final long l1 = f1.length();
        final long l2 = f2.length();
        boolean ret = l1 == l2 && l1 >= 0 && l2 >= 0;
        if (ret) {
            InputStream is1 = ByteStreams.limit(new FileInputStream(f1), lenLimit);
            InputStream is2 = ByteStreams.limit(new FileInputStream(f2), lenLimit);
            try {
                is1 = IOHelper.getBufferedInput(is1, (long) IOHelper.calcBufferSize(-lenLimit));
                is2 = IOHelper.getBufferedInput(is2, (long) IOHelper.calcBufferSize(-lenLimit));
                ret = IOUtils.contentEquals(is1, is2);
            } finally {
                is1.close();
                is2.close();
            }
        }
        return ret;
    }

    public static boolean shallowContentEquals(final File f1, final long crc2, final long l2, final long lenLimit) throws IOException {
        final long l1 = f1.length();
        if (l1 != l2 || l1 == -1 || l2 == -1) {
            return false;
        }
        if (l1 <= lenLimit) {
            final CRC32 crc = new CRC32();
            final InputStream br = IOHelper.getBufferedInput(f1);
            final byte[] buf = new byte[FileUtils.largeBufferSize];
            int read;
            try {
                while ((read = br.read(buf)) >= 0) {
                    crc.update(buf, 0, read);
                }
            } finally {
                br.close();
            }
            return crc2 == crc.getValue();
        }
        return true;
    }

    public static boolean shallowContentEquals(final File f1, final String md5, final long l2, final long lenLimit) throws IOException {
        final long l1 = f1.length();
        if (l1 != l2 || l1 == -1 || l2 == -1) {
            return false;
        }
        final String md51 = FileUtils.md5Hash(f1, lenLimit);
        return md5.equalsIgnoreCase(md51);
    }

    public static boolean shallowContentEquals(final File f1, final ZipEntry ent) throws IOException {
        return FileUtils.shallowContentEquals(f1, ent.getCrc(), ent.getSize(), FileUtils.hashLimit);
    }

    public static boolean shallowContentEquals(final File f1, final ZipEntry ent, final long lenLimit) throws IOException {
        return FileUtils.shallowContentEquals(f1, ent.getCrc(), ent.getSize(), lenLimit);
    }

    public static String shallowMd5Hash(final File f1) throws IOException {
        return FileUtils.md5Hash(f1, FileUtils.hashLimit);
    }

    public static File tempFile(final String name) throws IOException {
        final File ret = new File(SystemUtils.JAVA_IO_TMPDIR, name);
        org.apache.commons.io.FileUtils.deleteQuietly(ret);
        return ret;
    }

    public static File toFile(Object write, final String ext) throws IOException {
        if (!(write instanceof File)) {
            if (write instanceof byte[]) {
                write = new ByteArrayInputStream((byte[]) write);
            } else if (write instanceof CharSequence) {
                final File tf = File.createTempFile("read", "." + ext);
                org.apache.commons.io.FileUtils.write(tf, (CharSequence) write);
                write = tf;
            }
            if (!(write instanceof File)) {
                final File tf = File.createTempFile("merge", "." + ext);
                try (final IOHelper io = IOHelper.attempt(write)) {
                    io.copyBytes(tf);
                    write = tf;
                }
            }
        }
        return (File) write;
    }

    public static String toPath(Object any) {
        if (any instanceof File) {
            any = ((File) any).getAbsolutePath();
        } else if (any instanceof URL) {
            any = ((URL) any).getPath();
        }
        if (hu.detox.utils.StringUtils.isNull(any)) {
            return null;
        }
        return String.valueOf(any).replace("\\", "/");
    }

    public static String toPath(String spec, int from, int to) {
        spec = FilenameUtils.normalize(spec, true);
        if (from == 0 && to == 0 && !spec.contains("/")) {
            return spec;
        } else {
            final String[] items = spec.split("/");
            if (from == 0 && to == 0) {
                to = items.length - 1;
            }
            from = SystemUtils.toIndex(items, from);
            to = SystemUtils.toIndex(items, to);
            return StringUtils.join(items, "/", from, to + 1);
        }
    }

    public static void truncate(final File f) throws IOException {
        FileUtils.truncate(f, 0);
    }

    public static void truncate(final File f, final long s) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(f, true); FileChannel outChan = fos.getChannel()) {
            outChan.truncate(s);
        }
    }

    private FileUtils() {
        // Util
    }
}
