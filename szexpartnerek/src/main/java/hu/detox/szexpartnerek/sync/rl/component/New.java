package hu.detox.szexpartnerek.sync.rl.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.szexpartnerek.sync.IPersister;
import hu.detox.szexpartnerek.sync.ITrafoEngine;
import hu.detox.szexpartnerek.sync.rl.component.sub.Partner;
import hu.detox.szexpartnerek.sync.rl.component.sub.User;
import hu.detox.utils.Serde;
import okhttp3.HttpUrl;
import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.function.Function;

@Component
public class New implements ITrafoEngine {
    public static final String PARTNERS = "partners";
    public static final String USERS = "users";
    private final ITrafoEngine[] sub;

    private New(Partner partner, User user) {
        sub = new ITrafoEngine[]{user, partner};
    }

    @Override
    public void reStart(boolean force) {
        // Nothing to do
    }

    @Override
    public Function<String, String> url() {
        return rest -> {
            if (StringUtil.isBlank(rest)) {
                rest = "a";
            }
            if (rest.length() == 1) {
                rest = "setFrehContent_ajax.php?fresh_type=" + rest + "&fresh_category=SP";
            }
            return rest;
        };
    }

    @Override
    public ITrafoEngine[] subTrafos() {
        return sub;
    }

    @Override
    public IPersister persister() {
        return null;
    }

    @Override
    public Iterator<?> input(JsonNode parent) {
        return null;
    }

    @Override
    public Iterator<String> pager() {
        return null;
    }

    @Override
    public ObjectNode apply(String s) {
        ObjectNode on = Serde.OM.createObjectNode();
        on.put(PARTNERS, Serde.OM.createArrayNode());
        on.put(USERS, Serde.OM.createArrayNode());
        for (Element e : Jsoup.parse(s).select("a")) {
            var url = HttpUrl.get("http://" + e.attr("href"));
            Integer id = Integer.valueOf(url.queryParameter("id").replaceAll("[^0-9]+", ""));
            switch (url.queryParameter("pid")) {
                case "user-data":
                    ((ArrayNode) on.get(USERS)).add(id);
                case "szexpartner-data":
                    ((ArrayNode) on.get(PARTNERS)).add(id);
            }
        }
        System.err.println("New stuff: " + on);
        return on;
    }
}
