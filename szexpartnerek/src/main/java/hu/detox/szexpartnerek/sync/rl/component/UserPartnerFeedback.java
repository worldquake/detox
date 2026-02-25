package hu.detox.szexpartnerek.sync.rl.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.szexpartnerek.sync.AbstractTrafoEngine;
import hu.detox.szexpartnerek.sync.ITrafoEngine;
import hu.detox.szexpartnerek.sync.rl.component.sub.Partner;
import hu.detox.szexpartnerek.sync.rl.component.sub.User;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;

import static hu.detox.spring.DetoxConfig.ctx;
import static hu.detox.szexpartnerek.spring.SzexConfig.jdbc;

@Component
public class UserPartnerFeedback extends AbstractFeedbackTrafo {
    protected UserPartnerFeedback() {
        super(null);
    }

    @Override
    public Function<String, String> url() {
        return rest -> {
            String res = super.url().apply(rest);
            if (res != null) res = res.replace("???", "user_");
            return res;
        };
    }

    @Override
    public Iterator<?> input(JsonNode parent) {
        ArrayNode an = (ArrayNode) parent.get(New.USERS);
        if (an != null) {
            return an.iterator();
        } else if (parent instanceof ObjectNode) {
            return List.of(parent.get("id")).iterator();
        }
        return null;
    }

    protected String[] selectors() {
        return new String[]{
                "div#beszamoloMainContent>div[style*=\"A0706E\"]", // The list items
                "a[href], a[onclick]" // The partner's id
        };
    }

    @Override
    protected Integer getUserId(Document soup, Element curr) {
        Comment cmt = ((Comment) soup.childNode(0));
        Matcher m = Partner.IDP.matcher(cmt.getData());
        Integer userId = null;
        if (m.find()) userId = Integer.parseInt(m.group(2));
        return userId;
    }

    @Override
    protected Set<Integer> findProcessableIds() {
        String missingFullReview = "SELECT user_id FROM user_partner_feedback " +
                "WHERE partner_id IS NULL " +
                "AND log > DATETIME('now', '-1 year') AND ts < DATETIME('now', '-10 day') " +
                "AND (user_id NOT IN (SELECT id FROM user WHERE del = true))";
        Collection<Integer> extra = jdbc().query(missingFullReview, AbstractTrafoEngine::asInt);
        return new HashSet<>(extra);
    }

    @Override
    public ITrafoEngine[] preTrafos() {
        // Because of circular dependencies
        if (pre == null) pre = new ITrafoEngine[]{ctx().getBean(Partner.class)};
        return pre;
    }

    @Override
    protected Timestamp tsIfToProcess(ObjectNode ret, Comment c, String ts) {
        Timestamp now = super.tsIfToProcess(ret, c, ts);
        Integer uid = ret.get(User.IDR).asInt();
        Timestamp last = processableIds != null && processableIds.contains(uid) ? null : persister.maxLogTime(uid, null);
        if (!hu.detox.szexpartnerek.sync.Main.args().isFull() && last != null && now.before(last)) now = null;
        return now;
    }

    protected Element partnerIdAdderGetExtra(ObjectNode map, Element curr) {
        Element a = curr.selectFirst("a[href], a[onclick]");
        if (a == null) return null;
        String href = a.attr("href");
        Matcher idMatch = Partner.IDP.matcher(href);
        if (idMatch.find()) {
            map.put(Partner.IDR, Integer.parseInt(idMatch.group(2)));
        }
        return a.parent();
    }

}
