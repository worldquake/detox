package hu.detox.szexpartnerek.sync.rl.component.sub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.szexpartnerek.sync.AbstractTrafoEngine;
import hu.detox.szexpartnerek.sync.IPager;
import hu.detox.szexpartnerek.sync.ITrafoEngine;
import hu.detox.szexpartnerek.sync.rl.Http;
import hu.detox.szexpartnerek.sync.rl.component.New;
import hu.detox.szexpartnerek.sync.rl.component.UserPartnerFeedback;
import hu.detox.szexpartnerek.sync.rl.persister.UserPersister;
import hu.detox.utils.strings.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static hu.detox.szexpartnerek.spring.SyncCommand.text;

@Component
public class User extends AbstractTrafoEngine implements ITrafoEngine.Filters {
    public static final String IDR = "user_id";
    private final ITrafoEngine[] sub;
    private transient UserPersister persister;

    private User(UserPartnerFeedback ur, Http cl) {
        sub = new ITrafoEngine[]{ur};
        persister = new UserPersister();
    }

    @Override
    public Function<String, String> url() {
        return rest -> {
            if (StringUtil.isBlank(rest)) {
                rest = "707194";
            }
            if (StringUtil.isNumeric(rest)) {
                if (untouchableIds != null) untouchableIds.add(Integer.parseInt(rest));
                rest = "rosszlanyok.php?pid=user-data&id=" + rest;
            }
            return rest;
        };
    }

    public ObjectNode getNodeFromLines(String... lines) {
        String nameLine = lines[0];
        String formLine = lines[1];
        String infoText = lines[2] + " " + lines[3];

        ObjectNode result = JsonNodeFactory.instance.objectNode();

        // Extract name and optional data
        Document soup = Jsoup.parse(nameLine);
        String nameFull = soup.text().trim();
        String name, opt;
        int idx = nameFull.indexOf(" (");
        if (idx != -1) {
            name = nameFull.substring(0, idx).trim();
            opt = nameFull.substring(idx + 2).replaceAll("\\)$", "").trim();
        } else {
            name = nameFull;
            opt = "";
        }
        if (!name.isEmpty()) result.put("name", name);

        // Gender
        String gender = null;
        String optLower = opt.toLowerCase();
        if (optLower.contains("férfi")) gender = "FIU";
        else if (optLower.contains("nő")) gender = "LANY";
        else if (optLower.contains("transz")) gender = "TRANSZSZEXUALIS";
        if (gender != null) result.put("gender", gender);

        Matcher ageMatch = Pattern.compile("(\\d+\\+?)\\s*éves").matcher(opt);
        if (ageMatch.find()) result.put("age", ageMatch.group(1));

        Matcher heightMatch = Pattern.compile("(\\d+)\\s*cm").matcher(opt);
        if (heightMatch.find()) result.put("height", Integer.parseInt(heightMatch.group(1)));

        Matcher weightMatch = Pattern.compile("(\\d+)\\s*kg").matcher(opt);
        if (weightMatch.find()) result.put("weight", Integer.parseInt(weightMatch.group(1)));

        Matcher sizeMatch = Pattern.compile("(\\d+)\\s*cm\\s*intim méret").matcher(infoText);
        if (sizeMatch.find()) result.put("size", Integer.parseInt(sizeMatch.group(1)));

        Matcher regMatch = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})-én regisztrált").matcher(infoText);
        if (regMatch.find()) result.put("regd", regMatch.group(1));

        Matcher lastMatch = Pattern.compile("(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})-kor járt itt").matcher(infoText);
        if (lastMatch.find()) result.put("last", lastMatch.group(1));

        Matcher idMatch = Partner.IDP.matcher(formLine);
        if (idMatch.find()) result.put("id", Integer.parseInt(idMatch.group(2)));

        if (infoText.contains("Amit szeret:")) {
            Matcher likesMatcher = Pattern.compile("Amit szeret:(.*?)(?:</div>|$)", Pattern.DOTALL).matcher(infoText);
            if (likesMatcher.find()) {
                String likesStr = likesMatcher.group(1).trim();
                // Apply replacements to avoid comma problems
                likesStr = likesStr.replace("Domina, ", "Domina ");
                likesStr = likesStr.replace(", Rab", " Rab");
                likesStr = likesStr.replace("s, b", "s b");
                likesStr = likesStr.replace(", tort", " tort");
                likesStr = likesStr.replace(", CBT", " CBT");
                // Split and add to array
                String[] likesArr = likesStr.split(", ");
                ArrayNode likesNode = JsonNodeFactory.instance.arrayNode();
                for (String like : likesArr) {
                    addProp(null, LIKES, likesNode, null, like);
                }
                if (!likesNode.isEmpty()) {
                    result.set("likes", likesNode);
                }
            }
        }
        return result;
    }

    @Override
    public Iterator<?> input(JsonNode parent) {
        ArrayNode an = (ArrayNode) parent.get(New.USERS);
        JsonNode uid = parent.get(User.IDR);
        if (an != null) {
            return an.iterator();
        } else if (uid != null) {
            return List.of(uid).iterator();
        } else if (parent instanceof ObjectNode on) {
            ArrayList<Integer> res = new ArrayList<>(2000);
            for (Map.Entry<String, JsonNode> ian : on.properties()) {
                if (IPager.PAGER.equals(ian.getKey())) continue;
                for (JsonNode ien : ian.getValue()) {
                    JsonNode userId = ien.get(User.IDR);
                    if (userId != null) res.add(userId.asInt());
                }
            }
            return res.iterator();
        } else if (parent instanceof ArrayNode on) {
            return on.iterator();
        }
        return null;
    }

    @Override
    public ITrafoEngine[] subTrafos() {
        return sub;
    }

    @Override
    public UserPersister persister() {
        return persister;
    }

    @Override
    public ObjectNode apply(String data) {
        var soup = Jsoup.parse(data);
        Matcher idm = Partner.IDP.matcher(((Comment) soup.childNode(0)).getData());
        Element frst = soup.selectFirst("#about-me-user-list");
        String frstln = frst == null ? "" : text(frst);
        String name = text(soup.selectFirst("div#content h1"));
        ObjectNode result = null;
        if (name == null) {
            // Deleted user trigger
            if (idm.find()) {
                result = JsonNodeFactory.instance.objectNode();
                result.put("id", Integer.parseInt(idm.group(2)));
            }
        } else {
            result = getNodeFromLines(
                    name, soup.selectFirst("td#felsoLanguage a").attr("href"),
                    soup.selectFirst("table#dataUpperTable").text(),
                    frstln
            );
        }
        return result;
    }

    @Override
    public boolean skips(String in) {
        if (untouchableIds == null || !StringUtils.isNumeric(in)) return false;
        return !this.untouchableIds.add(Integer.parseInt(in));
    }

    @Override
    protected Integer onEnum(Properties map, ArrayNode arr, ArrayNode propsArr, AtomicReference<String> en) {
        return null;
    }

    @Override
    protected Set<Integer> findProcessableIds() {
        return persister.getProcessableIds(untouchableIds, 1000);
    }
}
