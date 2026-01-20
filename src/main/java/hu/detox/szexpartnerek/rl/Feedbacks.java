package hu.detox.szexpartnerek.rl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.szexpartnerek.Pager;
import hu.detox.szexpartnerek.Utils;
import okhttp3.RequestBody;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Element;

import java.io.File;
import java.util.Iterator;
import java.util.function.Function;
import java.util.regex.Matcher;

public class Feedbacks extends UserReview {
    public static final Feedbacks INSTANCE = new Feedbacks();

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

    @Override
    public String[] in() {
        return new String[]{
                "src/main/resources/search-reviews.txt"
        };
    }

    protected String[] selectors() {
        return new String[]{"div.beszOuter"};
    }

    @Override
    public File out() {
        return new File("target/gen-search-reviews.jsonl");
    }

    @Override
    protected int pageSize() {
        return 15;
    }

    @Override
    protected ObjectNode readSingle(Integer idp, Element elem) {
        String href = elem.selectFirst("div.beszHeader a").attr("href");
        Matcher m = Advertiser.IDP.matcher(href);
        if (m.find()) idp = Integer.parseInt(m.group(2));
        else return null;
        return super.readSingle(idp, elem);
    }

    @Override
    public Pager pager() {
        Pager p = super.pager();
        return new Pager.PagerWrap(p) {

            @Override
            public RequestBody req() {
                return Utils.bodyOf("filterMegye=&filterBeszamolo=1&clearFilters=0&filterGender=no&filterSubGenders%5B%5D=sp&filterSubGenders%5B%5D=ma&filterSubGenders%5B%5D=do&nev=&tel1=&nick=&szoveg=&datum_ev=&datum_ho=");
            }
        };
    }
}
