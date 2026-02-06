package hu.detox.parsers;

import org.dom4j.*;
import org.dom4j.tree.DefaultElement;

public class NameSpaceCleaner extends VisitorSupport {
    public static final NameSpaceCleaner INSTANCE = new NameSpaceCleaner();

    protected NameSpaceCleaner() {
        // singleton
    }

    @Override
    public void visit(final Attribute node) {
        if (node.toString().contains("xmlns") || node.toString().contains("xsi:")) {
            node.detach();
        }
    }

    @Override
    public void visit(final Document document) {
        ((DefaultElement) document.getRootElement()).setNamespace(Namespace.NO_NAMESPACE);
        document.getRootElement().additionalNamespaces().clear();
    }

    @Override
    public void visit(final Element node) {
        if (node instanceof DefaultElement) {
            ((DefaultElement) node).setNamespace(Namespace.NO_NAMESPACE);
        }
    }

    @Override
    public void visit(final Namespace namespace) {
        namespace.detach();
    }
}
