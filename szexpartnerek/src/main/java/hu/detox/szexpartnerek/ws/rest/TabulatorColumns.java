package hu.detox.szexpartnerek.ws.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import hu.detox.Agent;
import hu.detox.io.CharIOHelper;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static hu.detox.parsers.JSonUtils.OM;

@Component
@RequiredArgsConstructor
public class TabulatorColumns implements ApplicationListener<ContextRefreshedEvent> {
    public static final String F_TABLE = "tbl";
    public static final String F_COLUMNS = "cols";
    public static final String F_SORT = "srt";
    private static final String F_SUB = "sub";
    private final static ObjectNode ROWID;
    private ObjectNode root;

    static {
        try {
            ROWID = OM.readValue("""
                    {
                        "title": "ID",
                        "field": "rowid",
                        "sorter": "number",
                        "hozAlign": "left",
                        "width": 100
                    }""", ObjectNode.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public @NotNull ObjectNode generateTabulatorColumns(PlainSelect q) {
        String name = Converters.valueOf(q.getFromItem());
        StringBuilder sb = new StringBuilder();
        ArrayNode an = OM.createArrayNode();
        ArrayNode sort = OM.createArrayNode();
        Collection<String> cLst = q.getSelectItems().stream().map(Converters::valueOf).collect(Collectors.toUnmodifiableSet());
        findAndBuildColumns(root, cLst, an, sort, name, sb);
        if (name.contentEquals(sb)) {
            ObjectNode ret = OM.createObjectNode();
            ret.set(F_SORT, sort);
            ret.set(F_COLUMNS, an);
            ret.put(F_TABLE, name);
            return ret;
        }
        throw new ValidationException("No such table: " + name);
    }

    private static void findAndBuildColumns(ObjectNode node, Collection<String> prj, ArrayNode cols, ArrayNode sort, String finalName, StringBuilder sb) {
        if (node == null) return;
        final AtomicReference<String> longest = new AtomicReference<>("");
        node.forEachEntry((k, jsonNode) -> {
            if (!F_COLUMNS.equals(k) && finalName.startsWith(sb.toString() + k) && longest.get().length() < k.length())
                longest.set(k);
        });
        if (longest.get().isEmpty()) return;
        sb.append(longest.get());
        JsonNode currCols = node.get(F_COLUMNS);
        if (currCols instanceof ObjectNode)
            cols.add(currCols);
        node = (ObjectNode) node.get(longest.get());
        if (cols.isEmpty()) cols.add(ROWID);
        currCols = node.get(F_COLUMNS);
        boolean prjAll = prj == null || prj.contains("*");
        if (currCols instanceof ArrayNode) currCols.forEach(c -> {
            if (prjAll || prj.contains(c.get("field").asText())) cols.add(c);
        });
        JsonNode minus = node.get("minus");
        if (minus instanceof ArrayNode) {
            minus.forEach(c -> {
                removeByField(cols, (TextNode) c);
            });
        } else if (minus instanceof TextNode tn) {
            if (tn.asText().equals("*")) {
                Iterator<JsonNode> it = cols.iterator();
                while (it.hasNext()) {
                    JsonNode iel = it.next();
                    JsonNode sel = iel.get(F_SUB);
                    if (sel != null && sel.asBoolean() || iel == ROWID)
                        continue;
                    it.remove();
                }
            } else removeByField(cols, tn);
        }
        JsonNode s = node.get(F_SORT);
        if (s != null) sort.add(s);
        if (finalName.contentEquals(sb)) return;
        ObjectNode subObj = (ObjectNode) node.get(F_SUB);
        findAndBuildColumns(subObj, prj, cols, sort, finalName, sb);
        subObj = (ObjectNode) node.get("view");
        findAndBuildColumns(subObj, prj, cols, sort, finalName, sb);
    }

    private static void removeByField(ArrayNode cols, TextNode node) {
        for (int i = cols.size() - 1; i >= 0; i--) {
            if (node.asText().equals(cols.get(i).get("field").asText())) {
                cols.remove(i);
            }
        }
    }

    @SneakyThrows
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        Resource rs = Agent.resource("sql/tables.json");
        try (CharIOHelper cio = CharIOHelper.attempt(rs)) {
            root = (ObjectNode) OM.readTree(cio.getReader());
        }
    }
}