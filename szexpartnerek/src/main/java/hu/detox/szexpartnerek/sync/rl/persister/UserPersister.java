package hu.detox.szexpartnerek.sync.rl.persister;

import com.fasterxml.jackson.databind.JsonNode;
import hu.detox.szexpartnerek.Main;
import hu.detox.szexpartnerek.sync.AbstractPersister;
import hu.detox.szexpartnerek.sync.rl.component.sub.User;

import java.util.ArrayList;
import java.util.List;

import static hu.detox.parsers.JSonUtil.getField;

public class UserPersister extends AbstractPersister {
    private final List<Object[]> userBatch = new ArrayList<>();
    private final List<Object[]> userLikesBatch = new ArrayList<>();
    private final List<Object[]> userLikesDeleteBatch = new ArrayList<>();
    private final List<Object[]> userDeleteBatch = new ArrayList<>();

    @Override
    public void save(StringBuilder sb, JsonNode user) {
        Object idObj = getField(user, "id");
        Object nameObj = getField(user, "name");
        if (nameObj == null) {
            sb.append(" del=").append(idObj);
            userDeleteBatch.add(new Object[]{idObj});
            return;
        }

        sb.append(" n=").append(nameObj);
        Object ageObj = getField(user, "age");
        Object heightObj = getField(user, "height");
        Object weightObj = getField(user, "weight");
        Object genderObj = getField(user, "gender");
        Object regdObj = getField(user, "regd");
        Object sizeObj = getField(user, "size");
        Object regdParam = null;
        if (regdObj instanceof String && !((String) regdObj).isEmpty()) {
            regdParam = java.sql.Date.valueOf((String) regdObj);
        }
        Object sizeParam = sizeObj != null ? sizeObj : null;

        userBatch.add(new Object[]{idObj, nameObj, ageObj, heightObj, weightObj, genderObj, regdParam, sizeParam});
        userLikesDeleteBatch.add(new Object[]{idObj});

        // re-insert likes
        JsonNode likesNode = user.get("likes");
        if (likesNode != null && likesNode.isArray()) {
            for (JsonNode likeNode : likesNode) {
                String like = likeNode.asText();
                if (like != null && !like.isEmpty()) {
                    userLikesBatch.add(new Object[]{idObj, like});
                }
            }
        }
    }

    @Override
    public void flush() {
        if (notBigEnoughBatch()) return;
        Main.jdbc().batchUpdate("INSERT INTO " + getId() + " (id, name, age, height, weight, gender, regd, size) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET " +
                "name = excluded.name, " +
                "age = COALESCE(excluded.age, " + getId() + ".age), " +
                "height = COALESCE(excluded.height, " + getId() + ".height), " +
                "weight = COALESCE(excluded.weight, " + getId() + ".weight), " +
                "gender = COALESCE(excluded.gender, " + getId() + ".gender), " +
                "regd = COALESCE(excluded.regd, " + getId() + ".regd), " +
                "size = COALESCE(excluded.size, " + getId() + ".size), " +
                "del = false", userBatch);
        Main.jdbc().batchUpdate("DELETE FROM tmp_user_like WHERE " + User.IDR + " = ?", userLikesDeleteBatch);
        Main.jdbc().batchUpdate("INSERT INTO tmp_user_like (" + User.IDR + ", like) VALUES (?, ?) " +
                "ON CONFLICT(" + User.IDR + ", like) DO NOTHING", userLikesBatch);
        Main.jdbc().batchUpdate("UPDATE user SET del=true WHERE id=?", userDeleteBatch);
        userBatch.clear();
        userLikesBatch.clear();
        userLikesDeleteBatch.clear();
        userDeleteBatch.clear();
        super.flush();
    }
}