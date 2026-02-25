package hu.detox.szexpartnerek.ws.rest;

import com.fasterxml.jackson.databind.JsonNode;
import hu.detox.utils.Database;
import hu.detox.utils.strings.StringUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kotlin.Pair;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.util.cnfexpression.MultiAndExpression;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.ArrayList;
import java.util.List;

import static hu.detox.szexpartnerek.spring.SzexConfig.jdbc;
import static hu.detox.szexpartnerek.spring.SzexConfig.query;

@Component
@RequiredArgsConstructor
public class DataRepository {
    public static final String FIELD_ROWID = "rowid";
    private static final SelectItem<?> CNT;

    static {
        Function cnt = new Function();
        cnt.setName("COUNT");
        cnt.setParameters(new ExpressionList<>(new LongValue(1)));
        CNT = new SelectItem<>(cnt);
    }

    @Getter
    public static class SelectParams {
        public static final String H_COUNT = "X-Page-Count";
        public static final String H_CURRENT = "X-Page-Current";
        public static final String H_SIZE = "X-Page-Size";
        public static final String H_TOTAL = "X-Total";
        private final PlainSelect q;
        boolean onlyHeader;

        private static List<OrderByElement> toOrder(JsonNode o) {
            List<OrderByElement> orderByElements = new ArrayList<>();
            if (o == null) return orderByElements;
            for (JsonNode sortItem : o) {
                String field = sortItem.has("field") ? sortItem.get("field").asText() : null;
                String dir = sortItem.has("dir") ? sortItem.get("dir").asText() : "asc";

                if (field != null && !field.isEmpty()) {
                    OrderByElement element = new OrderByElement();
                    element.setExpression(new Column(field));
                    element.setAsc("asc".equalsIgnoreCase(dir));
                    orderByElements.add(element);
                }
            }
            return orderByElements;
        }


        private static Expression toWhere(JsonNode w) {
            List<Expression> expressions = new ArrayList<>();
            for (JsonNode filterItem : w) {
                String field = filterItem.has("field") ? filterItem.get("field").asText() : null;
                String type = filterItem.has("type") ? filterItem.get("type").asText() : "=";
                JsonNode valueNode = filterItem.get("value");

                if (field == null || valueNode == null) continue;

                Column column = new Column(field);
                Expression expr = null;

                switch (type.toLowerCase()) {
                    case "!=":
                    case "<>":
                        expr = new NotEqualsTo(column, toValue(valueNode));
                        break;
                    case ">":
                        expr = new GreaterThan().withLeftExpression(column).withRightExpression(toValue(valueNode));
                        break;
                    case "<":
                        expr = new MinorThan().withLeftExpression(column).withRightExpression(toValue(valueNode));
                        break;
                    case ">=":
                        expr = new GreaterThanEquals().withLeftExpression(column).withRightExpression(toValue(valueNode));
                        break;
                    case "<=":
                        expr = new MinorThanEquals().withLeftExpression(column).withRightExpression(toValue(valueNode));
                        break;
                    case "in":
                        if (valueNode.isArray()) {
                            List<Expression> inList = new ArrayList<>();
                            for (JsonNode v : valueNode) {
                                inList.add(toValue(v));
                            }
                            expr = new InExpression(column, new ExpressionList(inList));
                        }
                        break;
                    case "like":
                        expr = new LikeExpression().withLeftExpression(column).withRightExpression(toValue(valueNode));
                        break;
                    default:
                        expr = new EqualsTo(column, toValue(valueNode));
                }

                if (expr != null) {
                    expressions.add(expr);
                }
            }
            return new MultiAndExpression(expressions);
        }

        private static Expression toValue(JsonNode node) {
            if (node.isInt() || node.isLong()) {
                return new LongValue(node.asText());
            } else if (node.isDouble() || node.isFloat()) {
                return new DoubleValue(node.asText());
            } else if (node.isBoolean()) {
                return new StringValue(node.asBoolean() ? "true" : "false");
            } else {
                return new StringValue(node.asText());
            }
        }

        SelectParams(
                @PathVariable String table,
                @Valid DataRepository.PagingParam pg,
                @Valid PlainSelect q, JsonNode w, JsonNode o
        ) {
            if (q == null) {
                q = StringUtils.to(PlainSelect.class, "* FROM " + table
                        + (w == null ? "" : " WHERE " + toWhere(w))
                        + (o == null ? "" : " ORDER BY " + toOrder(o)), null);
            }
            if (pg == null) pg = new PagingParam(1, 25);
            else if (pg.size() == 0) onlyHeader = true;
            Limit l = q.getLimit();
            if (l == null) {
                l = new Limit();
                l.setRowCount(new LongValue(pg.size()));
                Offset os = q.getOffset();
                l.setOffset(os == null ? new LongValue(pg.offset()) : os.getOffset());
                q.setLimit(l);
                q.setOffset(null);
            }
            this.q = q;
        }

        public void table(String override) {
            String n = Converters.valueOf(q.getFromItem());
            if (n == null) q.setFromItem(new Table(override));
        }

        public HttpHeaders headers(int total) {
            HttpHeaders heads = new HttpHeaders();
            Limit limit = q.getLimit();
            long size = ((LongValue) limit.getRowCount()).getValue();
            long offset = limit.getOffset() != null ? ((LongValue) limit.getOffset()).getValue() : 0;
            long cnt = total / size + (total % size > 0 ? 1 : 0);
            long currentPage = (offset / size) + 1;

            heads.add(H_COUNT, String.valueOf(cnt));
            heads.add(H_CURRENT, String.valueOf(currentPage));
            heads.add(H_SIZE, String.valueOf(size));
            heads.add(H_TOTAL, String.valueOf(total));
            return heads;
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

        @Override
        public @NonNull String toString() {
            return "LIMIT ? OFFSET ?";
        }
    }

    @SneakyThrows
    private Object select(PlainSelect sel) {
        boolean pg = sel.getLimit() != null;
        if (pg) {
            PlainSelect cnt = (PlainSelect) CCJSqlParserUtil.parse(sel.toString());
            cnt.setLimit(null);
            cnt.setOffset(null);
            cnt.setTop(null);
            cnt.setSelectItems(List.of(CNT));
            int total = query(cnt.toString(), rs -> Database.quickViewOne(rs, Integer.class));
            return new Pair<>(total, sel.toString());
        }
        return sel.toString();
    }

    public <T> T getResults(SelectParams select, Object map) {
        return getResults(select.getQ(), map);
    }

    @SuppressWarnings({"unchecked"})
    public <T> T getResults(PlainSelect select, Object map) {
        String sql;
        T ret;
        Integer total = null;
        Object retObj = select(select);
        if (retObj instanceof Pair p) {
            total = (Integer) p.getFirst();
            sql = (String) p.getSecond();
        } else sql = (String) retObj;
        if (map == null) retObj = jdbc().query(sql, (ResultSetExtractor<? extends Object>) Database::quickViewOne);
        else retObj = jdbc().query(sql, (RowMapper<? extends Object>) map);
        if (total != null) ret = (T) new Pair(total, retObj);
        else ret = (T) retObj;
        return ret;
    }

}
