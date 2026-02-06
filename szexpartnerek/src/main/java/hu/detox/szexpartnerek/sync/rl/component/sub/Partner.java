package hu.detox.szexpartnerek.sync.rl.component.sub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.szexpartnerek.Main;
import hu.detox.szexpartnerek.sync.AbstractTrafoEngine;
import hu.detox.szexpartnerek.sync.IPager;
import hu.detox.szexpartnerek.sync.IPersister;
import hu.detox.szexpartnerek.sync.ITrafoEngine;
import hu.detox.szexpartnerek.sync.rl.Entry;
import hu.detox.szexpartnerek.sync.rl.component.New;
import hu.detox.szexpartnerek.sync.rl.component.PartnerFeedback;
import hu.detox.szexpartnerek.sync.rl.persister.PartnerPersister;
import hu.detox.utils.Serde;
import hu.detox.utils.StringUtils;
import okhttp3.HttpUrl;
import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static hu.detox.szexpartnerek.spring.SyncCommand.normalize;
import static hu.detox.szexpartnerek.spring.SyncCommand.text;

@Component
public class Partner extends AbstractTrafoEngine implements ITrafoEngine.Filters, ApplicationListener<ContextRefreshedEvent> {
    public static final String IDR = "partner_id";
    public static final Pattern IDP = Pattern.compile("(id=|member/|adatlap/)([0-9]+)");

    private static final String EMAILP = "mailto:";
    private static final String DATEP = "\\d{4}-\\d{2}-\\d{2}";
    private static final Pattern DATEF = Pattern.compile(DATEP);
    private static final Pattern LOOKING_AGE = Pattern.compile("(\\d+)\\D*(felett)?\\D*(\\d+)?\\D*(alatt)?");
    private static final Pattern READING = Pattern.compile("(\\d+) levél, (\\d+) olvasatlan");
    private static final Pattern MEASUERS = Pattern.compile("(\\d+\\+?)\\s*(éves|kg|mell|derék|csípő|cm)");

    private transient PartnerPersister persister;
    private transient StringBuilder extra = new StringBuilder();
    private Configuration massage;
    private Configuration looking;
    private Configuration massageReverse = new BaseConfiguration();
    protected final ITrafoEngine[] sub;

    private Partner(PartnerFeedback fb) {
        persister = new PartnerPersister();
        sub = new ITrafoEngine[]{fb};
    }

    protected boolean addMassage(ArrayNode arr, String prp) {
        String enm = Main.toEnumLike(prp);
        boolean anyMass = enm != null && enm.contains("MASSZAZS") && !enm.equals("MASSZAZS");
        if (enm != null && !MASSAGES.containsKey(enm)) enm = massageReverse.getString(enm);
        boolean ret = enm != null || anyMass;
        if (ret) {
            if (enm == null) {
                enm = massage.getKeys().next();
            }
            addProp(extra, MASSAGES, arr, null, enm);
        }
        return ret;
    }

    @Override
    public boolean skips(String in) {
        if (untouchableIds == null || !StringUtils.isNumeric(in)) return false;
        return !this.untouchableIds.add(Integer.parseInt(in));
    }

    protected Integer onEnum(Properties map, ArrayNode arr, ArrayNode propsArr, AtomicReference<String> en) {
        String enm = en.get();
        Integer res = null;
        if (map == LOOKING) {
            String nenm = looking.getString(enm);
            if (nenm == null) {
                for (Object w : looking.getList("MINDEN")) {
                    if (enm.contains((String) w)) {
                        res = doAddProp(map, arr, (String) w);
                    }
                }
            } else if (nenm.equals("-")) {
                if (propsArr != null) doAddProp(PROPS, propsArr, enm);
                return -1;
            }
            en.set(nenm);
        }
        return res;
    }

    @Override
    protected Set<Integer> findProcessableIds() {
        return persister.getProcessableIds(untouchableIds, 50);
    }

    @Override
    public Function<String, String> url() {
        return rest -> {
            if (StringUtil.isBlank(rest)) return null;
            if (StringUtil.isNumeric(rest)) {
                if (untouchableIds != null) untouchableIds.add(Integer.parseInt(rest));
                rest = "rosszlanyok.php?pid=szexpartner-data&id=" + rest + "&instantStat=0";
            }
            return rest;
        };
    }

    @Override
    public Iterator<?> input(JsonNode parent) {
        ArrayNode an = (ArrayNode) parent.get(New.PARTNERS);
        JsonNode node = parent.get(Partner.IDR);
        if (an != null) {
            return an.iterator();
        } else if (node != null) {
            return List.of(node).iterator();
        } else if (parent instanceof ObjectNode on) {
            ArrayList<Integer> res = new ArrayList<>(2000);
            for (Map.Entry<String, JsonNode> ian : on.properties()) {
                if (IPager.PAGER.equals(ian.getKey())) continue;
                for (JsonNode ien : ian.getValue()) {
                    if (ien instanceof ArrayNode iian) { // List node
                        res.add(iian.get(0).asInt());
                    } else {
                        JsonNode partnerId = ien.get(Partner.IDR);
                        if (partnerId != null) res.add(partnerId.asInt());
                    }
                }
            }
            return res.iterator();
        }
        return null;
    }

    @Override
    public ITrafoEngine[] subTrafos() {
        return sub;
    }

    @Override
    public IPersister persister() {
        return persister;
    }

    private Element html(Element el, String repl) {
        if (repl == null) repl = ",";
        return Jsoup.parseBodyFragment(el.html().replaceAll("<br\\s*/?>", repl)).body();
    }

    private void quality(Element dataCol, ArrayNode propsArr, ObjectNode result) {
        for (Element li : dataCol.select("li.check")) {
            String txt = text(li);
            if (txt == null
                    || txt.contains("adatlap kitöltés") || txt.contains("Belépett")) continue;
            addProp(extra, PROPS, propsArr, null, txt);
        }
    }

    private void secondData(Element dataCol, ArrayNode propsArr, ArrayNode massageArr, ObjectNode result) {
        ArrayNode openArr = Serde.OM.createArrayNode();
        Elements rows = dataCol.select("table.dataUpperRightTable tr");
        Map<String, Integer> dayMap = Map.of(
                "hétfő", 0, "kedd", 1, "szerda", 2, "csütörtök", 3, "péntek", 4, "szombat", 5, "vasárnap", 6
        );
        String[] openHours = new String[7];
        Arrays.fill(openHours, null);
        for (Element row : rows) {
            String days = text(row.selectFirst("td[align=right]"));
            String time = text(row.select("td").get(1));
            if (time != null) for (String d : dayMap.keySet()) {
                if (days.contains(d)) openHours[dayMap.get(d)] = time;
            }
        }
        for (String oh : openHours) openArr.add(oh);
        result.set("openHours", openArr);

        ArrayNode langsArr = Serde.OM.createArrayNode();
        for (Element img : dataCol.select("img[src*=flags]")) {
            String src = img.attr("src");
            Matcher m = Pattern.compile("flags/(\\w+)\\.").matcher(src);
            if (m.find()) langsArr.add(m.group(1));
        }
        result.set("langs", langsArr);

        Element msgStat = dataCol.selectFirst("div#msgLoginStatMiddle");
        if (msgStat != null) {
            Matcher m = READING.matcher(msgStat.text());
            if (m.find()) {
                ArrayNode msgsArr = Serde.OM.createArrayNode();
                msgsArr.add(Integer.parseInt(m.group(1)));
                msgsArr.add(Integer.parseInt(m.group(2)));
                result.set("msgs", msgsArr);
            }
        }

        // PROPS enum (br-ek alapján, továbbiak)
        String txt;
        try {
            var dc = dataCol.child(0);
            txt = html(dc, null).ownText();
            for (String prop : txt.split(",")) {
                prop = Jsoup.parseBodyFragment(prop).text();
                if (!addMassage(massageArr, prop)) {
                    addProp(extra, PROPS, propsArr, null, prop);
                }
            }
        } catch (IndexOutOfBoundsException ioob) {
            // Ok, no props
        }
        txt = text(dataCol.selectFirst("a[href^=" + EMAILP + "]"), "href");
        if (txt != null) result.put("email", txt.replaceFirst(EMAILP, ""));
    }

    private ArrayNode firstData(Element dataCol, ArrayNode propsArr, ObjectNode result) {
        Element seg = html(dataCol, "⚠️");
        Element a = seg.selectFirst("a");
        if (a != null) a.append("⚠️");
        String txt = text(seg).replaceAll("Fontos:", "⚠️");

        int idx2 = txt.indexOf("⚠️");
        ArrayNode loc = null;
        if (!txt.contains("Nincs személyes találkozás")) {
            loc = Serde.OM.createArrayNode();
            int idx = txt.indexOf("⚠️"); // Remove the location
            loc.add(normalize(txt.substring(0, idx)));
            idx2 = txt.indexOf("⚠️", idx + 1);
            loc.add(normalize(txt.substring(idx + 3, idx2).replace("Térkép", "")));
            result.put("location", loc);
        }
        txt = parseOutMeasures(result, txt.substring(idx2 + 1));

        for (String prop : txt.split("[,⚠️]\\s*")) {
            addProp(extra, PROPS, propsArr, null, prop);
        }
        result.set("properties", propsArr);
        return loc;
    }

    @NotNull
    private void parseOutMyInfo(ObjectNode result, ArrayNode propsArr, String txt) {
        var arr = Serde.OM.createArrayNode();
        Properties props = null;
        var answerMap = Serde.OM.createObjectNode();
        String enumOfQ = null;
        for (String val : txt.split("⚠️")) {
            if (val.contains("💕")) {
                if (val.contains("it keresek")) props = LOOKING;
                else if (val.contains("Amilyen vagyok")) props = ANSWERS;
                else if (val.contains("Amit még")) {
                    int cln = val.indexOf(':');
                    result.put("knowit", normalize(val.substring(cln + 1)));
                }
                continue;
            }
            if (props == LOOKING) {
                val = val.replace("Negyed (SOS francia)", "SOS")
                        .replace("órára", "").replace("akár", "");
                var am = LOOKING_AGE.matcher(val);
                boolean find = am.find();
                if (find && val.contains("év")) {
                    Integer from = Integer.valueOf(am.group(1));
                    Integer to;
                    ArrayNode ara = Serde.OM.createArrayNode();
                    if (StringUtil.isNumeric(am.group(3))) {
                        to = Integer.valueOf(am.group(3));
                        if (from < to) {
                            ara.add(from);
                            ara.add(to);
                        } else {
                            ara.add(to);
                            ara.add(from);
                        }
                    } else {
                        if (!StringUtil.isBlank(am.group(2))) {
                            ara.add(18);
                        }
                        ara.add(from);
                    }
                    result.put("looking_age", ara);
                } else {
                    for (String iv : val.split(",")) {
                        addProp(extra, LOOKING, arr, propsArr, iv);
                    }
                }
            } else if (props == ANSWERS) {
                val = StringUtils.trimToNull(val);
                if (enumOfQ == null) {
                    enumOfQ = val;
                } else {
                    int qenum = addProp(extra, props, null, null, enumOfQ);
                    answerMap.put(String.valueOf(qenum), val);
                    enumOfQ = null;
                }
            }
        }
        if (!answerMap.isEmpty()) result.put("answers", answerMap);
        if (!arr.isEmpty()) result.put("looking", arr);
    }

    @NotNull
    private String parseOutMeasures(ObjectNode result, String txt) {
        ObjectNode measures = Serde.OM.createObjectNode();
        Matcher all = MEASUERS.matcher(txt);
        StringBuilder sb = new StringBuilder();
        while (all.find()) {
            all.appendReplacement(sb, "");
            String val = normalize(all.group(1));
            if (val == null) continue;
            String mode = all.group(2);
            mode = switch (mode) {
                case "éves" -> "age";
                case "kg" -> "weight";
                case "mell" -> "breast";
                case "derék" -> "waist";
                case "csípő" -> "hips";
                case "cm" -> "height";
                default -> null;
            };
            if (mode != null) {
                try {
                    Integer mi = Integer.parseInt(val);
                    measures.put(mode, mi);
                } catch (NumberFormatException nfe) {
                    measures.put(mode, val);
                }
            }

        }
        result.put("measures", measures);
        all.appendTail(sb);
        return sb.toString();
    }

    @Override
    public ObjectNode apply(String html) {
        extra.setLength(0);
        Document doc = Jsoup.parse(html);
        ObjectNode result = Serde.OM.createObjectNode();

        Element leftContainer = doc.selectFirst("div#girlMainLeftContainer");
        Element err = doc.selectFirst(".mainError");
        if (err != null || leftContainer == null) {
            String msg = err.text();
            Matcher idm = Partner.IDP.matcher(((Comment) doc.childNode(0)).getData());
            if (idm.find() && msg.contains("Nincs") && msg.contains("adatlap")) {
                result.put("id", Integer.parseInt(idm.group(2)));
            } else {
                result = null;
                System.err.println("Failed to get: " + msg + "!");
            }
            return result;
        }
        ArrayNode propsArr = Serde.OM.createArrayNode();
        ArrayNode massageArr = Serde.OM.createArrayNode();

        String name = text(doc.selectFirst(".mainDataRow a.datasheetColorLink,div#memberReportingMain p.title"));
        result.put("name", name.replace(" - Jelentés", ""));
        name = text(
                doc.selectFirst("div#externalContainer a[href~=(member|adatlap)], form#tag_felhaszn_comment"),
                "href", "action");
        Matcher m = IDP.matcher(name);
        m.find();
        name = m.group(2);
        result.put("id", Integer.parseInt(name));

        String intro = null;
        for (Element s : doc.select("div:containsOwn(Jelige: ),div#content h1")) {
            intro = text(s);
            if (intro != null && intro.contains("Jelige")) {
                break;
            }
        }
        intro = normalize(intro);
        if (intro != null && (intro.startsWith(name) || intro.endsWith(".hu"))) intro = null;
        result.put("pass", intro);

        ArrayNode phoneArr = Serde.OM.createArrayNode();
        Elements phoneLinks = doc.select("a.phone-number");
        for (Element link : phoneLinks) {
            String txt = link.text().replaceAll("[^\\d+]", "");
            if (!txt.isEmpty()) phoneArr.add(txt);
            for (Element img : link.select("img")) {
                String alt = img.attr("src").replaceAll(".+/([a-z]+)_icon.+", "$1");
                addProp(extra, PROPS, phoneArr, null, alt);
            }
        }
        result.set("phone", phoneArr);

        Elements dataCols = leftContainer.select("div.dataSheetColumnData");
        ArrayNode location = firstData(dataCols.get(0), propsArr, result);
        if (location != null) {
            String glink = text(doc.selectFirst("div#mapsInnerContainer iframe"), "src");
            if (glink != null) {
                glink = HttpUrl.get(glink).queryParameter("q");
                if (glink.matches("[0-9.,]+")) { // geo coordinate
                    location.add(glink);
                }
            }
        }
        secondData(dataCols.get(1), propsArr, massageArr, result);

        var inact = doc.selectFirst("div#phoneMiddle div:containsOwn(Most inaktív)");
        if (inact == null) {
            quality(doc.selectFirst("div#rightVerifiedStat ul"), propsArr, result);
            Element lf = html(doc.selectFirst("div#dataLowerRight"), "⚠️");
            lf.select("h2").append("💕⚠️");
            lf.select(".dlBAnswer").append("⚠️");
            parseOutMyInfo(result, propsArr, lf.text());
            result.put("active", true);
        } else {
            inact = doc.selectFirst("div#statusContainer span:containsOwn(Legközelebb)");
            name = null;
            if (inact != null) {
                m = DATEF.matcher(inact.text());
                while (m.find()) {
                    name = m.group(0);
                }
            }
            if (name == null) {
                result.put("active", false);
            } else {
                result.put("active", name);
            }
        }

        Element logContainer = leftContainer.selectFirst("div#logContainer");
        if (logContainer != null) {
            ObjectNode activity = Serde.OM.createObjectNode();
            for (Element font : logContainer.select("font.leftLikes2")) {
                String txt = font.text().trim();
                String[] parts = txt.split(" ", 2);
                if (parts.length == 2 && parts[0].matches("^[0-9-]+")) activity.put(parts[0], parts[1]);
            }
            result.set("activity", activity);
        }

        Element imagesDiv = leftContainer.selectFirst("div#imagesDiv");
        if (imagesDiv != null) {
            ArrayNode imgsArr = Serde.OM.createArrayNode();
            for (Element imgCont : imagesDiv.select("div.imageEmelemntContainer")) {
                Element img = imgCont.selectFirst("img");
                if (img != null) {
                    String src = img.attr("src");
                    String title = img.attr("title");
                    m = Pattern.compile("Feltöltve: (" + DATEP + ")").matcher(title);
                    String date = m.find() ? m.group(1) : "";
                    ArrayNode imgData = Serde.OM.createArrayNode();
                    imgData.add(normalize(date));
                    imgData.add(src);
                    imgsArr.add(imgData);
                }
            }
            result.set("imgs", imgsArr);
        }

        Element aboutDiv = leftContainer.selectFirst("div#bemutatkozasContainer");
        if (aboutDiv != null) {
            aboutDiv.select("div,span").remove();
            String introHtml = aboutDiv.html().replaceAll("\\s+", " ").replaceAll(">\\s+<", "><").trim();
            result.put("about", introHtml);
        }

        Element likesDiv = leftContainer.selectFirst("div#dsLeftLikeContainer");
        if (likesDiv != null) {
            ObjectNode possibilitiesObj = Serde.OM.createObjectNode();
            ArrayNode yesArr = Serde.OM.createArrayNode();
            ArrayNode askArr = Serde.OM.createArrayNode();
            ArrayNode noArr = Serde.OM.createArrayNode();
            for (Element font : likesDiv.select("font")) {
                String txt = font.text().replaceAll("\\s*\\(ha megkérsz\\)", "");
                if (txt.contains(":")) {
                    int cln = txt.indexOf(':');
                    String key = Main.toEnumLike(txt.substring(0, cln));
                    result.put(key, normalize(txt.substring(cln + 1)));
                } else if (!addMassage(massageArr, txt)) {
                    ArrayNode arr = noArr;
                    if (font.hasClass("leftLikes1")) arr = yesArr;
                    else if (font.hasClass("leftLikes2")) arr = askArr;
                    addProp(extra, LIKES, arr, null, txt);
                }
            }
            possibilitiesObj.set("no", noArr);
            possibilitiesObj.set("yes", yesArr);
            possibilitiesObj.set("ask", askArr);
            result.set("likes", possibilitiesObj);
        }
        result.put("massages", massageArr);
        if (!extra.isEmpty()) {
            result.put("expect", normalize(extra.toString()));
        }
        return result;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (massage != null) return;
        try {
            massage = Entry.cfg("massage-mapping.kv");
            looking = Entry.cfg("looking-mapping.kv");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        for (Map.Entry<String, Object> me : massage.entrySet()) {
            for (String altm : String.valueOf(me.getValue()).split(",")) {
                massageReverse.setProperty(altm, me.getKey());
            }
        }
    }
}