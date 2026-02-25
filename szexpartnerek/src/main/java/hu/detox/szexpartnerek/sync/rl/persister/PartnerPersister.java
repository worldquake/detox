package hu.detox.szexpartnerek.sync.rl.persister;

import com.fasterxml.jackson.databind.JsonNode;
import hu.detox.szexpartnerek.sync.AbstractPersister;
import hu.detox.szexpartnerek.sync.IPersister;
import hu.detox.szexpartnerek.sync.rl.component.sub.Partner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static hu.detox.szexpartnerek.spring.SzexConfig.jdbc;

public class PartnerPersister extends AbstractPersister {
    private final List<Object[]> partnerDelBatch = new ArrayList<>();
    private final List<Object[]> partnerBatch = new ArrayList<>();
    private final List<Object[]> phonePropBatch = new ArrayList<>();
    private final List<Object[]> partnerPropBatch = new ArrayList<>();
    private final List<Object[]> openHourBatch = new ArrayList<>();
    private final List<Object[]> langBatch = new ArrayList<>();
    private final List<Object[]> answerBatch = new ArrayList<>();
    private final List<Object[]> lookingBatch = new ArrayList<>();
    private final List<Object[]> massageBatch = new ArrayList<>();
    private final List<Object[]> likeBatch = new ArrayList<>();
    private final List<Object[]> imgBatch = new ArrayList<>();
    private final List<Object[]> activityBatch = new ArrayList<>();

    @Override
    public void save(StringBuilder sb, JsonNode root) {
        JsonNode name = root.get("name");
        int partnerId = root.get("id").asInt();
        if (name == null) {
            sb.append(" del=").append(partnerId);
            partnerDelBatch.add(new Object[]{partnerId});
            return;
        }
        Object[] partnerParams = new Object[19];
        partnerParams[0] = partnerId;
        JsonNode phn = root.get("phone");
        boolean active = phn != null && !phn.isEmpty();
        partnerParams[1] = active ? phn.get(0).asText() : null;
        partnerParams[2] = name.asText();
        sb.append(' ').append(name);
        partnerParams[3] = root.get("pass").asText();
        partnerParams[4] = active ? root.get("about").asText() : null;
        partnerParams[5] = root.get("active").asText();
        partnerParams[6] = root.has("expect") && !root.get("expect").isNull() ? root.get("expect").asText() : null;

        JsonNode msrs = root.get("measures");
        if (msrs == null) {
            Arrays.fill(partnerParams, 7, 13, null);
        } else {
            partnerParams[7] = msrs.has("age") && !msrs.get("age").isNull() ? msrs.get("age").asText() : null;
            partnerParams[8] = msrs.has("height") && !msrs.get("height").isNull() ? msrs.get("height").asInt() : null;
            partnerParams[9] = msrs.has("weight") && !msrs.get("weight").isNull() ? msrs.get("weight").asInt() : null;
            partnerParams[10] = msrs.has("breast") && !msrs.get("breast").isNull() ? msrs.get("breast").asInt() : null;
            partnerParams[11] = msrs.has("waist") && !msrs.get("waist").isNull() ? msrs.get("waist").asInt() : null;
            partnerParams[12] = msrs.has("hips") && !msrs.get("hips").isNull() ? msrs.get("hips").asInt() : null;
        }

        JsonNode loc = root.get("location");
        if (loc == null) {
            Arrays.fill(partnerParams, 13, 17, null);
        } else {
            partnerParams[13] = loc.get(0).asText();
            partnerParams[14] = loc.get(1).isNull() ? null : loc.get(1).asText();
            String[] coords = null;
            if (loc.size() > 2) coords = loc.get(2).asText().split(",");
            if (coords != null && coords.length == 2) {
                partnerParams[15] = Double.parseDouble(coords[0]);
                partnerParams[16] = Double.parseDouble(coords[1]);
            } else {
                partnerParams[15] = null;
                partnerParams[16] = null;
            }
        }

        JsonNode la = root.get("looking_age");
        if (la != null) {
            partnerParams[17] = la.get(0).asInt();
            partnerParams[18] = la.size() == 1 ? null : la.get(1).asInt();
        } else {
            partnerParams[17] = null;
            partnerParams[18] = null;
        }

        partnerBatch.add(partnerParams);

        if (phn != null) {
            for (int i = 1; i < phn.size(); i++) {
                phonePropBatch.add(new Object[]{partnerId, phn.get(i).asInt()});
            }
        }

        for (JsonNode n : root.get("properties")) {
            partnerPropBatch.add(new Object[]{partnerId, n.asInt()});
        }

        for (int i = 0; i < root.get("openHours").size(); i++) {
            openHourBatch.add(new Object[]{partnerId, i, root.get("openHours").get(i).asText()});
        }

        for (JsonNode n : root.get("langs")) {
            langBatch.add(new Object[]{partnerId, n.asText()});
        }

        JsonNode ans = root.get("answers");
        if (ans != null) {
            Iterator<String> answerKeys2 = ans.fieldNames();
            while (answerKeys2.hasNext()) {
                String key = answerKeys2.next();
                answerBatch.add(new Object[]{partnerId, Integer.parseInt(key), ans.get(key).asText()});
            }
        }

        JsonNode lkng = root.get("looking");
        if (lkng != null) for (JsonNode n : lkng) {
            lookingBatch.add(new Object[]{partnerId, n.asInt()});
        }

        JsonNode masgs = root.get("massages");
        if (masgs != null) for (JsonNode n : masgs) {
            massageBatch.add(new Object[]{partnerId, n.asInt()});
        }

        for (String status : Arrays.asList("no", "yes", "ask")) {
            for (JsonNode n : root.get("likes").get(status)) {
                likeBatch.add(new Object[]{partnerId, n.asInt(), status});
            }
        }

        for (JsonNode arr : root.get("imgs")) {
            Object ondate = arr.get(0).isNull() ? null : arr.get(0).asText();
            imgBatch.add(new Object[]{partnerId, ondate, arr.get(1).asText()});
        }

        JsonNode activity = root.get("activity");
        if (activity != null) {
            Iterator<String> activityKeys = activity.fieldNames();
            while (activityKeys.hasNext()) {
                String date = activityKeys.next();
                activityBatch.add(new Object[]{partnerId, date, activity.get(date).asText()});
            }
        }
    }

    @Override
    public void flush() {
        if (notBigEnoughBatch()) return;
        // SQL strings as local variables
        String partnerSql = "INSERT INTO " + getId() + " (" +
                "    id, call_number, name, pass, about, active_info, expect, age, height, weight, breast, waist, hips, location, location_extra, latitude, longitude, looking_age_min, looking_age_max\n" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\n" +
                "ON CONFLICT(id) DO UPDATE SET\n" +
                "    call_number = COALESCE(excluded.call_number, " + getId() + ".call_number),\n" +
                "    about = COALESCE(excluded.about, " + getId() + ".about),\n" +
                "    name = excluded.name,\n" +
                "    pass = excluded.pass,\n" +
                "    active_info = excluded.active_info,\n" +
                "    expect = excluded.expect,\n" +
                "    age = excluded.age,\n" +
                "    height = excluded.height,\n" +
                "    weight = excluded.weight,\n" +
                "    breast = excluded.breast,\n" +
                "    waist = excluded.waist,\n" +
                "    hips = excluded.hips,\n" +
                "    location = COALESCE(excluded.location, " + getId() + ".location),\n" +
                "    location_extra = COALESCE(excluded.location_extra, " + getId() + ".location_extra),\n" +
                "    latitude = excluded.latitude,\n" +
                "    longitude = excluded.longitude,\n" +
                "    looking_age_min = excluded.looking_age_min,\n" +
                "    looking_age_max = excluded.looking_age_max,\n" +
                "    del=false";
        jdbc().batchUpdate(partnerSql, partnerBatch);
        List<Object[]> delBatch = partnerBatch.stream().map(objects -> new Object[]{objects[0]}).toList();
        partnerBatch.clear();
        Object[][] tableNameOps = {
                new Object[]{getId() + "_phone_prop", "(" + Partner.IDR + ", " + IPersister.ENUM_IDR + ") VALUES (?, ?)", phonePropBatch},
                new Object[]{getId() + "_prop", "(" + Partner.IDR + ", " + IPersister.ENUM_IDR + ") VALUES (?, ?)", partnerPropBatch},
                new Object[]{getId() + "_open_hour", "(" + Partner.IDR + ", onday, hours) VALUES (?, ?, ?)", openHourBatch},
                new Object[]{getId() + "_lang", "(" + Partner.IDR + ", lang) VALUES (?, ?)", langBatch},
                new Object[]{getId() + "_answer", "(" + Partner.IDR + ", " + IPersister.ENUM_IDR + ", answer) VALUES (?, ?, ?)", answerBatch},
                new Object[]{getId() + "_looking", "(" + Partner.IDR + ", " + IPersister.ENUM_IDR + ") VALUES (?, ?)", lookingBatch},
                new Object[]{getId() + "_massage", "(" + Partner.IDR + ", " + IPersister.ENUM_IDR + ") VALUES (?, ?)", massageBatch},
                new Object[]{getId() + "_like", "(" + Partner.IDR + ", " + IPersister.ENUM_IDR + ", option) VALUES (?, ?, ?)", likeBatch},
                new Object[]{getId() + "_img", "(" + Partner.IDR + ", ondate, path) VALUES (?, ?, ?)", imgBatch}
        };
        for (Object[] arr : tableNameOps) {
            String sql = "DELETE FROM " + arr[0] + " WHERE " + Partner.IDR + " = ?";
            jdbc().batchUpdate(sql, delBatch);
        }
        for (Object[] arr : tableNameOps) {
            List<Object[]> batch = (List) arr[2];
            if (batch.isEmpty()) continue;
            String sql = "INSERT OR IGNORE INTO " + arr[0] + arr[1];
            jdbc().batchUpdate(sql, batch);
            batch.clear();
        }
        String activitySql = "INSERT OR IGNORE INTO " + getId() + "_activity (" + Partner.IDR + ", ondate, description) VALUES (?, ?, ?)";
        String partnerDelSql = "UPDATE " + getId() + " SET del = true WHERE id = ?";
        jdbc().batchUpdate(activitySql, activityBatch);
        jdbc().batchUpdate(partnerDelSql, partnerDelBatch);

        activityBatch.clear();
        partnerDelBatch.clear();
        super.flush();
    }
}