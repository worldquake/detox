package hu.detox.szexpartnerek.ws.rest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.szexpartnerek.Main;
import hu.detox.utils.Database;
import hu.detox.utils.StringUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import kotlin.Pair;
import lombok.SneakyThrows;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DataRepository {
    private static final String FIELD_ROWID = "rowid";
    public static final String FIELD_REGEX = "^[a-zA-Z0-9_]+$";
    private static final Function CNT = new Function();

    static {
        CNT.setName("COUNT");
        CNT.setParameters(new ExpressionList<>(new LongValue(1)));
    }

    public record SelectParams(
            @Valid DataRepository.PagingParam pg,
            @Valid List<@Pattern(regexp = FIELD_REGEX, message = "Invalid field name") String> prj,
            @Valid List<@Valid SortParam> srt,
            @Valid List<@Valid FilterParam> flt
    ) {
    }

    public record SortParam(
            @Pattern(regexp = FIELD_REGEX, message = "Invalid field name")
            String field,
            @Pattern(regexp = "asc|desc", flags = Pattern.Flag.CASE_INSENSITIVE, message = "Sort direction must be 'asc' or 'desc'")
            String dir
    ) {
        @Override
        public @NonNull String toString() {
            return field + (dir == null ? "" : " " + dir.toUpperCase());
        }

        public static SortParam valueOf(String a) {
            String[] arr = a.split(" ");
            String field = arr[0];
            String dir = arr.length == 1 ? "ASC" : arr[1];
            return new SortParam(field, dir);
        }

    }

    public record FilterParam(
            @Pattern(regexp = FIELD_REGEX, message = "Invalid field name")
            String field,
            @Pattern(regexp = "=|<|>|<=|>=|is( not)?|like", flags = Pattern.Flag.CASE_INSENSITIVE, message = "Invalid flt operation")
            String type,
            String value
    ) {
        public static FilterParam valueOf(String a) {
            String[] arr = a.split(" ");
            String field = arr[0];
            String typ = arr.length <= 2 ? "is not" : arr[1];
            String val = arr.length <= 2 ? null : StringUtils.join(arr, ' ', 2, arr.length);
            return new FilterParam(field, typ, val);
        }

        @Override
        public @NonNull String toString() {
            return field + " " + type + " " + (value == null ? "NULL" : "?");
        }
    }

    public record PagingParam(
            @Min(1) Integer page,
            @Min(0) @Max(100) Integer size
    ) {
        public static PagingParam valueOf(String a) {
            String[] arr = a.split("/");
            int page = Integer.parseInt(arr[0]);
            Integer size = arr.length == 1 ? null : Integer.parseInt(arr[1]);
            return new PagingParam(page, size);
        }

        public Integer page() {
            return page == null ? 1 : page;
        }

        public Integer size() {
            return size == null ? 25 : size;
        }

        public int offset() {
            return (page() - 1) * size();
        }

        public HttpHeaders headers(int total) {
            HttpHeaders heads = new HttpHeaders();
            var cnt = total / size() + (total % size() > 0 ? 1 : 0);
            heads.add("X-Page-Count", String.valueOf(cnt));
            heads.add("X-Page-Size", String.valueOf(size()));
            heads.add("X-Page-Current", String.valueOf(size()));
            heads.add("X-Total", String.valueOf(total));
            return heads;
        }

        @Override
        public @NonNull String toString() {
            return "LIMIT ? OFFSET ?";
        }
    }

    @SneakyThrows
    private Object select(ObjectNode table, Object by, StringBuilder sb, List<Object> args) {
        PagingParam pgp = null;
        List<FilterParam> fp = null;
        List<SortParam> sp = null;
        if (by instanceof SelectParams si) {
            pgp = si.pg();
            fp = si.flt();
            sp = si.srt();
        } else if (by instanceof PagingParam pi) pgp = pi;
        else if (by instanceof FilterParam fi) fp = List.of(fi);
        else if (by instanceof SortParam si) sp = List.of(si);
        project(table, sb);
        sb.append(" FROM ").append(table.get(TabulatorColumns.F_TABLE).asText()).append(' ');
        filter(fp, sb, args);
        sort(sp, sb);
        boolean pg = paging(pgp, sb, args);
        if (pg) {
            int total = countOf(sb);
            return new Pair<>(total, sb.toString());
        }
        return sb.toString();
    }

    private void project(ObjectNode table, StringBuilder sb) {
        sb.append(' ');
        Set<String> cols = new LinkedHashSet<>();
        table.get(TabulatorColumns.F_COLUMNS).forEach(n -> cols.add(n.get("field").asText()));
        int idx = cols.contains(FIELD_ROWID) ? 0 : 1;
        if (idx >= 1) sb.append(FIELD_ROWID + " AS " + FIELD_ROWID);
        for (String p : cols) {
            if (!cols.contains(p)) continue;
            if (idx > 0) sb.append(",");
            sb.append(p);
            if (FIELD_ROWID.equals(p)) sb.append(" AS ").append(FIELD_ROWID);
            idx++;
        }
    }

    private void filter(Collection<FilterParam> flt, StringBuilder sb, List<Object> args) {
        if (flt == null) return;
        sb.append(" WHERE ");
        int idx = 0;
        for (FilterParam p : flt) {
            if (idx > 0) sb.append(" AND ");
            sb.append(p);
            if (p.value() != null) args.add(p.value());
            idx++;
        }
    }

    private void sort(Collection<SortParam> srt, StringBuilder sb) {
        if (srt == null) return;
        sb.append(" ORDER BY ");
        int idx = 0;
        for (SortParam p : srt) {
            if (idx > 0) sb.append(",");
            sb.append(p);
            idx++;
        }
    }

    private boolean paging(PagingParam pp, StringBuilder sb, List<Object> args) {
        if (pp == null) return false;
        sb.append(" LIMIT ? OFFSET ?");
        args.add(pp.size());
        args.add(pp.offset());
        return true;
    }

    private int countOf(StringBuilder sb) throws JSQLParserException {
        PlainSelect select = (PlainSelect) CCJSqlParserUtil.parse(sb.toString());
        select.setLimit(null);
        select.setOffset(null);
        select.setTop(null);
        select.setSelectItems(List.of(new SelectItem<>(CNT)));
        return Main.query(select.toString(), rs -> Database.quickViewOne(rs, Integer.class));
    }

    @SuppressWarnings({"unchecked"})
    public <T> T getResults(ObjectNode table, Object select, Object map) {
        StringBuilder sb = new StringBuilder("SELECT ");
        String sql;
        T ret;
        Object retObj = null;
        List<Object> args = new LinkedList<>();
        Integer total = null;
        if (select instanceof Number) {
            sb.append("* FROM " + table + " WHERE rowid = ?");
            args.add(select);
            sql = sb.toString();
        } else {
            retObj = select(table, select, sb, args);
            if (retObj instanceof Pair p) {
                total = (Integer) p.getFirst();
                sql = (String) p.getSecond();
            } else sql = (String) retObj;
        }
        if (map == null)
            retObj = Main.jdbc().query(sql, (ResultSetExtractor<? extends Object>) Database::quickViewOne, args.toArray(new Object[0]));
        else retObj = Main.jdbc().query(sql, (RowMapper<? extends Object>) map, args.toArray(new Object[0]));
        if (total != null) ret = (T) new Pair(total, retObj);
        else ret = (T) retObj;
        return ret;
    }

    @Bean
    Converter<String, FilterParam> filterConverter() {
        return FilterParam::valueOf;
    }

    @Bean
    Converter<String, PagingParam> pagingConverter() {
        return PagingParam::valueOf;
    }

    @Bean
    Converter<String, SortParam> sortConverter() {
        return SortParam::valueOf;
    }
}
