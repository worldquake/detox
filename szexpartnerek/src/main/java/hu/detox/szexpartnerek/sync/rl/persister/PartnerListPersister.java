package hu.detox.szexpartnerek.sync.rl.persister;

import com.fasterxml.jackson.databind.JsonNode;
import hu.detox.szexpartnerek.Main;
import hu.detox.szexpartnerek.sync.AbstractPersister;
import hu.detox.szexpartnerek.sync.AbstractTrafoEngine;
import hu.detox.szexpartnerek.sync.IPersister;
import hu.detox.szexpartnerek.sync.rl.component.sub.Partner;

import java.util.ArrayList;
import java.util.List;

public class PartnerListPersister extends AbstractPersister {
    private final List<Object[]> partnerListBatch = new ArrayList<>();

    public void init() {
        // Delete from partner_prop for dynamic lists
        Main.exec("DELETE FROM " + getId());
        Main.exec(
                "DELETE FROM partner_prop " +
                        "WHERE " + IPersister.ENUM_IDR + " IN (SELECT id FROM int_enum WHERE type = 'properties' AND name IN ('AJANLOTT', 'BARATNOVEL'))"
        );
    }

    @Override
    public void save(StringBuilder sb, JsonNode root) {
        String title = root.get("title").asText();
        sb.append(' ').append(title);
        title = AbstractTrafoEngine.LISTING.getString(title, title);
        for (JsonNode item : root.get("list")) {
            Integer partnerId = item.get(0).asInt();
            Object age = item.get(2).isNull() ? null : item.get(2).asText();
            Object image = item.get(3).isNull() ? null : item.get(3).asText();
            Object[] params = new Object[]{title, partnerId, item.get(1).asText(), age, image};
            partnerListBatch.add(params);
            incBatch();
        }
    }

    @Override
    public void flush() {
        if (notBigEnoughBatch()) return;
        String partnerListSql = "INSERT OR IGNORE INTO " + getId() + " (tag, partner_id, name, age, image) VALUES (?, ?, ?, ?, ?)";
        Main.jdbc().batchUpdate(partnerListSql, partnerListBatch);
        partnerListBatch.clear();
        super.flush();
    }

    @Override
    public void close() {
        super.close();
        // Insert or ignore into partner_prop based on lists
        Main.jdbc().update(
                "INSERT OR IGNORE INTO partner_prop (" + Partner.IDR + ", " + IPersister.ENUM_IDR + ") " +
                        "SELECT pl." + Partner.IDR + ", ie.id " +
                        "FROM " + getId() + " pl " +
                        "JOIN int_enum ie ON ie.type = 'properties' AND ie.name = pl.tag "
        );
    }
}