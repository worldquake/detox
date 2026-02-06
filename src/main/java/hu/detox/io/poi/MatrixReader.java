package hu.detox.io.poi;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.ibm.icu.lang.UCharacter;
import com.jayway.jsonpath.internal.function.numeric.Max;
import hu.detox.Agent;
import hu.detox.config.ConfigReader;
import hu.detox.io.CharIOHelper;
import hu.detox.io.FileUtils;
import hu.detox.io.IOHelper;
import hu.detox.parsers.JSonUtil;
import hu.detox.parsers.JsonConverter;
import hu.detox.utils.*;
import hu.detox.utils.url.URL;
import kotlin.Pair;
import lombok.Getter;
import lombok.Setter;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.Zip4jConstants;
import org.apache.commons.beanutils.BasicDynaBean;
import org.apache.commons.beanutils.BasicDynaClass;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.LazyDynaBean;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.AbstractFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.hssf.record.crypto.Biff8EncryptionKey;
import org.apache.poi.hssf.usermodel.HSSFFormulaEvaluator;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.ooxml.POIXMLProperties.CoreProperties;
import org.apache.poi.ooxml.POIXMLProperties.CustomProperties;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.formula.WorkbookEvaluator;
import org.apache.poi.ss.formula.functions.Column;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.officeDocument.x2006.customProperties.CTProperty;
import org.springframework.http.MediaType;
import org.supercsv.io.CsvListReader;
import org.supercsv.io.CsvListWriter;
import org.supercsv.prefs.CsvPreference;
import org.supercsv.prefs.CsvPreference.Builder;
import org.supercsv.quote.AlwaysQuoteMode;

import javax.crypto.Cipher;
import javax.print.attribute.standard.Destination;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class MatrixReader extends BasicDynaBean implements Closeable {
    public enum Property {
        APP, AUTHOR, TITLE, DESC, CREATED, MODIFIED
    }

    private static final EncryptionInfo CRYPTOR = new EncryptionInfo(EncryptionMode.standard);
    private static final Integer ZERO = 0;

    private static final String HEADS = "heads";
    private static final String VALUES = "values";
    public static final String CSV = "csv";
    public static final String ECSV = "ecsv";
    public static final String TXT = "txt";
    public static final String XLS = "xls";
    public static final String XLSX = "xlsx";
    public static final String XLSM = "xlsm";
    private static final String[] XPOIEXTS = new String[]{MatrixReader.XLSM, MatrixReader.XLSX};
    private static final String[] POIEXTS = new String[]{MatrixReader.XLS, MatrixReader.XLSX, MatrixReader.XLSM};
    public static final String[] TXTEXTS = new String[]{MatrixReader.ECSV, MatrixReader.CSV, MatrixReader.TXT};
    public static final String[] TAB = new String[]{MatrixReader.TXT, "tsv"};
    private static final String[] EXTS = new String[]{MatrixReader.ECSV, MatrixReader.CSV, MatrixReader.TXT, MatrixReader.XLS, MatrixReader.XLSX,
            MatrixReader.XLSM};
    public static final Pattern NAMEPATT = Pattern.compile("\\+([^\\+]+)");
    public static final Pattern FNAMEPATT = Pattern.compile("[\\[<]([^\\]\\)]+)[\\]>]");
    public static final String DATE_TIME = "[date_time]";

    public static final IOFileFilter RECOGNIZED = new AbstractFileFilter() {
        @Override
        public boolean accept(final File file) {
            return file.isFile() && MatrixReader.isRecognizedExtension(file.getName());
        }
    };

    private static final Logger logger = LogManager.getLogger(MatrixReader.class);

    public static Row addRow(final Sheet sh, final List<Object> vals) {
        final Object[] arr = new Object[vals.size()];
        vals.toArray(arr);
        return MatrixReader.addRow(sh, arr);
    }

    public static Row addRow(final Sheet sh, final Object... vals) {
        return MatrixReader.addRowTo(sh, ArrayUtils.INDEX_NOT_FOUND, vals);
    }

    public static Row addRowTo(final Sheet sh, int rn, final Object... vals) {
        Row r;
        if (rn == ArrayUtils.INDEX_NOT_FOUND) {
            rn = sh.getPhysicalNumberOfRows() == 0 ? 0 : sh.getLastRowNum() + 1;
            r = sh.createRow(rn);
        } else if (rn < ArrayUtils.INDEX_NOT_FOUND) {
            rn = -rn + ArrayUtils.INDEX_NOT_FOUND;
            r = sh.createRow(rn);
        } else {
            r = sh.getRow(rn);
        }
        if (r == null) {
            r = sh.createRow(rn);
        }
        for (int ci = 0; ci < vals.length; ci++) {
            final Cell c = r.createCell(ci);
            Source.setCellVal(c, vals[ci]);
        }
        return r;
    }

    public static List<String> alignHeaderCase(final List<String> hs, final boolean upper) {
        String h;
        for (final ListIterator<String> it = hs.listIterator(); it.hasNext(); ) {
            h = it.next();
            if (upper) {
                h = UCharacter.toUpperCase(h);
            } else {
                h = UCharacter.toLowerCase(h);
            }
            it.set(h);
        }
        return hs;
    }

    public static void alignHeaderCase(final String[] h, final boolean upper) {
        for (int i = 0; i < h.length; i++) {
            if (upper) {
                h[i] = UCharacter.toUpperCase(h[i]);
            } else {
                h[i] = UCharacter.toLowerCase(h[i]);
            }
        }
    }

    public static String[] alignHeaderCaseCopy(final String[] h, final boolean upper) {
        final String[] ret = new String[h.length];
        System.arraycopy(h, 0, ret, 0, h.length);
        MatrixReader.alignHeaderCase(ret, upper);
        return ret;
    }

    public static int copy(final CsvListReader lr, final CsvListWriter cw) throws IOException {
        return MatrixReader.copy(lr, cw, null);
    }

    public static int copy(final CsvListReader lr, final CsvListWriter cw, String[] head) throws IOException {
        if (head == null) {
            head = lr.getHeader(true);
        }
        List<String> ln;
        if (cw.getLineNumber() == 0) {
            cw.writeHeader(head);
        }
        int ret = 0;
        while ((ln = lr.read()) != null) {
            cw.write(ln);
            ret++;
        }
        return ret;
    }

    public static int copy(final CharIOHelper input, final File out) throws IOException {
        try (CsvListReader r = MatrixReader.to(input).getSecond(); CsvListWriter w = MatrixReader.toWriter(out)) {
            return MatrixReader.copy(r, w);
        }
    }

    public static void copyProperties(final Workbook src, final Workbook targ) throws IOException {
        if (src == null || targ == null || src == targ) {
            return;
        }
        for (final Property p : Property.values()) {
            final Object pv = MatrixReader.getProperty(src, p);
            MatrixReader.setProperty(targ, p, pv);
        }
        if (src instanceof XSSFWorkbook && targ instanceof XSSFWorkbook) {
            final CustomProperties sxp = ((XSSFWorkbook) src).getProperties().getCustomProperties();
            final CustomProperties txp = ((XSSFWorkbook) targ).getProperties().getCustomProperties();
            final CTProperty[] cps = sxp.getUnderlyingProperties().getPropertyArray();
            for (final CTProperty cp : cps) {
                final Object ret = MatrixReader.getCTProp(sxp, cp.getName());
                MatrixReader.setCTProp(txp, cp.getName(), ret);
            }
        } else if (src instanceof HSSFWorkbook && targ instanceof HSSFWorkbook) {
            final SummaryInformation ssi = ((HSSFWorkbook) src).getSummaryInformation();
            if (ssi != null) {
                SummaryInformation tsi = ((HSSFWorkbook) targ).getSummaryInformation();
                if (tsi == null) {
                    ((HSSFWorkbook) targ).createInformationProperties();
                    tsi = ((HSSFWorkbook) targ).getSummaryInformation();
                }
                try {
                    BeanUtils.copyProperties(tsi, ssi);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("Copying " + ssi + " failed", e);
                }
            }
        } else {
            throw new UnsupportedOperationException("Copying " + src + " to " + targ + " unsupported");
        }
    }

    public static Workbook empty(final String fname) {
        Workbook wb;
        if (FilenameUtils.isExtension(fname, MatrixReader.XPOIEXTS)) {
            wb = new XSSFWorkbook();
        } else {
            wb = new HSSFWorkbook();
        }
        MatrixReader.setProperty(wb, Property.CREATED, Time.date());
        return wb;
    }

    public static Pair<CsvPreference, Charset> forNameWithExtension(final AtomicReference<String> name) throws IOException {
        return forNameWithExtension(name, null);
    }

    public static Pair<CsvPreference, Charset> forNameWithExtension(final AtomicReference<String> name, MediaType mt) throws IOException {
        String ext = ObjectUtils.firstNonNull(FilenameUtils.getExtension(name.get()), MatrixReader.CSV);
        Charset cs = SystemUtils.UTF8CS;
        if (mt != null) {
            cs = ObjectUtils.firstNonNull(mt.getCharset(), cs);
            ext = StringUtils.firstNonBlank(mt.getSubtype(), ext);
        }
        CsvPreference preference = toPreference(ext);
        final Pair<String, Charset> pcs = CharIOHelper.getEncoding(name.get());
        if (pcs != null) {
            cs = pcs.getSecond();
            name.set(pcs.getFirst());
        }
        final LazyDynaBean to = new LazyDynaBean();
        final Matcher m = MatrixReader.NAMEPATT.matcher(FilenameUtils.getBaseName(name.get()));
        name.set(StringUtils.resolveName(m, to));
        final Pair<Character, String> sf = ConfigReader.getSeparatorChar(name.get());
        final Character quot = ConfigReader.toSeparatorChar(to.get("q"));
        Character sep = ConfigReader.toSeparatorChar(to.get("s"));
        final String nl = (String) to.get("l");
        if (sep == null && sf != null) {
            sep = sf.getFirst();
        }
        if (quot != null || sep != null || nl != null) {
            preference = new Builder(quot == null ? '"' : quot, sep == null ? ',' : sep, nl == null ? "\n" : nl).build();
        }
        if (sf != null) {
            name.set(sf.getSecond());
        }
        if (name.get().contains("-aqm")) {
            preference = new Builder(preference).useQuoteMode(new AlwaysQuoteMode()).build();
        }
        name.set(FilenameUtils.getName(name.get()));
        if (!name.get().contains(".")) {
            name.set(name.get() + "." + ext);
        }
        return new Pair<>(preference, cs);
    }

    public static String getCommentFor(final Cell c) {
        final Comment co = c.getCellComment();
        if (co == null) {
            return null;
        }
        final RichTextString str = co.getString();
        if (str == null) {
            return null;
        }
        return str.getString();
    }

    private static <T> T getCTProp(final CustomProperties cup, final String key) {
        final CTProperty retp = cup.getProperty(key);
        Object ret = null;
        if (retp == null || retp.isNil()) {
            // Nothing to do
        } else if (retp.isSetBool()) {
            ret = retp.getBool();
        } else if (retp.isSetR8()) {
            ret = retp.getR8();
        } else if (retp.isSetR4()) {
            ret = retp.getR4();
        } else if (retp.isSetLpwstr()) {
            ret = retp.getLpwstr();
        } else if (retp.isSetLpstr()) {
            ret = retp.getLpstr();
        } else if (retp.isSetFiletime()) {
            ret = retp.getFiletime();
        } else if (retp.isSetDate()) {
            ret = retp.getDate();
        } else if (retp.isSetDecimal()) {
            ret = retp.getDecimal();
        } else if (retp.isSetI1()) {
            ret = retp.getI1();
        } else if (retp.isSetI1()) {
            ret = retp.getI1();
        } else if (retp.isSetI2()) {
            ret = retp.getI2();
        } else if (retp.isSetI4()) {
            ret = retp.getI4();
        } else if (retp.isSetI8()) {
            ret = retp.getI8();
        } else if (retp.isSetInt()) {
            ret = retp.getInt();
        } else if (retp.isSetNull()) {
            // Nothing to do
        } else {
            throw new IllegalArgumentException("Unsupported " + retp);
        }
        return (T) ret;
    }

    public static FormulaEvaluator getEvalutor(final Workbook wb) {
        if (wb == null) {
            return null;
        }
        return wb instanceof XSSFWorkbook ? new XSSFFormulaEvaluator((XSSFWorkbook) wb) : new HSSFFormulaEvaluator((HSSFWorkbook) wb);
    }

    public static String getExtensionFor(final Workbook wb) {
        String ret = ".";
        if (wb instanceof XSSFWorkbook) {
            ret += ((XSSFWorkbook) wb).isMacroEnabled() ? MatrixReader.XLSM : MatrixReader.XLSX;
        } else {
            ret += MatrixReader.XLS;
        }
        return ret;
    }

    public static <T> T getProperty(final Workbook wb, final Object key) {
        Object ret = null;
        if (wb == null) {
            return (T) ret;
        }
        if (key instanceof Property) {
            ret = MatrixReader.getStandardProperty(wb, (Property) key);
        } else if (wb instanceof XSSFWorkbook) {
            final POIXMLProperties xp = ((XSSFWorkbook) wb).getProperties();
            final CustomProperties cup = xp.getCustomProperties();
            ret = MatrixReader.getCTProp(cup, (String) key);
        } else {
            final SummaryInformation si = ((HSSFWorkbook) wb).getSummaryInformation();
            if (si != null) {
                JsonObject obj = null;
                if (si.getComments() != null) {
                    obj = (JsonObject) JsonConverter.INSTANCE.apply(si.getComments());
                }
                ret = JSonUtil.getProperty(obj, (String) key);
            }
        }
        if (ret instanceof Calendar) {
            final String tz = MatrixReader.getProperty(wb, TimeZone.class.getSimpleName());
            if (tz != null) {
                ((Calendar) ret).setTimeZone(TimeZone.getTimeZone(tz));
            }
        }
        return (T) ret;
    }

    public static <T> T getProperty(final Workbook wb, final Object key, final T def) {
        T any = MatrixReader.getProperty(wb, key);
        if (any == null) {
            any = def;
        }
        return any;
    }

    private static Object getStandardProperty(final Workbook wb, final Property key) {
        Object ret = null;
        if (wb == null) {
            return ret;
        }
        if (wb instanceof XSSFWorkbook) {
            final POIXMLProperties xp = ((XSSFWorkbook) wb).getProperties();
            final CoreProperties cp = xp.getCoreProperties();
            switch (key) {
                case TITLE:
                    ret = cp.getTitle();
                    break;
                case MODIFIED:
                    ret = cp.getModified();
                    break;
                case AUTHOR:
                    ret = cp.getCreator();
                    break;
                case APP:
                    ret = MatrixReader.getProperty(wb, key.name());
                    break;
                case CREATED:
                    ret = cp.getCreated();
                    break;
                case DESC:
                    ret = cp.getDescription();
                    break;
            }
        } else {
            final SummaryInformation si = ((HSSFWorkbook) wb).getSummaryInformation();
            if (si == null) {
                return null;
            }
            switch (key) {
                case AUTHOR:
                    ret = si.getAuthor();
                    break;
                case MODIFIED:
                    ret = si.getLastSaveDateTime();
                    break;
                case TITLE:
                    ret = si.getTitle();
                    break;
                case CREATED:
                    ret = si.getCreateDateTime();
                    break;
                case APP:
                    ret = si.getApplicationName();
                    break;
                case DESC:
                    ret = si.getComments();
                    break;
            }
        }
        return ret;
    }

    public static CellStyle getStyle(final Cell c, final String name, final Function<CreationHelper, CellStyle> trafo) {
        final Number csi = MatrixReader.getProperty(c.getSheet().getWorkbook(), name);
        CellStyle cs = null;
        if (csi != null) {
            try {
                cs = c.getSheet().getWorkbook().getCellStyleAt(csi.shortValue());
            } catch (final IndexOutOfBoundsException oob) {
                // No worries
            }
        }
        if (cs == null) {
            cs = trafo.apply(c.getSheet().getWorkbook().getCreationHelper());
            MatrixReader.setProperty(c.getSheet().getWorkbook(), name, cs.getIndex());
        }
        return cs;
    }

    public static boolean isPOIExtension(final String fname) {
        return FilenameUtils.isExtension(fname, MatrixReader.POIEXTS);
    }

    public static boolean isRecognizedExtension(final String fname) {
        return FilenameUtils.isExtension(fname, MatrixReader.EXTS) || ArrayUtils.indexOf(MatrixReader.EXTS, fname) >= 0;
    }

    public static Workbook open(final CharIOHelper file) throws IOException {
        return MatrixReader.open(file, null);
    }

    public static Workbook open(final CharIOHelper file, final String pw) throws IOException {
        Workbook workbook;
        try (final CharIOHelper fis = file) {
            workbook = MatrixReader.openInternal(fis, pw);
        }
        return workbook;
    }

    private static Workbook openInternal(CharIOHelper src, final String pw) throws IOException {
        Workbook workbook;
        final String cup = MatrixReader.setUserPassword(pw);
        POIFSFileSystem fs = null;
        InputStream stream = null;
        String path = src.getName();
        try {
            final String cp = Biff8EncryptionKey.getCurrentUserPassword();
            if (cp != null) {
                try {
                    final byte[] arr = IOUtils.toByteArray(src.getInput());
                    src.close();
                    stream = new ByteArrayInputStream(arr);
                    stream.mark(Integer.MAX_VALUE);
                    fs = new POIFSFileSystem(stream);
                    if (MatrixReader.XLS.equalsIgnoreCase(FilenameUtils.getExtension(path))) {
                        workbook = new HSSFWorkbook(fs.getRoot(), true);
                        return workbook;
                    }
                    final EncryptionInfo dec = new EncryptionInfo(fs);
                    final Decryptor d = dec.getDecryptor();
                    try {
                        if (!d.verifyPassword(cp)) {
                            throw new IOException("Unable to process " + path + ", document is encrypted, "
                                    + (pw == null ? "no password given" : "pass-length=" + pw.length()));
                        }
                        stream = d.getDataStream(fs);
                    } catch (final GeneralSecurityException e) {
                        throw new IOException("Document " + path + " is not decryptable", e);
                    }
                } catch (final org.apache.poi.poifs.filesystem.OfficeXmlFileException ex) {
                    // The XLSX is not encrypted, we reset the stream and go ahead
                    stream.reset();
                }
            }
            if (FilenameUtils.isExtension(path, MatrixReader.XPOIEXTS)) {
                workbook = new XSSFWorkbook(stream);
            } else {
                workbook = new HSSFWorkbook(stream);
            }
        } catch (final RuntimeException ex) {
            throw ReflectionUtils.extendMessage(ex, "f=" + path);
        } catch (final IOException ex) {
            throw ReflectionUtils.extendMessage(ex, "f=" + path);
        } finally {
            Biff8EncryptionKey.setCurrentUserPassword(cup);
            if (fs != null) {
                fs.close();
            }
            hu.detox.io.IOHelper.closeSilently(src);
            hu.detox.io.IOHelper.closeSilently(stream);
        }
        return workbook;
    }

    public static void removeRow(final Sheet sheet, final int rowIndex) {
        final int lastRowNum = sheet.getLastRowNum();
        if (rowIndex >= 0 && rowIndex < lastRowNum) {
            sheet.shiftRows(rowIndex + 1, lastRowNum, ArrayUtils.INDEX_NOT_FOUND);
        }
        if (rowIndex == lastRowNum) {
            final Row removingRow = sheet.getRow(rowIndex);
            if (removingRow != null) {
                sheet.removeRow(removingRow);
            }
        }
    }

    public static void rotate(final File text, final String... cols) throws IOException {
        final List<List<?>> ll = new LinkedList<>();
        if (text.lastModified() == 0) {
            return;
        }
        try (CsvListReader read = MatrixReader.to(CharIOHelper.attempt(text)).getSecond()) {
            List<?> l;
            while ((l = read.read()) != null) {
                ll.add(l);
            }
        } finally {
            final List<Object> anys = new LinkedList<>();
            try (final CsvListWriter lw = MatrixReader.toWriter(text)) {
                if (!ArrayUtils.isEmpty(cols)) {
                    lw.writeHeader(cols);
                }
                for (int i = ll.get(0).size() - 1; i >= 0; i--) {
                    anys.clear();
                    for (final List<?> l : ll) {
                        anys.add(l.get(i));
                    }
                    lw.write(anys);
                }
            }
            text.setLastModified(0);
        }
    }

    public static File saveTo(final Workbook wb, final File file) throws IOException {
        return MatrixReader.saveTo(wb, file, null);
    }

    public static synchronized File saveTo(final Workbook wb, File ret, String pw) throws IOException {
        if (StringUtils.isEmpty(pw)) pw = null;
        final String cup = MatrixReader.setUserPassword(pw);
        final String cp = Biff8EncryptionKey.getCurrentUserPassword();
        try {
            final Date now = Time.date();
            if (ret != null && !ret.exists() && MatrixReader.getProperty(wb, Property.CREATED) == null) {
                MatrixReader.setProperty(wb, Property.CREATED, now);
            }
            MatrixReader.setProperty(wb, Property.MODIFIED, now);
            if (wb instanceof HSSFWorkbook) {
                final HSSFWorkbook hwb = (HSSFWorkbook) wb;
                try (OutputStream nos = IOHelper.getBufferedOutput(new FileOutputStream(ret))) {
                    if (cp != null) {
                        hwb.writeProtectWorkbook(cp, StringUtils.EMPTY);
                    }
                    hwb.write(nos);
                }
                if (cp != null) {
                    // TODO This is a workaround to encrypt xls files as the POI does not seem to properly support it
                    final File nret = new File(ret.getParentFile(), ret.getName() + ".zip");
                    try {
                        FileUtils.delete(nret);
                        final net.lingala.zip4j.core.ZipFile zf = new net.lingala.zip4j.core.ZipFile(nret);
                        final ZipParameters zp = new ZipParameters();
                        zp.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);
                        zp.setAesKeyStrength(Zip4jConstants.AES_STRENGTH_256);
                        zp.setEncryptionMethod(Zip4jConstants.ENC_METHOD_AES);
                        zp.setPassword(cp.toCharArray());
                        zp.setEncryptFiles(true);
                        zp.setIncludeRootFolder(false);
                        zf.createZipFile(ret, zp);
                        ret = nret;
                    } catch (final ZipException e) {
                        throw new IOException("Failed to create encrypted zip for " + ret, e);
                    }
                }
            } else {
                final XSSFWorkbook xwb = (XSSFWorkbook) wb;
                File ffile = ret;
                if (cp != null) {
                    ffile = new File(ffile.getParentFile(), "_" + ret.getName());
                }
                try (OutputStream fos = IOHelper.getBufferedOutput(new FileOutputStream(ffile))) {
                    xwb.write(fos);
                }
                if (cp != null) {
                    try (BufferedOutputStream fos = IOHelper.getOutput(ret)) {
                        final Encryptor enc = MatrixReader.CRYPTOR.getEncryptor();
                        enc.confirmPassword(cp);
                        final POIFSFileSystem fs = new POIFSFileSystem();
                        try (final OPCPackage opc = OPCPackage.open(ffile, PackageAccess.READ_WRITE)) {
                            final OutputStream os = enc.getDataStream(fs);
                            opc.save(os);
                        } catch (final GeneralSecurityException | InvalidFormatException e) {
                            throw new IOException("Can't encrypt file " + ret, e);
                        }
                        fs.writeFilesystem(fos);
                    }
                    ffile.delete();
                }
            }
        } finally {
            Biff8EncryptionKey.setCurrentUserPassword(cup);
        }
        String url = MatrixReader.getProperty(wb, Destination.class.getSimpleName());
        if (url != null) {
            if (url.contains(MatrixReader.DATE_TIME)) {
                url = url.replace(MatrixReader.DATE_TIME, StringUtils.format(7, Time.date()));
            }
            final URL ourl = URL.valueOf(url);
            ourl.setEncode(SystemUtils.UTF8CS);
            final URLConnection c = ourl.toURL().openConnection();
            c.setDoOutput(true);
            try (OutputStream os = c.getOutputStream()) {
                org.apache.commons.io.FileUtils.copyFile(ret, os);
                if (ret.getName().contains("-delete")) {
                    ret.delete();
                }
            }
            MatrixReader.logger.info("res=" + c.getContent());
        }
        return ret;
    }

    public static Comment setCommentFor(final Cell c, final String comm) {
        Comment co = c.getCellComment();
        final Sheet sh = c.getRow().getSheet();
        final CreationHelper ch = sh.getWorkbook().getCreationHelper();
        if (co == null) {
            final Drawing dr = sh.createDrawingPatriarch();
            final ClientAnchor a = ch.createClientAnchor();
            a.setCol1(c.getColumnIndex());
            a.setCol2(c.getColumnIndex() + 5);
            a.setRow1(c.getRowIndex());
            a.setRow2(c.getRowIndex() + 3);
            co = dr.createCellComment(a);
        }
        final RichTextString str = ch.createRichTextString(comm);
        co.setString(str);
        c.setCellComment(co);
        return co;
    }

    private static void setCTProp(final CustomProperties cup, final String key, final Object val) {
        final CTProperty cp = cup.getProperty(key);
        if (cp != null) {
            if (val instanceof Boolean) {
                cp.setBool((Boolean) val);
            } else if (val instanceof Integer) {
                cp.setI4((Integer) val);
            } else if (val instanceof Number) {
                cp.setR8(((Number) val).doubleValue());
            } else if (val instanceof Calendar) {
                cp.setDate((Calendar) val);
            } else if (val == null) {
                cp.setNil();
            } else {
                cp.setLpwstr(String.valueOf(val));
            }
        } else if (val instanceof Boolean) {
            cup.addProperty(key, (Boolean) val);
        } else if (val instanceof Integer) {
            cup.addProperty(key, (Integer) val);
        } else if (val instanceof Number) {
            cup.addProperty(key, ((Number) val).doubleValue());
        } else {
            cup.addProperty(key, String.valueOf(val));
        }

    }

    public static void setProperty(final Workbook wb, final Property key, final Object val) {
        if (wb instanceof XSSFWorkbook) {
            final POIXMLProperties xp = ((XSSFWorkbook) wb).getProperties();
            final CoreProperties cp = xp.getCoreProperties();
            final CustomProperties cup = xp.getCustomProperties();
            switch (key) {
                case AUTHOR:
                    cp.setCreator((String) val);
                    break;
                case TITLE:
                    cp.setTitle((String) val);
                    break;
                case MODIFIED:
                    cp.setModified(Optional.ofNullable((Date) val));
                    break;
                case APP:
                    final CTProperty prop = cup.getProperty(key.name());
                    if (prop == null) {
                        cup.addProperty(key.name(), (String) val);
                    } else {
                        MatrixReader.setCTProp(cup, key.name(), val);
                    }
                    break;
                case CREATED:
                    cp.setCreated(Optional.ofNullable((Date) val));
                    break;
                case DESC:
                    cp.setDescription((String) val);
                    break;
            }
        } else {
            SummaryInformation si = ((HSSFWorkbook) wb).getSummaryInformation();
            if (si == null) {
                ((HSSFWorkbook) wb).createInformationProperties();
                si = ((HSSFWorkbook) wb).getSummaryInformation();
            }
            switch (key) {
                case AUTHOR:
                    if (val == null) {
                        si.removeAuthor();
                    } else {
                        si.setAuthor((String) val);
                    }
                    break;
                case MODIFIED:
                    if (val == null) {
                        si.removeLastSaveDateTime();
                    } else {
                        si.setLastSaveDateTime((Date) val);
                    }
                    break;
                case TITLE:
                    if (val == null) {
                        si.removeTitle();
                    } else {
                        si.setTitle((String) val);
                    }
                    break;
                case CREATED:
                    if (val == null) {
                        si.removeCreateDateTime();
                    } else {
                        si.setCreateDateTime((Date) val);
                    }
                    break;
                case APP:
                    if (val == null) {
                        si.removeApplicationName();
                    } else {
                        si.setApplicationName((String) val);
                    }
                    break;
                case DESC:
                    if (val == null) {
                        si.removeComments();
                    } else {
                        si.setComments((String) val);
                    }
                    break;
            }
        }
    }

    public static void setProperty(final Workbook wb, final String key, final Object val) {
        try {
            final Property p = Property.valueOf(key);
            MatrixReader.setProperty(wb, p, val);
        } catch (final IllegalArgumentException ia) {
            if (wb instanceof XSSFWorkbook) {
                final POIXMLProperties xp = ((XSSFWorkbook) wb).getProperties();
                final CustomProperties cup = xp.getCustomProperties();
                MatrixReader.setCTProp(cup, key, val);
            } else if (wb instanceof HSSFWorkbook) {
                SummaryInformation si = ((HSSFWorkbook) wb).getSummaryInformation();
                if (si == null) {
                    ((HSSFWorkbook) wb).createInformationProperties();
                    si = ((HSSFWorkbook) wb).getSummaryInformation();
                }
                JsonObject obj;
                if (si.getComments() == null) {
                    obj = new JsonObject();
                } else {
                    obj = (JsonObject) JsonConverter.INSTANCE.apply(si.getComments());
                }
                JSonUtil.setProperty(obj, key, val);
                si.setComments(JSonUtil.toString(obj));
            }
        }
    }

    public static Row setRow(final Sheet sh, final FormulaEvaluator eval, final List<Object> vals) {
        final Object[] arr = new Object[vals.size()];
        vals.toArray(arr);
        return MatrixReader.setRow(sh, eval, arr);
    }

    public static Row setRow(final Sheet sh, final FormulaEvaluator eval, final Object... vals) {
        int found = ArrayUtils.INDEX_NOT_FOUND;
        if (vals.length > 0) {
            for (final Row r : sh) {
                final Object val = Source.getCellVal(r.getCell(0), eval);
                if (ObjectUtils.equals(val, vals[0])) {
                    found = r.getRowNum();
                    break;
                }
            }
        }
        return MatrixReader.addRowTo(sh, found, vals);
    }

    private static String setUserPassword(String pw) {
        pw = StringUtils.isEmpty(pw) ? null : pw;
        final String cup = Biff8EncryptionKey.getCurrentUserPassword();
        Biff8EncryptionKey.setCurrentUserPassword(pw);
        return cup;
    }

    public static void standardize(final List<Object> ol) {
        Object o;
        for (final ListIterator<Object> oi = ol.listIterator(); oi.hasNext(); ) {
            o = oi.next();
            if (o instanceof String) {
                oi.set(MatrixReader.standardize((String) o));
            }
        }
    }

    public static String standardize(final String n) {
        if (StringUtils.isEmpty(n)) {
            return null;
        }
        return n.replaceAll("[^a-zA-Z0-9_]+", "").toLowerCase(Locale.ENGLISH);
    }

    public static Pair<String, CsvListReader> to(final CharIOHelper src) throws IOException {
        return to(src, null);
    }

    public static Pair<String, CsvListReader> to(final CharIOHelper src, AtomicReference<CsvPreference> prefs) throws IOException {
        String name = src.getName();
        String ext = FilenameUtils.getExtension(name);
        CsvPreference preference = toPreference(ext);
        final Pair<Character, String> sf = ConfigReader.getSeparatorChar(src.getName());
        if (sf != null) {
            preference = new Builder('"', sf.getFirst(), "\n").build();
            name = sf.getSecond();
        }
        name = FilenameUtils.getBaseName(name);
        if (prefs != null) prefs.set(preference);
        return new Pair<>(name, new CsvListReader(src.getReader(), preference));
    }

    public static Pair<String, CsvListWriter> to(final OutputStream trg, final String name) throws IOException {
        final AtomicReference<String> sh = new AtomicReference<>(name);
        final Pair<CsvPreference, Charset> fn = MatrixReader.forNameWithExtension(sh);
        final OutputStreamWriter osw = new OutputStreamWriter(trg, fn.getSecond());
        return new Pair<>(name, new CsvListWriter(osw, fn.getFirst()));
    }

    public static Object[][] toArray(final List<List<Object>> matrix) {
        if (matrix == null) {
            return null;
        }
        final Object[][] ret = new Object[matrix.size()][];
        int ri = 0;
        for (final List<Object> r : matrix) {
            ret[ri] = new Object[r.size()];
            int si = 0;
            for (final Object s : r) {
                ret[ri][si] = s;
                si++;
            }
            ri++;
        }

        return ret;
    }

    public static String[] toHeaders(final CharIOHelper src) throws IOException {
        try (CsvListReader r = MatrixReader.to(src).getSecond()) {
            return r.getHeader(false);
        }
    }

    public static CsvPreference toPreference(final String ext) throws IOException {
        return MatrixReader.toPreference(ext, CsvPreference.EXCEL_PREFERENCE);
    }

    public static CsvPreference toPreference(final String ext, final CsvPreference def) throws IOException {
        CsvPreference preference = def;
        if (ArrayUtils.contains(MatrixReader.TAB, ext)) {
            preference = CsvPreference.TAB_PREFERENCE;
        } else if (MatrixReader.ECSV.equalsIgnoreCase(ext)) {
            preference = CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE;
        } else if ("html".equals(ext)) {
            preference = new CsvPreference.Builder('"', '␞', "<br/>").build();
        } else if (ext != null) {
            char quot = (char) CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE.getQuoteChar();
            String nl = CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE.getEndOfLineSymbols();
            boolean doFname = false;
            try {
                final JsonElement el = JSonUtil.toElement(ext);
                if (el.isJsonArray()) {
                    final JsonArray ob = (JsonArray) el;
                    final char sep = String.valueOf(JSonUtil.unwrap(ob.get(0))).charAt(0);
                    if (ob.size() > 1) {
                        quot = String.valueOf(JSonUtil.unwrap(ob.get(1))).charAt(0);
                        if (ob.size() > 2) {
                            nl = String.valueOf(JSonUtil.unwrap(ob.get(2)));
                        }
                    }
                    preference = new Builder(quot, sep, nl).build();
                } else {
                    doFname = true;
                }
            } catch (final JsonSyntaxException ex) {
                doFname = true;
            }
            if (doFname) {
                final Pair<Character, String> sf = ConfigReader.getSeparatorChar(ext);
                if (sf != null) {
                    preference = new Builder(quot, sf.getFirst(), nl).build();
                }
            }
        }
        return preference;
    }

    public static CsvListWriter toWriter(final File trg) throws IOException {
        return MatrixReader.toWriter(trg, null);
    }

    public static CsvListWriter toWriter(final File trg, final String pass) throws IOException {
        String name = trg.getName();
        if (name != null) {
            name = FilenameUtils.getName(name);
        }
        final boolean bom = (name == null ? trg.toString() : name).contains(CharIOHelper.BOM_FN);
        if (trg.isDirectory()) {
            final boolean nameDefined = name != null && name.contains(".");
            final AtomicReference<String> sh = new AtomicReference<>(nameDefined ? name : trg.getName());
            final Pair<CsvPreference, Charset> fn = MatrixReader.forNameWithExtension(sh);
            if (name != null && !name.contains(".")) {
                final String ext = FilenameUtils.getExtension(sh.get());
                sh.set(name + "." + ext);
            }
            final OutputStream os = IOHelper.openSSL(Cipher.ENCRYPT_MODE, pass, IOHelper.getOutput(new File(trg, sh.get()), null));
            if (bom) {
                CharIOHelper.addBOM(os, fn.getSecond());
            }
            final Writer w = new OutputStreamWriter(os, fn.getSecond());
            return new CsvListWriter(w, fn.getFirst());
        } else {
            final Pair<String, Charset> pcs = CharIOHelper.getEncoding(trg.getName());
            final Charset cs = pcs == null ? Charset.defaultCharset() : pcs.getSecond();
            final OutputStream os = IOHelper.openSSL(Cipher.ENCRYPT_MODE, pass, IOHelper.getOutput(trg, null));
            if (bom) {
                CharIOHelper.addBOM(os, cs);
            }
            return new CsvListWriter(new OutputStreamWriter(os, cs), MatrixReader.toPreference(name == null ? trg.getName() : name));
        }
    }

    private List<List<Object>> matrix;
    @Setter
    @Getter
    private transient int sheet;
    @Getter
    @Setter
    private DataFormatter formatter;
    @Getter
    private FormulaEvaluator evalutor;
    @Getter
    private String name;
    @Getter
    protected Workbook workbook;
    protected transient List<Object> types = new LinkedList<>();
    @Getter
    @Setter
    private Progress progress = Progress.INSTANCE;
    @Setter
    @Getter
    private String password;
    @Getter
    protected File out;
    protected Set<String> columns;
    private final Set<Object> processProperties = new LinkedHashSet<>();
    protected transient Object outObject;

    public MatrixReader() {
        super(new BasicDynaClass());
        this.setColumns(null);
    }

    protected boolean add(final List<Object> mr) throws IOException {
        if (mr == null) {
            return false;
        }
        final List<Integer> sizes = (List) this.get(Column.class.getSimpleName());
        final Integer ml = (Integer) this.get(Max.class.getSimpleName());
        if (sizes != null) {
            if (sizes.isEmpty()) {
                sizes.addAll(Arrays.asList(new Integer[mr.size()]));
                Collections.fill(sizes, ArrayUtils.INDEX_NOT_FOUND);
            }
            int len, clen, idx = 0, cml = 0;
            for (final Object o : mr) {
                if (o instanceof CharSequence) {
                    clen = ((CharSequence) o).length();
                    len = sizes.get(idx);
                    if (clen > len) {
                        sizes.set(idx, clen);
                    }
                }
                idx++;
            }
            for (final Integer s : sizes) {
                cml += s;
            }
            if (ml == null || ml < cml) {
                this.set(ArrayList.class.getSimpleName(), new ArrayList<>(mr));
            }
        }
        if (this.matrix == null) {
            this.transformLine(mr);
            this.writeToOut(mr);
            return true;
        } else {
            this.matrix.add(mr);
            return true;
        }
    }

    public void addProcessablePropery(final Object prp) {
        this.processProperties.add(prp);
    }

    protected String cleanNameSegment(String ns) {
        if (ns != null) {
            ns = ns.replaceAll("[\\\\/]+", "");
        }
        return ns;
    }

    public void clear() throws IOException {
        this.setCurrent(null);
        this.setName(null);
        this.clearMatrix();
    }

    private void clearMatrix() {
        final ArrayList<Integer> sizes = (ArrayList) this.get(Column.class.getSimpleName());
        if (sizes != null) {
            sizes.clear();
        }
        if (this.matrix != null) {
            this.matrix.clear();
        }
    }

    @Override
    public void close() throws IOException {
        this.clear();
        this.closeOut();
    }

    protected void closeOut() throws IOException {
        Workbook wb;
        if (this.outObject instanceof Workbook) {
            wb = (Workbook) this.outObject;
            this.out = this.save(wb, this.out);
            this.setWorkbook(null);
        } else {
            hu.detox.io.IOHelper.closeSilently(this.outObject);
        }
        this.set(Progress.class.getSimpleName(), 0);
        this.setSheet(ArrayUtils.INDEX_NOT_FOUND);
        this.outObject = null;
    }

    public ArrayList<Integer> collectSizes() {
        final ArrayList<Integer> ret = new ArrayList<>();
        this.set(Column.class.getSimpleName(), ret);
        this.set(Max.class.getSimpleName(), 0);
        return ret;
    }

    public void endName() {
        final Object head = this.get(MatrixReader.HEADS);
        this.set(MatrixReader.HEADS, null);
        Object sizes = this.get(Column.class.getSimpleName());
        if (sizes != null) {
            System.err.println("# Sizes=" + sizes);
            sizes = this.get(ArrayList.class.getSimpleName());
            System.err.println("# " + sizes);
        }
        if (!StringUtils.isEmpty(this.getName())) {
            int siz = this.matrix == null ? ArrayUtils.INDEX_NOT_FOUND : this.matrix.size();
            if (head != null) {
                siz--;
            }
            if (siz > ArrayUtils.INDEX_NOT_FOUND) {
                logger.info("Processed " + this.getName() + // Sheet
                        (this.matrix == null ? StringUtils.EMPTY : " with " + siz + " records") // lines
                        + (sizes == null ? StringUtils.EMPTY : ", sizes=" + sizes) // max sizes if measured
                );
            }
        }
    }

    protected Object getCellVal(final Object cell) {
        Object ret;
        if (this.formatter == null || !(cell instanceof Cell)) {
            ret = Source.getCellVal(cell, this.evalutor);
        } else {
            final Cell c = (Cell) cell;
            final CellType t = Source.getCellType(c, this.evalutor);
            if (this.keepTypeUnformatted(c, t)) {
                ret = Source.getCellVal(cell, this.evalutor);
            } else {
                ret = this.formatter.formatCellValue(c, this.evalutor);
            }
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    private SortedSet<Integer> getColumnIndexes(final boolean enabled, final List<?> ln) {
        SortedSet<Integer> idxs = null;
        if (org.apache.commons.collections.CollectionUtils.isNotEmpty(this.columns)) {
            idxs = new TreeSet<>();
            if (!enabled) {
                idxs.addAll(IteratorUtils.toList(IntStream.range(0, ln.size()).iterator()));
            }
            int ci;
            for (final String cl : this.columns) {
                try {
                    ci = Integer.parseInt(cl);
                    ci = SystemUtils.toIndex(ln.size(), ci);
                } catch (final NumberFormatException ex) {
                    ci = ln.indexOf(cl);
                }
                if (ci > ArrayUtils.INDEX_NOT_FOUND) {
                    if (enabled) {
                        idxs.add(ci);
                    } else {
                        idxs.remove(ci);
                    }
                }
            }
        }
        return org.apache.commons.collections.CollectionUtils.isEmpty(idxs) ? null : idxs;
    }

    public String getColumns() {
        final String cls = StringUtils.join(this.columns, ',');
        return (this.matrix == null ? ">" : StringUtils.EMPTY) + (cls == null ? StringUtils.EMPTY : cls);
    }

    public File getFile(final String f) throws IOException {
        final File ret = Agent.getFile(f, FileFilterUtils.fileFileFilter());
        return ret;
    }

    protected int getNumberOfTabs() {
        return this.workbook.getNumberOfSheets();
    }

    protected Configuration getTransformConfig() {
        String s;
        if (this.isHeader()) {
            s = MatrixReader.HEADS;
            this.set(MatrixReader.VALUES, null);
            this.set(MatrixReader.HEADS, null);
        } else {
            s = MatrixReader.VALUES;
        }
        Configuration vcfg = (Configuration) this.get(s);
        if (vcfg == null) {
            vcfg = new BaseConfiguration(); // Only for performance boost
            this.set(s, vcfg);
        }
        return vcfg;
    }

    protected void initPOIWriteOut(final Sheet sh) throws IOException {
        final String sn = sh.getSheetName();
        if (String.valueOf(sn).contains(IOHelper.APPEND)) {
            return;
        }
        //... we clean it and reload it first time as necessary
        final int fro = MatrixReader.getProperty(sh.getWorkbook(), sn + "_fro", 0);
        final int lr = sh.getLastRowNum();
        final int fr = sh.getFirstRowNum() + fro;
        for (int i = lr; i >= fr; i--) {
            MatrixReader.removeRow(sh, i);
        }
    }

    protected boolean isHeader() {
        return MatrixReader.ZERO.equals(this.getCurrentProgress());
    }

    protected boolean keepTypeUnformatted(final Cell c, final CellType t) {
        return false;
    }

    public List<List<Object>> nextMatrix() throws IOException {
        if (this.sheet == ArrayUtils.INDEX_NOT_FOUND || this.sheet >= this.getNumberOfTabs()) {
            this.setSheet(0);
            return null;
        }
        this.clearMatrix();
        final int idx = this.name.indexOf('/');
        if (idx >= 0) {
            this.name = this.name.substring(0, idx);
        }
        if (this.workbook != null) {
            final Sheet lsheet = this.workbook.getSheetAt(this.sheet);
            this.setName(this.name + "/" + this.cleanNameSegment(lsheet.getSheetName()));
            this.readPOIMatrix(lsheet);
        }
        this.setSheet(this.getSheet() + 1);
        return this.matrix;
    }

    public Integer predictRows() {
        Integer ret;
        if (this.workbook != null) {
            ret = 0;
            for (int i = 0; i < this.workbook.getNumberOfSheets(); i++) {
                final Sheet rem = this.workbook.getSheetAt(i);
                ret += rem.getLastRowNum() - rem.getFirstRowNum();
            }
        } else {
            ret = null;
        }
        if (ret != null && this.progress != null) {
            this.progress.setTotal(ret);
        }
        return ret;
    }

    public int process(final List<String> argumentList) throws Exception {
        if (org.apache.commons.collections.CollectionUtils.isEmpty(argumentList)) {
            throw new IllegalArgumentException("No arguments to process");
        }
        int lineCount = 0;
        try {
            final String fileParameter = argumentList.remove(0);
            final File file = this.getFile(fileParameter);
            final Object readData;
            String name = null;
            // determine read from a file or database
            CharIOHelper io = CharIOHelper.attempt(file == null ? new File(fileParameter) : file);
            readData = this.processAdditionalArgs(io, argumentList);
            if (name == null) {
                if (readData instanceof File) {
                    name = ((File) readData).getAbsolutePath();
                }
            }
            final Integer fp = this.getCurrentProgress();
            if (fp != null) {
                lineCount += fp;
            }
            if (readData != null) {
                this.readMatrix(readData, name);
                if (!this.processProperties(name)) {
                    return lineCount;
                }
                final boolean predicted = this.get(MatrixReader.class.getSimpleName()) != null;
                File ood = null;
                if (this.outObject instanceof File) {
                    ood = (File) this.outObject;
                }
                Boolean pn;
                outer:
                do {
                    pn = this.processName();
                    if (pn == null) {
                        continue;
                    } else if (!pn) {
                        break;
                    }
                    if (ood != null) {
                        this.setTextOutObject(ood);
                    }
                    try {
                        Boolean pl;
                        this.setCurrentProgress(0);
                        if (this.progress != null) {
                            final String msg = "Processing " + name + ", idx=" + this.getSheet();
                            if (predicted || this.matrix == null) {
                                this.progress.nextIs(msg);
                            } else {
                                this.progress.nextIs(msg, this.matrix.size());
                            }
                        }
                        if (this.matrix != null) {
                            for (final List<Object> line : this.matrix) {
                                ThreadUtils.checkInterruptionIo();
                                // here the read data can be transformed and then the output is created
                                pl = this.processLine(line);
                                this.incCurrentProgress();
                                if (pl == null) {
                                    break outer;
                                } else if (Boolean.FALSE.equals(pl)) {
                                    break;
                                }
                                lineCount++;
                            }
                        }
                        this.endName();
                        if (this.progress != null) {
                            this.progress.endNext();
                        }
                    } finally {
                        if (ood != null) {
                            hu.detox.io.IOHelper.closeSilently(this.outObject);
                        }
                    }
                } while (this.nextMatrix() != null);
                if (this.progress != null) {
                    this.progress.end();
                }
                if (ood != null) {
                    this.outObject = null;
                }
            }
            return lineCount;
        } catch (final RuntimeException ex) {
            throw ReflectionUtils.extendMessage(ex, "progress=" + this.getCurrentProgress() + ", ln=" + lineCount + " @" + this.getName());
        } finally {
            this.closeOut();
            if (this.out != null) {
                System.out.println(this.out.getAbsolutePath());
            }
        }
    }

    protected Object processAdditionalArgs(CharIOHelper source, final List<String> al) throws IOException {
        File res = null;
        if (org.apache.commons.collections.CollectionUtils.isNotEmpty(al)) {
            res = (File) source.getSrc();
            final Workbook wb = MatrixReader.open(source);
            String arg;
            while (!al.isEmpty()) {
                arg = al.remove(0);
                try {
                    final JsonObject o = JSonUtil.toElement(arg);
                    for (final Map.Entry<String, JsonElement> e : o.entrySet()) {
                        final Object av = JSonUtil.unwrap(e.getValue());
                        MatrixReader.setProperty(wb, e.getKey(), av);
                    }
                } catch (final JsonSyntaxException ex) {
                    try {
                        final Property p = Property.valueOf(arg);
                        this.addProcessablePropery(p);
                    } catch (final IllegalArgumentException iae) {
                        this.addProcessablePropery(arg);
                    }
                }
            }
            res = MatrixReader.saveTo(wb, res, this.password);
        }
        return res;
    }

    public final Boolean processLine(final List<Object> l) throws IOException {
        final Boolean ret = this.transformLine(l);
        if (Boolean.TRUE.equals(ret) && !l.isEmpty()) {
            if (!this.writeToOut(l)) {
                System.out.println(l);
            }
        }
        if (this.progress != null) {
            this.progress.step();
        }
        return ret;
    }

    protected Boolean processName() throws IOException {
        System.err.println("# Matrix=" + this.getName());
        return true;
    }

    protected boolean processProperties(final String name) throws IOException {
        boolean ret = true;
        Object val;
        final String nname = this.resolveName(name);
        for (final Object p : this.processProperties) {
            val = this.get(p instanceof Property ? ((Property) p).name() : (String) p);
            if (val == null) {
                val = MatrixReader.getProperty(this.workbook, p);
            }
            ret &= this.processProperty(p, val);
        }
        if (ret && this.progress != null) {
            this.progress.start("Reading " + name + (name.equals(nname) ? "" : " -> " + nname));
        }
        return ret;
    }

    protected boolean processProperty(final Object p, final Object val) throws IOException {
        System.out.println(p + "=" + val);
        return true;
    }

    public List<Object> read(final Row row, final int from, int to) {
        List<Object> mr = null;
        if (row == null) {
            return mr;
        }
        if (to <= 0) {
            to = row.getLastCellNum() + to;
        }
        mr = new ArrayList<>(to - from);
        for (int c = from; c < to; c++) {
            final Object val = this.getCellVal(row.getCell(c));
            mr.add(val);
        }
        return mr;
    }

    public List<List<Object>> readMatrix(final File src) throws IOException {
        return this.readMatrix(src, null);
    }

    public List<List<Object>> readMatrix(Object src, String name) throws IOException {
        if (src == null) {
            return this.nextMatrix();
        }
        this.clear();
        this.name = name;
        CharIOHelper cio = CharIOHelper.attempt(src);
        cio.setNameIfNull(name);
        this.name = cio.getName();
        if (FilenameUtils.isExtension(name, MatrixReader.TXTEXTS)) {
            this.readPlainMatrix(cio);
        } else {
            this.readPOIMatrix(cio);
        }
        final Integer pr = this.predictRows();
        this.set(MatrixReader.class.getSimpleName(), pr);
        return this.matrix;
    }

    protected void readPlainMatrix(final CharIOHelper src) throws IOException {
        this.name = src.getName(); // Intentionally not the setter!
        final Pair<String, CsvListReader> np = MatrixReader.to(src);
        this.setName(np.getFirst());
        List<String> ln = null, lni;
        try (CsvListReader csv = np.getSecond()) {
            Set<Integer> idxs = null;
            this.setCurrentProgress(0);
            final boolean broken = name.contains("-broken");
            int ec = 0;
            while ((ln = csv.read()) != null) {
                ThreadUtils.checkInterruptionIo();
                if (csv.getLineNumber() == 1) {
                    idxs = this.getColumnIndexes(true, ln);
                    if (broken) {
                        ec = ln.size();
                    }
                } else if (broken) {
                    lni = null;
                    while (ln.size() < ec) {
                        lni = csv.read();
                        if (lni.size() == ec) {
                            ln = lni;
                        }
                    }
                }
                final LinkedList<Object> mr = new LinkedList<Object>();
                for (int c = 0; c < ln.size(); c++) {
                    if (idxs != null && !idxs.contains(c)) {
                        continue;
                    }
                    final Object val = this.getCellVal(ln.get(c));
                    mr.add(val);
                }
                if (!this.add(mr)) {
                    break;
                }
                this.incCurrentProgress();
            }
        } catch (final RuntimeException ex) {
            throw ReflectionUtils.extendMessage(ex, "data=" + ln);
        }
        this.add(null);
        this.setSheet(1);
    }

    protected void readPOIMatrix(final CharIOHelper src) throws IOException {
        this.name = FilenameUtils.getBaseName(src.getName()); // Intentionally not the setter!
        try (final CharIOHelper file = src) {
            this.setWorkbook(MatrixReader.open(file, this.password));
            final Boolean cr = MatrixReader.getProperty(this.workbook, "formattedCellRead");
            if (Boolean.TRUE.equals(cr)) {
                this.setFormatter(new DataFormatter(Locale.getDefault()));
            } else if (Boolean.FALSE.equals(cr)) {
                this.setFormatter(null);
            }
            this.nextMatrix();
        }
    }

    protected void readPOIMatrix(final Sheet src) throws IOException {
        int rb = 0, re = 0;
        int[] aidxs = null;
        boolean first = true;
        this.setCurrentProgress(0);
        List<Object> mr = null;
        try {
            for (int r = src.getFirstRowNum(); r <= src.getLastRowNum(); r++, this.incCurrentProgress()) {
                ThreadUtils.checkInterruptionIo();
                final Row row = src.getRow(r);
                if (row == null) {
                    continue;
                }
                if (rb == re) {
                    rb = row.getFirstCellNum();
                    re = row.getLastCellNum();
                }
                mr = this.read(row, rb, re);
                if (first) {
                    first = false;
                    final SortedSet<Integer> idxs = this.getColumnIndexes(false, mr);
                    if (idxs != null) {
                        aidxs = idxs.stream().mapToInt(i -> i).toArray();
                    }
                }
                if (aidxs != null) {
                    CollectionUtils.removeAllInplaceSorted(mr, aidxs);
                }
                if (!this.add(mr)) {
                    break;
                }
            }
        } catch (final RuntimeException ex) {
            throw ReflectionUtils.extendMessage(ex, "data=" + mr);
        }
        this.add(null);
    }

    public void removeProcessablePropery(final Object prp) {
        this.processProperties.remove(prp);
    }

    protected String resolveName(final String n) {
        if (StringUtils.isEmpty(n)) {
            return null;
        }
        final Matcher m = MatrixReader.FNAMEPATT.matcher(n);
        return StringUtils.resolveName(m, this);
    }

    public File save(final Workbook wb, File file) throws IOException {
        if (file.isDirectory()) {
            if (this.name == null) {
                return null;
            }
            final int idx = this.name.indexOf('/');
            String sn = this.name;
            if (idx >= 0) {
                sn = this.name.substring(0, idx);
            }
            file = new File(file, sn + MatrixReader.getExtensionFor(wb));
        }
        return MatrixReader.saveTo(wb, file, this.password);
    }

    protected void setCellVal(final Row r, final int cell, final Object val) {
        Source.setCellVal(r, cell, val);
    }

    public void setColumns(String cls) {
        this.matrix = new LinkedList<List<Object>>();
        if (cls != null && cls.startsWith(">")) {
            cls = cls.substring(1);
            this.collectSizes();
            this.matrix = null;
        }
        this.clearMatrix();
        if (StringUtils.isEmpty(cls)) {
            this.columns = null;
            return;
        }
        final String[] a = cls.split(StringUtils.SPLIT);
        this.columns = new LinkedHashSet<>(Arrays.asList(a));
    }

    public void setCurrent(final Workbook current) throws IOException {
        this.setWorkbook(current);
        this.setName(null);
    }

    public void incCurrentProgress() {
        this.setCurrentProgress(this.getCurrentProgress() + 1);
    }

    public void setCurrentProgress(final int cp) {
        this.set(Progress.class.getSimpleName(), cp);
    }

    public Integer getCurrentProgress() {
        return (Integer) this.get(Progress.class.getSimpleName());
    }

    public void setEvalutor(final boolean yes) {
        this.setEvalutor(yes ? MatrixReader.getEvalutor(this.workbook) : null);
    }

    public void setEvalutor(final FormulaEvaluator evalutor) {
        this.evalutor = evalutor;
    }

    public void setName(String name) {
        if (name != null) {
            name = this.resolveName(name);
        }
        this.name = StringUtils.trimToEmpty(name);
    }

    public Workbook setOut(final File out) throws IOException {
        this.out = out;
        this.out = this.transformOutFile();
        Workbook wb = null;
        if (this.out == null) {
            this.closeOut();
        } else {
            if (!this.out.exists() || this.out.isFile()) {
                if (this.out.isFile() || MatrixReader.isRecognizedExtension(this.out.getName())) {
                    if (this.out.isFile() && out.getName().contains(IOHelper.APPEND)) {
                        if (FilenameUtils.isExtension(this.out.getName(), MatrixReader.POIEXTS)) {
                            this.outObject = wb = MatrixReader.open(CharIOHelper.attempt(out), this.getPassword());
                        } else {
                            this.setTextOutObject(null);
                        }
                    } else if (FilenameUtils.isExtension(this.out.getName(), MatrixReader.POIEXTS)) {
                        wb = FilenameUtils.isExtension(this.out.getName(), MatrixReader.XLS) ? new HSSFWorkbook() : new XSSFWorkbook();
                        this.outObject = wb;
                    } else {
                        this.setTextOutObject(null);
                    }
                } else {
                    this.out.mkdir();
                    this.outObject = this.out;
                }
            } else if (this.out.isDirectory()) {
                this.outObject = this.out;
            }
            if (this.outObject instanceof Pair) {
                this.outObject = ((Pair) this.outObject).getSecond();
            }
        }
        return wb;
    }

    protected void setTextOutObject(final File ood) throws FileNotFoundException, IOException {
        if (ood == null) {
            this.outObject = MatrixReader.toWriter(this.out, this.getPassword());
        } else {
            this.outObject = MatrixReader.toWriter(ood, this.getPassword());
        }
    }

    public void setTypes(final String t) {
        if (StringUtils.isEmpty(t)) {
            this.types = null;
        } else {
            this.types = new ArrayList<>();
            for (final String a : t.split(StringUtils.SPLIT)) {
                if (StringUtils.isEmpty(a)) {
                    this.types.add(null);
                } else if (StringUtils.isExplicitNull(a)) {
                    this.types.add(StringUtils.NULL);
                } else {
                    try {
                        final Class<?> c = ReflectionUtils.toClass(a);
                        this.types.add(c);
                    } catch (final ClassNotFoundException e) {
                        throw new IllegalArgumentException("Not parseble " + a + " from " + t);
                    }
                }
            }
        }
    }

    public void setWorkbook(final Workbook current) throws IOException {
        if (this.workbook != null && current != this.workbook) {
            IOHelper.closeSilently(this.workbook);
        }
        this.workbook = current;
        final Boolean yes = MatrixReader.getProperty(this.workbook, WorkbookEvaluator.class.getSimpleName());
        this.setEvalutor(Boolean.TRUE.equals(yes));
    }

    public Object[][] toArray() {
        return this.matrix == null ? null : MatrixReader.toArray(this.matrix);
    }

    protected Boolean transformLine(final List<Object> l) throws IOException {
        if (l == null) {
            return Boolean.FALSE;
        }
        int i = 0;
        if (this.types != null && !this.isHeader()) {
            Object nv;
            for (final Object c : this.types) {
                nv = l.get(i);
                if (nv == null) {
                    i++;
                    continue;
                }
                if (nv instanceof CharSequence) {
                    nv = String.valueOf(nv);
                }
                if (c != null && !(nv instanceof String)) {
                    nv = StringConverter.INSTANCE.convertString(String.class, nv, true);
                }
                if (StringUtils.isEmpty(nv)) {
                    nv = null;
                    if (!StringUtils.NULL.equals(c)) {
                        l.set(i, null);
                    }
                } else if (c instanceof Class) {
                    if (nv instanceof String) {
                        nv = StringUtils.to(c, (String) nv, this);
                    }
                    l.set(i, nv);
                } else if (c instanceof Function<?, ?>) {
                    nv = ((Function) c).apply(nv);
                    l.set(i, nv);
                }
                i++;
            }
        }
        final Configuration tcfg = this.getTransformConfig();
        if (!tcfg.isEmpty()) {
            i = 0;
            for (final ListIterator<Object> li = l.listIterator(); li.hasNext(); ) {
                final Object o = li.next();
                if (!(o instanceof CharSequence)) {
                    continue;
                }
                final String h = String.valueOf(o);
                final String kh = MatrixReader.standardize(h);
                String nh;
                nh = tcfg.getString(h, kh);
                if (!this.isHeader() && h.equals(nh)) {
                    final List<String> hl = (List) this.get(MatrixReader.HEADS);
                    nh = tcfg.subset(hl.get(i)).getString(h, kh);
                }
                li.set(nh);
                i++;
            }
        }
        if (this.isHeader()) {
            this.set(MatrixReader.HEADS, l);
        }
        return Boolean.TRUE;
    }

    protected File transformOutFile() throws IOException {
        if (this.out == null) {
            return null;
        }
        String nf = this.resolveName(this.out.toString());
        return new File(nf);
    }

    public CsvListWriter write(final File trg) throws FileNotFoundException, IOException {
        return MatrixReader.toWriter(trg, null);
    }

    public void write(final List<?> cells, final Row row, final int from) {
        final int to = from + cells.size();
        int idx = 0;
        for (int c = from; c < to; c++) {
            this.setCellVal(row, c, cells.get(idx));
            idx++;
        }
    }

    protected boolean writeToOut(final List<Object> line) throws IOException {
        boolean ret = true;
        ThreadUtils.checkInterruptionIo();
        if (this.outObject instanceof Workbook) {
            final Workbook wb = (Workbook) this.outObject;
            final String sn = FilenameUtils.getName(this.name);
            Sheet sh;
            // At this stage the sheet is 1 if the source sheet one is already read
            final int shi = StringUtils.isEmpty(sn) ? ArrayUtils.INDEX_NOT_FOUND : wb.getSheetIndex(sn);
            if (shi > ArrayUtils.INDEX_NOT_FOUND) {
                // In case if the sheet already there we use it as is
                sh = wb.getSheetAt(shi);
                if (this.isHeader()) {
                    this.initPOIWriteOut(sh);
                }
            } else if (wb.getNumberOfSheets() < this.sheet) {
                // Happens if the sheet has to be added
                sh = wb.createSheet(sn);
            } else {
                // Can enter here only if there is sheet name is not given.
                // At this stage the sheet is 1 if the source sheet one is already read:
                final int ind = this.sheet - 1;
                sh = wb.getSheetAt(ind);
                if (this.isHeader()) {
                    wb.setSheetName(ind, sn);
                }
            }
            final int fro = MatrixReader.getProperty(wb, sn + "_fro", 0);
            if (this.getCurrentProgress() >= fro) {
                MatrixReader.addRow(sh, line);
            }
        } else if (this.outObject instanceof CsvListWriter) {
            final CsvListWriter lv = (CsvListWriter) this.outObject;
            lv.write(line);
        } else {
            ret = false;
        }
        return ret;
    }
}
