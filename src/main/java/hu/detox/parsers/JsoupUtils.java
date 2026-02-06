package hu.detox.parsers;

import hu.detox.utils.StringUtils;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.supercsv.io.CsvListWriter;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public class JsoupUtils {
    private final Element doc;

    public JsoupUtils(final Element d) {
        this.doc = d;
    }

    public int tableToCSV(final CsvListWriter writer) throws IOException {
        return this.tableToCSV(writer, null);
    }

    public int tableToCSV(final CsvListWriter writer, final Function<Element, String> t) throws IOException {
        List<String> anyl = new LinkedList<String>();
        final Elements els = this.doc.select("th");
        this.tableToRow(anyl, els, t);
        if (anyl.isEmpty()) {
            // There was no header, get the width by the first row
            final int siz = this.doc.select("tr").first().children().size();
            anyl = new LinkedList<String>(Collections.nCopies(siz, null));
        } else if (writer.getLineNumber() == 0) {
            writer.writeHeader(anyl.toArray(new String[anyl.size()]));
        }
        int ret = 0;
        do {
            this.tableToRow(anyl, this.doc.select("td"), t);
            if (anyl.isEmpty()) {
                break;
            }
            ret++;
            writer.write(anyl);
        } while (true);
        return ret;
    }

    private void tableToRow(final List<String> anyl, final Elements els, final Function<Element, String> t) {
        final boolean cntCols = anyl.isEmpty();
        final int ocols = anyl.size();
        int cols = 0;
        anyl.clear();
        String cs;
        int csi;
        for (final Element e : els) {
            cs = e.attr("colspan");
            csi = StringUtils.isEmpty(cs) ? 1 : Integer.valueOf(cs);
            cs = t == null ? e.html() : t.apply(e);
            for (int i = 0; i < csi; i++) {
                anyl.add(cs);
                cols++;
            }
            e.remove();
            if (!cntCols && cols == ocols) {
                break;
            }
        }
    }

}
