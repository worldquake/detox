package hu.detox.szexpartnerek.ws.rest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.config.ConfigReader;
import hu.detox.io.poi.MatrixReader;
import hu.detox.utils.CollectionUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.ValidationException;
import kotlin.Pair;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.supercsv.io.CsvListWriter;
import org.supercsv.io.ICsvListWriter;
import org.supercsv.prefs.CsvPreference;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static hu.detox.parsers.JSonUtils.OM;
import static hu.detox.szexpartnerek.ws.rest.DataRepository.SelectParams;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/szexpartnerek")
public class DataController {
    private final DataRepository repo;
    private final ContentNegotiationManager contentNegotiationManager;
    private final TabulatorColumns columns;

    private MediaType outType(HttpServletRequest request) throws HttpMediaTypeNotAcceptableException {
        List<MediaType> mediaTypes = contentNegotiationManager.resolveMediaTypes(new ServletWebRequest(request));
        MediaType accept = mediaTypes.isEmpty() ? MediaType.APPLICATION_JSON : mediaTypes.get(0);
        if (accept.isWildcardType() || accept.isWildcardSubtype()) accept = MediaType.TEXT_PLAIN;
        return accept;
    }

    private Pair<MediaType, String> validateAccept(HttpServletRequest request) throws HttpMediaTypeNotAcceptableException {
        MediaType accept = outType(request);
        if (accept.getType().equals("text")) return new Pair<>(accept, accept.getSubtype());
        if (accept.includes(MediaType.APPLICATION_JSON)) return new Pair<>(accept, ConfigReader.JSON);
        throw new ValidationException("Not able to provide: " + accept);
    }

    @GetMapping("/{table}/{id}")
    public ResponseEntity<?> getTableRow(
            @PathVariable String table,
            @PathVariable Integer id,
            SelectParams q,
            HttpServletRequest request
    ) throws HttpMediaTypeNotAcceptableException, IOException {
        var cm = new CsvListRowMapper();
        Pair<MediaType, String> ext = validateAccept(request);
        Pair<CsvPreference, Charset> pcs = MatrixReader.forNameWithExtension(
                new AtomicReference<>(table), ext.getFirst());
        Object res;
        q.table(table);
        q.getQ().setWhere(new EqualsTo(new Column(DataRepository.FIELD_ROWID), new LongValue(id)));
        if (ConfigReader.JSON.equals(ext.getSecond())) {
            repo.getResults(q, cm);
            ObjectNode node = OM.createObjectNode();
            if (CollectionUtils.isNotEmpty(cm.getValues())) {
                ObjectNode data = OM.createObjectNode();
                List<Object> single = cm.getValues().get(0);
                int i = 0;
                for (String h : cm.getHeaders()) {
                    Object any = single.get(i);
                    i++;
                    if (any == null) continue;
                    data.putPOJO(h, any);
                }
                node.set("data", data);
            }
            res = OM.writeValueAsString(node);
        } else {
            repo.getResults(q, cm);
            res = toSeparatedValues(cm, pcs.getFirst());
        }
        return ResponseEntity.ok().contentType(ext.getFirst()).body(res);
    }

    private Pair<String, Charset> getTableHeader(PlainSelect ps, MediaType accept, String ext) throws IOException {
        Pair<String, Charset> res;
        ObjectNode tableCols = columns.generateTabulatorColumns(ps);
        if (ConfigReader.JSON.equals(ext)) {
            res = new Pair<>(OM.writeValueAsString(tableCols), Charset.defaultCharset());
        } else {
            var ar = new AtomicReference<>(tableCols.get(TabulatorColumns.F_TABLE).asText());
            Pair<CsvPreference, Charset> pcs = MatrixReader.forNameWithExtension(ar, accept);
            StringWriter sw = new StringWriter();
            try (CsvListWriter writer = new CsvListWriter(sw, pcs.getFirst())) {
                tableCols.forEachEntry((k, v) -> {
                    List<Object> cur = new LinkedList<>();
                    v.forEach(vi -> cur.add(k + "=" + vi.toString()));
                    if (CollectionUtils.isEmpty(cur)) return;
                    try {
                        writer.write(cur);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });

            }
            res = new Pair<>(sw.toString(), pcs.getSecond());
        }
        return res;
    }

    @GetMapping("/{table}")
    public ResponseEntity<?> getTableData(
            @PathVariable String table,
            @Valid SelectParams params, HttpServletRequest request
    ) throws HttpMediaTypeNotAcceptableException, IOException {
        Pair<MediaType, String> ext = validateAccept(request);
        Object res = null;
        MediaType accept = ext.getFirst();
        Pair<Integer, List<Map<String, Object>>> content = null;
        params.table(table);
        if (params.isOnlyHeader()) {
            Pair<String, Charset> p = getTableHeader(params.getQ(), accept, ext.getSecond());
            if (p != null) {
                if (p.getSecond() != null) accept = new MediaType(accept.getType(), p.getSecond().name());
                res = p.getFirst();
            }
        } else if (ConfigReader.JSON.equals(ext.getSecond())) {
            content = repo.getResults(params, new ColumnMapRowMapper());
            ObjectNode node = OM.createObjectNode();
            node.set("pg", buildPagingNode(request, params, content.getFirst()));
            node.set("data", OM.valueToTree(content.getSecond()));
            res = OM.writeValueAsString(node);
        } else {
            CsvListRowMapper m = new CsvListRowMapper();
            content = repo.getResults(params, m);
            Pair<CsvPreference, Charset> pcs = MatrixReader.forNameWithExtension(new AtomicReference<>(table), accept);
            res = toSeparatedValues(m, pcs.getFirst());
        }
        var result = ResponseEntity.ok();
        if (content != null) {
            result.headers(params.headers(content.getFirst()));
        }
        return result.contentType(accept).body(res);
    }

    private String toSeparatedValues(CsvListRowMapper rows, CsvPreference pref) {
        StringWriter out = new StringWriter();
        Collection<String> heads = rows.getHeaders();
        if (CollectionUtils.isEmpty(heads)) return "";
        try (ICsvListWriter writer = new CsvListWriter(out, pref)) {
            String[] hs = rows.getHeaders().toArray(new String[0]);
            writer.writeHeader(hs);
            for (List<Object> row : rows.getValues()) {
                writer.write(row);
            }
        } catch (Exception e) {
            throw new RuntimeException("CSV/TSV serialization error", e);
        }
        return out.toString();
    }

    private ObjectNode buildPagingNode(HttpServletRequest req, SelectParams params, int total) {
        ObjectNode paging = OM.createObjectNode();
        StringBuffer baseUrl = req.getRequestURL();
        String prev = null, next = null;
        org.springframework.http.HttpHeaders hs = params.headers(total);
        String curr = hs.getFirst(SelectParams.H_CURRENT);
        String cnt = hs.getFirst(SelectParams.H_COUNT);
        String sz = hs.getFirst(SelectParams.H_SIZE);
        int currPg = Integer.parseInt(curr);
        if (currPg > 0) {
            prev = buildPageUrl(baseUrl, currPg - 1, sz);
        }
        if (!curr.equals(cnt)) {
            next = buildPageUrl(baseUrl, currPg + 1, sz);
        }
        if (prev != null) paging.put("previous", prev);
        if (next != null) paging.put("next", next);
        return paging;
    }

    private String buildPageUrl(Object baseUrl, int page, String size) {
        return String.format("%s?page=%d&size=%s", baseUrl, page, size);
    }
}