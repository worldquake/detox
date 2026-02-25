package hu.detox.szexpartnerek.ws.rest;

import hu.detox.utils.CollectionUtils;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.ASTNodeAccessImpl;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;

@RequiredArgsConstructor
public class NoSubQueryValidator {
    private final ASTNodeAccessImpl select;

    public void validate() {
        if (select instanceof Select) validate((Select) select);
        else if (select instanceof Expression) validate((Expression) select);
    }

    private void validate(PlainSelect select) {
        if (select.getFromItem() instanceof Select) {
            throw new ValidationException("Subquery: " + select.getFromItem());
        }
        if (CollectionUtils.isNotEmpty(select.getJoins())) {
            throw new ValidationException("Found JOINs " + select.getJoins().size());
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

    private static void validate(Expression expr) {
        if (expr == null) return;
        expr.accept(new ExpressionVisitorAdapter() {
            @Override
            public void visit(Select sel) {
                throw new ValidationException("Subquery found in " + expr);
            }
        });
    }
}