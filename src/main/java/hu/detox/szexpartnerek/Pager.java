package hu.detox.szexpartnerek;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;

public interface Pager extends Iterator<String> {
    void first(JsonNode node);

    boolean current(JsonNode node);
}
