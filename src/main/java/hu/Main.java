package hu;

import hu.detox.Agent;
import hu.detox.config.Cfg2PropertySourceFactory;
import hu.detox.io.IOUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component(Main.BEAN_NAME)
public class Main implements ApplicationContextAware {
    public static final String BEAN_NAME = "root";
    private static ApplicationContext context;
    private static PropertyResolver resolver;
    public static final boolean IDE = Boolean.getBoolean("IDE");

    public static ApplicationContext main(Class<?> any, String[] args) throws Exception {
        return main(true, any, args);
    }

    public static ApplicationContext main(boolean ws, Class<?> any, String[] args) throws Exception {
        System.setProperty(BEAN_NAME, any.getPackageName());
        SpringApplication application = new SpringApplication(any);
        application.setWebApplicationType(ws ? WebApplicationType.SERVLET : WebApplicationType.NONE);
        if (Main.IDE) application.setAdditionalProfiles("dev");
        application.addPrimarySources(List.of(Main.class));
        return application.run(args);
    }

    public static Resource resource(String res) {
        return context.getResource(res);
    }

    public static ApplicationContext ctx() {
        return context;
    }

    public static String prop(String prop) {
        return prop(prop, null);
    }

    public static String prop(String prop, String def) {
        return resolver.getProperty(prop, def);
    }

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        context = ctx;
        if (resolver != null) return;
        Agent.start();
        resolver = ctx.getBean(PropertyResolver.class);
        File my = Agent.getFile("application.yaml", FileFilterUtils.fileFileFilter());
        if (my != null) {
            FileSystemResource propertySource = new FileSystemResource(my);
            ctx.getBean(ConfigurableEnvironment.class).getPropertySources()
                    .addFirst(Cfg2PropertySourceFactory.make(propertySource, null));
        }
        IOUtils.initStatic();
    }

}
