package hu.detox.szexpartnerek.sync.rl.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.Agent;
import hu.detox.io.CharIOHelper;
import hu.detox.szexpartnerek.sync.IPager;
import hu.detox.szexpartnerek.sync.ITrafoEngine;
import hu.detox.szexpartnerek.sync.rl.Entry;
import hu.detox.szexpartnerek.sync.rl.component.sub.Partner;
import hu.detox.szexpartnerek.sync.rl.component.sub.User;
import hu.detox.utils.strings.StringUtils;
import org.apache.commons.io.IOUtils;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;

@Component
public class Feedbacks extends AbstractFeedbackTrafo implements ApplicationListener<ContextRefreshedEvent> {
    private final List<String> datas;
    private Timestamp last;
    private transient String tsFirst;

    public Feedbacks() {
        super(null);
        String f = Entry.toName(getId() + "-data.txt");
        try (CharIOHelper cio = CharIOHelper.attempt(Agent.resource(f))) {
            datas = IOUtils.readLines(cio.getReader());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + f, e);
        }
    }

    @Override
    protected ObjectNode readSingle(String[] sel, Document soup, Element elem) {
        ObjectNode on = super.readSingle(sel, soup, elem);
        if (tsFirst == null && on != null) tsFirst = on.get("log").asText();
        return on;
    }

    @Override
    public Function<String, String> url() {
        return rest -> {
            String lst = "4layer/beszamolo_list.php?tagcategories=&folder_postfix=";
            if (StringUtil.isBlank(rest)) return lst;
            else if (rest.matches("[a-z]+")) lst += "&status=" + rest;
            else if (StringUtil.isNumeric(rest)) lst += "&offset=" + rest;
            return lst;
        };
    }

    @Override
    public Iterator<?> input(JsonNode parent) {
        return null;
    }

    public ITrafoEngine[] preTrafos() {
        // Because of circular dependencies
        if (pre == null) pre = new ITrafoEngine[]{hu.detox.Main.ctx().getBean(Partner.class),
                hu.detox.Main.ctx().getBean(User.class)};
        return pre;
    }

    protected String[] selectors() {
        return new String[]{
                "div.beszOuter", // In this view this is the list item
                ".hiddenNev" // This is the holder of the partner info
        };
    }

    @Override
    protected int pageSize() {
        return 15;
    }

    @Override
    protected Element partnerIdAdderGetExtra(ObjectNode map, Element curr) {
        Element partner = curr.selectFirst("#placeholder");
        if (partner != null) {
            String id = partner.attr("data-memberthumb");
            map.put(Partner.IDR, Integer.parseInt(id));
        }
        return null;
    }

    @Override
    protected Integer getUserId(Document soup, Element curr) {
        String uid = curr.selectFirst(".beszHeader a").attr("href");
        Matcher m = Partner.IDP.matcher(uid);
        return m.find() ? Integer.parseInt(m.group(2)) : null;
    }

    @Override
    protected Timestamp tsIfToProcess(ObjectNode ret, Comment c, String ts) {
        Timestamp now = super.tsIfToProcess(ret, c, ts);
        if (!hu.detox.szexpartnerek.sync.Main.ARGS.get().isFull() && last != null && now.before(last)) return null;
        return now;
    }

    @Override
    public IPager pager() {
        IPager p = super.pager();
        return new IPager.PagerWrap(p) {
            private int di;

            @Override
            public boolean hasNext() {
                boolean has = super.hasNext();
                if (!has) {
                    reset();
                    if (tsFirst != null) {
                        Timestamp lst = StringUtils.to(Timestamp.class, tsFirst, null);
                        if (last.before(lst)) last = lst;
                        tsFirst = null;
                    }
                    di++;
                }
                return di < datas.size();
            }

            @Override
            public int current(JsonNode node) {
                if (node.size() == 1) return -1; // We skipped items, only pg is there
                return super.current(node);
            }

            @Override
            public String req() {
                return datas.get(di);
            }
        };
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (last != null) return;
        try {
            last = persister().maxLogTime(null, null);
        } catch (UncategorizedSQLException se) {
            last = new Timestamp(0);
        }
    }
}
