package hu.detox.szexpartnerek.ws.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.utils.CollectionUtils;
import jakarta.validation.ValidationException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.ASTNodeAccessImpl;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;

import java.util.HashSet;
import java.util.Set;

@RequiredArgsConstructor
public class NoSubQueryValidator {
    private final ASTNodeAccessImpl select;
    @Getter
    private final Set<String> allowed;

    public NoSubQueryValidator(ASTNodeAccessImpl sel, ObjectNode tableCols) {
        select = sel;
        allowed = new HashSet<>();
        for (JsonNode field : tableCols.get(TabulatorColumns.F_COLUMNS)) {
            allowed.add(field.get("field").asText());
        }
    }

    public void validate() {
        if (select instanceof Select) validate((Select) select);
        else if (select instanceof Expression) validate((Expression) select);
    }

    private void validate(PlainSelect select) {
        if (select.getFromItem() instanceof Select) {
            throw new ValidationException("Subquery: " + select.getFromItem());
        }
        if (CollectionUtils.isNotEmpty(select.getJoins())) {
            throw new ValidationException("Found " + select.getJoins().size() + " JOINs");
        }
        for (SelectItem<?> item : select.getSelectItems()) {
            validate(item.getExpression());
        }
        validate(select.getWhere());
        validate(select.getHaving());
        if (select.getGroupBy() != null && select.getGroupBy().getGroupByExpressionList() instanceof ExpressionList<?> el) {
            for (Expression groupByExpr : el) {
                validate(groupByExpr);
            }
        }
    }

    private void validate(Select select) {
        if (select.getOrderByElements() != null) {
            for (OrderByElement orderBy : select.getOrderByElements()) {
                validate(orderBy.getExpression());
            }
        }
        if (select instanceof PlainSelect) validate((PlainSelect) select);
    }

    private void validate(Expression expr) {
        if (expr == null) return;
        expr.accept(new ExpressionVisitorAdapter() {
            @Override
            public void visit(Column column) {
                if (!allowed.contains(column.getColumnName()))
                    throw new ValidationException("Column " + column + " is invalid in [" + expr + "]");
            }

            @Override
            public void visit(Select sel) {
                throw new ValidationException("Subquery " + sel + " found in [" + expr + "]");
            }
        });
    }
}