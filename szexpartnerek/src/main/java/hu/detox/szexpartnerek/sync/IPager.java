package hu.detox.szexpartnerek.sync;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;

public interface IPager extends Iterator<String> {
    String PAGER = "pg";

    class PagerWrap implements IPager {
        protected IPager wrapped;

        public PagerWrap(IPager p) {
            wrapped = p;
        }

        @Override
        public void reset() {
            wrapped.reset();
        }

        @Override
        public boolean first(JsonNode node) {
            return wrapped.first(node);
        }

        @Override
        public int current(JsonNode node) {
            return wrapped.current(node);
        }

        @Override
        public boolean hasNext() {
            return wrapped.hasNext();
        }

        @Override
        public String next() {
            return wrapped.next();
        }
    }

    void reset();

    boolean first(JsonNode node);

    int current(JsonNode node);

    default Object req() {
        return null;
    }
}
