package hu.detox.spring;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import hu.detox.io.CharIOHelper;
import hu.detox.io.poi.MatrixReader;
import hu.detox.parsers.JSonUtils;
import hu.detox.utils.Http;
import hu.detox.utils.ThreadUtils;
import hu.detox.utils.TimeUtils;
import hu.detox.utils.strings.StringUtils;
import kotlin.jvm.functions.Function2;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.supercsv.io.CsvListReader;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class GeoCode {
    private final static String BASE = "https://api.geoapify.com/v1/geocode";
    private final Http search;
    private final long[] sleepLastRun = new long[]{50, 0};

    public GeoCode(@Value("${detox.geoapify}") String apiKey) {
        search = new Http(BASE + "/search?lang=" + Locale.getDefault().getLanguage() + "&format=json&apiKey=" + apiKey);
    }

    private void makeStandard(JsonObject o) {
        JsonObject bbox = new JsonObject();
        JsonElement late = o.remove("lat");
        if (late != null) {
            JsonElement lat = late.getAsJsonPrimitive();
            JsonElement lon = o.remove("lon").getAsJsonPrimitive();
            bbox.add("lon1", lon);
            bbox.add("lon2", lon);
            bbox.add("lat1", lat);
            bbox.add("lat2", lat);
            o.add("bbox", bbox);
        }
        //o.remove("formatted");
        JsonElement tz = o.remove("timezone");
        if (tz != null) {
            o.add("tz", tz.getAsJsonObject().get("name"));
            o.remove("datasource");
            o.remove("lon");
            o.remove("lat");
            o.remove("rank");
            var on = (JsonObject) o.remove("other_names");
            if (on != null) {
                var ds = on.getAsJsonPrimitive("name:hu");
                if (ds == null) on.getAsJsonPrimitive("short_name");
                if (ds != null) o.add("name", ds);
            }
            o.remove("NUTS_2");
            o.remove("NUTS_3");
            o.remove("address_line1");
            o.remove("address_line2");
            o.remove("plus_code");
            o.remove("plus_code_short");
            o.remove("place_id");
            o.remove("iso3166_2");
        }
    }

    @SneakyThrows
    public List<JsonObject> getLocations(CharIOHelper in, Function2<List<String>, JsonObject, Boolean> filter) {
        List<JsonObject> ret = new LinkedList<>();
        try (CsvListReader mr = MatrixReader.to(in).getSecond()) {
            String[] hd = mr.getHeader(false);
            List<String> ln;
            int oh = 0;
            for (String h : hd) {
                if (!h.startsWith("original_")) break;
                oh++;
            }
            String h;
            while ((ln = mr.read()) != null) {
                JsonObject o = new JsonObject();
                for (int i = oh; i < ln.size(); i++) {
                    String v = ln.get(i);
                    h = hd[i];
                    if (StringUtils.isBlank(v) || h.startsWith("attribution") || h.startsWith("confidence")) continue;
                    o.addProperty(hd[i], v);
                }
                makeStandard(o);
                if (filter != null && filter.invoke(ln, o)) continue;
                ret.add(o);
            }
        }
        return ret;
    }

    @SneakyThrows
    public JsonObject getLocation(String q) {
        long lr = TimeUtils.time();
        if (lr - sleepLastRun[1] < sleepLastRun[0])
            ThreadUtils.sleep(sleepLastRun[0], TimeUnit.MILLISECONDS);
        sleepLastRun[1] = lr;
        JsonArray arr = JSonUtils.JSON_PARSER.parseString(search.get("?text={q}", q).getBody())
                .getAsJsonObject().get("results").getAsJsonArray();
        if (arr.isEmpty()) return null;
        JsonObject o = arr.get(0).getAsJsonObject();
        makeStandard(o);
        return o;
    }
}
