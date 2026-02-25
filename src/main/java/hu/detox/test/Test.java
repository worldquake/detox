package hu.detox.test;

import hu.Main;
import hu.detox.Agent;
import hu.detox.io.CharIOHelper;
import hu.detox.io.poi.MatrixReader;
import hu.detox.spring.DetoxConfig;
import hu.detox.spring.GeoCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.supercsv.io.CsvListReader;

import java.io.IOException;
import java.util.List;

@SpringBootApplication
@RequiredArgsConstructor
@Import(DetoxConfig.class)
public class Test {

    private final GeoCode code;

    public static void main(String[] args) throws Exception {
        Main.main(Test.class, args);
    }

    @PostConstruct
    public void test() throws IOException {
        CsvListReader lr = MatrixReader.to(CharIOHelper.attempt(Agent.resource("aa.csv"))).getSecond();
        lr.getHeader(true);
        List<String> ln;
        while ((ln = lr.read()) != null) {
            var n = code.getLocation(ln.get(1), null);
            System.err.println(ln.get(0) + "\t" + n);
        }
    }
}
