package hu.detox.szexpartnerek;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.sql.SQLException;

public interface Persister extends AutoCloseable {
    String ENUM_IDR = "enum_id";

    void save(JsonNode node) throws IOException, SQLException;
}
