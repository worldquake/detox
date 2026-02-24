package hu.detox.szexpartnerek.sync.rl.persister;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.szexpartnerek.Main;
import hu.detox.szexpartnerek.sync.AbstractPersister;
import hu.detox.szexpartnerek.sync.IPersister;
import hu.detox.szexpartnerek.sync.rl.component.sub.Partner;
import hu.detox.szexpartnerek.sync.rl.component.sub.User;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static hu.detox.parsers.JSonUtils.getField;

public class UserPartnerFeedbackPersister extends AbstractPersister {
    private final List<Object[]> feedbackBatch = new ArrayList<>();
    private final List<Object[]> ratingBatch = new ArrayList<>();
    private final List<Object[]> goodBatch = new ArrayList<>();
    private final List<Object[]> badBatch = new ArrayList<>();
    private final List<Object[]> detailsBatch = new ArrayList<>();

    public void saveSingle(ObjectNode item, Integer enumId) {
        int fbid = item.get("id").intValue();
        feedbackBatch.add(new Object[]{fbid, getField(item, User.IDR), getField(item, Partner.IDR), enumId, getField(item, "name"), getField(item, "after_name"), getField(item, "useful"), getField(item, "age"), getField(item, "log")});

        JsonNode rates = item.get("rates");
        if (rates != null && rates.isArray()) {
            for (int i = 0; i < rates.size(); i++) {
                JsonNode valNode = rates.get(i);
                if (!valNode.isNull()) {
                    ratingBatch.add(new Object[]{fbid, i, valNode.asInt()});
                }
            }
        }

        for (String key : new String[]{"good", "bad"}) {
            JsonNode arr = item.get(key);
            if (arr != null && arr.isArray()) {
                boolean isBad = key.equals("bad");
                for (JsonNode valNode : arr) {
                    if (isBad) badBatch.add(new Object[]{fbid, valNode.intValue()});
                    else goodBatch.add(new Object[]{fbid, valNode.intValue()});
                }
            }
        }

        JsonNode details = item.get("details");
        if (details != null && details.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = details.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                detailsBatch.add(new Object[]{fbid, Integer.parseInt(entry.getKey()), entry.getValue().asText()});
            }
        }
    }

    @Override
    public void save(StringBuilder sb, JsonNode root) {
        var itemi = root.fields();
        while (itemi.hasNext()) {
            var eitem = itemi.next();
            Integer enumId;
            try {
                enumId = Integer.parseInt(eitem.getKey());
            } catch (NumberFormatException nfe) {
                continue;
            }
            for (var item : eitem.getValue())
                saveSingle((ObjectNode) item, enumId);
        }
    }

    public Timestamp maxLogTime(Integer uid, Integer pid) {
        String maxDateSql = "SELECT MAX(log)|| ':00' FROM user_partner_feedback";
        if (uid != null || pid != null) {
            maxDateSql += " WHERE ";
            if (uid == null) maxDateSql += Partner.IDR + " = " + pid;
            else maxDateSql += User.IDR + " = " + uid;
        }
        return Main.jdbc().queryForObject(maxDateSql, Timestamp.class);
    }

    @Override
    public void flush() {
        if (notBigEnoughBatch()) return;
        String feedbackSql = "INSERT INTO " + getId() + " (id, " + User.IDR + ", " + Partner.IDR + ", " + IPersister.ENUM_IDR + ", name, after_name, useful, age, log) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET " + User.IDR + " = COALESCE(" + getId() + "." + User.IDR + ", excluded." + User.IDR + "), " + Partner.IDR + " = COALESCE(" + getId() + "." + Partner.IDR + ", excluded." + Partner.IDR + "), " + IPersister.ENUM_IDR + " = COALESCE(" + getId() + "." + IPersister.ENUM_IDR + ", excluded." + IPersister.ENUM_IDR + "), name = excluded.name, after_name = excluded.after_name, useful = COALESCE(" + getId() + ".useful, excluded.useful), age = COALESCE(" + getId() + ".age, excluded.age), log = COALESCE(" + getId() + ".log, excluded.log)";
        Main.jdbc().batchUpdate(feedbackSql, feedbackBatch);
        String ratingSql = "INSERT INTO " + getId() + "_rating (fbid, " + IPersister.ENUM_IDR + ", val) VALUES (?, ?, ?) ON CONFLICT(fbid, " + IPersister.ENUM_IDR + ") DO UPDATE SET val=excluded.val";
        Main.jdbc().batchUpdate(ratingSql, ratingBatch);
        String goodSql = "INSERT INTO " + getId() + "_good (fbid, " + IPersister.ENUM_IDR + ") VALUES (?, ?) ON CONFLICT(fbid, " + IPersister.ENUM_IDR + ") DO NOTHING";
        Main.jdbc().batchUpdate(goodSql, goodBatch);
        String badSql = "INSERT INTO " + getId() + "_bad (fbid, " + IPersister.ENUM_IDR + ") VALUES (?, ?) ON CONFLICT(fbid, " + IPersister.ENUM_IDR + ") DO NOTHING";
        Main.jdbc().batchUpdate(badSql, badBatch);
        String detailsSql = "INSERT INTO " + getId() + "_details (fbid, " + IPersister.ENUM_IDR + ", val) VALUES (?, ?, ?) ON CONFLICT(fbid, " + IPersister.ENUM_IDR + ") DO UPDATE SET val=excluded.val";
        Main.jdbc().batchUpdate(detailsSql, detailsBatch);
        feedbackBatch.clear();
        ratingBatch.clear();
        goodBatch.clear();
        badBatch.clear();
        detailsBatch.clear();
        super.flush();
    }
}
