package hu.detox.test;

import hu.Main;
import hu.detox.spring.DetoxConfig;
import hu.detox.spring.GeoCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import java.io.IOException;

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
        System.out.println(code.getLocation("NAgy Ferenc tér 3, Pécs"));
    }
}
