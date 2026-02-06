package hu.detox.szexpartnerek.sync;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.Flushable;

public interface IPersister extends Flushable, AutoCloseable {
    String ENUM_IDR = "enum_id";

    void save(StringBuilder sb, JsonNode node);

    void incBatch();

    @Override
    void flush();

    @Override
    default void close() {
        flush();
    }
}
