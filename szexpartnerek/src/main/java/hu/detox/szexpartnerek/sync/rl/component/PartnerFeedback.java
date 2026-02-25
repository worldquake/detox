package hu.detox.szexpartnerek.sync.rl.component;

import com.fasterxml.jackson.databind.JsonNode;
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

import static hu.detox.szexpartnerek.spring.SzexConfig.jdbc;

@Component
public class PartnerFeedback extends AbstractFeedbackTrafo {
    private static final String[] SELS = new String[]{".beszamoloMainContent .beszOuter"};

    protected PartnerFeedback(User user) {
        super(new ITrafoEngine[]{user});
    }

    @Override
    public Function<String, String> url() {
        return rest -> {
            String res = super.url().apply(rest);
            if (res != null) res = res.replace("???", "");
            return res;
        };
    }

    @Override
    protected String[] selectors() {
        return SELS;
    }

    @Override
    protected Integer getUserId(Document soup, Element curr) {
        String uid = curr.selectFirst("a").attr("href");
        Matcher m = Partner.IDP.matcher(uid);
        return m.find() ? Integer.parseInt(m.group(2)) : null;
    }

    @Override
    protected Element partnerIdAdderGetExtra(ObjectNode map, Element curr) {
        return null;
    }

    @Override
    protected Set<Integer> findProcessableIds() {
        String missingFullReview = "SELECT partner_id FROM user_partner_feedback f\n" +
                "   LEFT JOIN user_partner_feedback_rating d ON d.fbid = f.id\n" +
                "WHERE\n" +
                "   d.fbid IS NULL\n" +
                "   AND partner_id IS NOT NULL\n" +
                "   AND log > DATETIME('now', '-1 year') AND ts < DATETIME('now', '-10 day')\n" +
                "   AND (user_id IS NULL OR user_id IN (SELECT id FROM user WHERE del = true))\n" +
                "   AND (partner_id NOT IN (SELECT id FROM partner WHERE del = true))\n";
        Collection<Integer> extra = jdbc().query(missingFullReview, AbstractTrafoEngine::asInt);
        return new HashSet<>(extra);
    }

    @Override
    protected Timestamp tsIfToProcess(ObjectNode ret, Comment c, String ts) {
        Matcher m = Partner.IDP.matcher(c.getData());
        Timestamp now = null;
        if (m.find()) {
            Integer pid = Integer.parseInt(m.group(2));
            now = super.tsIfToProcess(ret, c, ts);
            Timestamp last = processableIds != null && processableIds.contains(pid) ? null : persister.maxLogTime(null, pid);
            if (!hu.detox.szexpartnerek.sync.Main.args().isFull() && last != null && now.before(last)) now = null;
            ret.put(Partner.IDR, pid);
        }
        return now;
    }

    @Override
    public Iterator<?> input(JsonNode parent) {
        return List.of(parent.get("id")).iterator();
    }
}
