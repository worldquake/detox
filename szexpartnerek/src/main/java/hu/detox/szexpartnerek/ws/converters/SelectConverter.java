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
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SelectConverter implements Converter<String, PlainSelect> {
    private final TabulatorColumns columns;

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
        adjust(select, tableCols);
        var v = new NoSubQueryValidator(select);
        v.validate();
        return select;
    }

    private void adjust(PlainSelect select, ObjectNode tableCols) {
        Set<String> allowed = new HashSet<>();
        for (JsonNode field : tableCols.get(TabulatorColumns.F_COLUMNS)) {
            allowed.add(field.get("field").asText());
        }

        LinkedHashSet<SelectItem<?>> newItems = new LinkedHashSet<>();
        for (SelectItem<?> item : select.getSelectItems()) {
            if (item.getExpression() instanceof AllColumns) {
                for (String col : allowed) {
                    var si = new SelectItem<>(new Column(col));
                    if (col.equals(DataRepository.FIELD_ROWID)) si.setAlias(new Alias(col));
                    newItems.add(si);
                }
            } else if (item.getExpression() instanceof Column c) {
                String colName = c.getColumnName();
                if (!allowed.contains(colName)) {
                    throw new ValidationException("Column not allowed: " + colName);
                }
                newItems.add(item);
            } else {
                throw new ValidationException("Only column names, not [" + item + "]");
            }
        }
        select.setSelectItems(new LinkedList<>(newItems));
    }
}
