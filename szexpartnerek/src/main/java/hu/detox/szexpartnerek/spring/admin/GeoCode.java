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
        query("SELECT rowid,CONCAT(location,'; ',location_extra),lat,lon AS l FROM partner_address" +
                " WHERE json IS NULL AND location_extra<>'' LIMIT 500", rs -> {
            Map<Integer, JsonObject> map = new HashMap<>();
            while (rs.next()) {
                String name = rs.getString(2);
                Double lat = (Double) rs.getObject(3);
                double[] latLon = lat == null ? null : new double[]{lat, rs.getDouble(4)};
                var loc = geo.getLocation(name, latLon);
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
        query("SELECT DISTINCT location FROM partner_address" +
                " WHERE location_extra='' AND lat IS NULL LIMIT 500", rs -> {
            Map<String, JsonObject> map = new HashMap<>();
            while (rs.next()) {
                String name = rs.getString(1);
                var loc = geo.getLocation(name, null);
                if (loc == null) {
                    loc = new JsonObject();
                    loc.addProperty("name", name);
                }
                System.err.print(".");
                map.put(name, loc);
            }
            return map;
        }).forEach((key, jsonObject) -> {
            exec("UPDATE partner_address SET json=? WHERE location_extra='' AND lat IS NULL AND location=?", jsonObject.toString(), key);
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
