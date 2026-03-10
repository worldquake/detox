package hu.detox.szexpartnerek.ws.converters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.szexpartnerek.ws.rest.DataRepository;
import hu.detox.szexpartnerek.ws.rest.NoSubQueryValidator;
import hu.detox.szexpartnerek.ws.rest.TabulatorColumns;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.LinkedList;

@Component
@RequiredArgsConstructor
public class SelectConverter implements Converter<String, PlainSelect> {
    private final TabulatorColumns columns;

    public static void setLimit(PlainSelect q, DataRepository.PagingParam limit) {
        if (limit == null || !limit.hasAnything()) return;
        Limit l = q.getLimit();
        if (l == null) {
            l = new Limit();
            l.setRowCount(new LongValue(limit.size()));
            Offset os = q.getOffset();
            l.setOffset(os == null ? new LongValue(limit.offset()) : os.getOffset());
            q.setLimit(l);
            q.setOffset(null);
        } else {
            LongValue size = (LongValue) l.getRowCount();
            if (size != null) {
                long nLimit = Math.min(size.getValue(), limit.size());
                l.setRowCount(new LongValue(nLimit));
            }
            if (l.getOffset() == null)
                l.setOffset(new LongValue(limit.offset()));
        }

    }

    @Override
    public PlainSelect convert(String s) {
        PlainSelect select;
        s = "SELECT " + s;
        try {
            select = (PlainSelect) CCJSqlParserUtil.parse(s);
        } catch (JSQLParserException e) {
            throw new ValidationException("Invalid query: " + s, e);
        }
        ObjectNode tableCols = columns.generateTabulatorColumns(select);
        var v = new NoSubQueryValidator(select, tableCols);
        adjust(select, v);
        JsonNode pgs = tableCols.get(TabulatorColumns.F_PAGES);
        if (pgs != null) {
            setLimit(select, new DataRepository.PagingParam(null, pgs.asInt()));
        }
        v.validate();
        return select;
    }

    private void adjust(PlainSelect select, NoSubQueryValidator val) {
        LinkedHashSet<SelectItem<?>> newItems = new LinkedHashSet<>();
        for (SelectItem<?> item : select.getSelectItems()) {
            if (item.getExpression() instanceof AllColumns) {
                for (String col : val.getAllowed()) {
                    var si = new SelectItem<>(new Column(col));
                    if (col.equals(DataRepository.FIELD_ROWID)) si.setAlias(new Alias(col));
                    newItems.add(si);
                }
            } else newItems.add(item);
        }
        select.setSelectItems(new LinkedList<>(newItems));
    }
}
