package hu.detox.szexpartnerek.sync.rl.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import hu.detox.szexpartnerek.sync.AbstractTrafoEngine;
import hu.detox.szexpartnerek.sync.IPersister;
import hu.detox.szexpartnerek.sync.ITrafoEngine;
import hu.detox.szexpartnerek.sync.rl.component.sub.Partner;
import hu.detox.szexpartnerek.sync.rl.component.sub.User;
import hu.detox.utils.strings.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;

import static hu.detox.parsers.JSonUtils.OM;

@Component
public class UserList extends AbstractTrafoEngine {
    private final ITrafoEngine[] sub;
    private transient String data;

    UserList(User usr) {
        sub = new ITrafoEngine[]{usr};
    }

    @Override
    protected Integer onEnum(Properties map, ArrayNode arr, ArrayNode propsArr, AtomicReference<String> en) {
        return null;
    }

    @Override
    protected Set<Integer> findProcessableIds() {
        return null;
    }

    @Override
    public Function<String, String> url() {
        return rest -> {
            if (StringUtils.isBlank(rest)) data = null;
            else data = "filterdata[userage]=\"" + rest + "\"";
            return "rosszlanyok.php?pid=proud_of_users";
        };
    }

    @Override
    public boolean post() {
        return data != null;
    }

    @Override
    public Iterator<?> input(JsonNode parent) {
        return null;
    }

    @Override
    public IPersister persister() {
        return null;
    }

    @Override
    public ITrafoEngine[] subTrafos() {
        return sub;
    }

    @Override
    public boolean skips(String in) {
        return false;
    }

    @Override
    public ArrayNode apply(String s) {
        ArrayNode res = OM.createArrayNode();
        for (Element e : Jsoup.parse(s).select("a[href*='user-data']")) {
            Matcher m = Partner.IDP.matcher(e.attr("href"));
            if (m.find()) res.add(m.group(2));
        }
        return res;
    }
}
