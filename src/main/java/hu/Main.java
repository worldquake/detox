package hu;

import hu.detox.Agent;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

public class Main {
    public static ApplicationContext main(Class<?> any, String[] args) throws Exception {
        System.setProperty("root", any.getPackageName());
        Agent.init();
        SpringApplication application = new SpringApplication(any);
        if (Agent.IDE) application.setAdditionalProfiles("dev");
        return application.run(args);
    }
}
