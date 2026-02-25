package hu.detox.szexpartnerek.spring.admin;

import com.google.gson.JsonObject;
import hu.detox.szexpartnerek.sync.rl.Http;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static hu.detox.szexpartnerek.spring.SzexConfig.exec;
import static hu.detox.szexpartnerek.spring.SzexConfig.query;

@Component("geocode")
@RequiredArgsConstructor
@Import(Http.class)
public class GeoCode implements Admin {
    private final hu.detox.spring.GeoCode geo;

    @SneakyThrows
    @Override
    public void run() {
        query("SELECT rowid,CONCAT(location,'; ',location_extra) AS l FROM partner_address" +
                " WHERE json IS NULL AND location_extra<>'' LIMIT 50", rs -> {
            Map<Integer, JsonObject> map = new HashMap<>();
            while (rs.next()) {
                String name = rs.getString(2);
                var loc = geo.getLocation(name);
                if (loc == null) {
                    loc = new JsonObject();
                    loc.addProperty("name", name);
                }
                System.err.print(".");
                map.put(rs.getInt(1), loc);
            }
            return map;
        }).forEach((key, jsonObject) -> {
            exec("UPDATE partner_address SET json=? WHERE rowid=?", jsonObject.toString(), key);
        });
        System.err.println(" Geocoding done");
    }

    @Override
    public String getId() {
        return "geocode";
    }

    @Override
    public String toString() {
        return getId() + ": geocodes missing address json";
    }
}
