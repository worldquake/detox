package hu.detox.szexpartnerek.spring;

import hu.Main;
import hu.detox.spring.GeoCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.ContextRefreshedEvent;

@ComponentScan(basePackageClasses = hu.detox.szexpartnerek.Main.class)
@RequiredArgsConstructor
public class Test implements ApplicationListener<ContextRefreshedEvent> {

    private final GeoCode code;

    public static void main(String[] args) throws Exception {
        Main.main(Test.class, args);
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
    }
}