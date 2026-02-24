package hu.detox.io.poi;

import hu.detox.utils.strings.StringUtils;
import hu.detox.utils.reflection.ReflectionUtils;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFRichTextString;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.function.Function;

public abstract class Source {
    public static final String RICH = "rich";

    private static void addFont(final StringBuilder htmlCode, final Cell cell, final Font fnt) {
        if (fnt == null) {
            return;
        }
        htmlCode.append("<font style=\"");
        final boolean uln = fnt.getUnderline() != Font.U_NONE;
        if (uln) {
            String ls = "inherit";
            switch (fnt.getUnderline()) {
                case Font.U_SINGLE:
                case Font.U_SINGLE_ACCOUNTING:
                    ls = "solid";
                    break;
                case Font.U_DOUBLE:
                case Font.U_DOUBLE_ACCOUNTING:
                    ls = "double";
                    break;
            }
            htmlCode.append("text-decoration-style: " + ls + ";");
        }
        if (fnt.getStrikeout() || uln) {
            String str = "";
            if (fnt.getStrikeout()) {
                str += "line-through ";
            }
            if (uln) {
                str += "underline ";
            }
            htmlCode.append("text-decoration-line: " + str.trim() + ";");
        }
        if (fnt.getItalic()) {
            htmlCode.append("font-style: italic;");
        }
        if (fnt.getFontName() != null) {
            htmlCode.append("font-family: " + fnt.getFontName() + ";");
        }
        if (fnt.getFontHeightInPoints() > 0) {
            htmlCode.append("font-size: " + fnt.getFontHeightInPoints() + "px;");
        }
        if (fnt.getTypeOffset() != Font.SS_NONE) {
            String va = "inherit";
            switch (fnt.getTypeOffset()) {
                case Font.SS_SUB:
                    va = "sub";
                    break;
                case Font.SS_SUPER:
                    va = "super";
                    break;
            }
            htmlCode.append("vertical-align: " + va + ";");
        }
        if (fnt.getBold()) {
            String bw = "bold";
            if (fnt.getBold()) {
                bw = "bolder";
            }
            htmlCode.append("font-weight: " + bw + ";");
        }
        if (fnt.getColor() != 0 || fnt instanceof XSSFFont) {
            String c;
            if (fnt instanceof HSSFFont) {
                final Workbook wb = cell.getSheet().getWorkbook();
                final HSSFColor cl = ((HSSFFont) fnt).getHSSFColor((HSSFWorkbook) wb);
                c = cl.getHexString();
            } else {
                final XSSFColor cl = ((XSSFFont) fnt).getXSSFColor();
                c = cl.getARGBHex().substring(2, 8);
            }
            if (c == null) {
                c = "inherit";
            } else {
                c = "#" + c;
            }
            htmlCode.append("color: " + c + ";");
        }
        htmlCode.append("\">");
    }

    private static void closeFont(final StringBuilder htmlCode, final Font pfnt) {
        if (pfnt == null) {
            return;
        }
        htmlCode.append("</font>");
    }

    private static void getHtmlInner(final StringBuilder htmlCode, final Cell cell, Object str) {
        if (str == null && cell != null) {
            str = cell.getRichStringCellValue();
        }
        if (str == null) {
            return;
        }
        final String sstr = str.toString();
        Font fnt = null, pfnt = null;
        final int nr = str instanceof RichTextString ? ((RichTextString) str).numFormattingRuns() : 0;
        if (nr == 0) {
            htmlCode.append(sstr);
        } else {
            for (int fr = 0; fr < nr; fr++) {
                final int i = ((RichTextString) str).getIndexOfFormattingRun(fr);
                int ei;
                if (str instanceof XSSFRichTextString) {
                    final XSSFRichTextString xstr = (XSSFRichTextString) str;
                    fnt = xstr.getFontOfFormattingRun(fr);
                    ei = i + xstr.getLengthOfFormattingRun(fr);
                } else {
                    final HSSFRichTextString hstr = (HSSFRichTextString) str;
                    final HSSFWorkbook wb = ((HSSFCell) cell).getSheet().getWorkbook();
                    fnt = wb.getFontAt(hstr.getFontOfFormattingRun(fr));
                    ei = nr > fr ? ((RichTextString) str).getIndexOfFormattingRun(fr + 1) : sstr.length();
                }
                closeFont(htmlCode, pfnt);
                addFont(htmlCode, cell, fnt);
                htmlCode.append(sstr.substring(i, ei));
                pfnt = fnt;
            }
        }
        closeFont(htmlCode, pfnt);
    }

    public static void getHtml(final StringBuilder htmlCode, final Cell cell, Object str) {
        if (str == null && cell != null) {
            str = cell.getRichStringCellValue();
        }
        final int fi = cell.getCellStyle().getFontIndex();
        final Font f = cell.getSheet().getWorkbook().getFontAt(fi);
        addFont(htmlCode, cell, f);
        getHtmlInner(htmlCode, cell, str);
        closeFont(htmlCode, f);
    }

    public static CellType getCellType(final Cell c, final FormulaEvaluator eval) {
        CellType ct = c.getCellType();
        if (ct == CellType.FORMULA) {
            ct = c.getCachedFormulaResultType();
            if (eval != null) {
                ct = eval.evaluateFormulaCell(c);
            }
        }
        return ct;
    }

    public static <T> T getCellVal(final Object cell, final FormulaEvaluator eval) {
        return getCellVal(cell, eval, false);
    }

    public static <T> T getCellVal(final Object cell, final FormulaEvaluator eval, boolean rich) {
        Object val = null;
        final StringBuilder sb = new StringBuilder();
        if (cell instanceof RichTextString) {
            val = ((RichTextString) cell).getString();
        } else if (cell instanceof Cell) {
            final Cell c = (Cell) cell;
            final CellType ct = Source.getCellType(c, eval);
            if (ct == CellType.BOOLEAN) {
                val = c.getBooleanCellValue();
            } else if (ct == CellType.NUMERIC) {
                val = c.getNumericCellValue();
                if (DateUtil.isCellDateFormatted(c)) {
                    val = Source.getDateCellValue(c);
                }
            } else if (ct == CellType.STRING) {
                if (rich) {
                    getHtml(sb, c, null);
                    val = sb.toString();
                } else {
                    val = c.getStringCellValue();
                }
            } else if (ct == CellType.ERROR) {
                val = c.getErrorCellValue();
            }
        } else if (cell != null) {
            val = String.valueOf(cell);
        }
        if (sb.length() == 0 && cell instanceof Cell) {
            sb.setLength(0);
            getHtml(sb, (Cell) cell, val);
            val = sb.toString();
        }
        if (val instanceof String) {
            if (StringUtils.isEmpty((String) val)) val = null;
        }
        return (T) val;
    }

    private static Date getDateCellValue(final Cell c) {
        final String tz = MatrixReader.getProperty(c.getSheet().getWorkbook(), TimeZone.class.getSimpleName());
        if (tz == null) {
            return c.getDateCellValue();
        }
        final double value = c.getNumericCellValue();
        final Workbook wb = c.getSheet().getWorkbook();
        boolean date1904 = false;
        if (wb instanceof XSSFWorkbook) {
            date1904 = ReflectionUtils.invokeMethod(wb, "isDate1904");
        } else if (wb instanceof HSSFWorkbook) {
            final Object o = ReflectionUtils.invokeMethod(wb, "getWorkbook");
            date1904 = ReflectionUtils.invokeMethod(o, "isUsing1904DateWindowing");
        }
        return DateUtil.getJavaDate(value, date1904, TimeZone.getTimeZone(tz));
    }

    public static String normalizeNewline(CharSequence val, final String newLine) {
        if (val == null) {
            return null;
        }
        if (newLine != null) {
            val = String.valueOf(val).replaceAll("(\r?\n|\r)", newLine);
        }
        return String.valueOf(val);
    }

    public static void setCellVal(final Cell c, final Object val) {
        if (val instanceof Date || val instanceof Calendar) {
            final Function<CreationHelper, CellStyle> tr = paramObject -> {
                final CellStyle cs = c.getSheet().getWorkbook().createCellStyle();
                cs.setDataFormat(paramObject.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
                return cs;
            };
            final CellStyle cs = MatrixReader.getStyle(c, Source.class.getName(), tr);
            c.setCellStyle(cs);
        }
        if (val instanceof Boolean) {
            c.setCellValue((Boolean) val);
        } else if (val instanceof Calendar) {
            c.setCellValue((Calendar) val);
        } else if (val instanceof Date) {
            c.setCellValue((Date) val);
        } else if (val instanceof Byte) {
            c.setCellValue((Byte) val);
        } else if (val instanceof Number) {
            c.setCellValue(((Number) val).doubleValue());
        } else if (val instanceof RichTextString) {
            c.setCellValue((RichTextString) val);
        } else if (val == null) {
            c.setCellValue((String) null);
        } else {
            String smsg = String.valueOf(val);
            smsg = smsg.substring(0, Math.min(32765, smsg.length()));
            if (smsg.startsWith("=")) {
                c.setCellFormula(smsg.substring(1));
            } else {
                c.setCellValue(smsg);
            }
        }
    }

    public static Cell setCellVal(final Row r, final int cell, final Object val) {
        Cell c = r.getCell(cell);
        if (c == null) {
            c = r.createCell(cell);
        }
        Source.setCellVal(c, val);
        return c;
    }

    private Source() {
        // Utility class
    }

}
