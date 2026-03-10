package hu.detox.szexpartnerek.spring;

import com.google.gson.JsonObject;
import hu.Main;
import hu.detox.Agent;
import hu.detox.io.CharIOHelper;
import hu.detox.io.poi.MatrixReader;
import hu.detox.spring.GeoCode;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.ContextRefreshedEvent;
import org.supercsv.io.CsvListReader;

import java.util.List;

@ComponentScan(basePackageClasses = hu.detox.szexpartnerek.Main.class)
@RequiredArgsConstructor
public class Test implements ApplicationListener<ContextRefreshedEvent> {

    private final GeoCode code;

    public static void main(String[] args) throws Exception {
        Main.main(Test.class, args);
    }

    @SneakyThrows
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        try (CharIOHelper cio = CharIOHelper.attempt(Agent.resource("../addr.txt"))) {
            CsvListReader r = MatrixReader.to(cio).getSecond();
            String[] h = r.getHeader(true);
            List<String> ln;
            while ((ln = r.read()) != null) {
                int rowid = Integer.parseInt(ln.get(0));
                JsonObject ex = code.getLocation(ln.get(1), null);
                if (ex != null) {
                    String[] lna = ln.get(1).split("; ");
                    ex.addProperty("extra", lna[1]);
                    SzexConfig.exec("UPDATE partner_address SET json=json_patch(json_object('oextra', location_extra), ?) WHERE rowid=?", ex.toString(), rowid);
                    System.err.print(rowid + ", ");
                }
            }
        }
    }
}