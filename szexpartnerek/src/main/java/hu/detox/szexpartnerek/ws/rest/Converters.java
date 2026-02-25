package hu.detox.szexpartnerek.ws.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.parsers.JSonUtils;
import hu.detox.utils.strings.StringUtils;
import jakarta.validation.ValidationException;
import lombok.SneakyThrows;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.*;

@Configuration
public class Converters implements WebMvcConfigurer {
    public static final String valueOf(FromItem fi) {
        if (fi == null) return null;
        String name = StringUtils.trimToNull(fi.toString());
        if (fi.getAlias() != null) name = fi.getAlias().getName();
        return name;
    }

    public static final String valueOf(SelectItem<?> si) {
        if (si == null) return null;
        String name = StringUtils.trimToNull(si.toString());
        if (si.getAlias() != null) name = si.getAlias().getName();
        return name;
    }

    @Bean
    Converter<String, PlainSelect> selectConverter(TabulatorColumns columns) {
        return s -> {
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
        };
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
                    newItems.add(new SelectItem<>(new Column(col)));
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

    @Bean
    Converter<String, DataRepository.PagingParam> pagingConverter() {
        return DataRepository.PagingParam::valueOf;
    }

    @Bean
    Converter<String, JsonNode> nodeConverter() {
        return new Converter<>() {
            @SneakyThrows
            @Override
            public JsonNode convert(String s) {
                return JSonUtils.OM.readValue(s, JsonNode.class);
            }
        };
    }


    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new GenericRequestParamArgumentResolver());
    }

}
